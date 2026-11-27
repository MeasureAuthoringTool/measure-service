package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.InvalidIdException;
import cms.gov.madie.measure.exceptions.InvalidRequestException;
import cms.gov.madie.measure.repositories.TestCasePatchRepository;
import cms.gov.madie.measure.utils.DraftValueSetOverrideUtil;
import gov.cms.madie.models.measure.DraftValueSetOverride;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCaseConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Maintains the list of value sets for which draft content should be included when a measure's
 * value set expansions are retrieved for a specific manifest.
 *
 * <p>The list is held on the measure's test case configuration, so it belongs to a single measure
 * version and is carried along when a measure is versioned, drafted or cloned. Only the measure
 * owner or a user the measure is shared with may change it; it is readable by any user who can see
 * the measure. Like the rest of the test case configuration, it may be changed on both draft and
 * versioned measures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftValueSetOverrideService {

  private final MeasureService measureService;
  private final TestCasePatchRepository testCasePatchRepository;

  /** Returns the value sets currently on the override list for the given measure version. */
  public List<DraftValueSetOverride> getDraftValueSetOverrides(String measureId) {
    Measure measure = findMeasure(measureId);
    return getOverrides(measure);
  }

  /**
   * Adds a value set to the override list. A value set already on the list is not added a second
   * time, so repeating the request leaves the list unchanged.
   *
   * @return the override list as it stands after the request
   */
  public List<DraftValueSetOverride> addDraftValueSetOverride(
      String measureId, DraftValueSetOverride draftValueSetOverride, String username) {
    Measure measure = findMeasure(measureId);
    measureService.verifyAuthorization(username, measure);

    DraftValueSetOverride override = normalize(draftValueSetOverride);
    String identifier = DraftValueSetOverrideUtil.getValueSetIdentifier(override);
    List<DraftValueSetOverride> overrides = new ArrayList<>(getOverrides(measure));

    if (containsValueSet(overrides, identifier)) {
      log.info(
          "Value set [{}] is already on the draft value set override list of Measure [{}], so it"
              + " was not added again by user [{}]",
          identifier,
          measureId,
          username);
      return overrides;
    }

    overrides.add(override);
    log.info(
        "User [{}] added value set [{}] to the draft value set override list of Measure [{}]",
        username,
        identifier,
        measureId);
    return save(measureId, overrides);
  }

  /**
   * Removes a value set from the override list, identified by its OID or its url. Removing a value
   * set that is not on the list leaves the list unchanged.
   *
   * @return the override list as it stands after the request
   */
  public List<DraftValueSetOverride> removeDraftValueSetOverride(
      String measureId, String oid, String url, String username) {
    Measure measure = findMeasure(measureId);
    measureService.verifyAuthorization(username, measure);

    String identifier =
        DraftValueSetOverrideUtil.getValueSetIdentifier(
            DraftValueSetOverride.builder()
                .oid(StringUtils.trimToNull(oid))
                .url(StringUtils.trimToNull(url))
                .build());
    if (identifier == null) {
      throw new InvalidRequestException(
          "A draft value set override is identified by an OID or a url, neither of which was"
              + " provided.");
    }

    List<DraftValueSetOverride> overrides = new ArrayList<>(getOverrides(measure));
    boolean removed =
        overrides.removeIf(
            existing ->
                identifier.equals(DraftValueSetOverrideUtil.getValueSetIdentifier(existing)));
    if (!removed) {
      log.info(
          "Value set [{}] is not on the draft value set override list of Measure [{}], so nothing"
              + " was removed for user [{}]",
          identifier,
          measureId,
          username);
      return overrides;
    }

    log.info(
        "User [{}] removed value set [{}] from the draft value set override list of Measure [{}]",
        username,
        identifier,
        measureId);
    return save(measureId, overrides);
  }

  private Measure findMeasure(String measureId) {
    if (StringUtils.isBlank(measureId)) {
      log.error("No measure ID was provided for a draft value set override request");
      throw new InvalidIdException("Measure", "Draft Value Set Override");
    }
    return measureService.findActiveMeasureById(measureId);
  }

  private List<DraftValueSetOverride> getOverrides(Measure measure) {
    TestCaseConfiguration testCaseConfiguration = measure.getTestCaseConfiguration();
    return testCaseConfiguration == null
        ? List.of()
        : List.copyOf(ListUtils.emptyIfNull(testCaseConfiguration.getDraftValueSetOverrides()));
  }

  /** Trims the override and rejects one that does not identify a value set. */
  private DraftValueSetOverride normalize(DraftValueSetOverride draftValueSetOverride) {
    if (draftValueSetOverride == null) {
      throw new InvalidRequestException("Draft value set override cannot be null");
    }
    DraftValueSetOverride override =
        DraftValueSetOverride.builder()
            .oid(
                DraftValueSetOverrideUtil.removeUrnOidPrefix(
                    StringUtils.trimToNull(draftValueSetOverride.getOid())))
            .url(StringUtils.trimToNull(draftValueSetOverride.getUrl()))
            .name(StringUtils.trimToNull(draftValueSetOverride.getName()))
            .build();
    if (DraftValueSetOverrideUtil.getValueSetIdentifier(override) == null) {
      throw new InvalidRequestException(
          "A draft value set override requires an OID or a url for the value set.");
    }
    return override;
  }

  private boolean containsValueSet(List<DraftValueSetOverride> overrides, String identifier) {
    return overrides.stream()
        .map(DraftValueSetOverrideUtil::getValueSetIdentifier)
        .filter(Objects::nonNull)
        .anyMatch(identifier::equals);
  }

  private List<DraftValueSetOverride> save(
      String measureId, List<DraftValueSetOverride> overrides) {
    Measure updatedMeasure =
        testCasePatchRepository.findAndModifyDraftValueSetOverrides(overrides, measureId);
    return updatedMeasure == null ? List.copyOf(overrides) : getOverrides(updatedMeasure);
  }
}
