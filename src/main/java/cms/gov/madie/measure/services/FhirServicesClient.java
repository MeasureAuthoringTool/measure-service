package cms.gov.madie.measure.services;

import cms.gov.madie.measure.config.FhirServicesConfig;
import gov.cms.madie.models.measure.Measure;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Slf4j
@Service
@AllArgsConstructor
public class FhirServicesClient {

  private FhirServicesConfig fhirServicesConfig;
  private RestTemplate fhirServicesRestTemplate;

  public String getMeasureBundle(Measure measure, String accessToken) {
    URI uri =
        URI.create(
            fhirServicesConfig.getMadieFhirServiceBaseUrl()
                + fhirServicesConfig.getMadieFhirServiceMeasuresBundleUri());
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    HttpEntity<Measure> measureEntity = new HttpEntity<>(measure, headers);
    return fhirServicesRestTemplate
        .exchange(uri, HttpMethod.PUT, measureEntity, String.class)
        .getBody();
  }

  public ResponseEntity<String> validateBundle(String testCaseJson, String accessToken) {
    URI uri =
        URI.create(
            fhirServicesConfig.getMadieFhirServiceBaseUrl()
                + fhirServicesConfig.getMadieFhirServiceValidateBundleUri());
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    HttpEntity<String> measureEntity = new HttpEntity<>(testCaseJson, headers);
    return fhirServicesRestTemplate.exchange(uri, HttpMethod.POST, measureEntity, String.class);
  }

  public ResponseEntity<String> saveMeasureInHapiFhir(Measure measure, String accessToken) {
    URI uri =
        URI.create(
            fhirServicesConfig.getMadieFhirServiceBaseUrl()
                + fhirServicesConfig.getMadieFhirServiceSaveMeasureUri());
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    HttpEntity<Measure> measureEntity = new HttpEntity<>(measure, headers);
    return fhirServicesRestTemplate.exchange(uri, HttpMethod.POST, measureEntity, String.class);
  }
}
