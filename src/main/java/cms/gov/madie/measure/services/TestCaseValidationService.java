package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.JsonUtil;
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

@Slf4j
@Service
@AllArgsConstructor
public class TestCaseValidationService {

  @Qualifier("testCaseValidationExecutor")
  private final ThreadPoolTaskExecutor taskExecutor;

  private final FhirServicesClient fhirServicesClient;

  private final MeasureRepository measureRepository;

  private final ObjectMapper mapper;

  @PostConstruct
  public void populateValidationQueue() {
    // TODO Future task, Populate the validation queue with test cases marked
    //  as "Pending" in the database.
  }

  void submitValidationTask(
      String measureId, TestCase testCase, String accessToken, ModelType modelType) {
    UUID taskId = UUID.randomUUID();
    log.info(
        "TestCase Validation::submit::{}::{}::{}::{}",
        testCase.getId(),
        taskId,
        Instant.now(),
        taskExecutor.getQueueSize());
    taskExecutor.submit(() -> validate(taskId, measureId, testCase, modelType, accessToken));
  }

  TestCase validate(
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
    TestCase currentTestCase =
        measureRepository
            .findAndUpdateValidationStatus(
                submittedTestCase.getId(), measureId, TestCaseValidationStatus.VALIDATING)
            .getTestCases()
            .stream()
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
    log.info(
        "TestCase Validation::taskId::{}::lastModifiedDateTime::{}::",
        taskId,
        submittedTestCase.getLastModifiedAt());
    try {
      // TODO What should happen when fhir-services is down?
      validateTestCaseJson(currentTestCase, modelType, accessToken);
      Instant stopTime = Instant.now();
      log.info(
          "TestCase Validation::completed::{}::{}::{}::{}",
          currentTestCase.getId(),
          taskId,
          Duration.between(startTime, stopTime),
          taskExecutor.getQueueSize());
      return currentTestCase;
    } catch (Exception e) {
      log.error(
          "Error validating Test Case with Id {} from Measure {} ",
          currentTestCase.getId(),
          measureId);
    }
    return submittedTestCase;
  }

  public TestCase validateResourceAsynchronously(
      Measure measure, TestCase testCase, String accessToken) {
    TestCase updatedTestCase =
        measureRepository
            .findAndUpdateValidationStatus(
                testCase.getId(), measure.getId(), TestCaseValidationStatus.PENDING)
            .getTestCases()
            .stream()
            .filter((tc -> tc.getId().equals(testCase.getId())))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Test Case", testCase.getId()));
    submitValidationTask(
        measure.getId(), updatedTestCase, accessToken, ModelType.valueOfName(measure.getModel()));
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
