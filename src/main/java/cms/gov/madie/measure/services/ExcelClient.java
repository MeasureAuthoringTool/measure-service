package cms.gov.madie.measure.services;

import cms.gov.madie.measure.config.ExcelConfig;
import cms.gov.madie.measure.dto.excel.TestCaseExcelExportDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

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

    URI uri = URI.create(excelConfig.getExcelExportServiceBaseUrl() + excelConfig.getGetExcelUrn());

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    headers.set(HttpHeaders.CONTENT_TYPE, "application/json");

    HttpEntity<Map<String, List<TestCaseExcelExportDTO>>> entity =
        new HttpEntity<>(requestBody, headers);

    ResponseEntity<byte[]> response =
        excelRestTemplate.exchange(uri, HttpMethod.PUT, entity, byte[].class);

    return response.getBody();
  }
}
