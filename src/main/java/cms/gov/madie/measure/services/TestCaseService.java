package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.*;
import gov.cms.madie.models.common.*;
import gov.cms.madie.models.measure.*;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.locks.TestCaseLock;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.*;
import tools.jackson.core.JacksonException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.*;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static cms.gov.madie.measure.utils.JsonUtil.convertDateTimeToUTC;
import static cms.gov.madie.measure.utils.TestCaseServiceUtil.checkIfAnyCreatedBeforeVersioning;
import static java.util.stream.Collectors.*;
import static org.apache.commons.collections4.CollectionUtils.*;

@Slf4j
@Service
public class TestCaseService {
  private final MeasureRepository measureRepository;
  private final ActionLogService actionLogService;
  private final FhirServicesClient fhirServicesClient;
  private final MeasureService measureService;
  private final TestCaseSequenceService sequenceService;
  private final AppConfigService appConfigService;
  final TestCaseValidationService testCaseValidationService;
  private final TestCaseLockService testCaseLockService;

  @Value("${madie.json.resources.base-uri}")
  @Getter
  private String madieJsonResourcesBaseUri;

  private static final String QDM_PATIENT =
      "{\"qdmVersion\":\"5.6\",\"dataElements\":[],\"_id\":\"OBJECTID\"}";

  @Autowired
  public TestCaseService(
      MeasureRepository measureRepository,
      ActionLogService actionLogService,
      FhirServicesClient fhirServicesClient,
      MeasureService measureService,
      TestCaseSequenceService sequenceService,
      AppConfigService appConfigService,
      TestCaseValidationService testCaseValidationService,
      TestCaseLockService testCaseLockService) {
    this.measureRepository = measureRepository;
    this.actionLogService = actionLogService;
    this.fhirServicesClient = fhirServicesClient;
    this.measureService = measureService;
    this.sequenceService = sequenceService;
    this.appConfigService = appConfigService;
    this.testCaseValidationService = testCaseValidationService;
    this.testCaseLockService = testCaseLockService;
  }

  protected TestCase enrichNewTestCase(
      TestCase testCase, String username, String measureId, String model) {
    final TestCase enrichedTestCase = testCase.toBuilder().build();
    Instant now = Instant.now();
    enrichedTestCase.setId(ObjectId.get().toString());
    enrichedTestCase.setCreatedAt(now);
    enrichedTestCase.setCreatedBy(username);
    enrichedTestCase.setLastModifiedAt(now);
    enrichedTestCase.setLastModifiedBy(username);
    enrichedTestCase.setResourceUri(null);
    enrichedTestCase.setHapiOperationOutcome(null);
    enrichedTestCase.setValidResource(false);
    enrichedTestCase.setPatientId(UUID.randomUUID());
    if (appConfigService.isFlagEnabled(MadieFeatureFlag.TEST_CASE_SET_ID)
        && !ModelType.QDM_5_6.getValue().equalsIgnoreCase(model)) {
      enrichedTestCase.setTestCaseSetId(UUID.randomUUID());
    } else {
      enrichedTestCase.setTestCaseSetId(null);
    }
    enrichedTestCase.setCaseNumber(sequenceService.generateSequence(measureId));

    return enrichedTestCase;
  }

  protected void verifyUniqueTestCaseName(TestCase testCase, Measure measure) {
    if (isEmpty(measure.getTestCases())) {
      return;
    }
    // ignore spaces
    final String newName = StringUtils.deleteWhitespace(testCase.getTitle() + testCase.getSeries());
    boolean matchesExistingTestCaseName =
        measure.getTestCases().stream()
            // exclude the current test case
            .filter(tc -> !StringUtils.equalsIgnoreCase(tc.getId(), testCase.getId()))
            .map(tc -> StringUtils.deleteWhitespace(tc.getTitle() + tc.getSeries()))
            .anyMatch(existingName -> existingName.equalsIgnoreCase(newName));
    if (matchesExistingTestCaseName) {
      throw new DuplicateTestCaseNameException();
    }
  }

  public TestCase persistTestCase(
      TestCase testCase, String measureId, String username, String accessToken) {
    final Measure measure = measureService.findActiveMeasureById(measureId);

    verifyUniqueTestCaseName(testCase, measure);
    if (StringUtils.join(testCase.getTitle(), testCase.getSeries()).length() > 250) {
      throw new TestCaseNameLengthException(testCase.getId());
    }
    defaultTestCaseJsonForQdmMeasure(testCase, measure);
    TestCaseServiceUtil.checkTestCaseSpecialCharacters(testCase);
    TestCase enrichedTestCase =
        enrichNewTestCase(testCase, username, measureId, measure.getModel());
    boolean lenientPatientRefs = MeasureUtil.isInvalidTestCaseExecutionEnabled(measure);
    enrichedTestCase =
        testCaseValidationService.validateTestCaseAsResource(
            enrichedTestCase,
            ModelType.valueOfName(measure.getModel()),
            accessToken,
            lenientPatientRefs);

    if (enrichedTestCase != null && !measure.getMeasureMetaData().isDraft()) {
      enrichedTestCase.setCreatedBeforeVersioning(false);
    }

    measureRepository.addOrUpdateTestCase(measureId, enrichedTestCase);

    actionLogService.logAction(
        enrichedTestCase.getId(), TestCase.class, ActionType.CREATED, username);
    log.info(
        "User [{}] successfully created new test case with ID [{}] for the measure with ID[{}] ",
        username,
        testCase.getId(),
        measureId);
    return enrichedTestCase;
  }

