package cms.gov.madie.measure.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Getter
@Configuration
public class FhirServicesConfig {

  @Value("${madie.fhir.service.base-url}")
  private String madieFhirServiceBaseUrl;

  @Value("${madie.fhir.service.hapi-fhir.measures.bundle-uri}")
  private String madieFhirServiceMeasuresBundleUri;

  @Bean
  public RestTemplate fhirServicesRestTemplate() {
    return new RestTemplate();
  }
}
