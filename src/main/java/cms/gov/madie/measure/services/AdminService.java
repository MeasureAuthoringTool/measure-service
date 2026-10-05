package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.DeleteMeasuresByOwnersResult;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.ActionLogRepositoryImpl;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.repositories.MeasureLockRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.repositories.TestCaseLockRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import gov.cms.madie.models.measure.TestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Service
public class AdminService {
  private final MeasureService measureService;
  private final MeasureRepository measureRepository;
  private final MeasureSetRepository measureSetRepository;
  private final ExportRepository exportRepository;
  private final ActionLogRepositoryImpl actionLogRepository;
  private final MongoGridFsService mongoGridFsService;
  private final MeasureLockRepository measureLockRepository;
  private final TestCaseLockRepository testCaseLockRepository;

  /**
   * Hard deletes everything MADiE holds for the given owners: their measure sets, every measure in
   * those sets (test cases ride along, being embedded in the measure document), the exports for
   * those measures along with the GridFS blobs the exports point at, the measure, measure set and
   * test case action logs for exactly those records, and any measure or test case locks held on
   * those measures.
   */
  public DeleteMeasuresByOwnersResult deleteMeasuresByOwners(
      List<String> harpIds, String username) {
    if (CollectionUtils.isEmpty(harpIds)) {
      throw new InvalidRequestException("At least one HARP ID must be provided");
    }

    List<String> owners =
        harpIds.stream()
            .filter(StringUtils::isNotBlank)
            .map(harpId -> harpId.trim().toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
    if (owners.isEmpty()) {
      throw new InvalidRequestException("At least one HARP ID must be provided");
    }

    log.info("Admin [{}] is hard deleting all measures owned by {}", username, owners);

    List<MeasureSet> measureSets = measureSetRepository.findAllByOwnerIn(owners);
    if (CollectionUtils.isEmpty(measureSets)) {
      log.info("No measure sets found for owners {}; nothing to delete", owners);
      return DeleteMeasuresByOwnersResult.builder().harpIds(owners).build();
    }

    List<String> measureSetIds = measureSets.stream().map(MeasureSet::getMeasureSetId).toList();
    List<Measure> measures = measureRepository.findByMeasureSetIdIn(measureSetIds);
    List<String> measureIds = measures.stream().map(Measure::getId).toList();
    // Test cases ids to clear the separate testCaseActionLog collection.
    List<String> testCaseIds =
        measures.stream()
            .map(Measure::getTestCases)
            .filter(CollectionUtils::isNotEmpty)
            .flatMap(List::stream)
            .map(TestCase::getId)
            .filter(Objects::nonNull)
            .toList();

    List<Export> exports =
        measureIds.isEmpty() ? List.of() : exportRepository.findAllByMeasureIdIn(measureIds);
    List<String> gridFsIds =
        exports.stream()
            .flatMap(
                export ->
                    Stream.of(
                        export.getMeasureBundleGridFsId(),
                        export.getMeasureBundleWithoutWarningsGridFsId()))
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toList();

    DeleteMeasuresByOwnersResult.DeleteMeasuresByOwnersResultBuilder result =
        DeleteMeasuresByOwnersResult.builder()
            .harpIds(owners)
            .measureSetCount(measureSets.size())
            .measureCount(measures.size())
            .exportCount(exports.size())
            .exportGridFsFileCount(gridFsIds.size())
            .measureSetIds(measureSetIds)
            .measureIds(measureIds);

    gridFsIds.forEach(mongoGridFsService::deleteById);
    if (CollectionUtils.isNotEmpty(exports)) {
      exportRepository.deleteAll(exports);
    }

    long measureActionLogCount =
        actionLogRepository.deleteActionLogsByTargetIds(measureIds, Measure.class);
    long measureSetActionLogCount =
        actionLogRepository.deleteActionLogsByTargetIds(measureSetIds, MeasureSet.class);
    long testCaseActionLogCount =
        actionLogRepository.deleteActionLogsByTargetIds(testCaseIds, TestCase.class);

    long measureLockCount = 0L;
    long testCaseLockCount = 0L;
    if (CollectionUtils.isNotEmpty(measureIds)) {
      measureLockCount = measureLockRepository.deleteByMeasureIdIn(measureIds);
      testCaseLockCount = testCaseLockRepository.deleteByMeasureIdIn(measureIds);
    }

    if (CollectionUtils.isNotEmpty(measures)) {
      measureRepository.deleteAll(measures);
    }
    measureSetRepository.deleteAll(measureSets);

    log.info(
        "Admin [{}] hard deleted for owners {}: {} measure set(s), {} measure(s), "
            + "{} export(s), {} GridFS file(s), {} measure action log(s), {} measure set action "
            + "log(s), {} test case action log(s), {} measure lock(s), {} test case lock(s). "
            + "Measure ids: {}",
        username,
        owners,
        measureSets.size(),
        measures.size(),
        exports.size(),
        gridFsIds.size(),
        measureActionLogCount,
        measureSetActionLogCount,
        testCaseActionLogCount,
        measureLockCount,
        testCaseLockCount,
        measureIds);

    return result
        .measureActionLogCount(measureActionLogCount)
        .measureSetActionLogCount(measureSetActionLogCount)
        .testCaseActionLogCount(testCaseActionLogCount)
        .measureLockCount(measureLockCount)
        .testCaseLockCount(testCaseLockCount)
        .build();
  }

  public List<Integer> updateCodeSystem(
      String id, String username, String incorrectCodeSystem, String correctCodeSystem) {
    Measure targetMeasure = measureService.findMeasureById(id);

    if (StringUtils.isBlank(incorrectCodeSystem) || StringUtils.isBlank(correctCodeSystem)) {
      throw new InvalidRequestException(
          "Please provide both incorrect and correct code system values");
    }

    if (targetMeasure == null) {
      throw new ResourceNotFoundException("Measure", id);
    }

    if (!Objects.equals(targetMeasure.getModel(), ModelType.QDM_5_6.getValue())) {
      log.info(
          "Measure with id: "
              + id
              + " is not a QDM measure. No HCPCSReleaseCodeSets updates made.");
      throw new InvalidRequestException(
          "Measure is not a QDM measure. No code system updates made.");
    }

    List<Integer> caseNumbers = new ArrayList<>();
    targetMeasure
        .getTestCases()
        .forEach(
            testCase -> {
              String updatedJson =
                  testCase.getJson().replace(incorrectCodeSystem.trim(), correctCodeSystem.trim());
              if (!Objects.equals(testCase.getJson(), updatedJson)) {
                log.info(
                    "{} is updating the code system in the test case with id: {}",
                    username,
                    testCase.getId());
                testCase.setJson(updatedJson);
                caseNumbers.add(testCase.getCaseNumber());
              }
            });

    if (CollectionUtils.isNotEmpty(caseNumbers)) {
      measureRepository.save(targetMeasure);
    }
    return caseNumbers;
  }

  public Measure backfillTestCaseSetIds(Measure measure, String userName) {
    List<TestCase> testCases = measure.getTestCases();

    if (CollectionUtils.isEmpty(testCases)) {
      throw new InvalidResourceStateException("Test cases cannot be empty or null");
    }

    boolean measureHasTestCaseSetId =
        testCases.stream().anyMatch(tc -> tc.getTestCaseSetId() != null);
    if (measureHasTestCaseSetId) {
      throw new TestCaseSetIdsAlreadyAssignedException(
          "One or more test cases already have a testCaseSetId.");
    }

    boolean measureSetHasTestCaseSetId =
        measureRepository.testCaseSetIdExistsInSet(measure.getMeasureSetId());

    if (measureSetHasTestCaseSetId) {
      throw new UnsupportedTypeException(
          "One or more test cases in this measure set already have a testCaseSetId.");
    }

    testCases.forEach(tc -> tc.setTestCaseSetId(UUID.randomUUID()));
    return measureRepository.save(measure);
  }
}