  public List<TestCase> persistTestCases(
      List<TestCase> newTestCases, String measureId, String username, String accessToken) {
    if (newTestCases == null || newTestCases.isEmpty()) {
      return newTestCases;
    }
    final Measure measure = measureService.findActiveMeasureById(measureId);
    List<TestCase> enrichedTestCases = new ArrayList<>(newTestCases.size());
    boolean lenientPatientRefs = MeasureUtil.isInvalidTestCaseExecutionEnabled(measure);
    for (TestCase testCase : newTestCases) {
      TestCaseServiceUtil.checkTestCaseSpecialCharacters(testCase);
      TestCase enriched = enrichNewTestCase(testCase, username, measureId, measure.getModel());
      enriched =
          testCaseValidationService.validateTestCaseAsResource(
              enriched, ModelType.valueOfName(measure.getModel()), accessToken, lenientPatientRefs);
      if (enriched != null && !measure.getMeasureMetaData().isDraft()) {
        enriched.setCreatedBeforeVersioning(false);
      }
      enrichedTestCases.add(enriched);
      actionLogService.logAction(enriched.getId(), TestCase.class, ActionType.IMPORTED, username);
    }
    if (measure.getTestCases() == null) {
      measure.setTestCases(enrichedTestCases);
    } else {
      measure.getTestCases().addAll(enrichedTestCases);
    }
    measureRepository.save(measure);
    log.info(
        "User [{}] successfully imported [{}] test cases to the measure with ID[{}] ",
        username,
        enrichedTestCases.size(),
        measureId);
    return enrichedTestCases;
  }

  public MeasureTestCaseValidationReport updateTestCaseValidResourcesWithReport(
      final String measureId, final String accessToken) {
    log.info(
        "Thread [{}] :: Updating ValidResource flag for all test cases on measure [{}]",
        Thread.currentThread().getName(),
        measureId);
    final Optional<Measure> measureOpt = measureRepository.findById(measureId);
    if (measureOpt.isPresent()) {
      final Measure measure = measureOpt.get();
      MeasureTestCaseValidationReport measureReport =
          MeasureTestCaseValidationReport.builder()
              .measureName(measure.getMeasureName())
              .measureId(measure.getId())
              .measureSetId(measure.getMeasureSetId())
              .measureVersionId(measure.getVersionId())
              .testCaseValidationReports(List.of())
              .build();

      if (!isEmpty(measure.getTestCases())) {
        List<TestCaseValidationReport> reports =
            measure.getTestCases().stream()
                .map(
                    testCase ->
                        TestCaseValidationReport.builder()
                            .testCaseId(testCase.getId())
                            .patientId(testCase.getPatientId().toString())
                            .previousValidResource(testCase.isValidResource())
                            .build())
                .toList();
        List<TestCase> validatedTestCases =
            updateTestCaseValidResourcesForMeasure(measure, accessToken);
        Map<String, TestCase> testCaseMap =
            validatedTestCases.stream().collect(toMap(TestCase::getId, Function.identity()));
        reports.forEach(
            report ->
                report.setCurrentValidResource(
                    testCaseMap.get(report.getTestCaseId()).isValidResource()));
        measureReport.setTestCaseValidationReports(reports);
      }
      measureReport.setJobStatus(JobStatus.COMPLETED);
      return measureReport;
    }
    return MeasureTestCaseValidationReport.builder()
        .measureId(measureId)
        .jobStatus(JobStatus.SKIPPED)
        .build();
  }

  public List<TestCase> updateTestCaseValidResourcesForMeasure(
      Measure measure, final String accessToken) {
    List<TestCase> validatedTestCases =
        testCaseValidationService.validateTestCasesAsResources(measure, accessToken);
    measure.setTestCases(validatedTestCases);
    measureRepository.save(measure);
    return validatedTestCases;
  }

  // updateTestCase for QDM
  public TestCase updateTestCase(
      TestCase testCase, String measureId, String username, String accessToken) {
    Measure measure = getAndCheckMeasure(measureId, testCase.getId(), username);

    handleTestCasesForUpdate(testCase, measureId, username, measure);
    return validateAndSave(testCase, measure, username, accessToken);
  }

  // common method 1 for two overloading updateTestCase() method
  private Measure getAndCheckMeasure(String measureId, String testCaseId, String username) {
    Measure measure = measureService.findMeasureById(measureId);
    if (measure == null) {
      throw new ResourceNotFoundException("Measure", measureId);
    }
    TestCaseLock lock = testCaseLockService.findByTestCaseId(testCaseId);
    if (lock != null && !username.equalsIgnoreCase(lock.getLockedBy())) {
      throw new LockNotObtainedException(
          "Unable to update Test Case. Test Case is locked by: " + lock.getLockedBy());
    }
    return measure;
  }

