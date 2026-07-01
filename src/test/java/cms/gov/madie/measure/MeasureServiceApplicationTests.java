package cms.gov.madie.measure;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import cms.gov.madie.measure.resources.MeasureController;

@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {MongoAutoConfiguration.class, DataMongoAutoConfiguration.class})
@SpringBootTest
class MeasureServiceApplicationTests {
  @Autowired private MeasureController controller;

  @Test
  /*
   * Note the use of @SpringBootTest annotation which tells Spring Boot to look
   * for a main configuration class (one with @SpringBootApplication, for
   * instance) and use that to start a Spring application context. Empty
   * contextLoads() is a test to verify if the application is able to load Spring
   * context successfully or not.
   * https://stackoverflow.com/questions/49887939/what-is-the-use-of-contextloads-
   * method-in-spring-boot-junit-testcases
   */
  void contextLoads() {
    // if the Spring App loads, this will pass
    assertNotNull(controller);
  }
}
