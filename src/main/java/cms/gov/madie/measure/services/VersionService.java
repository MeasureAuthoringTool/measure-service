package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.dto.MadieFeatureFlag;
import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.CqmMeasureRepository;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.TestCaseServiceUtil;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.cqm.CqmMeasure;
import gov.cms.madie.models.measure.*;
import gov.cms.madie.packaging.utils.PackagingUtility;
import gov.cms.madie.packaging.utils.PackagingUtilityFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cms.gov.madie.measure.utils.JsonUtil.convertDateTimeToUTC;

@Slf4j
@AllArgsConstructor
@Service
public class VersionService {

  private final ActionLogService actionLogService;
  private final MeasureRepository measureRepository;
  private final ElmTranslatorClient elmTranslatorClient;
  private final FhirServicesClient fhirServicesClient;
  private final ExportRepository exportRepository;
  private final CqmMeasureRepository cqmMeasureRepository;
  private final MeasureService measureService;
  private final QdmPackageService qdmPackageService;
  private final TestCaseSequenceService sequenceService;
  private final ElmToJsonService elmToJsonService;
  private final MongoGridFsService mongoGridFsService;
  private final AppConfigService appConfigService;
  private final TestCaseValidationService testCaseValidationService;
  private final MeasureLockService measureLockService;
  private final TestCaseLockService testCaseLockService;

  public enum VersionValidationResult {
    VALID,
    TEST_CASE_ERROR
  }

  private static final String VERSION_TYPE_MAJOR = "MAJOR";
  private static final String VERSION_TYPE_MINOR = "MINOR";
  private static final String VERSION_TYPE_PATCH = "PATCH";

  public VersionValidationResult checkValidVersioning(
      String id, String versionType, String username, String accessToken) {
    Measure measure = validateVersionOptions(id, versionType, username, accessToken);

    //    if test cases are invalid but no exception has been thrown the versioning may continue.
    if (measure.getTestCases() != null
        && measure.getTestCases().stream().anyMatch(p -> !p.isValidResource())) {
      log.warn(
          "User [{}] attempted to version measure with id [{}] which has invalid test cases",
          username,
          measure.getId());
      return VersionValidationResult.TEST_CASE_ERROR;
    }
    return VersionValidationResult.VALID;
  }