  // common method 2 for two overloading updateTestCase() method
  private void handleTestCasesForUpdate(
      TestCase testCase, String measureId, String username, Measure measure) {
    TestCaseServiceUtil.checkTestCaseSpecialCharacters(testCase);
    if (measure.getTestCases() == null) {
      measure.setTestCases(new ArrayList<>());
    }
    verifyUniqueTestCaseName(testCase, measure);
    measureService.verifyAuthorization(username, measure);
    Instant now = Instant.now();
    testCase.setLastModifiedAt(now);
    testCase.setLastModifiedBy(username);

    Optional<TestCase> existingOpt =
        measure.getTestCases().stream().filter(p -> p.getId().equals(testCase.getId())).findFirst();
    if (existingOpt.isPresent()) {
      TestCase existing = existingOpt.get();
      testCase.setCreatedAt(existing.getCreatedAt());
      testCase.setCreatedBy(existing.getCreatedBy());
      testCase.setResourceUri(existing.getResourceUri());
      testCase.setPatientId(existing.getPatientId()); // assure patientId is not overwritten
      testCase.setValidationStatus(existing.getValidationStatus());
      testCase.setValidationTaskId(existing.getValidationTaskId());
      measure.getTestCases().remove(existing);
    } else {
      // still allowing upsert
      testCase.setId(ObjectId.get().toString());
      testCase.setCreatedAt(now);
      testCase.setCreatedBy(username);
      if (testCase.getPatientId() == null) {
        testCase.setPatientId(UUID.randomUUID());
      }
    }
  }

  // common method 3 for two overloading updateTestCase() method
  private TestCase validateAndSave(
      TestCase testCase, Measure measure, String username, String accessToken) {
    boolean lenientPatientRefs = MeasureUtil.isInvalidTestCaseExecutionEnabled(measure);
    TestCase validatedTestCase =
        testCaseValidationService.validateTestCaseAsResource(
            testCase, ModelType.valueOfName(measure.getModel()), accessToken, lenientPatientRefs);
    measure.getTestCases().add(validatedTestCase);

    measureRepository.save(measure); // TODO MAT-8921: Replace with Test Case FindAndModify
    log.info(
        "User [{}] successfully updated the test case with ID [{}] for the measure with ID[{}] ",
        username,
        testCase.getId(),
        measure.getId());
    return validatedTestCase;
  }

  // overloading method
  public TestCase updateTestCase(
      TestCase testCase, String measureId, String username, String accessToken, String queueType) {
    Measure measure = getAndCheckMeasure(measureId, testCase.getId(), username);

    handleTestCasesForUpdate(testCase, measureId, username, measure);

    boolean isQiCoreModel =
        ModelType.QI_CORE.getValue().equalsIgnoreCase(measure.getModel())
            || ModelType.QI_CORE_6_0_0.getValue().equalsIgnoreCase(measure.getModel());

    boolean hasJson = StringUtils.isNotBlank(testCase.getJson());
    // this transformation logic needs to be run before hapiFhirValidations or they will fail.
    if (isQiCoreModel && hasJson) {
      testCase.setJson(JsonUtil.enforcePatientId(testCase, madieJsonResourcesBaseUri));
      testCase.setJson(JsonUtil.updateResourceFullUrls(testCase, madieJsonResourcesBaseUri));
      testCase.setJson(
          JsonUtil.replacePatientRefs(testCase.getJson(), testCase.getPatientId().toString()));
      testCase.setBundleTypeUpdated(false);
      try {
        testCase.setJson(JsonUtil.updateBundleTypeAndRemoveRequest(testCase));
      } catch (JacksonException e) {
        log.error(
            "Error reading testCaseJson while updating TestCase with id: " + testCase.getId(), e);
      }
      if (ModelType.QI_CORE_6_0_0.getValue().equalsIgnoreCase(measure.getModel())) {
        Measure updatedMeasure = measureRepository.addOrUpdateTestCase(measureId, testCase);

        if (updatedMeasure == null) {
          log.error(
              "Failed to add or update test case [{}] for measure [{}]",
              testCase.getId(),
              measureId);
          throw new ResourceNotFoundException(
              String.format(
                  "Unable to add or update test case [%s] for measure [%s]",
                  testCase.getId(), measureId));
        }
        log.info(
            "User [{}] successfully updated the test case with ID [{}] for the measure with ID[{}] ",
            username,
            testCase.getId(),
            measureId);
        return testCaseValidationService.validateResourceAsynchronously(
            measure, testCase, queueType, accessToken);
      }
    }
    return validateAndSave(testCase, measure, username, accessToken);
  }

  public TestCase getTestCase(
      String measureId, String testCaseId, boolean validate, String accessToken, String username) {
    Measure measure = measureService.findActiveMeasureById(measureId);
    TestCase testCase =
        Optional.ofNullable(measure.getTestCases())
            .orElseThrow(() -> new ResourceNotFoundException("Test Case", testCaseId))
            .stream()
            .filter(tc -> tc.getId().equals(testCaseId))
            .findFirst()
            .orElse(null);
    if (testCase == null) {
      throw new ResourceNotFoundException("Test Case", testCaseId);
    }

    TestCaseLock lock = testCaseLockService.findByTestCaseId(testCaseId);
    testCase.setTestCaseLock(
        lock != null && !username.equalsIgnoreCase(lock.getLockedBy())
            ? TestCaseLockInfo.builder()
                .measureId(lock.getMeasureId())
                .testCaseId(lock.getTestCaseId())
                .lockedBy(lock.getLockedBy())
                .build()
            : null);

    if (validate) {
      boolean lenientPatientRefs = MeasureUtil.isInvalidTestCaseExecutionEnabled(measure);
      return testCaseValidationService.validateTestCaseAsResource(
          testCase, ModelType.valueOfName(measure.getModel()), accessToken, lenientPatientRefs);
    }
    return testCase;
  }

