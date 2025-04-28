package cms.gov.madie.measure.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.HapiOperationOutcome;
import gov.cms.madie.models.measure.TestCase;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@AllArgsConstructor
public class AsyncService {
  private FhirServicesClient fhirServicesClient;
  private ObjectMapper mapper;

  // Async call to validates test case bundle, returns a Future which needs to be resolved
  @Async
  public CompletableFuture<HapiOperationOutcome> validateTestCaseJsonAsync(
      TestCase testCase, ModelType modelType, String accessToken) {

    if (testCase == null || StringUtils.isBlank(testCase.getJson())) {
      return CompletableFuture.completedFuture(null);
    }

    try {
      Thread.sleep(3000);
      // calls madie-fhir-service
      HapiOperationOutcome outcome =
          fhirServicesClient.validateBundle(testCase.getJson(), modelType, accessToken).getBody();

      return CompletableFuture.completedFuture(outcome);

    } catch (HttpClientErrorException ex) {
      log.warn("HAPI FHIR returned response code [{}]", ex.getRawStatusCode(), ex);
      try {
        HapiOperationOutcome outcome =
            HapiOperationOutcome.builder()
                .code(ex.getRawStatusCode())
                .message("Unable to validate test case JSON due to errors")
                .outcomeResponse(mapper.readValue(ex.getResponseBodyAsString(), Object.class))
                .build();
        return CompletableFuture.completedFuture(outcome);
      } catch (JsonProcessingException e) {
        return CompletableFuture.completedFuture(handleJsonProcessingException());
      }

    } catch (Exception ex) {
      log.error("Exception occurred validating bundle with FHIR Service:", ex);
      return CompletableFuture.completedFuture(
          HapiOperationOutcome.builder()
              .code(500)
              .message("An unknown exception occurred while validating the test case JSON.")
              .build());
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