  public Measure createVersion(String id, String versionType, String username, String accessToken) {
    Measure measure = validateVersionOptions(id, versionType, username, accessToken);

    if (appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)) {
      return checkLockAndVersionMeasure(versionType, username, accessToken, measure);
    } else {
      return versionMeasure(measure, versionType, username, accessToken);
    }
  }

  private Measure versionMeasure(
      Measure measure, String versionType, String username, String accessToken) {
    if (measure instanceof FhirMeasure) {
      return versionFhirMeasure(versionType, username, accessToken, measure);
    }
    return versionQdmMeasure(versionType, username, measure, accessToken);
  }

  private Measure checkLockAndVersionMeasure(
      String versionType, String username, String accessToken, Measure measure) {
    LockInfo lock = measureLockService.lockMeasure(measure.getId(), username);
    boolean isAnyTestCaseLocked =
        testCaseLockService.isAnyTestCaseLockedByOthers(measure.getId(), username);
    if (lock.isLocked() && !username.equals(lock.getLockedBy()) && !isAnyTestCaseLocked) {
      log.info(
          "user: [{}] can't version Measure: [{}], because measure is locked by: [{}]",
          username,
          measure.getId(),
          lock.getLockedBy());
      throw new LockNotObtainedException(
          "Unable to version measure. Locked while being edited by " + lock.getLockedBy());
    }

    if (isAnyTestCaseLocked) {
      log.info(
          "user: [{}] can't de-activate Measure: [{}], because one or more test cases are locked by other users",
          username,
          measure.getId());
      measureLockService.unlockMeasure(measure.getId(), username);
      throw new LockNotObtainedException(
          "Unable to version measure. One or more test cases are locked by another user.");
    } else {
      // no lock on measure and no locks on any test cases, version is ok
      measure = versionMeasure(measure, versionType, username, accessToken);
      measureLockService.unlockMeasure(measure.getId(), username);
      return measure;
    }
  }

  /**
   * @param versionType - Major, Minor or Patch Version
   * @param username - Harp User Name
   * @param measure - Draft Measure
   * @param accessToken - accessToken
   * @return Versioned Measure Generates a measurePackage that includes ELM Warning Annotations ( if
   *     available ) and also a publishableMeasurePackage which does not include ELM Warnings and
   *     saves both copies of exportPackage for future exports
   */
  private Measure versionQdmMeasure(
      String versionType, String username, Measure measure, String accessToken) {
    Measure upversionedMeasure = version(versionType, username, measure);

    PackageDto measurePackage =
        qdmPackageService.createNewMeasurePackage(upversionedMeasure, accessToken, true);
    PackageDto publishableMeasurePackage =
        qdmPackageService.createNewMeasurePackage(upversionedMeasure, accessToken, false);

    String humanReadable =
        qdmPackageService.getHumanReadable(upversionedMeasure, username, accessToken);
    // save exports
    savePackageData(
        upversionedMeasure, measurePackage, publishableMeasurePackage, humanReadable, username);
    // convert to CqmMeasure and save it
    CqmMeasure cqmMeasure = qdmPackageService.convertCqm(upversionedMeasure, accessToken);
    cqmMeasureRepository.save(cqmMeasure);

    return applyMeasureVersion(versionType, username, upversionedMeasure);
  }

  /**
   * Measure Versioning: 1. Apply the version operation to the measure 2. Generate FHIR bundles with
   * updated measure version info 3. Persist the measure bundles to the exports/gridFs collections
   * 4. Persist the up-versioned measure to the measure collection
   *
   * @param versionType - Major, Minor or Patch Version
   * @param username - Harp User Name
   * @param accessToken - accessToken
   * @param measure - Draft Measure to be versioned
   * @return Versioned Measure
   */
  private Measure versionFhirMeasure(
      String versionType, String username, String accessToken, Measure measure) {
    Measure upversionedMeasure = version(versionType, username, measure);

    // Generate Bundle for versioned Measure with ELM at error severity Info
    elmToJsonService.retrieveElmJson(measure, "Info", accessToken);
    var measureBundle =
        fhirServicesClient.getMeasureBundle(upversionedMeasure, accessToken, "export", "Info");

    // Generate Bundle for versioned Measure with ELM at error severity Error
    elmToJsonService.retrieveElmJson(measure, "Error", accessToken);
    var measureBundleWithoutWarnings =
        fhirServicesClient.getMeasureBundle(upversionedMeasure, accessToken, "export", "Error");

    saveMeasureBundle(upversionedMeasure, measureBundle, measureBundleWithoutWarnings, username);
    return applyMeasureVersion(versionType, username, upversionedMeasure);
  }

  private Measure version(String versionType, String username, Measure measure) {
    Measure upversionedMeasure = measure.toBuilder().build();
    upversionedMeasure.getMeasureMetaData().setDraft(false);
    upversionedMeasure.getMeasureMetaData().setVersionDate(Instant.now());
    upversionedMeasure.setLastModifiedAt(Instant.now());
    upversionedMeasure.setLastModifiedBy(username);
    Version oldVersion = upversionedMeasure.getVersion();
    Version newVersion = getNextVersion(upversionedMeasure, versionType);
    upversionedMeasure.setVersion(newVersion);
    if (!CollectionUtils.isEmpty(upversionedMeasure.getTestCases())) {
      upversionedMeasure
          .getTestCases()
          .forEach(testCase -> testCase.setCreatedBeforeVersioning(true));
    }
    String newCql =
        upversionedMeasure
            .getCql()
            .replace(
                generateLibraryContentLine(upversionedMeasure.getCqlLibraryName(), oldVersion),
                generateLibraryContentLine(upversionedMeasure.getCqlLibraryName(), newVersion));
    upversionedMeasure.setCql(newCql);
    return upversionedMeasure;
  }

  private Measure applyMeasureVersion(
      String versionType, String username, Measure upversionedMeasure) {
    Measure savedMeasure = measureRepository.save(upversionedMeasure);
    actionLogService.logAction(
        upversionedMeasure.getId(),
        Measure.class,
        VERSION_TYPE_MAJOR.equalsIgnoreCase(versionType)
            ? ActionType.VERSIONED_MAJOR
            : (VERSION_TYPE_MINOR.equalsIgnoreCase(versionType)
                ? ActionType.VERSIONED_MINOR
                : ActionType.VERSIONED_REVISIONNUMBER),
        username,
        String.format("Versioned to %s", upversionedMeasure.getVersion()));
    log.info(
        "User [{}] successfully versioned measure with ID [{}]", username, savedMeasure.getId());
    return savedMeasure;
  }

  private Measure validateVersionOptions(
      String id, String versionType, String username, String accessToken) {
    Measure measure = measureService.findMeasureById(id);
    if (measure == null) {
      throw new ResourceNotFoundException("Measure", id);
    }

    if (!VERSION_TYPE_MAJOR.equalsIgnoreCase(versionType)
        && !VERSION_TYPE_MINOR.equalsIgnoreCase(versionType)
        && !VERSION_TYPE_PATCH.equalsIgnoreCase(versionType)) {
      log.error(
          "User [{}] attempted to version measure with id [{}] with an invalid version type"
              + " [{}]",
          username,
          measure.getId(),
          versionType);
      throw new BadVersionRequestException(
          "Measure", measure.getId(), username, "Invalid version type received.");
    }
    measureService.verifyAuthorization(username, measure);
    validateMeasureForVersioning(measure, username, accessToken);
    return measure;
  }

  public Measure createDraft(
      String id, String measureName, String model, String username, String accessToken) {
    Measure measure =
        measureRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Measure", id));

    measureService.verifyAuthorization(username, measure);

    if (measure.getMeasureMetaData() == null || measure.getMeasureMetaData().isDraft()) {
      throw new MeasureNotDraftableException(
          measure.getMeasureName(), "Only versioned measure can be drafted.");
    }
    if (!isDraftable(measure)) {
      throw new MeasureNotDraftableException(
          measure.getMeasureName(), "Only one draft is permitted per measure.");
    }

    if (!isValidQiCoreWithNoOtherQiCoreVersion(measure)) {
      throw new MeasureNotDraftableException(
          measure.getMeasureName(),
          "You cannot draft a "
              + measure.getModel()
              + " measure when a newer version is available.");
    }

    if (!isValidDraftableVersion(measure, model)) {
      throw new MeasureNotDraftableException(
          measure.getMeasureName(),
          "You cannot draft a " + measure.getModel() + " measure to a " + model + " measure.");
    }

    Measure measureDraft = measure.toBuilder().build();
    measureDraft.setId(null);
    measureDraft.setVersionId(UUID.randomUUID().toString());
    measureDraft.setMeasureName(measureName);
    if (!model.equals(measure.getModel())) {
      measureDraft.setModel(model);
      measureDraft.setCql(updateUsingStatement(model, measure.getCql()));
    }

    measureDraft.getMeasureMetaData().setDraft(true);
    measureDraft.getMeasureMetaData().setVersionDate(null);
    measureDraft.setGroups(cloneMeasureGroups(measure.getGroups()));
    measureDraft.setReviewMetaData(new ReviewMetaData());

    measureDraft.setTestCases(cloneTestCases(measure, measureDraft.getGroups(), accessToken));
    var now = Instant.now();
    measureDraft.setCreatedAt(now);
    measureDraft.setLastModifiedAt(now);
    measureDraft.setCreatedBy(username);
    Measure savedDraft = measureRepository.save(measureDraft);
    log.info(
        "User [{}] created a draft for measure with id [{}]. Draft id is [{}]",
        username,
        measure.getId(),
        savedDraft.getId());

    // need to generate sequence AFTER measure is created with the new measure id
    if (!CollectionUtils.isEmpty(savedDraft.getTestCases())) {
      if (!checkCaseNumberExists(measure.getTestCases())) {
        savedDraft.setTestCases(
            assignCaseNumbersWhenCaseNumbersNotExist(
                savedDraft.getTestCases(), savedDraft.getId()));
        savedDraft = measureRepository.save(savedDraft);
      } else {
        sequenceService.setSequence(
            savedDraft.getId(),
            findHighestCaseNumberWhenCaseNumbersExist(savedDraft.getTestCases()));
      }

      if (!measure.getModel().equalsIgnoreCase(ModelType.QDM_5_6.getValue())
          && !measure.getModel().equals(model)
          && appConfigService.isFlagEnabled(MadieFeatureFlag.STU_6_TEST_CASE_VALIDATION)) {
        for (TestCase testCase : savedDraft.getTestCases()) {
          testCaseValidationService.validateResourceAsynchronously(
              savedDraft, testCase, TestCaseServiceUtil.SAVE, accessToken);
        }
      }
    }

    actionLogService.logAction(
        savedDraft.getId(),
        Measure.class,
        ActionType.DRAFTED,
        username,
        String.format("Draft created from version %s", measure.getVersion()));

    return savedDraft;
  }

  private String updateUsingStatement(String model, String cql) {
    Pattern qicorePattern = Pattern.compile("using QICore .*version '[0-9]\\.[0-9](\\.[0-9])?'");
    Matcher matcher = qicorePattern.matcher(cql);
    if (matcher.find()) {
      cql =
          matcher.replaceAll(
              "using QICore version '" + model.substring(model.lastIndexOf("v") + 1) + "'");
    }
    return cql;
  }

  private List<Group> cloneMeasureGroups(List<Group> groups) {
    if (!CollectionUtils.isEmpty(groups)) {
      return groups.stream()
          .map(group -> group.toBuilder().id(ObjectId.get().toString()).build())
          .collect(Collectors.toList());
    }
    return List.of();
  }

  private List<TestCase> cloneTestCases(
      Measure currentMeasure, List<Group> draftGroups, String accessToken) {
    List<TestCase> testCases = currentMeasure.getTestCases();
    if (CollectionUtils.isEmpty(testCases)) {
      return List.of();
    }
    return testCases.stream()
        .map(
            testCase -> {
              AtomicInteger indexHolder = new AtomicInteger();
              List<TestCaseGroupPopulation> updatedTestCaseGroupPopulations =
                  Optional.ofNullable(testCase.getGroupPopulations()).orElse(List.of()).stream()
                      .map(
                          testCaseGroupPopulation ->
                              testCaseGroupPopulation.toBuilder()
                                  .groupId(draftGroups.get(indexHolder.getAndIncrement()).getId())
                                  .build())
                      .toList();

              if (ModelType.QDM_5_6.getValue().equalsIgnoreCase(currentMeasure.getModel())) {
                return testCase.toBuilder()
                    .id(ObjectId.get().toString())
                    .groupPopulations(updatedTestCaseGroupPopulations)
                    .build();
              } else {
                testCase.setJson(convertDateTimeToUTC(testCase.getJson()));
              }

              return testCase.toBuilder()
                  .id(ObjectId.get().toString())
                  .groupPopulations(updatedTestCaseGroupPopulations)
                  .build();
            })
        .collect(Collectors.toList());
  }

  /** Returns false if there is already a draft for the measure family. */
  private boolean isDraftable(Measure measure) {
    return !measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
        measure.getMeasureSetId(), true, true);
  }

  /**
   * Returns false if an older QI-Core versioned measure with another newer versioned measure in the
   * measure set
   */
  private boolean isValidQiCoreWithNoOtherQiCoreVersion(Measure measure) {
    if (ModelType.QI_CORE.getValue().equals(measure.getModel())) {
      List<Measure> measures =
          measureRepository.findByMeasureSetIdAndModelInAndMeasureMetaDataDraft(
              measure.getMeasureSetId(),
              List.of(ModelType.QI_CORE_6_0_0.getValue(), ModelType.QI_CORE_7_0_0.getValue()),
              false);
      return CollectionUtils.isEmpty(measures);
    } else if (ModelType.QI_CORE_6_0_0.getValue().equals(measure.getModel())) {
      List<Measure> measures =
          measureRepository.findByMeasureSetIdAndModelInAndMeasureMetaDataDraft(
              measure.getMeasureSetId(), List.of(ModelType.QI_CORE_7_0_0.getValue()), false);
      return CollectionUtils.isEmpty(measures);
    }
    return true;
  }

  /** Returns false if a newer QI-Core versioned measure is drafted with an older model version */
  private boolean isValidDraftableVersion(Measure measure, String model) {
    boolean valid = true;
    if (ModelType.QI_CORE_6_0_0.getValue().equals(measure.getModel())
        && ModelType.QI_CORE.getValue().equals(model)) {
      valid = false;
    } else if (ModelType.QI_CORE_7_0_0.getValue().equals(measure.getModel())
        && (ModelType.QI_CORE_6_0_0.getValue().equals(model)
            || ModelType.QI_CORE.getValue().equals(model))) {
      valid = false;
    }
    return valid;
  }

  private void validateMeasureForVersioning(Measure measure, String username, String accessToken) {
    if (!measure.getMeasureMetaData().isDraft()) {
      log.error(
          "User [{}] attempted to version measure with id [{}] which is not in a draft state",
          username,
          measure.getId());
      throw new BadVersionRequestException(
          "Measure", measure.getId(), username, "Measure is not in a draft state.");
    }
    if (measure.isCqlErrors()) {
      log.error(
          "User [{}] attempted to version measure with id [{}] which has CQL errors",
          username,
          measure.getId());
      throw new BadVersionRequestException(
          "Measure", measure.getId(), username, "Measure has CQL errors.");
    }
    if (StringUtils.isBlank(measure.getCql())) {
      log.error(
          "User [{}] attempted to version measure with id [{}] which has empty CQL",
          username,
          measure.getId());
      throw new BadVersionRequestException(
          "Measure", measure.getId(), username, "Measure has no CQL.");
    }
    if (CollectionUtils.isEmpty(measure.getGroups())) {
      log.error(
          "User [{}] attempted to version measure with id [{}] which does not have at least "
              + "one Population Criteria",
          username,
          measure.getId());
      throw new BadVersionRequestException(
          "Measure",
          measure.getId(),
          username,
          "Measure does not have at least one Population Criteria.");
    }

    final ElmJson elmJson =
        elmTranslatorClient.getElmJson(measure.getCql(), measure.getModel(), accessToken);
    if (elmTranslatorClient.hasErrors(elmJson)) {
      throw new CqlElmTranslationErrorException(measure.getMeasureName());
    }
  }

  public Version getNextVersion(Measure measure, String versionType) {
    Version version;

    if (VERSION_TYPE_MAJOR.equalsIgnoreCase(versionType)) {
      version =
          measureRepository
              .findMaxVersionByMeasureSetId(measure.getMeasureSetId())
              .orElse(new Version());
      return version.toBuilder().major(version.getMajor() + 1).minor(0).revisionNumber(0).build();

    } else if (VERSION_TYPE_MINOR.equalsIgnoreCase(versionType)) {
      version =
          measureRepository
              .findMaxMinorVersionByMeasureSetIdAndVersionMajor(
                  measure.getMeasureSetId(), measure.getVersion().getMajor())
              .orElse(new Version());
      return version.toBuilder().minor(version.getMinor() + 1).revisionNumber(0).build();

    } else if (VERSION_TYPE_PATCH.equalsIgnoreCase(versionType)) {
      version =
          measureRepository
              .findMaxRevisionNumberByMeasureSetIdAndVersionMajorAndMinor(
                  measure.getMeasureSetId(),
                  measure.getVersion().getMajor(),
                  measure.getVersion().getMinor())
              .orElse(new Version());
      return version.toBuilder().revisionNumber(version.getRevisionNumber() + 1).build();
    }

    return new Version();
  }

  public String generateLibraryContentLine(String cqlLibraryName, Version version) {
    return "library " + cqlLibraryName + " version " + "'" + version + "'";
  }

  public Export saveExport(
      Measure savedMeasure,
      String measureBundle,
      String measureBundleWithoutWarnings,
      String humanReadableWithCss) {
    ObjectId measureBundleId =
        mongoGridFsService.save(
            new ByteArrayInputStream(measureBundle.getBytes()),
            savedMeasure.getEcqmTitle() + "-v" + savedMeasure.getVersion().toString(),
            "application/json");
    ObjectId measureBundleWithoutWarningsId =
        mongoGridFsService.save(
            new ByteArrayInputStream(measureBundleWithoutWarnings.getBytes()),
            savedMeasure.getEcqmTitle()
                + "-v"
                + savedMeasure.getVersion().toString()
                + "-withoutWarnings",
            "application/json");
    Export export =
        Export.builder()
            .measureId(savedMeasure.getId())
            .measureBundleGridFsId(measureBundleId.toHexString())
            .measureBundleWithoutWarningsGridFsId(measureBundleWithoutWarningsId.toHexString())
            .humanReadable(humanReadableWithCss)
            .build();

    return exportRepository.save(export);
  }

  private void saveMeasureBundle(
      Measure savedMeasure,
      String measureBundle,
      String measureBundleWithoutWarnings,
      String username) {
    String humanReadableWithCss;
    try {
      PackagingUtility utility = PackagingUtilityFactory.getInstance(savedMeasure.getModel());
      humanReadableWithCss = utility.getHumanReadableWithCSS(measureBundle);
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException
        | ClassNotFoundException e) {
      throw new BundleOperationException("Measure", savedMeasure.getId(), e);
    }

    Export savedExport =
        saveExport(savedMeasure, measureBundle, measureBundleWithoutWarnings, humanReadableWithCss);
    log.info(
        "User [{}] successfully saved versioned measure's export data with ID [{}]",
        username,
        savedExport.getId());
  }

  private void savePackageData(
      Measure savedMeasure,
      PackageDto packageData,
      PackageDto publishableMeasurePackage,
      String humanReadable,
      String username) {
    Export export =
        Export.builder()
            .measureId(savedMeasure.getId())
            .packageData(packageData.getExportPackage())
            .publishablePackageData(publishableMeasurePackage.getExportPackage())
            .humanReadable(humanReadable)
            .build();
    Export savedExport = exportRepository.save(export);
    log.info(
        "User [{}] successfully saved versioned measure's export data with ID [{}]",
        username,
        savedExport.getId());
  }

  private boolean checkCaseNumberExists(List<TestCase> testCases) {
    if (!CollectionUtils.isEmpty(testCases)) {
      for (TestCase testCase : testCases) {
        if (testCase.getCaseNumber() == null || testCase.getCaseNumber() == 0) {
          return false;
        }
      }
    } else {
      return false;
    }
    return true;
  }

  List<TestCase> assignCaseNumbersWhenCaseNumbersNotExist(
      List<TestCase> testCases, String measureId) {
    List<TestCase> sortedTestCases = new ArrayList<>(testCases);
    return sortedTestCases.stream()
        .sorted(
            Comparator.comparing(
                TestCase::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
        .map(
            testCase -> {
              testCase.setCaseNumber(sequenceService.generateSequence(measureId));
              return testCase;
            })
        .collect(Collectors.toList());
  }

  int findHighestCaseNumberWhenCaseNumbersExist(List<TestCase> testCases) {
    List<TestCase> sortedTestCases = new ArrayList<>(testCases);
    return sortedTestCases.stream()
        .sorted(
            Comparator.comparing(
                TestCase::getCaseNumber, Comparator.nullsFirst(Comparator.reverseOrder())))
        .toList()
        .get(0)
        .getCaseNumber();
  }
}