  public List<TestCase> findTestCasesByMeasureId(String measureId, String username) {
    List<TestCase> testCases = measureService.findActiveMeasureById(measureId).getTestCases();
    if (!isEmpty(testCases)) {
      testCases.forEach(
          testCase -> {
            TestCaseLock lock = testCaseLockService.findByTestCaseId(testCase.getId());
            testCase.setTestCaseLock(
                lock != null && !username.equalsIgnoreCase(lock.getLockedBy())
                    ? TestCaseLockInfo.builder()
                        .measureId(lock.getMeasureId())
                        .testCaseId(lock.getTestCaseId())
                        .lockedBy(lock.getLockedBy())
                        .build()
                    : null);
          });
    }
    return testCases;
  }

  public String deleteTestCases(String measureId, List<String> testCaseIds, String username) {
    if (isEmpty(testCaseIds)
        || StringUtils.isBlank(measureId)
        || testCaseIds.stream().allMatch(StringUtils::isBlank)) {
      log.info("Test case Ids or Measure Id is Empty");
      throw new InvalidIdException("Test cases cannot be deleted, please contact the helpdesk");
    }
    Measure measure = measureService.findActiveMeasureById(measureId);
    measureService.verifyAuthorization(username, measure);
    if (isEmpty(measure.getTestCases())) {
      log.info("Measure with ID [{}] doesn't have any test cases", measureId);
      throw new InvalidIdException(
          "Measure {} doesn't have any existing test cases to delete", measureId);
    }
    checkIfAnyCreatedBeforeVersioning(
        measure.getTestCases(), testCaseIds, measure.getMeasureMetaData().isDraft());

    List<TestCase> testCasesToDelete =
        measure.getTestCases().stream().filter(tc -> testCaseIds.contains(tc.getId())).toList();

    List<String> testCaseIdsDeleted = new ArrayList<>();
    List<String> testCaseIdsLockedByOtherUser = new ArrayList<>();

    testCasesToDelete.forEach(
        testCase -> {
          try {
            testCaseLockService.lockTestCaseForUser(measureId, testCase.getId(), username);
            measureRepository.removeTestCase(measureId, testCase.getId());
            testCaseIdsDeleted.add(testCase.getId());
            testCaseLockService.unlockTestCase(measureId, testCase.getId());
          } catch (LockNotObtainedException e) {
            testCaseIdsLockedByOtherUser.add(testCase.getId());
          } catch (InvalidIdException e) {
            // no-op - this means the test case was not found in the measure
          }
        });

    if (testCaseIdsDeleted.size() == measure.getTestCases().size()) {
      sequenceService.resetSequence(measureId);
    }

    if (isEmpty(testCaseIdsLockedByOtherUser)) {
      log.info(
          "User [{}] deleted test cases with Ids [{}] from measure [{}]",
          username,
          testCaseIds,
          measureId);
      String successMessage =
          "Successfully deleted test cases: " + String.join(", ", testCaseIdsDeleted);
      if (testCaseIdsDeleted.size() == testCaseIds.size()) {
        return successMessage;
      }
      return successMessage
          + ", unable to delete "
          + String.join(", ", CollectionUtils.removeAll(testCaseIds, testCaseIdsDeleted));
    }

    log.info(
        "User [{}] deleted test cases with Ids {} from measure [{}]. "
            + "Test Cases with Ids {} were locked by another user and could not be deleted.",
        username,
        String.join(", ", testCaseIdsDeleted),
        measureId,
        String.join(", ", testCaseIdsLockedByOtherUser));
    throw new LockNotObtainedException(String.join(",", testCaseIdsLockedByOtherUser));
  }

