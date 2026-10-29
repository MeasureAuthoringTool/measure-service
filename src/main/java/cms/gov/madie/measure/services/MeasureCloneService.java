package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.resources.DuplicateKeyException;
import cms.gov.madie.measure.utils.CloneNamingUtil;
import cms.gov.madie.measure.utils.MeasureServiceUtil;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.ReviewMetaData;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

/**
 * Clones a measure into a new draft measure in a new measure set. Everything is copied except the
 * names and identifiers, so the clone is not linked to the original.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeasureCloneService {
  static final int MAX_CLONE_NUMBER = 100;

  private final MeasureRepository measureRepository;
  private final MeasureService measureService;
  private final MeasureSetService measureSetService;
  private final VersionService versionService;
  private final ActionLogService actionLogService;
  private final CompositeRelationshipService compositeRelationshipService;

  public Measure cloneMeasure(String measureId, String username) {
    // becomes the clone in place and is inserted as a new document; the original is never saved
    Measure clone =
        measureRepository
            .findByIdAndActive(measureId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Measure", measureId));
    String originalCqlLibraryName = clone.getCqlLibraryName();

    assignCloneNames(clone);
    resetToNewDraft(clone, username);
    Map<String, String> newGroupIdsByOldId = regenerateGroupIds(clone);
    regenerateTestCaseIds(clone, newGroupIdsByOldId);
    clone = renameCqlLibrary(clone, originalCqlLibraryName);

    Measure savedClone = measureRepository.save(clone);
    log.info(
        "User [{}] cloned measure with id [{}] into new measure with id [{}]",
        username,
        measureId,
        savedClone.getId());

    // case numbering is keyed by measure id, so it needs the saved clone
    if (CollectionUtils.isNotEmpty(savedClone.getTestCases())) {
      savedClone = versionService.initCaseNumbering(savedClone, savedClone.getTestCases());
    }

    actionLogService.logAction(savedClone.getId(), Measure.class, ActionType.CREATED, username);
    measureSetService.createMeasureSet(
        username, savedClone.getId(), savedClone.getMeasureSetId(), null);
    compositeRelationshipService.addCompositeRelationships(savedClone, username);
    return savedClone;
  }

  /** Uses the lowest number free for both the measure name and the CQL library name. */
  private void assignCloneNames(Measure clone) {
    for (int cloneNumber = 1; cloneNumber <= MAX_CLONE_NUMBER; cloneNumber++) {
      String cqlLibraryName =
          CloneNamingUtil.cqlLibraryName(cloneNumber, clone.getCqlLibraryName());
      String measureName = CloneNamingUtil.measureName(cloneNumber, clone.getMeasureName());

      if (!measureRepository.existsByCqlLibraryNameAndActive(cqlLibraryName, true)
          && !measureRepository.existsByMeasureNameAndActive(measureName, true)) {
        clone.setCqlLibraryName(cqlLibraryName);
        clone.setMeasureName(measureName);
        clone.setEcqmTitle(CloneNamingUtil.ecqmTitle(clone.getEcqmTitle()));
        return;
      }
    }
    throw new DuplicateKeyException(
        "measureName",
        String.format(
            "Could not find an unused clone name for measure %s after %d attempts.",
            clone.getMeasureName(), MAX_CLONE_NUMBER));
  }

  private void resetToNewDraft(Measure clone, String username) {
    Instant now = Instant.now();
    clone.setId(null);
    clone.setVersionId(UUID.randomUUID().toString());
    clone.setMeasureSetId(UUID.randomUUID().toString());
    clone.setVersion(new Version(0, 0, 0));
    clone.setCreatedBy(username);
    clone.setCreatedAt(now);
    clone.setLastModifiedBy(username);
    clone.setLastModifiedAt(now);
    clone.getMeasureMetaData().setDraft(true);
    clone.getMeasureMetaData().setVersionDate(null);
    clone.setReviewMetaData(new ReviewMetaData());
    // the clone is not a component of any composite measure
    clone.setCompositeMeasureIds(null);
  }

  /** Group ids must be unique across all measures. */
  private Map<String, String> regenerateGroupIds(Measure clone) {
    Map<String, String> newGroupIdsByOldId = new HashMap<>();
    if (clone.getGroups() != null) {
      clone
          .getGroups()
          .forEach(
              group -> {
                String newGroupId = ObjectId.get().toString();
                newGroupIdsByOldId.put(group.getId(), newGroupId);
                group.setId(newGroupId);
              });
    }
    return newGroupIdsByOldId;
  }

  private void regenerateTestCaseIds(Measure clone, Map<String, String> newGroupIdsByOldId) {
    if (clone.getTestCases() == null) {
      return;
    }
    boolean isQdm = ModelType.QDM_5_6.getValue().equalsIgnoreCase(clone.getModel());
    clone
        .getTestCases()
        .forEach(
            testCase -> {
              testCase.setId(ObjectId.get().toString());
              // test case set ids are per measure set (QDM test cases have none)
              if (!isQdm) {
                testCase.setTestCaseSetId(UUID.randomUUID());
              }
              // remap by id, not position: a test case need not cover every group
              if (testCase.getGroupPopulations() != null) {
                testCase
                    .getGroupPopulations()
                    .forEach(
                        groupPopulation ->
                            groupPopulation.setGroupId(
                                newGroupIdsByOldId.getOrDefault(
                                    groupPopulation.getGroupId(), groupPopulation.getGroupId())));
              }
            });
  }

  /**
   * Renames the CQL library statement to the clone's library and re-translates the CQL. CQL that
   * doesn't declare the original library is left as is.
   */
  private Measure renameCqlLibrary(Measure clone, String originalCqlLibraryName) {
    if (StringUtils.isBlank(clone.getCql())) {
      return clone;
    }
    Matcher libraryStatement =
        Pattern.compile(
                "\\blibrary\\s+" + Pattern.quote(originalCqlLibraryName) + "\\s+version\\s+'[^']*'")
            .matcher(clone.getCql());
    if (!libraryStatement.find()) {
      return clone;
    }
    clone.setCql(
        libraryStatement.replaceFirst(
            Matcher.quoteReplacement(
                MeasureServiceUtil.generateLibraryContentLine(
                    clone.getCqlLibraryName(), clone.getVersion()))));
    return measureService.revalidateCqlDependencies(clone);
  }
}
