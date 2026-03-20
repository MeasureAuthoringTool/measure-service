package cms.gov.madie.measure.services;

import cms.gov.madie.measure.config.ExcelConfig;
import cms.gov.madie.measure.dto.excel.MeasureAccessReportDTO;
import cms.gov.madie.measure.dto.excel.TestCaseExcelExportDTO;
import cms.gov.madie.measure.exceptions.InternalServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelClientTest {

  @Mock private RestTemplate excelRestTemplate;
  @Mock private ExcelConfig excelConfig;
  @InjectMocks private ExcelClient excelClient;

  private static final String BEARER_TOKEN = "Bearer TOKEN";
  private List<TestCaseExcelExportDTO> testCaseExcelExportDtos;
  private List<MeasureAccessReportDTO> measureAccessReportDTOs;

  @BeforeEach
  void setUp() {
    testCaseExcelExportDtos = List.of(new TestCaseExcelExportDTO());
    measureAccessReportDTOs = List.of(MeasureAccessReportDTO.builder().id("measure-id-1").build());

    when(excelConfig.getExcelExportServiceBaseUrl()).thenReturn("http://localhost:3000/api");
  }

  @Test
  void testExportExcelSuccessful() {
    byte[] expectedBytes = "excel-data".getBytes();
    when(excelConfig.getTestCasesExcelExportApiPath()).thenReturn("/excel");
    when(excelRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok(expectedBytes));

    byte[] result = excelClient.exportExcel("measureId", testCaseExcelExportDtos, BEARER_TOKEN);

    assertThat(result, is(equalTo(expectedBytes)));
    verify(excelRestTemplate, times(1))
        .exchange(any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class));
  }

  @Test
  void testExportExcelThrowsInternalServerException() {
    when(excelConfig.getTestCasesExcelExportApiPath()).thenReturn("/excel");
    when(excelRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class)))
        .thenThrow(new RestClientException("connection refused"));

    InternalServerException ex =
        assertThrows(
            InternalServerException.class,
            () -> excelClient.exportExcel("measureId", testCaseExcelExportDtos, BEARER_TOKEN));

    assertThat(ex.getMessage(), is(equalTo("An error occurred while exporting Excel.")));
    verify(excelRestTemplate, times(1))
        .exchange(any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class));
  }

  @Test
  void testGetSharedAccessReportForMeasuresSuccessful() {
    byte[] expectedBytes = "report-data".getBytes();
    when(excelConfig.getMeasureSharedAccessReportApiPath()).thenReturn("/shared-access-report");
    when(excelRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok(expectedBytes));

    byte[] result =
        excelClient.getSharedAccessReportForMeasures(measureAccessReportDTOs, BEARER_TOKEN);

    assertThat(result, is(equalTo(expectedBytes)));
    verify(excelRestTemplate, times(1))
        .exchange(any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class));
  }

  @Test
  void testGetSharedAccessReportForMeasuresThrowsInternalServerException() {
    when(excelConfig.getMeasureSharedAccessReportApiPath()).thenReturn("/shared-access-report");
    when(excelRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class)))
        .thenThrow(new RestClientException("connection refused"));

    InternalServerException ex =
        assertThrows(
            InternalServerException.class,
            () ->
                excelClient.getSharedAccessReportForMeasures(
                    measureAccessReportDTOs, BEARER_TOKEN));

    assertThat(
        ex.getMessage(), is(equalTo("An error occurred while generating measure access report.")));
    verify(excelRestTemplate, times(1))
        .exchange(any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class));
  }
}