  public CopyTestCaseResult copyTestCasesToMeasure(
      String targetMeasureId, List<TestCase> sourceTestCases, String username, String accessToken) {
    List<TestCase> copiedTestCases = new ArrayList<>(sourceTestCases.size());

    Measure targetMeasure = measureService.findMeasureById(targetMeasureId);

    List<Group> targetGroups =
        TestCaseServiceUtil.getGroupsWithValidPopulations(targetMeasure.getGroups());

    boolean clearedExpectedValues = false;
    List<TestCase> failedTestCases = new ArrayList<>();
    for (TestCase sourceTestCase : sourceTestCases) {
      TestCase dupTestCase = sourceTestCase.deepCopy();

      // Check and update any fields with proper timestamp
      // Only applies to QiCore
      if (!targetMeasure.getModel().equals(ModelType.QDM_5_6.getValue())) {
        dupTestCase.setJson(convertDateTimeToUTC(dupTestCase.getJson()));
      }

      if (targetMeasure != null && !targetMeasure.getMeasureMetaData().isDraft()) {
        dupTestCase.setCreatedBeforeVersioning(false);
      }

      // Empty Test Case Group Populations match any Measure Pop Criteria.
      boolean doesPopCriteriaMatch =
          isEmpty(dupTestCase.getGroupPopulations())
              || TestCaseServiceUtil.matchCriteriaGroups(
                  dupTestCase.getGroupPopulations(), targetGroups, dupTestCase);

      if (!doesPopCriteriaMatch) {
        clearedExpectedValues = true;
        clearExpectedValues(dupTestCase);
        // When the target has fewer groups than the source, drop the extra group
        // populations to prevent IndexOutOfBoundsExceptions and stale data.
        if (isNotEmpty(targetGroups)
            && isNotEmpty(dupTestCase.getGroupPopulations())
            && dupTestCase.getGroupPopulations().size() > targetGroups.size()) {
          dupTestCase.setGroupPopulations(
              new ArrayList<>(dupTestCase.getGroupPopulations().subList(0, targetGroups.size())));
        }
      }
      Optional<TestCase> copiedTestCase = Optional.empty();
      try {
        copiedTestCase = copyTestCaseToMeasure(targetMeasureId, dupTestCase, username, accessToken);
      } catch (TestCaseNameLengthException e) {
        failedTestCases.add(sourceTestCase);
      } catch (Exception e) {
        log.error(
            "Failed to copy Test Case {} to Measure {}",
            sourceTestCase.getId(),
            targetMeasureId,
            e);
      }
      copiedTestCase.ifPresent(copiedTestCases::add);
    }
    return CopyTestCaseResult.builder()
        .copiedTestCases(copiedTestCases)
        .failedTestCases(failedTestCases)
        .didClearExpectedValues(Boolean.valueOf(clearedExpectedValues))
        .build();
  }

  private Optional<TestCase> copyTestCaseToMeasure(
      String targetMeasureId, TestCase dupTestCase, String username, String accessToken) {
    try {
      return Optional.of(persistTestCase(dupTestCase, targetMeasureId, username, accessToken));
    } catch (DuplicateTestCaseNameException e) {
      dupTestCase.setTitle(dupTestCase.getTitle() + "-" + new ObjectId());
      return Optional.of(persistTestCase(dupTestCase, targetMeasureId, username, accessToken));
    }
  }

  private void clearExpectedValues(TestCase testCase) {
    if (isNotEmpty(testCase.getGroupPopulations())) {
      for (TestCaseGroupPopulation tcGroupPopulation : testCase.getGroupPopulations()) {
        if (isNotEmpty(tcGroupPopulation.getPopulationValues())) {
          for (TestCasePopulationValue populationValue : tcGroupPopulation.getPopulationValues()) {
            populationValue.setExpected(null);
          }
        }
        if (isNotEmpty(tcGroupPopulation.getStratificationValues())) {
          for (TestCaseStratificationValue stratificationValue :
              tcGroupPopulation.getStratificationValues()) {
            stratificationValue.setExpected(null);
          }
        }
      }
    }
  }

  /**
   * This logic is shared by both the QI-Core "Import from MADiE" workflow, and QDM "Import from
   * Bonnie" workflow
   *
   * @param testCaseImportRequests
   * @param measureId
   * @param userName
   * @param accessToken
   * @param model
   * @return
   */
  public List<TestCaseImportOutcome> importTestCases(
      List<TestCaseImportRequest> testCaseImportRequests,
      String measureId,
      String userName,
      String accessToken,
      String model) {
    Measure measure = measureService.findActiveMeasureById(measureId);
    Set<UUID> checkedTestCases = new HashSet<>();
    return testCaseImportRequests.stream()
        .filter(
            testCaseImportRequest ->
                !checkedTestCases.contains(testCaseImportRequest.getPatientId()))
        .map(
            testCaseImportRequest -> {
              checkedTestCases.add(testCaseImportRequest.getPatientId());
              if (testCaseImportRequests.stream()
                      .map(TestCaseImportRequest::getPatientId)
                      .filter(uuid -> uuid.equals(testCaseImportRequest.getPatientId()))
                      .count()
                  > 1) {
                return TestCaseImportOutcome.builder()
                    .patientId(testCaseImportRequest.getPatientId())
                    .successful(false)
                    .message(
                        "Multiple test case files are not supported."
                            + " Please make sure only one JSON file is in the folder.")
                    .build();
              }
              if (testCaseImportRequest.getJson() == null
                  || testCaseImportRequest.getJson().isEmpty()) {
                return TestCaseImportOutcome.builder()
                    .patientId(testCaseImportRequest.getPatientId())
                    .successful(false)
                    .message("Test Case file is missing.")
                    .build();
              }
              TestCaseImportOutcome outCome =
                  TestCaseServiceUtil.checkErrorSpecialChar(model, testCaseImportRequest);
              if (outCome != null) {
                return outCome;
              }
              if (isEmpty(measure.getTestCases())) {
                return validateTestCaseJsonAndCreateTestCase(
                    testCaseImportRequest, measure, userName, accessToken, model);
              }
              Optional<TestCase> existingTestCase =
                  measure.getTestCases().stream()
                      .filter(
                          testCase ->
                              testCase.getPatientId().equals(testCaseImportRequest.getPatientId()))
                      .findFirst();
              if (existingTestCase.isPresent()) {
                return updateTestCaseJsonAndSaveTestCase(
                    existingTestCase.get(),
                    testCaseImportRequest,
                    measureId,
                    userName,
                    accessToken,
                    null,
                    model);
              } else {
                return validateTestCaseJsonAndCreateTestCase(
                    testCaseImportRequest, measure, userName, accessToken, model);
              }
            })
        .toList();
  }

