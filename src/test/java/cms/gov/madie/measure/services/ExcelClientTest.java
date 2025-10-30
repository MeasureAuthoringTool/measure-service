package cms.gov.madie.measure.services;

import cms.gov.madie.measure.config.ExcelConfig;
import cms.gov.madie.measure.dto.excel.TestCaseExcelExportDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;

@ExtendWith(MockitoExtension.class)
class ExcelClientTest {

  @Mock
  private RestTemplate excelRestTemplate;

  @Mock
  private ExcelConfig excelConfig;

  @InjectMocks
  private ExcelClient excelClient;

  private List<TestCaseExcelExportDTO> testCaseExcelExportDtos;

  @BeforeEach
  void setUp() {
    TestCaseExcelExportDTO dto = new TestCaseExcelExportDTO();
    testCaseExcelExportDtos = List.of(dto);

    when(excelConfig.getExcelExportServiceBaseUrl()).thenReturn("http://localhost:3000/api");
    when(excelConfig.getGetExcelUrn()).thenReturn("/excel");
  }

  @Test
  void testExportExcelSuccessful() {
    String measureId = "measureId";
    String accessToken = "Bearer FAKE_TOKEN";
    byte[] expectedBytes = "excel-data".getBytes();

    ResponseEntity<byte[]> responseEntity = ResponseEntity.ok(expectedBytes);

    when(excelRestTemplate.exchange(
        any(URI.class),
        eq(HttpMethod.PUT),
        any(HttpEntity.class),
        eq(byte[].class)
    )).thenReturn(responseEntity);

    byte[] result = excelClient.exportExcel(measureId, testCaseExcelExportDtos, accessToken);

    assertThat(result, is(equalTo(expectedBytes)));

    verify(excelRestTemplate, times(1))
        .exchange(any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class));
  }

  @Test
  void testExportExcelThrowsException() {
    String measureId = "measureId";
    String accessToken = "Bearer FAKE_TOKEN";

    when(excelRestTemplate.exchange(
        any(URI.class),
        eq(HttpMethod.PUT),
        any(HttpEntity.class),
        eq(byte[].class)
    )).thenThrow(new RuntimeException("Excel Export failed"));

    try {
      excelClient.exportExcel(measureId, testCaseExcelExportDtos, accessToken);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(), is(equalTo("Excel Export failed")));
    }

    verify(excelRestTemplate, times(1))
        .exchange(any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class));
  }
}
