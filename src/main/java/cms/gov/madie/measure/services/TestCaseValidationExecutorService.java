package cms.gov.madie.measure.services;

import gov.cms.madie.models.common.ModelType;
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

  /**
   * ThreadPoolExecutor config corePoolSize - this handles the number of concurrent threads
   * maximumPoolSize - The max threshold of threads that will be created if the queue is full
   * KeepAliveTime - Number of seconds a thread will be active, if no new task is submitted it will
   * be terminated Unbounded Queue - New Tasks will be added to Queue if number of tasks are above
   * CorePoolSize. if Queue is filled (unbounded queues doesn't have an upper limit) then executor
   * increase the CorePoolSize from 10 until it reaches 50 to handle extra load. So technically only
   * 10 concurrent tasks will be executed, Those 50 threads will not be created until we have a cap
   * on Queue size.
   */
  public TestCaseValidationExecutorService() {
    this.executor =
        new ThreadPoolExecutor(10, 50, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
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
}