  private TestCaseImportOutcome validateTestCaseJsonAndCreateTestCase(
      TestCaseImportRequest testCaseImportRequest,
      Measure measure,
      String userName,
      String accessToken,
      String model) {
    try {
      String familyName =
          TestCaseServiceUtil.getPatientFamilyNameFromJson(model, testCaseImportRequest.getJson());
      String givenName =
          TestCaseServiceUtil.getPatientGivenNameFromJson(model, testCaseImportRequest.getJson());
      log.info("Test Case title + Test Case Group:  {}", givenName + " " + familyName);
      if (StringUtils.isBlank(givenName)) {
        return TestCaseServiceUtil.buildTestCaseImportOutcome(
            testCaseImportRequest.getPatientId(), false, "Test Case Title is required.");
      }
      TestCase newTestCase =
          TestCase.builder()
              .title(getTitle(testCaseImportRequest, givenName))
              .series(getSeries(testCaseImportRequest, familyName))
              .patientId(testCaseImportRequest.getPatientId())
              .build();

      newTestCase.setCaseNumber(sequenceService.generateSequence(measure.getId()));

      List<TestCaseGroupPopulation> testCaseGroupPopulations =
          getTestCaseGroupPopulationsFromImportRequest(
              model, testCaseImportRequest.getJson(), measure);
      List<Group> groups = TestCaseServiceUtil.getGroupsWithValidPopulations(measure.getGroups());
      String warningMessage = null;
      if (ModelType.QDM_5_6.getValue().equalsIgnoreCase(model)) {
        testCaseGroupPopulations =
            TestCaseServiceUtil.assignStratificationValuesQdm(testCaseGroupPopulations, groups);
        QdmMeasure qdmMeasure = (QdmMeasure) measure;
        if (MeasureScoring.CONTINUOUS_VARIABLE.toString().equalsIgnoreCase(qdmMeasure.getScoring())
            && measure.getGroups().size() > 1) {
          warningMessage =
              "observation values were not imported. MADiE cannot import expected "
                  + "values for Continuous Variable measures with multiple population criteria.";
        }
      } else {
        if (appConfigService.isFlagEnabled(MadieFeatureFlag.TEST_CASE_SET_ID)) {
          newTestCase.setTestCaseSetId(UUID.randomUUID());
        }
        testCaseGroupPopulations =
            TestCaseServiceUtil.assignStratificationValuesQiCore(testCaseGroupPopulations, groups);
      }
      // Compare main populations from the measure pop criteria against incoming test case.
      // Check includes Stratification and excludes Observations.
      boolean matched =
          TestCaseServiceUtil.matchCriteriaGroups(testCaseGroupPopulations, groups, newTestCase);
      if (!matched) {
        warningMessage =
            "the measure populations do not match the populations in the import file. "
                + "The Test Case has been imported, but no expected values have been set.";
      }
      if (ModelType.QI_CORE.getValue().equalsIgnoreCase(model)) {
        TestCaseServiceUtil.assignObservationIdAndCriteriaReferenceCVAndRatio(
            testCaseGroupPopulations, groups);
      }
      return updateTestCaseJsonAndSaveTestCase(
          newTestCase,
          testCaseImportRequest,
          measure.getId(),
          userName,
          accessToken,
          warningMessage,
          model);
    } catch (JacksonException ex) {
      log.info(
          "User {} is unable to import test case with patient id : "
              + "{} because of JacksonException: "
              + ex.getMessage(),
          userName,
          testCaseImportRequest.getPatientId());
      return TestCaseServiceUtil.buildTestCaseImportOutcome(
          testCaseImportRequest.getPatientId(),
          false,
          "Error while processing Test Case JSON. Please make sure Test Case JSON is valid.");
    }
  }

  private List<TestCaseGroupPopulation> getTestCaseGroupPopulationsFromImportRequest(
      String model, String json, Measure measure) throws JacksonException {
    List<TestCaseGroupPopulation> testCaseGroupPopulations = null;
    if (ModelType.QI_CORE.getValue().equalsIgnoreCase(model)) {
      testCaseGroupPopulations =
          JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(
              json,
              "boolean"
                  .equals(StringUtils.lowerCase(measure.getGroups().get(0).getPopulationBasis())),
              measure);
    } else if (ModelType.QDM_5_6.getValue().equalsIgnoreCase(model)) {
      testCaseGroupPopulations = JsonUtil.getTestCaseGroupPopulationsQdm(json, measure);
    }
    return testCaseGroupPopulations;
  }

