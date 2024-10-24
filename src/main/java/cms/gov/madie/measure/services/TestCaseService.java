package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.JobStatus;
import cms.gov.madie.measure.dto.MadieFeatureFlag;
import cms.gov.madie.measure.dto.MeasureTestCaseValidationReport;
import cms.gov.madie.measure.dto.TestCaseValidationReport;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.*;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.JsonUtil;
import cms.gov.madie.measure.utils.TestCaseServiceUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@Slf4j
@Service
public class TestCaseService {

  private final MeasureRepository measureRepository;
  private ActionLogService actionLogService;
  private FhirServicesClient fhirServicesClient;
  private ObjectMapper mapper;
  private MeasureService measureService;
  private TestCaseSequenceService sequenceService;
  private AppConfigService appConfigService;

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
      ObjectMapper mapper,
      MeasureService measureService,
      TestCaseSequenceService sequenceService,
      AppConfigService appConfigService) {
    this.measureRepository = measureRepository;
    this.actionLogService = actionLogService;
    this.fhirServicesClient = fhirServicesClient;
    this.mapper = mapper;
    this.measureService = measureService;
    this.sequenceService = sequenceService;
    this.appConfigService = appConfigService;
  }

  protected TestCase enrichNewTestCase(TestCase testCase, String username, String measureId) {
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
    if (appConfigService.isFlagEnabled(MadieFeatureFlag.TEST_CASE_ID)) {
      enrichedTestCase.setCaseNumber(sequenceService.generateSequence(measureId));
    }
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
            .filter(tc -> !tc.getId().equals(testCase.getId()))
            .map(tc -> StringUtils.deleteWhitespace(tc.getTitle() + tc.getSeries()))
            .anyMatch(existingName -> existingName.equalsIgnoreCase(newName));
    if (matchesExistingTestCaseName) {
      throw new DuplicateTestCaseNameException();
    }
  }

  public TestCase persistTestCase(
      TestCase testCase, String measureId, String username, String accessToken) {
    final Measure measure = findMeasureById(measureId);
    if (!measure.getMeasureMetaData().isDraft()) {
      throw new InvalidDraftStatusException(measure.getId());
    }

    verifyUniqueTestCaseName(testCase, measure);

    defaultTestCaseJsonForQdmMeasure(testCase, measure);
    checkTestCaseSpecialCharacters(testCase);

    TestCase enrichedTestCase = enrichNewTestCase(testCase, username, measureId);
    enrichedTestCase =
        validateTestCaseAsResource(
            enrichedTestCase, ModelType.valueOfName(measure.getModel()), accessToken);

    if (measure.getTestCases() == null) {
      measure.setTestCases(List.of(enrichedTestCase));
    } else {
      measure.getTestCases().add(enrichedTestCase);
    }

    measureRepository.save(measure);

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
    final Measure measure = findMeasureById(measureId);
    if (!measure.getMeasureMetaData().isDraft()) {
      throw new InvalidDraftStatusException(measure.getId());
    }

    List<TestCase> enrichedTestCases = new ArrayList<>(newTestCases.size());
    for (TestCase testCase : newTestCases) {
      checkTestCaseSpecialCharacters(testCase);
      TestCase enriched = enrichNewTestCase(testCase, username, measureId);
      enriched =
          validateTestCaseAsResource(
              enriched, ModelType.valueOfName(measure.getModel()), accessToken);
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
            validatedTestCases.stream()
                .collect(Collectors.toMap(TestCase::getId, Function.identity()));
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
        validateTestCasesAsResources(
            measure.getTestCases(), ModelType.valueOfName(measure.getModel()), accessToken);
    measure.setTestCases(validatedTestCases);
    measureRepository.save(measure);
    return validatedTestCases;
  }

  public List<TestCase> validateTestCasesAsResources(
      final List<TestCase> testCases, final ModelType modelType, final String accessToken) {
    List<TestCase> validatedTestCases = new ArrayList<>();

    if (!isEmpty(testCases)) {
      validatedTestCases =
          testCases.stream()
              .map(testCase -> validateTestCaseAsResource(testCase, modelType, accessToken))
              .collect(Collectors.toList());
    }

    return validatedTestCases;
  }

  public TestCase validateTestCaseAsResource(
      final TestCase testCase, final ModelType modelType, final String accessToken) {
    if (ModelType.QDM_5_6.equals(modelType)) {
      return testCase == null
          ? null
          : testCase.toBuilder().validResource(JsonUtil.isValidJson(testCase.getJson())).build();
    } else {
      final HapiOperationOutcome hapiOperationOutcome =
          validateTestCaseJson(testCase, modelType, accessToken);
      return testCase == null
          ? null
          : testCase.toBuilder()
              .hapiOperationOutcome(hapiOperationOutcome)
              .validResource(hapiOperationOutcome != null && hapiOperationOutcome.isSuccessful())
              .build();
    }
  }

  public TestCase updateTestCase(
      TestCase testCase, String measureId, String username, String accessToken) {
    Measure measure = measureService.findMeasureById(measureId);
    if (measure == null) {
      throw new ResourceNotFoundException("Measure", measureId);
    }

    if (!measure.getMeasureMetaData().isDraft()) {
      throw new InvalidDraftStatusException(measure.getId());
    }
    checkTestCaseSpecialCharacters(testCase);
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
      // assure patientId is not overwritten
      testCase.setPatientId(existing.getPatientId());
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
    TestCase validatedTestCase =
        validateTestCaseAsResource(
            testCase, ModelType.valueOfName(measure.getModel()), accessToken);
    if (ModelType.QI_CORE.getValue().equalsIgnoreCase(measure.getModel())
        && StringUtils.isNotBlank(testCase.getJson())) {
      validatedTestCase.setJson(
          JsonUtil.enforcePatientId(validatedTestCase, madieJsonResourcesBaseUri));
      validatedTestCase.setJson(
          JsonUtil.updateResourceFullUrls(validatedTestCase, madieJsonResourcesBaseUri));
      validatedTestCase.setJson(
          JsonUtil.replacePatientRefs(
              validatedTestCase.getJson(), validatedTestCase.getPatientId().toString()));
    }
    measure.getTestCases().add(validatedTestCase);

    measureRepository.save(measure);
    log.info(
        "User [{}] successfully updated the test case with ID [{}] for the measure with ID[{}] ",
        username,
        testCase.getId(),
        measureId);
    return validatedTestCase;
  }

  public TestCase getTestCase(
      String measureId, String testCaseId, boolean validate, String accessToken) {
    Measure measure = findMeasureById(measureId);
    TestCase testCase =
        Optional.ofNullable(measure.getTestCases())
            .orElseThrow(() -> new ResourceNotFoundException("Test Case", testCaseId))
            .stream()
            .filter(tc -> tc.getId().equals(testCaseId))
            .findFirst()
            .orElse(null);
    if (testCase == null) {
      throw new ResourceNotFoundException("Test Case", testCaseId);
    } else if (validate) {
      testCase.setHapiOperationOutcome(
          validateTestCaseJson(testCase, ModelType.valueOfName(measure.getModel()), accessToken));
    }
    return testCase;
  }

  public List<TestCase> findTestCasesByMeasureId(String measureId) {
    return findMeasureById(measureId).getTestCases();
  }

  public String deleteTestCase(String measureId, String testCaseId, String username) {
    if (StringUtils.isBlank(testCaseId) || StringUtils.isBlank(measureId)) {
      log.info("Test case/Measure Id cannot be null");
      throw new InvalidIdException("Test case cannot be deleted, please contact the helpdesk");
    }
    Measure measure = findMeasureById(measureId);
    if (!measure.getMeasureMetaData().isDraft()) {
      throw new InvalidDraftStatusException(measure.getId());
    }
    measureService.verifyAuthorization(username, measure);
    if (isEmpty(measure.getTestCases())) {
      log.info("Measure with ID [{}] doesn't have any test cases", measureId);
      throw new InvalidIdException("Test case cannot be deleted, please contact the helpdesk");
    }
    List<TestCase> remainingTestCases =
        measure.getTestCases().stream().filter(g -> !g.getId().equals(testCaseId)).toList();
    // to check if given test case id is present
    if (remainingTestCases.size() == measure.getTestCases().size()) {
      log.info(
          "Measure with ID [{}] doesn't have any test case with ID [{}]", measureId, testCaseId);
      throw new InvalidIdException("Test case cannot be deleted, please contact the helpdesk");
    }
    measure.setTestCases(remainingTestCases);
    log.info(
        "User [{}] has successfully deleted a test case with Id [{}] from measure [{}]",
        username,
        testCaseId,
        measureId);
    measureRepository.save(measure);
    if (isEmpty(remainingTestCases)
        && appConfigService.isFlagEnabled(MadieFeatureFlag.TEST_CASE_ID)) {
      sequenceService.resetSequence(measureId);
    }
    return "Test case deleted successfully: " + testCaseId;
  }

  public String deleteTestCases(String measureId, List<String> testCaseIds, String username) {
    if (isEmpty(testCaseIds) || StringUtils.isBlank(measureId)) {
      log.info("Test case Ids or Measure Id is Empty");
      throw new InvalidIdException("Test cases cannot be deleted, please contact the helpdesk");
    }
    Measure measure = findMeasureById(measureId);
    if (!measure.getMeasureMetaData().isDraft()) {
      throw new InvalidDraftStatusException(measure.getId());
    }
    measureService.verifyAuthorization(username, measure);
    if (isEmpty(measure.getTestCases())) {
      log.info("Measure with ID [{}] doesn't have any test cases", measureId);
      throw new InvalidIdException(
          "Measure {} doesn't have any existing test cases to delete", measureId);
    }
    List<TestCase> deletedTestCases =
        measure.getTestCases().stream().filter(tc -> testCaseIds.contains(tc.getId())).toList();
    List<TestCase> remainingTestCases =
        measure.getTestCases().stream().filter(tc -> !testCaseIds.contains(tc.getId())).toList();
    measure.setTestCases(remainingTestCases);
    measureRepository.save(measure);

    if (appConfigService.isFlagEnabled(MadieFeatureFlag.TEST_CASE_ID)) {
      sequenceService.resetSequence(measureId);
    }

    List<String> notDeletedTestCases =
        testCaseIds.stream()
            .filter(
                id -> deletedTestCases.stream().noneMatch(tc -> tc.getId().equalsIgnoreCase(id)))
            .toList();
    if (!isEmpty(notDeletedTestCases)) {
      log.info(
          "User [{}] was unable to delete following test cases with Ids [{}] from measure [{}]",
          username,
          String.join(", ", notDeletedTestCases),
          measureId);
      return "Successfully deleted provided test cases except [ "
          + String.join(", ", notDeletedTestCases)
          + " ]";
    }
    log.info(
        "User [{}] has successfully deleted following test cases with Ids [{}] from measure [{}]",
        username,
        String.join(", ", testCaseIds),
        measureId);
    return "Successfully deleted provided test cases";
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
    Measure measure = findMeasureById(measureId);
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
              TestCaseImportOutcome outCome = checkErrorSpecialChar(model, testCaseImportRequest);
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
      String familyName = getPatientFamilyName(model, testCaseImportRequest.getJson());
      String givenName = getPatientGivenName(model, testCaseImportRequest.getJson());
      log.info("Test Case title + Test Case Group:  {}", givenName + " " + familyName);
      if (StringUtils.isBlank(givenName)) {
        return buildTestCaseImportOutcome(
            testCaseImportRequest.getPatientId(), false, "Test Case Title is required.");
      }
      TestCase newTestCase =
          TestCase.builder()
              .title(getTitle(testCaseImportRequest, givenName))
              .series(getSeries(testCaseImportRequest, familyName))
              .patientId(testCaseImportRequest.getPatientId())
              .build();
      if (appConfigService.isFlagEnabled(MadieFeatureFlag.TEST_CASE_ID)) {
        newTestCase.setCaseNumber(sequenceService.generateSequence(measure.getId()));
      }
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
    } catch (JsonProcessingException ex) {
      log.info(
          "User {} is unable to import test case with patient id : "
              + "{} because of JsonProcessingException: "
              + ex.getMessage(),
          userName,
          testCaseImportRequest.getPatientId());
      return buildTestCaseImportOutcome(
          testCaseImportRequest.getPatientId(),
          false,
          "Error while processing Test Case JSON. Please make sure Test Case JSON is valid.");
    }
  }

  public String getPatientFamilyName(String model, String json) throws JsonProcessingException {
    String patientFamilyName = null;
    if (ModelType.QI_CORE.getValue().equalsIgnoreCase(model)) {
      patientFamilyName = JsonUtil.getPatientName(json, "family");
    } else if ((ModelType.QDM_5_6.getValue().equalsIgnoreCase(model))) {
      patientFamilyName = JsonUtil.getPatientNameQdm(json, "familyName");
    }
    return patientFamilyName;
  }

  public String getPatientGivenName(String model, String json) throws JsonProcessingException {
    String patientGivenName = null;
    if (ModelType.QI_CORE.getValue().equalsIgnoreCase(model)) {
      patientGivenName = JsonUtil.getPatientName(json, "given");
    } else if ((ModelType.QDM_5_6.getValue().equalsIgnoreCase(model))) {
      patientGivenName = JsonUtil.getPatientNameQdm(json, "givenNames");
    }
    return patientGivenName;
  }

  private List<TestCaseGroupPopulation> getTestCaseGroupPopulationsFromImportRequest(
      String model, String json, Measure measure) throws JsonProcessingException {
    List<TestCaseGroupPopulation> testCaseGroupPopulations = null;
    if (ModelType.QI_CORE.getValue().equalsIgnoreCase(model)) {
      testCaseGroupPopulations = JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(json);
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
    try {
      existingTestCase.setDescription(
          getDescription(model, testCaseImportRequest.getJson(), testCaseImportRequest));
      existingTestCase.setJson(getJson(model, testCaseImportRequest.getJson()));
      TestCase updatedTestCase = updateTestCase(existingTestCase, measureId, userName, accessToken);
      log.info(
          "User {} successfully imported test case with patient id : {}",
          userName,
          updatedTestCase.getPatientId());
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
    } catch (JsonProcessingException e) {
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
          formatErrorMessage(e));
      failureOutcome.setMessage(formatErrorMessage(e));
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
      throws JsonProcessingException {
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

  private String getJson(String model, String json) throws JsonProcessingException {
    String jsonFromImportRequest = null;
    if (ModelType.QI_CORE.getValue().equalsIgnoreCase(model)) {
      jsonFromImportRequest = JsonUtil.removeMeasureReportFromJson(json);
    } else if (ModelType.QDM_5_6.getValue().equalsIgnoreCase(model)) {
      jsonFromImportRequest = JsonUtil.getTestCaseJson(json);
    }
    return jsonFromImportRequest;
  }

  private String formatErrorMessage(Exception e) {
    return e.getClass().getSimpleName().equals("DuplicateTestCaseNameException")
        ? "The Test Case Group and Title are already used in another test case on this "
            + "measure. The combination must be unique (case insensitive,"
            + " spaces ignored) across all test cases associated with the measure."
        : e.getMessage();
  }

  public Measure findMeasureById(String measureId) {
    Measure measure = measureRepository.findById(measureId).orElse(null);
    if (measure == null) {
      log.info("Could not find Measure with id: {}", measureId);
      throw new ResourceNotFoundException("Measure", measureId);
    }
    return measure;
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
        .collect(Collectors.toList());
  }

  public HapiOperationOutcome validateTestCaseJson(
      TestCase testCase, ModelType modelType, String accessToken) {
    if (testCase == null || StringUtils.isBlank(testCase.getJson())) {
      return null;
    }

    try {
      return fhirServicesClient
          .validateBundle(testCase.getJson(), modelType, accessToken)
          .getBody();
    } catch (HttpClientErrorException ex) {
      log.warn("HAPI FHIR returned response code [{}]", ex.getRawStatusCode(), ex);
      try {
        return HapiOperationOutcome.builder()
            .code(ex.getRawStatusCode())
            .message("Unable to validate test case JSON due to errors")
            .outcomeResponse(mapper.readValue(ex.getResponseBodyAsString(), Object.class))
            .build();
      } catch (JsonProcessingException e) {
        return handleJsonProcessingException();
      }
    } catch (Exception ex) {
      log.error("Exception occurred validating bundle with FHIR Service:", ex);
      return HapiOperationOutcome.builder()
          .code(500)
          .message("An unknown exception occurred while validating the test case JSON.")
          .build();
    }
  }

  public List<TestCase> shiftMultiQiCoreTestCaseDates(
      List<TestCase> testCases, int shifted, String accessToken) {
    if (isEmpty(testCases)) {
      return Collections.emptyList();
    }
    return fhirServicesClient.shiftTestCaseDates(testCases, shifted, accessToken).getBody();
  }

  public TestCase shiftQiCoreTestCaseDates(TestCase testCase, int shifted, String accessToken) {
    if (testCase == null) {
      return null;
    }
    List<TestCase> shiftedTestCases =
        fhirServicesClient.shiftTestCaseDates(List.of(testCase), shifted, accessToken).getBody();

    if (isNotEmpty(shiftedTestCases)) {
      return shiftedTestCases.get(0);
    }
    return null;
  }

  private HapiOperationOutcome handleJsonProcessingException() {
    return HapiOperationOutcome.builder()
        .code(500)
        .message(
            "Unable to validate test case JSON due to errors, "
                + "but outcome not able to be interpreted!")
        .build();
  }

  protected void defaultTestCaseJsonForQdmMeasure(TestCase testCase, Measure measure) {
    if (ModelType.QDM_5_6.getValue().equalsIgnoreCase(measure.getModel())
        && StringUtils.isBlank(testCase.getJson())) {
      String objectId = ObjectId.get().toHexString();
      testCase.setJson(QDM_PATIENT.replace("OBJECTID", objectId));
    }
  }

  protected void checkTestCaseSpecialCharacters(TestCase testCase) {
    if (StringUtils.isBlank(testCase.getTitle())) {
      throw new InvalidRequestException("Test Case title is required.");
    }
    Pattern alpahNumeric = Pattern.compile("^[a-zA-Z0-9\s_-]*$");
    Matcher title = alpahNumeric.matcher(testCase.getTitle());
    if (!title.matches()) {
      throw new SpecialCharacterException("Title");
    }
    if (StringUtils.isNotBlank(testCase.getSeries())) {
      Matcher group = alpahNumeric.matcher(testCase.getSeries());
      if (!group.matches()) {
        throw new SpecialCharacterException("Group");
      }
    }
  }

  protected TestCaseImportOutcome checkErrorSpecialChar(
      String model, TestCaseImportRequest testCaseImportRequest) {
    if (ModelType.QDM_5_6.getValue().equalsIgnoreCase(model)) {
      try {
        checkTestCaseSpecialCharacters(
            TestCase.builder()
                .title(
                    testCaseImportRequest.getGivenNames() != null
                        ? testCaseImportRequest.getGivenNames().get(0)
                        : null)
                .series(testCaseImportRequest.getFamilyName())
                .build());
      } catch (InvalidRequestException ex) {
        return TestCaseImportOutcome.builder()
            .patientId(testCaseImportRequest.getPatientId())
            .successful(false)
            .message(ex.getMessage())
            .build();
      } catch (SpecialCharacterException ex) {
        return TestCaseImportOutcome.builder()
            .patientId(testCaseImportRequest.getPatientId())
            .successful(false)
            .message("Test Cases Group or Title cannot contain special characters.")
            .build();
      }
    }
    return null;
  }

  private TestCaseImportOutcome buildTestCaseImportOutcome(
      UUID patientId, boolean successful, String message) {
    return TestCaseImportOutcome.builder()
        .patientId(patientId)
        .successful(successful)
        .message(message)
        .build();
  }
}
