package cms.gov.madie.measure.services;

import cms.gov.madie.measure.config.FhirServicesConfig;
import cms.gov.madie.measure.exceptions.UnsupportedTypeException;
import gov.cms.madie.models.common.BundleType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.dto.ExportDTO;
import gov.cms.madie.models.measure.HapiOperationOutcome;
import gov.cms.madie.models.measure.Measure;
import java.net.URI;
import java.util.List;

import gov.cms.madie.models.measure.TestCase;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@AllArgsConstructor
public class FhirServicesClient {

  private FhirServicesConfig fhirServicesConfig;
  private RestTemplate fhirServicesRestTemplate;

  public String getMeasureBundle(Measure measure, String accessToken, String bundleType) {
    URI uri =
        buildMadieFhirServiceUri(
            bundleType, fhirServicesConfig.getMadieFhirServiceMeasuresBundleUri());

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    HttpEntity<Measure> measureEntity = new HttpEntity<>(measure, headers);
    return fhirServicesRestTemplate
        .exchange(uri, HttpMethod.PUT, measureEntity, String.class)
        .getBody();
  }

  public byte[] getMeasureBundleExport(Measure measure, String accessToken) {
    URI uri =
        URI.create(
            fhirServicesConfig.getMadieFhirServiceBaseUrl()
                + fhirServicesConfig.getMadieFhirServiceMeasureseExportUri());
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    HttpEntity<Measure> measureEntity = new HttpEntity<>(measure, headers);
    return fhirServicesRestTemplate
        .exchange(uri, HttpMethod.PUT, measureEntity, byte[].class)
        .getBody();
  }

  public ResponseEntity<HapiOperationOutcome> validateBundle(
      String testCaseJson, ModelType modelType, String accessToken) {
    if (modelType == null) {
      throw new UnsupportedTypeException("Please provide model type.");
    }

    String modelVersion = modelType.getVersionNumber().replace(".", "-");
    URI uri =
        URI.create(
            fhirServicesConfig.getMadieFhirServiceBaseUrl()
                + fhirServicesConfig
                    .getMadieFhirServiceValidateBundleUri()
                    .replace("$model", modelVersion));
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    HttpEntity<String> measureEntity = new HttpEntity<>(testCaseJson, headers);
    return fhirServicesRestTemplate.exchange(
        uri, HttpMethod.POST, measureEntity, HapiOperationOutcome.class);
  }

  public ResponseEntity<byte[]> getTestCaseExports(
      Measure measure, String accessToken, List<String> testCaseId, String bundleType) {
    URI uri =
        URI.create(
            fhirServicesConfig.getMadieFhirServiceBaseUrl()
                + fhirServicesConfig.madieFhirServiceTestCaseUri
                + "/export-all");
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

    ExportDTO dto =
        ExportDTO.builder()
            .measure(measure)
            .bundleType(BundleType.valueOf(bundleType))
            .testCaseIds(testCaseId)
            .build();

    HttpEntity<ExportDTO> measureEntity = new HttpEntity<>(dto, headers);

    try {
      return fhirServicesRestTemplate.exchange(uri, HttpMethod.PUT, measureEntity, byte[].class);
    } catch (RestClientResponseException ex) {
      return new ResponseEntity<>(
          ex.getResponseBodyAsByteArray(), HttpStatus.valueOf(ex.getStatusCode().value()));
    }
  }

  public ResponseEntity<List<TestCase>> shiftTestCaseDates(
      List<TestCase> testCases, int shifted, String accessToken) {
    URI uri =
        URI.create(
            fhirServicesConfig.getMadieFhirServiceBaseUrl()
                + fhirServicesConfig.getMadieFhirServiceTestCaseUri()
                + "/shift-dates?shifted="
                + shifted);
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    HttpEntity<List<TestCase>> testCasesEntity = new HttpEntity<>(testCases, headers);

    return fhirServicesRestTemplate.exchange(
        uri, HttpMethod.PUT, testCasesEntity, new ParameterizedTypeReference<>() {});
  }

  private URI buildMadieFhirServiceUri(String bundleType, String fhirServiceUri) {
    return UriComponentsBuilder.fromHttpUrl(
            fhirServicesConfig.getMadieFhirServiceBaseUrl() + fhirServiceUri)
        .queryParam("bundleType", bundleType)
        .build()
        .encode()
        .toUri();
  }
}