  private TestCaseImportOutcome updateTestCaseJsonAndSaveTestCase(
      TestCase existingTestCase,
      TestCaseImportRequest testCaseImportRequest,
      String measureId,
      String userName,
      String accessToken,
      String warningMessage,
      String model) {
    TestCaseImportOutcome failureOutcome =
        TestCaseImportOutcome.builder()
            .familyName(testCaseImportRequest.getFamilyName())
            .givenNames(testCaseImportRequest.getGivenNames())
            .patientId(testCaseImportRequest.getPatientId())
            .successful(false)
            .build();
    if (existingTestCase.getId() != null) {
      LockInfo lock =
          testCaseLockService.lockTestCase(measureId, existingTestCase.getId(), userName);
      log.info(
          "User [{}] is trying to lock test case id: [{}]  for measureId: [{}]",
          userName,
          existingTestCase.getId(),
          measureId);
      if (lock != null && !userName.equals(lock.getLockedBy())) {
        log.info(
            "User [{}] failed to acquire lock for test case id : [{}], measureId: [{}]. "
                + "The test case is locked by another user: [{}]",
            userName,
            existingTestCase.getId(),
            measureId,
            lock.getLockedBy());
        failureOutcome.setMessage(
            "Failed to import test case: "
                + existingTestCase.getId()
                + ". The test case is locked by another user: "
                + lock.getLockedBy());
        return failureOutcome;
      }
    }
    try {
      existingTestCase.setDescription(
          getDescription(model, testCaseImportRequest.getJson(), testCaseImportRequest));
      existingTestCase.setJson(TestCaseServiceUtil.getJson(model, testCaseImportRequest.getJson()));
      TestCase updatedTestCase =
          updateTestCase(
              existingTestCase, measureId, userName, accessToken, TestCaseServiceUtil.IMPORT);
      log.info(
          "User {} successfully imported test case with patient id : {}",
          userName,
          updatedTestCase.getPatientId());

      LockInfo lock = testCaseLockService.unlockTestCase(existingTestCase.getId(), userName);
      if (lock != null) {
        if (!lock.isLocked()) {
          log.info(
              "User [{}] unlocked test case id: [{}] for measureId: [{}]",
              userName,
              existingTestCase.getId(),
              measureId);
        } else {
          log.info(
              "User [{}] failed unlocking test case id: [{}] for measureId: [{}], test case locked by: [{}}",
              userName,
              existingTestCase.getId(),
              measureId,
              lock.getLockedBy());
        }
      }

      TestCaseImportOutcome testCaseImportOutcome =
          TestCaseImportOutcome.builder()
              .familyName(testCaseImportRequest.getFamilyName())
              .givenNames(testCaseImportRequest.getGivenNames())
              .patientId(updatedTestCase.getPatientId())
              .successful(true)
              .build();
      if (warningMessage != null) {
        testCaseImportOutcome.setMessage(warningMessage);
      }
      return testCaseImportOutcome;
    } catch (JacksonException e) {
      log.info(
          "User {} is unable to import test case with patient id : "
              + "{} due to Malformed test case json bundle",
          userName,
          testCaseImportRequest.getPatientId());
      failureOutcome.setMessage(
          "Error while processing Test Case JSON.  Please make sure Test Case JSON is valid.");
      return failureOutcome;
    } catch (ResourceNotFoundException
        | InvalidDraftStatusException
        | InvalidMeasureStateException
        | UnauthorizedException
        | DuplicateTestCaseNameException e) {
      log.info(
          "User {} is unable to import test case with patient id : {}; Error Message : {}",
          userName,
          testCaseImportRequest.getPatientId(),
          TestCaseServiceUtil.formatErrorMessage(e));
      failureOutcome.setMessage(TestCaseServiceUtil.formatErrorMessage(e));
      return failureOutcome;
    } catch (Exception e) {
      log.info(
          "User {} is unable to import test case with patient id : {}; Error Message:",
          userName,
          testCaseImportRequest.getPatientId(),
          e);
      failureOutcome.setMessage(
          "Unable to import test case, please try again. "
              + "If the error persists, Please contact helpdesk.");
      return failureOutcome;
    }
  }

  protected String getTitle(TestCaseImportRequest importRequest, final String givenName) {
    return importRequest == null || importRequest.getTestCaseMetaData() == null
        ? givenName
        : importRequest.getTestCaseMetaData().getTitle();
  }

  protected String getSeries(TestCaseImportRequest importRequest, final String familyName) {
    return importRequest == null || importRequest.getTestCaseMetaData() == null
        ? familyName
        : importRequest.getTestCaseMetaData().getSeries();
  }

  protected String getDescription(
      String model, String json, TestCaseImportRequest testCaseImportRequest)
      throws JacksonException {
    String description = null;
    if (ModelType.QI_CORE.getValue().equalsIgnoreCase(model)) {
      String defaultDescription = JsonUtil.getTestDescription(json);
      description =
          testCaseImportRequest == null || testCaseImportRequest.getTestCaseMetaData() == null
              ? defaultDescription
              : ObjectUtils.defaultIfNull(
                  testCaseImportRequest.getTestCaseMetaData().getDescription(), defaultDescription);
    } else if (ModelType.QDM_5_6.getValue().equalsIgnoreCase(model)) {
      description = JsonUtil.getTestDescriptionQdm(json);
    }
    return description;
  }

  public List<String> findTestCaseSeriesByMeasureId(String measureId) {
    Measure measure =
        measureRepository
            .findAllTestCaseSeriesByMeasureId(measureId)
            .orElseThrow(() -> new ResourceNotFoundException("Measure", measureId));
    return Optional.ofNullable(measure.getTestCases()).orElse(List.of()).stream()
        .map(TestCase::getSeries)
        .filter(series -> series != null && !series.trim().isEmpty())
        .distinct()
        .collect(toList());
  }

