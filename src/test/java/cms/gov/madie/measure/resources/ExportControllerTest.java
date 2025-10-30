package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.dto.excel.TestCaseExcelExportDTO;
import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.ActionLogService;
import cms.gov.madie.measure.services.ExcelClient;
import cms.gov.madie.measure.services.ExportService;
import cms.gov.madie.measure.services.FhirServicesClient;
import cms.gov.madie.measure.services.MeasureService;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ExportControllerTest {

  @Mock private MeasureRepository measureRepository;
  @Mock private FhirServicesClient fhirServicesClient;
  @Mock private ExcelClient excelClient;
  @Mock private ExportService exportService;
  @Mock private MeasureService measureService;
  @Mock private ActionLogService actionLogService;
  @InjectMocks private ExportController exportController;

  @Test
  void getZipThrowsNotFoundException() {
    Principal principal = mock(Principal.class);
    when(measureService.findMeasureById(anyString())).thenReturn(null);
    assertThrows(
        ResourceNotFoundException.class,
        () -> exportController.getZip(principal, "test_id", "Info", "Bearer TOKEN"));
  }

  @Test
  void getZipReturnsACreatedResponse() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    final Measure measure =
        Measure.builder()
            .ecqmTitle("test_ecqm_title")
            .version(new Version(0, 0, 0))
            .model("QiCore 4.1.1")
            .createdBy("test.user")
            .build();

    byte[] response = new byte[0];
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    when(exportService.getMeasureExport(eq(measure), anyString(), anyString()))
        .thenReturn(PackageDto.builder().fromStorage(false).exportPackage(response).build());
    ResponseEntity<byte[]> output =
        exportController.getZip(principal, "test_id", "Info", "Bearer TOKEN");
    assertEquals(HttpStatus.CREATED, output.getStatusCode());
    verify(actionLogService, times(1))
        .logAction("test_id", Measure.class, ActionType.EXPORTED_MEASURE, "test.user");
  }

  @Test
  void getZipFromStorageReturnsAnOKResponse() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    final Measure measure =
        Measure.builder()
            .ecqmTitle("test_ecqm_title")
            .version(new Version(0, 0, 0))
            .model("QiCore 4.1.1")
            .createdBy("test.user")
            .build();

    byte[] response = new byte[0];
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    when(exportService.getMeasureExport(eq(measure), anyString(), anyString()))
        .thenReturn(PackageDto.builder().fromStorage(true).exportPackage(response).build());
    ResponseEntity<byte[]> output =
        exportController.getZip(principal, "test_id", "Info", "Bearer TOKEN");
    assertEquals(HttpStatus.OK, output.getStatusCode());
    verify(actionLogService, times(1))
        .logAction("test_id", Measure.class, ActionType.EXPORTED_MEASURE, "test.user");
  }

  @Test
  void getTestCaseExportAll() throws IOException {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    List<TestCase> testCases = new ArrayList<>();
    File jsonFile =
        new File(this.getClass().getResource("/test_case_exported_json.json").getFile());

    String jsonData = new String(Files.readAllBytes(jsonFile.toPath()));

    testCases.add(TestCase.builder().json(jsonData).build());
    final Measure measure =
        Measure.builder()
            .ecqmTitle("test_ecqm_title")
            .version(new Version(0, 0, 0))
            .testCases(testCases)
            .model("QiCore 4.1.1")
            .createdBy("test.user")
            .build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(fhirServicesClient.getTestCaseExports(
            any(Measure.class), anyString(), anyList(), anyString()))
        .thenReturn(new ResponseEntity<byte[]>(HttpStatus.OK));
    ResponseEntity<byte[]> output =
        exportController.getTestCaseExport(
            principal,
            "access-token",
            "example-measure-id",
            Optional.of("COLLECTION"),
            asList("example-test-case-id-1", "example-test-case-id-2"));
    assertEquals(HttpStatus.OK, output.getStatusCode());
  }

  @Test
  void getTestCaseExportAllPartialContent() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    final Measure measure =
        Measure.builder()
            .ecqmTitle("test_ecqm_title")
            .version(new Version(0, 0, 0))
            .model("QiCore 4.1.1")
            .createdBy("test.user")
            .build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(fhirServicesClient.getTestCaseExports(
            any(Measure.class), anyString(), anyList(), anyString()))
        .thenReturn(new ResponseEntity<byte[]>(HttpStatus.PARTIAL_CONTENT));
    ResponseEntity<byte[]> output =
        exportController.getTestCaseExport(
            principal,
            "access-token",
            "example-measure-id",
            Optional.of("COLLECTION"),
            asList("example-test-case-id-1", "example-test-case-id-2"));
    assertEquals(HttpStatus.PARTIAL_CONTENT, output.getStatusCode());
  }

  @Test
  void getTestCaseExportAllPartialContentWithDefaultBundleType() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    final Measure measure =
        Measure.builder()
            .ecqmTitle("test_ecqm_title")
            .version(new Version(0, 0, 0))
            .model("QiCore 4.1.1")
            .createdBy("test.user")
            .build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(fhirServicesClient.getTestCaseExports(
            any(Measure.class), anyString(), anyList(), anyString()))
        .thenReturn(new ResponseEntity<byte[]>(HttpStatus.PARTIAL_CONTENT));
    ResponseEntity<byte[]> output =
        exportController.getTestCaseExport(
            principal,
            "access-token",
            "example-measure-id",
            Optional.empty(),
            asList("example-test-case-id-1", "example-test-case-id-2"));
    assertEquals(HttpStatus.PARTIAL_CONTENT, output.getStatusCode());
  }

  @Test
  void getTestCaseExportErrorResponse() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    final Measure measure =
        Measure.builder()
            .ecqmTitle("test_ecqm_title")
            .version(new Version(0, 0, 0))
            .model("QiCore 4.1.1")
            .createdBy("test.user")
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));

    when(fhirServicesClient.getTestCaseExports(
            any(Measure.class), anyString(), anyList(), anyString()))
        .thenReturn(new ResponseEntity<byte[]>(HttpStatus.INTERNAL_SERVER_ERROR));

    ResponseEntity<byte[]> output =
        exportController.getTestCaseExport(
            principal,
            "access-token",
            "example-measure-id",
            Optional.of("COLLECTION"),
            asList("example-test-case-id-1", "example-test-case-id-2"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, output.getStatusCode());
  }

  @Test
  void getTestCaseExportAllThrowsResourceNotFoundException() {
    Principal principal = mock(Principal.class);
    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            exportController.getTestCaseExport(
                principal,
                "access-token",
                "example-measure-id",
                Optional.of("COLLECTION"),
                asList("example-test-case-id-1", "example-test-case-id-2")));
  }

  @Test
  void testGetQRDAThrowsNotFoundException() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());
    assertThrows(
        ResourceNotFoundException.class,
        () -> exportController.getQRDA(principal, "test_id", new QrdaRequestDTO(), "Bearer TOKEN"));
  }

  @Test
  @Disabled
  void testGetQRDASuccess() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    final Measure measure =
        Measure.builder()
            .ecqmTitle("test_ecqm_title")
            .version(new Version(0, 0, 0))
            .model("QiCore 4.1.1")
            .createdBy("test.user")
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(exportService.getQRDA(eq(QrdaRequestDTO.builder().measure(measure).build()), anyString()))
        .thenReturn(new byte[0]);
    ResponseEntity<byte[]> output =
        exportController.getQRDA(
            principal,
            "test_id",
            QrdaRequestDTO.builder().measure(measure).build(),
            "Bearer TOKEN");
    assertEquals(HttpStatus.OK, output.getStatusCode());
  }

  @Test
  void testGetExcelSuccess() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    final List<TestCaseExcelExportDTO> testCaseDtos = List.of(new TestCaseExcelExportDTO());

    byte[] excelBytes = "excel-data".getBytes();

    when(excelClient.exportExcel(eq("measure-id"), eq(testCaseDtos), eq("Bearer TOKEN")))
        .thenReturn(excelBytes);

    ResponseEntity<byte[]> response =
        exportController.getExcel(principal, "measure-id", testCaseDtos, "Bearer TOKEN");

    assertEquals(HttpStatus.OK, response.getStatusCode());

    assertNotNull(
        response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION),
        "Content-Disposition header missing");
    assertFalse(
        response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION).isEmpty(),
        "Content-Disposition header empty");
    assertEquals(
        "attachment; filename=\"testCases.xlsx\"",
        response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION).get(0));

    assertNotNull(
        response.getHeaders().get(HttpHeaders.CONTENT_TYPE), "Content-Type header missing");
    assertFalse(
        response.getHeaders().get(HttpHeaders.CONTENT_TYPE).isEmpty(), "Content-Type header empty");
    assertEquals(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        response.getHeaders().get(HttpHeaders.CONTENT_TYPE).get(0));

    assertEquals(response.getBody(), excelBytes);

    verify(actionLogService, times(1))
        .logAction("measure-id", Measure.class, ActionType.EXPORTED_TESTCASES, "test.user");
  }

  @Test
  void testGetExcelThrowsException() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    final List<TestCaseExcelExportDTO> testCaseDtos = List.of(new TestCaseExcelExportDTO());

    when(excelClient.exportExcel(eq("measure-id"), eq(testCaseDtos), eq("Bearer TOKEN")))
        .thenThrow(new RuntimeException("Excel export failed"));

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> exportController.getExcel(principal, "measure-id", testCaseDtos, "Bearer TOKEN"));

    assertEquals("Excel export failed", thrown.getMessage());

    verify(actionLogService, times(0))
        .logAction("measure-id", Measure.class, ActionType.EXPORTED_TESTCASES, "test.user");
  }
}
