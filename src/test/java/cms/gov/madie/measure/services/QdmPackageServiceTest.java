package cms.gov.madie.measure.services;

import cms.gov.madie.measure.config.QdmServiceConfig;
import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import cms.gov.madie.measure.exceptions.HQMFServiceException;
import cms.gov.madie.measure.exceptions.InternalServerException;
import cms.gov.madie.measure.exceptions.InvalidRequestException;
import cms.gov.madie.measure.repositories.ExportRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.cqm.CqmMeasure;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.QdmMeasure;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
class QdmPackageServiceTest {
  @Mock private QdmServiceConfig qdmServiceConfig;
  @Mock private RestTemplate qdmServiceRestTemplate;
  @Mock private ExportRepository exportRepository;
  @InjectMocks private QdmPackageService qdmPackageService;
  private final String token = "token";
  private final String userID = "userID";
  private Measure measure;
  private final String humanReadable = "Test Human Readable";

  @BeforeEach
  void setUp() {
    measure =
        Measure.builder()
            .id("1")
            .ecqmTitle("test")
            .cql("fake cql")
            .model(String.valueOf(ModelType.QDM_5_6))
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();
    Mockito.lenient().when(qdmServiceConfig.getBaseUrl()).thenReturn("baseurl");
  }

  @Test
  void getCreateMeasurePackage() {
    when(qdmServiceConfig.getCreatePackageUrn()).thenReturn("/elm/uri");
    String packageContent = "Measure Package Contents";
    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenReturn(ResponseEntity.ok(packageContent.getBytes()));
    PackageDto measurePackage = qdmPackageService.getMeasurePackage(measure, token, true);
    assertThat(measurePackage.isFromStorage(), is(false));
    byte[] packageContents = measurePackage.getExportPackage();
    assertThat(packageContents, is(notNullValue()));
    assertThat(new String(packageContents), is(equalTo(packageContent)));
  }

  @Test
  void getCreateMeasurePackageForVersionedWithExistingPersistedExport() {
    measure.getMeasureMetaData().setDraft(false);
    String packageContent = "Measure Package Contents";
    when(exportRepository.findByMeasureId(anyString()))
        .thenReturn(
            Optional.of(
                Export.builder()
                    .measureId(measure.getId())
                    .packageData(packageContent.getBytes())
                    .build()));
    PackageDto measurePackage = qdmPackageService.getMeasurePackage(measure, token, true);
    assertThat(measurePackage.isFromStorage(), is(true));
    byte[] packageContents = measurePackage.getExportPackage();
    assertThat(packageContents, is(notNullValue()));
    assertThat(new String(packageContents), is(equalTo(packageContent)));
  }