  public List<TestCase> shiftQiCoreTestCaseDates(
      List<TestCase> testCases, int shifted, String accessToken, String measureId, String userId) {
    if (isEmpty(testCases)) {
      return Collections.emptyList();
    }

    List<String> testCaseIds = testCases.stream().map(testCase -> testCase.getId()).toList();
    log.info(
        "User: [{}} is trying to shift dates for measureId: [{}] - testCaseIds: {}",
        userId,
        measureId,
        testCaseIds);
    List<LockInfo> failedLocks =
        testCaseLockService.lockAllTestCases(measureId, testCaseIds, userId);
    // only when all locks are acquired can test cases' dates be shifted
    if (isEmpty(failedLocks)) {
      log.info("Locking all test cases for testCaseIds: {} successful", testCaseIds);
      List<TestCase> shiftedTestCases =
          fhirServicesClient.shiftTestCaseDates(testCases, shifted, accessToken).getBody();
      testCaseLockService.unlockAllTestCases(testCaseIds, userId);
      return shiftedTestCases;
    } else {
      // otherwise, unlock previously locked test cases, and shift dates should not happen
      List<String> failedIds =
          failedLocks.stream().map(failedLock -> failedLock.getLockedId()).toList();
      log.info("Failed locking test cases for testCaseIds: {}", failedIds);
      List<String> successLocks =
          testCaseIds.stream().filter(testCaseId -> !failedIds.contains(testCaseId)).toList();
      log.info("Revert locking test cases for testCaseIds: {}", successLocks);
      List<String> failedMsgs =
          failedLocks.stream()
              .map(
                  failedLock ->
                      "Test Case: "
                          + failedLock.getLockedId()
                          + " is locked by user: "
                          + failedLock.getLockedBy()
                          + ".\n")
              .toList();
      testCaseLockService.unlockAllTestCases(successLocks, userId);
      throw new LockNotObtainedException(failedMsgs.toString());
    }
  }

  protected void defaultTestCaseJsonForQdmMeasure(TestCase testCase, Measure measure) {
    if (ModelType.QDM_5_6.getValue().equalsIgnoreCase(measure.getModel())
        && StringUtils.isBlank(testCase.getJson())) {
      String objectId = ObjectId.get().toHexString();
      testCase.setJson(QDM_PATIENT.replace("OBJECTID", objectId));
    }
  }

  public Map<String, Object> updateQiCoreJsonWithGroupAndTitle(
      List<TestCase> testCases, String userName, String measureId, String accessToken) {
    Map<String, Object> response = new HashMap<>();
    List<String> updatedTestCases = new ArrayList<>();
    List<String> failedTestCases = new ArrayList<>();
    List<String> testCaseIds = testCases.stream().map(TestCase::getId).toList();
    log.info(
        "User: [{}] is trying to update json with group and title: [{}] - testCaseIds: {}",
        userName,
        measureId,
        testCaseIds);
    try {
      List<LockInfo> failedLocks =
          testCaseLockService.lockAllTestCases(measureId, testCaseIds, userName);
      if (!failedLocks.isEmpty()) {
        // Handle failed locks
        testCases =
            testCases.stream()
                .filter(
                    tc -> {
                      boolean isNotFailedLock =
                          failedLocks.stream()
                              .noneMatch(lock -> lock.getLockedId().equals(tc.getId()));
                      if (!isNotFailedLock) {
                        failedTestCases.add(
                            TestCaseServiceUtil.getTestCaseDisplayName(
                                tc.getSeries(), tc.getTitle()));
                      }
                      return isNotFailedLock;
                    })
                .toList();
      }
      // Process remaining test cases
      for (TestCase testCase : testCases) {
        try {
          if (StringUtils.isBlank(testCase.getJson())) {
            failedTestCases.add(
                TestCaseServiceUtil.getTestCaseDisplayName(
                    testCase.getSeries(), testCase.getTitle()));
            continue;
          }
          String updatedJson =
              TestCaseServiceUtil.parseAndUpdateJsonWithGroupAndTitle(
                  testCase.getJson(), testCase.getSeries(), testCase.getTitle());
          testCase.setJson(updatedJson);
          updateTestCase(testCase, measureId, userName, accessToken);
          updatedTestCases.add(
              TestCaseServiceUtil.getTestCaseDisplayName(
                  testCase.getSeries(), testCase.getTitle()));
        } catch (Exception e) {
          log.error("Failed to update Test Case [{}]: {}", testCase.getId(), e.getMessage());
          failedTestCases.add(
              TestCaseServiceUtil.getTestCaseDisplayName(
                  testCase.getSeries(), testCase.getTitle()));
        }
      }
    } finally {
      testCaseLockService.unlockAllTestCases(testCaseIds, userName);
    }
    response.put("updated", updatedTestCases);
    response.put("failed", failedTestCases);
    log.info("Updated test cases JSON with group and title: {}", updatedTestCases);
    log.info("Failed updating test cases JSON with group and title: {}", failedTestCases);
    return response;
  }
}
