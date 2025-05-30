package cms.gov.madie.measure.services;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;
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

  Future<TestCase> submitValidationTask(
      String measureId, TestCase testCase, String accessToken, ModelType modelType) {
    UUID taskId = UUID.randomUUID();
    log.info(
        "TestCase Validation::submit::{}::{}::{}::{}",
        testCase.getId(),
        taskId,
        Instant.now(),
        taskExecutor.getQueueSize());
    return taskExecutor.submit(() -> validate(taskId, measureId, testCase, modelType, accessToken));
  }

  TestCase validate(
      UUID taskId, String measureId, TestCase testCase, ModelType modelType, String accessToken) {
    Instant startTime = Instant.now();
    log.info(
        "TestCase Validation::execute::{}::{}::{}::{}",
        testCase.getId(),
        Thread.currentThread().getId(),
        taskId,
        startTime);
    try {
      TestCase latestTestCase =
          setValidationStatus(measureId, testCase.getId(), TestCaseValidationStatus.VALIDATING);
      // TODO What should happen when fhir-services is down?
      HapiOperationOutcome validationOutcome =
          validateTestCaseJson(latestTestCase, modelType, accessToken);
      setValidationStatus(
          measureId,
          testCase.getId(),
          validationOutcome.isSuccessful()
              ? TestCaseValidationStatus.VALID
              : TestCaseValidationStatus.INVALID);
      testCase.toBuilder()
          .hapiOperationOutcome(validationOutcome)
          .validResource(validationOutcome.isSuccessful())
          .build();
      // TODO MAT-8601: save the test case with the validation outcome
      Instant stopTime = Instant.now();
      log.info(
          "TestCase Validation::completed::{}::{}::{}::{}",
          testCase.getId(),
          taskId,
          Duration.between(startTime, stopTime),
          taskExecutor.getQueueSize());
    } catch (Exception e) {
      log.error(
          "Error validating Test Case with Id {} from Measure {} ", testCase.getId(), measureId);
    }
    return testCase;
  }

  private TestCase setValidationStatus(
      String measureId, String testCaseId, TestCaseValidationStatus status) {
    Optional<Measure> measure = measureRepository.findById(measureId);
    if (measure.isPresent()) {
      Optional<TestCase> updatedTestCase =
          measure.get().getTestCases().stream()
              .filter(testCase -> testCase.getId().equals(testCaseId))
              .findFirst();
      updatedTestCase.ifPresent(
          testCase -> {
            testCase.setTestCaseValidationStatus(status);
            measureRepository.save(measure.get());
          });
      return updatedTestCase.orElse(null);
    }
    return null;
  }

  public TestCase validateResourceAsynchronously(
      Measure measure, TestCase testCase, String accessToken) {
    TestCase updatedTestCase =
        testCase.toBuilder()
            .testCaseValidationStatus(TestCaseValidationStatus.PENDING)
            .hapiOperationOutcome(null)
            .build();
    measure.getTestCases().add(updatedTestCase);
    measureRepository.save(measure);
    submitValidationTask(
        measure.getId(), testCase, accessToken, ModelType.valueOfName(measure.getModel()));
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

  private HapiOperationOutcome handleJsonProcessingException() {
    return HapiOperationOutcome.builder()
        .code(500)
        .message(
            "Unable to validate test case JSON due to errors, "
                + "but outcome not able to be interpreted!")
        .build();
  }
}
