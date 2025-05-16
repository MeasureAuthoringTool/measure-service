package cms.gov.madie.measure.services;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.HapiOperationOutcome;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.TestCaseValidationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TestCaseValidationExecutorService {

  private final ExecutorService executor;
  private final TestCaseService testCaseService;
  private final MeasureRepository measureRepository;

  /**
   * ThreadPoolExecutor config corePoolSize - this handles the number of concurrent threads
   * maximumPoolSize - The max threshold of threads that will be created if the queue is full
   * KeepAliveTime - Number of seconds a thread will be active, if no new task is submitted it will
   * be terminated Unbounded Queue - New Tasks will be added to Queue if number of tasks are above
   * CorePoolSize. if Queue is filled (unbounded queues doesn't have an upper limit) then executor
   * increase the CorePoolSize from 10 until it reaches 50 to handle extra load. So technically only
   * 10 concurrent tasks will be executed. Those 50 threads will not be created until we have a cap
   * on Queue size.
   */
   TestCaseValidationExecutorService(
      TestCaseService testCaseService, MeasureRepository measureRepository) {
    this.executor =
        new ThreadPoolExecutor(10, 50, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    this.testCaseService = testCaseService;
    this.measureRepository = measureRepository;
  }

  void submitValidationTask(
      String measureId, TestCase testCase, String accessToken, ModelType modelType) {
    executor.submit(
        () -> {
          try {
            log.info("TestCase Validation Initiated for Test Case Id {} ", testCase.getId());
            setValidationStatus(measureId, testCase.getId(), TestCaseValidationStatus.PENDING);
            HapiOperationOutcome validationOutcome =
                testCaseService.validateTestCaseJson(testCase, modelType, accessToken);
            setValidationStatus(measureId, testCase.getId(),
                validationOutcome.isSuccessful() ?
                    TestCaseValidationStatus.VALID :
                    TestCaseValidationStatus.INVALID);
            testCase.toBuilder()
                .hapiOperationOutcome(validationOutcome)
                .validResource(validationOutcome.isSuccessful())
                .build();
          } catch (Exception e) {
            log.error(
                "Error validating Test Case with Id {} from Measure {} ",
                testCase.getId(),
                measureId);
          }
        });
  }

  private synchronized void setValidationStatus(
      String measureId, String testCaseId, TestCaseValidationStatus status) {
    measureRepository
        .findById(measureId)
        .ifPresent(
            measure -> {
              measure.getTestCases().stream()
                  .filter(testCase -> testCase.getId().equals(testCaseId))
                  .findFirst()
                  .ifPresent(testCase -> testCase.setTestCaseValidationStatus(status));
              measureRepository.save(measure);
            });
  }
}
