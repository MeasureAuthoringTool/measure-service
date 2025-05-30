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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

@Slf4j
@Service
public class TestCaseValidationService {

  private final ExecutorService executor;

  private final FhirServicesClient fhirServicesClient;

  private final MeasureRepository measureRepository;

  private final ObjectMapper mapper;

  public TestCaseValidationService(FhirServicesClient fhirServicesClient, MeasureRepository measureRepository, ObjectMapper mapper) {
    this.fhirServicesClient = fhirServicesClient;
    this.measureRepository = measureRepository;
    this.mapper = mapper;
    this.executor =
        new ThreadPoolExecutor(10, 50, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
  }

  @PostConstruct
  public void populateValidationQueue() {
    // TODO Populate the validation queue with test cases marked as "Pending" in the database.
  }

  public void submitValidationTask(
      String measureId, String testCaseId, String accessToken, ModelType modelType) {
    executor.submit(
        () -> {
          try {
            log.info("TestCase Validation Initiated for Test Case Id {} ", testCaseId);
          } catch (Exception e) {
            log.error(
                "Error validating Test Case with Id {} from Measure {} ", testCaseId, measureId);
          }
        });
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
        measure.getId(), testCase.getId(), accessToken, ModelType.valueOfName(measure.getModel()));

    // Return testCase with pending status and null validationOutcome.
    return updatedTestCase;
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
