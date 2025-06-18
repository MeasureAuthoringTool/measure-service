package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.JsonUtil;
import cms.gov.madie.measure.utils.TestCaseServiceUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.HapiOperationOutcome;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.TestCaseValidationStatus;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

/**
 * Asynchronously validates test cases against the FHIR services. Tracking of validation done with
 * TaskId and ValidationStatus.
 *
 * <p>Stages:
 *
 * <ol>
 *   <li>Submission: Caller requests async validation. TaskId: null, validationStatus: PENDING
 *   <li>Enqueue: Validation Task is added to the Executor. TaskId: Generated, validationStatus:
 *       PENDING
 *   <li>Validation: Validation Task is picked up by an open Thread and processed. Most recent Test
 *       Case data is retrieved. ValidationStatus: VALIDATING
 *   <li>Completion: FHIR Service returns validation results. ValidationStatus set to either VALID
 *       or INVALID based on results.
 * </ol>
 */
@Slf4j
@Service
@AllArgsConstructor
public class TestCaseValidationService {

  @Qualifier("saveExecutor")
  private final ThreadPoolTaskExecutor saveExecutor;

  @Qualifier("importExecutor")
  private final ThreadPoolTaskExecutor importExecutor;

  private final FhirServicesClient fhirServicesClient;

  private final MeasureRepository measureRepository;

  private final ObjectMapper mapper;

  @PostConstruct
  public void populateValidationQueue() {
    // TODO Future task, Populate the validation queue with test cases marked
    //  as "Pending" in the database.
  }

  void submitOnSaveValidationTask(
      String measureId, TestCase testCase, String accessToken, ModelType modelType) {
    UUID taskId = UUID.randomUUID();
    log.info(
        "TestCase Validation::submit::{}::{}::{}::{}",
        testCase.getId(),
        taskId,
        Instant.now(),
        saveExecutor.getQueueSize());
    saveExecutor.submit(() -> validate(taskId, measureId, testCase, modelType, accessToken));
  }

  void submitOnImportValidationTask(
      String measureId, TestCase testCase, String accessToken, ModelType modelType) {
    UUID taskId = UUID.randomUUID();
    log.info(
        "TestCase Validation Import Queue::submit::{}::{}::{}::{}",
        testCase.getId(),
        taskId,
        Instant.now(),
        importExecutor.getQueueSize());
    importExecutor.submit(
        () -> {
          validate(taskId, measureId, testCase, modelType, accessToken);
        });
  }

  void validate(
      UUID taskId,
      String measureId,
      TestCase submittedTestCase,
      ModelType modelType,
      String accessToken) {
    // TODO replace with decorator
    Instant startTime = Instant.now();
    log.info(
        "TestCase Validation::execute::{}::{}::{}::{}",
        submittedTestCase.getId(),
        Thread.currentThread().getId(),
        taskId,
        startTime);
    Measure measure =
        measureRepository.setValidationStatusToValidating(submittedTestCase.getId(), measureId, taskId);
    TestCase currentTestCase =
        measure.getTestCases().stream()
            .filter((tc -> tc.getId().equals(submittedTestCase.getId())))
            .findFirst()
            .orElseThrow(
                () -> {
                  log.error(
                      "TestCase with Id {} not found in Measure with Id {}",
                      submittedTestCase.getId(),
                      measureId);
                  return new ResourceNotFoundException("Test Case", submittedTestCase.getId());
                });
    try {
      // TODO What should happen when fhir-services is down?
      // Oh, also, what do if this sync call takes too long?
      // Consider putting a cache in front of madie-fhir-service's validation to quick return
      // duplicate requests based on the JSON hash.
      HapiOperationOutcome validationOutcome =
          validateTestCaseJson(currentTestCase, modelType, accessToken);
      measureRepository.findAndUpdateValidationResults(
          currentTestCase.getId(), measureId, taskId, validationOutcome);
      Instant stopTime = Instant.now();
      log.info(
          "TestCase Validation::completed::{}::{}::{}::{}",
          currentTestCase.getId(),
          taskId,
          Duration.between(startTime, stopTime),
          saveExecutor.getQueueSize());
    } catch (Exception e) {
      log.error(
          "Error validating Test Case with Id {} from Measure {}",
          submittedTestCase.getId(),
          measureId,
          e);
      measureRepository.setValidationStatusToNotComplete(
          currentTestCase.getId(), measureId, TestCaseValidationStatus.NOT_COMPLETE);
    }
  }

  public TestCase validateResourceAsynchronously(
      Measure measure, TestCase testCase, String source, String accessToken) {
    Measure updatedMeasure =
        measureRepository.setValidationStatusToPending(testCase.getId(), measure.getId());

    // If the measure is null, the test case has already has PENDING status.
    if (updatedMeasure == null) {
      log.info(
          "Test Case with Id {} already in validation queue for Measure with Id {}",
          testCase.getId(),
          measure.getId());
      return testCase;
    }

    TestCase updatedTestCase =
        updatedMeasure.getTestCases().stream()
            .filter((tc -> tc.getId().equals(testCase.getId())))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Test Case", testCase.getId()));

    if (source.equals(TestCaseServiceUtil.SAVE_VALIDATION_QUEUE)) {
      submitOnSaveValidationTask(
          measure.getId(), updatedTestCase, accessToken, ModelType.valueOfName(measure.getModel()));
    } else if (source.equals(TestCaseServiceUtil.IMPORT_VALIDATION_QUEUE)) {
      submitOnImportValidationTask(
          measure.getId(), updatedTestCase, accessToken, ModelType.valueOfName(measure.getModel()));
    }

    return updatedTestCase; // Return testCase with pending status and set validationOutcome to null
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
    if (testCase == null || StringUtils.isBlank(testCase.getJson())) {
      return testCase;
    }
    if (ModelType.QDM_5_6.equals(modelType)) {
      return testCase.toBuilder().validResource(JsonUtil.isValidJson(testCase.getJson())).build();
    } else {
      final HapiOperationOutcome hapiOperationOutcome =
          validateTestCaseJson(testCase, modelType, accessToken);
      return testCase.toBuilder()
          .hapiOperationOutcome(hapiOperationOutcome)
          .validResource(hapiOperationOutcome != null && hapiOperationOutcome.isSuccessful())
          .build();
    }
  }

  HapiOperationOutcome validateTestCaseJson(
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

  private HapiOperationOutcome handleJsonProcessingException() {
    return HapiOperationOutcome.builder()
        .code(500)
        .message(
            "Unable to validate test case JSON due to errors, "
                + "but outcome not able to be interpreted!")
        .build();
  }
}