  @Test
  void getCreateMeasurePackageForVersionedWithMissingPersistedExport() {
    measure.getMeasureMetaData().setDraft(false);
    when(qdmServiceConfig.getCreatePackageUrn()).thenReturn("/elm/uri");
    String packageContent = "Measure Package Contents";
    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenReturn(ResponseEntity.ok(packageContent.getBytes()));
    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.empty());
    PackageDto measurePackage = qdmPackageService.getMeasurePackage(measure, token, true);
    assertThat(measurePackage.isFromStorage(), is(false));
    byte[] packageContents = measurePackage.getExportPackage();
    assertThat(packageContents, is(notNullValue()));
    assertThat(new String(packageContents), is(equalTo(packageContent)));
  }

  @Test
  void getCreateMeasurePackageWhenQdmServiceReturnedErrors() {
    when(qdmServiceConfig.getCreatePackageUrn()).thenReturn("/elm/uri");
    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new RestClientException("something went wrong"));
    String errorMessage =
        "An unexpected error occurred while creating a measure package.something went wrong";
    Exception ex =
        assertThrows(
            InternalServerException.class,
            () -> qdmPackageService.getMeasurePackage(measure, token, true),
            errorMessage);
    assertThat(ex.getMessage(), is(equalTo(errorMessage)));
  }

  @Test
  void testGetMeasurePackageThrowsException() {
    when(qdmServiceConfig.getCreatePackageUrn()).thenReturn("/elm/uri");
    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenThrow(
            new HttpClientErrorException(HttpStatusCode.valueOf(404), "something went wrong"));
    String errorMessage =
        "An unexpected error occurred while creating a measure package.something went wrong";
    Exception ex =
        assertThrows(
            InternalServerException.class,
            () -> qdmPackageService.getMeasurePackage(measure, token, true),
            errorMessage);
    assertThat(ex.getMessage(), is(equalTo("QDM service error: ")));
  }

  @Test
  void testGetMeasurePackageHQMFError() {
    when(qdmServiceConfig.getCreatePackageUrn()).thenReturn("/elm/uri");
    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new HttpClientErrorException(HttpStatusCode.valueOf(404), "HQMF:"));
    String errorMessage = "HQMF: An unexpected error occurred while creating a measure package.";
    Exception ex =
        assertThrows(
            HQMFServiceException.class,
            () -> qdmPackageService.getMeasurePackage(measure, token, true),
            errorMessage);

    assertThat(
        ex.getMessage(), is(equalTo("An error occurred that caused the HQMF generation to fail.")));
  }

  @Test
  void testGetQRDASuccess() {
    when(qdmServiceConfig.getCreateQrdaUrn()).thenReturn("/qrda");
    String qrdaContent = "Test QRDA";
    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenReturn(ResponseEntity.ok(qrdaContent.getBytes()));
    byte[] qrda =
        qdmPackageService.getQRDA(QrdaRequestDTO.builder().measure(measure).build(), token);
    assertThat(qrda, is(notNullValue()));
    assertThat(new String(qrda), is(equalTo(qrdaContent)));
  }

  @Test
  void testConvertCqmSuccess() {
    when(qdmServiceConfig.getRetrieveCqmMeasureUrn()).thenReturn("/cqm");
    // Measure
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id("testId")
            .measureSetId("testMeasureSetId")
            .cqlLibraryName("TestCqlLibraryName")
            .ecqmTitle("testECqm")
            .measureName("testMeasureName")
            .versionId("0.0.000")
            .build();
    CqmMeasure cqmMeasure = CqmMeasure.builder().build();
    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenReturn(ResponseEntity.ok(cqmMeasure));
    CqmMeasure cqm = qdmPackageService.convertCqm(qdmMeasure, token);
    assertThat(cqm, is(notNullValue()));
    assertThat(cqm, is(equalTo(cqmMeasure)));
  }

  @Test
  void testConvertCqmThrowsException() {
    when(qdmServiceConfig.getRetrieveCqmMeasureUrn()).thenReturn("/cqm");
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id("testId")
            .measureSetId("testMeasureSetId")
            .cqlLibraryName("TestCqlLibraryName")
            .ecqmTitle("testECqm")
            .measureName("testMeasureName")
            .versionId("0.0.000")
            .build();
    CqmMeasure cqmMeasure = CqmMeasure.builder().build();
    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new RestClientException("something went wrong"));

    String errorMessage =
        "An error occurred while converting QdmMeasure to CqmMeasure: 1, please check qdm service logs for more information";
    Exception ex =
        assertThrows(
            InternalServerException.class,
            () -> qdmPackageService.convertCqm(qdmMeasure, token),
            errorMessage);
    assertThat(ex.getMessage(), is(equalTo("An error occurred while converting CqmMeasure.")));
  }

  @Test
  void testConvertCqmThrowsExceptionWhenHttpStatusIsNotOK() {
    when(qdmServiceConfig.getRetrieveCqmMeasureUrn()).thenReturn("/cqm");
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id("testId")
            .measureSetId("testMeasureSetId")
            .cqlLibraryName("TestCqlLibraryName")
            .ecqmTitle("testECqm")
            .measureName("testMeasureName")
            .versionId("0.0.000")
            .build();
    CqmMeasure cqmMeasure = CqmMeasure.builder().build();
    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error message"));

    String errorMessage =
        "An error occurred while converting QdmMeasure to CqmMeasure: 1, please check qdm service logs for more information";
    Exception ex =
        assertThrows(
            InternalServerException.class,
            () -> qdmPackageService.convertCqm(qdmMeasure, token),
            errorMessage);
    assertThat(ex.getMessage(), is(equalTo("An error occurred while converting CqmMeasure.")));
  }

  @Test
  void testGetQRDAThrowsRestClientException() {
    when(qdmServiceConfig.getCreateQrdaUrn()).thenReturn("/qrda");
    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new RestClientException("something went wrong"));
    String errorMessage =
        "An error occurred while creating QRDA for QDM measure: 1, please check qdm service logs for more information";
    Exception ex =
        assertThrows(
            InternalServerException.class,
            () ->
                qdmPackageService.getQRDA(QrdaRequestDTO.builder().measure(measure).build(), token),
            errorMessage);
    assertThat(ex.getMessage(), is(equalTo("An error occurred while creating a QRDA.")));
  }

  @Test
  void testGetHumanReadable() {
    when(qdmServiceConfig.getHumanReadableUrn()).thenReturn("/human-readable");

    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenReturn(ResponseEntity.ok(humanReadable.getBytes()));
    String result = qdmPackageService.getHumanReadable(measure, userID, token);
    assertThat(result, is(notNullValue()));
    assertThat(result, is(equalTo(humanReadable)));
  }

  @Test
  void testGetHumanReadableHttpError() {
    when(qdmServiceConfig.getHumanReadableUrn()).thenReturn("/human-readable");
    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));
    Exception ex =
        assertThrows(
            InternalServerException.class,
            () -> qdmPackageService.getHumanReadable(measure, userID, token));
    assertThat(ex.getMessage(), is(equalTo("QDM service error: ")));
  }

  @Test
  void testGetHumanReadableClientError() {
    when(qdmServiceConfig.getHumanReadableUrn()).thenReturn("/human-readable");
    when(qdmServiceRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new RestClientException("Failed Client Exception"));
    Exception ex =
        assertThrows(
            InternalServerException.class,
            () -> qdmPackageService.getHumanReadable(measure, userID, token));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "An unexpected error occurred while creating a human readable. Failed Client Exception")));
  }

  @Test
  void testGetHumanReadableForVersionedMeasure() {
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    when(exportRepository.findByMeasureId(anyString()))
        .thenReturn(Optional.of(Export.builder().humanReadable(humanReadable).build()));
    String result = qdmPackageService.getHumanReadableForVersionedMeasure(measure, token, token);
    assertThat(result, is(equalTo(humanReadable)));
  }

  @Test
  void testGetHumanReadableForVersionedMeasureThrowsExceptionForDraft() {
    Exception ex =
        assertThrows(
            InvalidRequestException.class,
            () -> qdmPackageService.getHumanReadableForVersionedMeasure(measure, userID, token));
    assertThat(
        ex.getMessage(),
        is(equalTo("Error getting human readable for QDM measure: " + measure.getId())));
  }

  @Test
  void testGetHumanReadableForVersionedMeasureThrowsExceptionNoExport() {
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.empty());
    Exception ex =
        assertThrows(
            InvalidRequestException.class,
            () -> qdmPackageService.getHumanReadableForVersionedMeasure(measure, userID, token));
    assertThat(
        ex.getMessage(),
        is(equalTo("Error getting human readable for QDM measure: " + measure.getId())));
  }

  @Test
  void testGetHumanReadableForVersionedMeasureThrowsExceptionNoHR() {
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    when(exportRepository.findByMeasureId(anyString()))
        .thenReturn(Optional.of(Export.builder().build()));
    Exception ex =
        assertThrows(
            InvalidRequestException.class,
            () -> qdmPackageService.getHumanReadableForVersionedMeasure(measure, userID, token));
    assertThat(
        ex.getMessage(),
        is(equalTo("Error getting human readable for QDM measure: " + measure.getId())));
  }
}
