package cms.gov.madie.measure.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.config.security.SecurityConfigTest;
import cms.gov.madie.measure.dto.excel.TestCaseExcelExportDTO;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.ActionLogService;
import cms.gov.madie.measure.services.ExcelClient;
import cms.gov.madie.measure.services.ExportService;
import cms.gov.madie.measure.services.FhirServicesClient;
import cms.gov.madie.measure.services.MeasureService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** MAT-10579 spike: the Excel export endpoint accepts a gzip-compressed request body. */
@WebMvcTest({ExportController.class})
@ActiveProfiles("test")
@Import(SecurityConfigTest.class)
class ExportControllerGzipMvcTest {

  private static final String TEST_USER_ID = "test-okta-user-id-123";
  private static final String EXCEL_URL = "/measures/m1/test-cases/excel";
  private static final String JSON =
      "[{\"groupId\":\"g1\",\"groupNumber\":\"1\",\"testCaseExecutionResults\":"
          + "[{\"testCaseId\":\"tc1\",\"definitions\":"
          + "[{\"logic\":\"define \\\"Initial Population\\\": true\",\"actual\":\"TRUE\"}]}]}]";
  private static final byte[] XLSX = {1, 2, 3};

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MeasureRepository measureRepository;
  @MockitoBean private FhirServicesClient fhirServicesClient;
  @MockitoBean private ExcelClient excelClient;
  @MockitoBean private ExportService exportService;
  @MockitoBean private MeasureService measureService;
  @MockitoBean private ActionLogService actionLogService;
  @MockitoBean private UserServiceClient userServiceClient;

  @Test
  void excelExportAcceptsGzipBody() throws Exception {
    when(excelClient.exportExcel(anyString(), anyList(), anyString())).thenReturn(XLSX);

    mockMvc
        .perform(
            put(EXCEL_URL)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gzip(JSON)))
        .andExpect(status().isOk())
        .andExpect(content().bytes(XLSX));

    assertForwardedDtos();
  }

  @Test
  void excelExportStillAcceptsUncompressedBody() throws Exception {
    when(excelClient.exportExcel(anyString(), anyList(), anyString())).thenReturn(XLSX);

    mockMvc
        .perform(
            put(EXCEL_URL)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON))
        .andExpect(status().isOk())
        .andExpect(content().bytes(XLSX));

    assertForwardedDtos();
  }

  @Test
  void excelExportRejectsBodyThatIsNotGzipWithBadRequest() throws Exception {
    mockMvc
        .perform(
            put(EXCEL_URL)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(excelClient);
  }

  @Test
  void excelExportRejectsBodyThatDecompressesPastLimitWithBadRequest() throws Exception {
    // "[" + whitespace: Jackson keeps reading, so the size cap is what stops it.
    byte[] gzipBomb = gzipJsonArrayPaddedTo(60L * 1024 * 1024);

    mockMvc
        .perform(
            put(EXCEL_URL)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gzipBomb))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(excelClient);
  }

  @SuppressWarnings("unchecked")
  private void assertForwardedDtos() {
    ArgumentCaptor<List<TestCaseExcelExportDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(excelClient).exportExcel(eq("m1"), captor.capture(), eq("test-okta"));
    TestCaseExcelExportDTO dto = captor.getValue().get(0);
    assertEquals("g1", dto.getGroupId());
    assertEquals(
        "define \"Initial Population\": true",
        dto.getTestCaseExecutionResults().get(0).getDefinitions().get(0).getLogic());
  }

  private static byte[] gzip(String value) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(value.getBytes(StandardCharsets.UTF_8));
    }
    return out.toByteArray();
  }

  private static byte[] gzipJsonArrayPaddedTo(long decompressedBytes) throws IOException {
    byte[] spaces = new byte[64 * 1024];
    Arrays.fill(spaces, (byte) ' ');
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write('[');
      for (long left = decompressedBytes - 1; left > 0; left -= spaces.length) {
        gzip.write(spaces, 0, (int) Math.min(spaces.length, left));
      }
    }
    return out.toByteArray();
  }
}
