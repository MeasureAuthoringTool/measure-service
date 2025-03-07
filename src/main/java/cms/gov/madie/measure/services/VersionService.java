package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.CqmMeasureRepository;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
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
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
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
import java.util.zip.Deflater;
import java.util.Arrays;

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
  private final ExportService exportService;
  private final TestCaseSequenceService sequenceService;
  private final ElmToJsonService elmToJsonService;
  private final GridFsTemplate gridFsTemplate;

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

  public Measure createVersion(String id, String versionType, String username, String accessToken)
      throws Exception {
    Measure measure = validateVersionOptions(id, versionType, username, accessToken);

    if (measure instanceof FhirMeasure) {
      return versionFhirMeasure(versionType, username, accessToken, measure);
    }

    return versionQdmMeasure(versionType, username, measure, accessToken);
  }

  private Measure versionQdmMeasure(
      String versionType, String username, Measure measure, String accessToken) throws Exception {
    Measure upversionedMeasure = version(versionType, username, measure);

    var measurePackage = exportService.getMeasureExport(upversionedMeasure, accessToken);

    // convert to CqmMeasure
    CqmMeasure cqmMeasure =
        qdmPackageService.convertCqm((QdmMeasure) upversionedMeasure, accessToken);

    String humanReadable =
        qdmPackageService.getHumanReadable(upversionedMeasure, username, accessToken);
    // save exports
    savePackageData(upversionedMeasure, measurePackage.getExportPackage(), humanReadable, username);
    //	save CqmMeasure
    cqmMeasureRepository.save(cqmMeasure);

    return applyMeasureVersion(versionType, username, upversionedMeasure);
  }

  /**
   * This method will first apply the version operation to the measure, fetch the FHIR bundle for
   * the measure, persist the measure bundle to the exports collection, and finally persist the
   * upversioned measure to the database.
   *
   * @param versionType
   * @param username
   * @param accessToken
   * @param measure
   * @return
   * @throws Exception
   */
  private Measure versionFhirMeasure(
      String versionType, String username, String accessToken, Measure measure) throws Exception {
    elmToJsonService.retrieveElmJson(measure, accessToken);
    Measure upversionedMeasure = version(versionType, username, measure);
    var measureBundle =
        fhirServicesClient.getMeasureBundle(upversionedMeasure, accessToken, "export");
    var measureBundleWithoutWarnings =
        fhirServicesClient.getMeasureBundle(
            upversionedMeasure, accessToken, "export", CqlCompilerException.ErrorSeverity.Error);

    saveMeasureBundle(
        upversionedMeasure, measureBundle, measureBundleWithoutWarnings, username);
    return applyMeasureVersion(versionType, username, upversionedMeasure);
  }

  private Measure version(String versionType, String username, Measure measure) throws Exception {
    Measure upversionedMeasure = measure.toBuilder().build();
    upversionedMeasure.getMeasureMetaData().setDraft(false);
    upversionedMeasure.setLastModifiedAt(Instant.now());
    upversionedMeasure.setLastModifiedBy(username);
    Version oldVersion = upversionedMeasure.getVersion();
    Version newVersion = getNextVersion(upversionedMeasure, versionType);
    upversionedMeasure.setVersion(newVersion);
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
        username);
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
      throw new BadVersionRequestException(
          "Measure", measure.getId(), username, "Invalid version request.");
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
    if (!isDraftable(measure)) {
      throw new MeasureNotDraftableException(measure.getMeasureName());
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
    measureDraft.setGroups(cloneMeasureGroups(measure.getGroups()));

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
    }

    actionLogService.logAction(savedDraft.getId(), Measure.class, ActionType.DRAFTED, username);

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
              }
              HapiOperationOutcome hapiOperationOutcome =
                  fhirServicesClient
                      .validateBundle(
                          testCase.getJson(),
                          ModelType.valueOfName(currentMeasure.getModel()),
                          accessToken)
                      .getBody();

              return testCase.toBuilder()
                  .id(ObjectId.get().toString())
                  .hapiOperationOutcome(hapiOperationOutcome)
                  .validResource(hapiOperationOutcome.isSuccessful())
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
    } else {
      final ElmJson elmJson =
          elmTranslatorClient.getElmJson(measure.getCql(), measure.getModel(), accessToken);
      if (elmTranslatorClient.hasErrors(elmJson)) {
        throw new CqlElmTranslationErrorException(measure.getMeasureName());
      }
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
        gridFsTemplate.store(
            new ByteArrayInputStream(measureBundle.getBytes()),
            "measureBundle.json",
            "application/json");
    ObjectId measureBundleWithoutWarningsId =
        gridFsTemplate.store(
            new ByteArrayInputStream(measureBundleWithoutWarnings.getBytes()),
            "measureBundleWithoutWarnings.json",
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

    Export savedExport = saveExport(savedMeasure, measureBundle, measureBundleWithoutWarnings, humanReadableWithCss);
    log.info(
            "User [{}] successfully saved versioned measure's export data with ID [{}]",
            username,
            savedExport.getId());
  }

  private void savePackageData(
      Measure savedMeasure, byte[] packageData, String humanReadable, String username) {
    Export export =
        Export.builder()
            .measureId(savedMeasure.getId())
            .packageData(packageData)
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
        .collect(Collectors.toList())
        .get(0)
        .getCaseNumber();
  }
}
