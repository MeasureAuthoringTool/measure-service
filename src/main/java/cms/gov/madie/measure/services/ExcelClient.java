package cms.gov.madie.measure.services;

import cms.gov.madie.measure.config.ExcelConfig;
import cms.gov.madie.measure.dto.excel.MeasureAccessReportDTO;
import cms.gov.madie.measure.dto.excel.TestCaseExcelExportDTO;
import cms.gov.madie.measure.exceptions.InternalServerException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Service
@AllArgsConstructor
public class ExcelClient {

  private final RestTemplate excelRestTemplate;
  private final ExcelConfig excelConfig;

  public byte[] exportExcel(
      String measureId, List<TestCaseExcelExportDTO> testCaseExcelExportDtos, String accessToken) {
    Map<String, List<TestCaseExcelExportDTO>> requestBody =
        Map.of("testCaseExcelExportDtos", testCaseExcelExportDtos);

    URI uri =
        URI.create(
            excelConfig.getExcelExportServiceBaseUrl()
                + excelConfig.getTestCasesExcelExportApiPath());

    HttpEntity<Map<String, List<TestCaseExcelExportDTO>>> entity =
        new HttpEntity<>(requestBody, buildHeaders(accessToken));

    return executeRequest(
        uri,
        entity,
        () -> "Failed to export Excel for measure " + measureId,
        "An error occurred while exporting Excel.");
  }

  public byte[] getSharedAccessReportForMeasures(
      List<MeasureAccessReportDTO> measureAccessReportDTOS, String accessToken) {
    URI uri =
        URI.create(
            excelConfig.getExcelExportServiceBaseUrl()
                + excelConfig.getMeasureSharedAccessReportApiPath());

    HttpEntity<List<MeasureAccessReportDTO>> entity =
        new HttpEntity<>(measureAccessReportDTOS, buildHeaders(accessToken));

    return executeRequest(
        uri,
        entity,
        () ->
            "Failed to export access report for measures "
                + measureAccessReportDTOS.stream()
                    .map(MeasureAccessReportDTO::getId)
                    .collect(java.util.stream.Collectors.joining(", ")),
        "An error occurred while generating measure access report.");
  }

  private HttpHeaders buildHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
    return headers;
  }

  private byte[] executeRequest(
      URI uri, HttpEntity<?> entity, Supplier<String> logMessageSupplier, String errorMessage) {
    try {
      ResponseEntity<byte[]> response =
          excelRestTemplate.exchange(uri, HttpMethod.PUT, entity, byte[].class);
      return response.getBody();
    } catch (RestClientException ex) {
      log.error("{}: {}", logMessageSupplier.get(), ex.getMessage(), ex);
      throw new InternalServerException(errorMessage);
    }
  }
}
