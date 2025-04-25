package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.ExportService;
import cms.gov.madie.measure.services.FhirServicesClient;
import cms.gov.madie.measure.services.MeasureService;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.dto.OverlappingCodeDto;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportControllerTest {

  @Mock private MeasureRepository measureRepository;
  @Mock private FhirServicesClient fhirServicesClient;
  @Mock private ExportService exportService;
  @Mock private MeasureService measureService;
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
  void testGetOverlappingValueSetsSuccess() {
    OverlappingCodeDto overlappingCodeDto =
        OverlappingCodeDto.builder()
            .code("4525004")
            .codeSystem("http://snomed.info/sct")
            .description("Emergency department patient visit (procedure)")
            .codeSystemName("http://snomed.info/sct")
            .codeSystemVersion("http://snomed.info/sct/731000124108/version/20250301")
            .build();
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
    when(exportService.getOverlappingValueSets(anyList())).thenReturn(new byte[0]);
    ResponseEntity<byte[]> output =
        exportController.getOverlappingValueSets(
            principal, "test_id", List.of(overlappingCodeDto), "Bearer TOKEN");
    assertEquals(HttpStatus.OK, output.getStatusCode());
  }

  @Test
  void testGetOverlappingValueSetsMeasureNotFound() {
    OverlappingCodeDto overlappingCodeDto =
        OverlappingCodeDto.builder()
            .code("4525004")
            .codeSystem("http://snomed.info/sct")
            .description("Emergency department patient visit (procedure)")
            .codeSystemName("http://snomed.info/sct")
            .codeSystemVersion("http://snomed.info/sct/731000124108/version/20250301")
            .build();
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            exportController.getOverlappingValueSets(
                principal, "test_id", List.of(overlappingCodeDto), "Bearer TOKEN"));
  }

  @Test
  void testGetOverlappingValueSetsMeasureEmptyCodes() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            exportController.getOverlappingValueSets(
                principal, "test_id", Collections.emptyList(), "Bearer TOKEN"));
  }
}
