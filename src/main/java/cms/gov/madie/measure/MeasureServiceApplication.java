package cms.gov.madie.measure;

import cms.gov.madie.measure.services.LogInterceptor;
import gov.cms.madie.models.validators.ValidLibraryNameValidator;
import io.mongock.runner.springboot.EnableMongock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
@EnableMongock
@EnableCaching
@EnableScheduling
public class MeasureServiceApplication {

  @Value("${madie.async.core-pool-size}")
  private int corePoolSize;

  @Value("${madie.async.queue-size}")
  private int queueCapacity;

  @Value("${madie.async.max-pool-size}")
  private int maxPoolSize;

  @Value("${madie.async.shutdown-wait-seconds}")
  private int shutdownWaitSeconds;

  public static void main(String[] args) {
    SpringApplication.run(MeasureServiceApplication.class, args);
  }

  /**
   * ThreadPoolExecutor config
   *
   * <ul>
   *   <li>CorePoolSize - Number of concurrent threads.
   *   <li>MaxPoolSize - Max number of threads that will be created if the queue is full.
   *   <li>KeepAliveTime - Number of seconds a thread will be active, if no new task is submitted it
   *       will be terminated.
   *   <li>QueueCapacity - New Tasks will be added to Queue if the number of tasks are above
   *       CorePoolSize.
   *   <li>AwaitTermination - How long in either seconds or millis to wait for enqueued tasks to
   *       complete
   *   <li>WaitForTasksToCompleteOnShutdown - True, new requests will be rejected, and graceful
   *       shutdown will be blocked until current tasks finish execution.
   * </ul>
   *
   * <br>
   * If the backing queue is filled (unbounded queues don't have an upper limit) then the
   * CorePoolSize will increase from the CorePoolSize until it reaches MaximumPoolSize. At startup,
   * the configured number of CorePoolSize concurrent tasks will be executed. The MaxPoolSize count
   * of threads will not be created until we have surpassed the QueueCapacity count.
   */
  @Bean
  ThreadPoolTaskExecutor saveExecutor() {
    ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();

    taskExecutor.setCorePoolSize(corePoolSize);
    taskExecutor.setMaxPoolSize(maxPoolSize);
    // Any positive value of QueueCapacity will lead to a LinkedBlockingQueue instance;
    // any other value will lead to a SynchronousQueue instance.
    taskExecutor.setQueueCapacity(queueCapacity);
    taskExecutor.setAwaitTerminationSeconds(shutdownWaitSeconds);
    taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
    return taskExecutor;
  }

  /*
   * testCaseValidationExecutor for imported test cases
   */
  @Bean
  ThreadPoolTaskExecutor importExecutor() {
    ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();

    taskExecutor.setCorePoolSize(corePoolSize);
    taskExecutor.setMaxPoolSize(maxPoolSize);
    taskExecutor.setQueueCapacity(queueCapacity);
    taskExecutor.setAwaitTerminationSeconds(shutdownWaitSeconds);
    taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
    return taskExecutor;
  }

  @Bean
  public ValidLibraryNameValidator libraryNameValidator() {
    return new ValidLibraryNameValidator();
  }

  @Bean
  public WebMvcConfigurer corsConfigurer(@Autowired LogInterceptor logInterceptor) {
    return new WebMvcConfigurer() {

      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        WebMvcConfigurer.super.addInterceptors(registry);
        registry.addInterceptor(logInterceptor);
      }

      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/**")
            .allowedMethods("PUT", "POST", "GET", "DELETE", "PATCH")
            .allowedOrigins(
                "http://localhost:9000",
                "https://dev-madie.hcqis.org",
                "https://test-madie.hcqis.org",
                "https://impl-madie.hcqis.org",
                "https://madie.cms.gov");
      }
    };
  }
}
