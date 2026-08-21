package cms.gov.madie.measure.services;

import static cms.gov.madie.measure.constants.BundleTypeConstants.EXPORT;
import static cms.gov.madie.measure.constants.BundleTypeConstants.PUBLISH;

import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import cms.gov.madie.measure.exceptions.BundleOperationException;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.utils.MeasureUtil;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.packaging.utils.PackagingUtilityFactory;
import gov.cms.madie.packaging.utils.qicore411.PackagingUtilityImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QicorePackageServiceTest {
  @Mock private BundleService bundleService;
  @Mock MeasureService measureService;
  @Mock FhirServicesClient fhirServicesClient;
  @Mock private ExportRepository exportRepository;
  @Mock private MeasureUtil measureUtil;

  @Captor private ArgumentCaptor<Export> exportArgumentCaptor;

  @InjectMocks private QicorePackageService qicorePackageService;

  private Measure existingMeasure;
  private static final String TEST_USER = "test-user";
  private static final String TEST_ACCESS_TOKEN = "test-access-token";
  private static final String TEST_MEASURE_ID = "testMeasureId";
  private static final String MODEL_QI_CORE = "QI-Core v4.1.1";
  private static final String MEASURE_BUNDLE_JSON =
      """
              {"resourceType": "Bundle","entry": [ {
                  "resource": {
                    "resourceType": "Measure","text":{"div":"humanReadable"}}}]}""";
  private static final String TEST_HUMAN_READABLE = "test human readable";
  private static final String BUNDLE_OPERATION_ERROR =
      "An error occurred while bundling Measure with ID testMeasureId. Please try again later or contact a System Administrator if this continues to occur.";

  private static MockedStatic<PackagingUtilityFactory> factory;

  @BeforeAll
  public static void staticSetup() {
    factory = mockStatic(PackagingUtilityFactory.class);
  }

  @BeforeEach
  public void setup() {
    existingMeasure = Measure.builder().id(TEST_MEASURE_ID).model(MODEL_QI_CORE).build();
  }

  @AfterAll
  public static void close() {
    factory.close();
  }

  @Test
  void getMeasurePackage() {
    String measurePackageStr = "measure package";
    PackageDto packageDto =
        PackageDto.builder().fromStorage(false).exportPackage(measurePackageStr.getBytes()).build();
    when(bundleService.getMeasureExport(any(Measure.class), anyString(), anyString()))
        .thenReturn(packageDto);
    PackageDto measurePackage =
        qicorePackageService.getMeasurePackage(new Measure(), true, "token");
    byte[] rawPackage = measurePackage.getExportPackage();
    assertThat(new String(rawPackage), is(equalTo(measurePackageStr)));
  }

  @Test
  void getMeasurePackageWithPublishBundleType() {
    // given
    PackageDto packageDto =
        PackageDto.builder().fromStorage(true).exportPackage("publish package".getBytes()).build();
    when(bundleService.getMeasureExport(any(Measure.class), eq(PUBLISH), eq("Error"), anyString()))
        .thenReturn(packageDto);

    // when
    PackageDto measurePackage =
        qicorePackageService.getMeasurePackage(new Measure(), PUBLISH, false, "token");

    // then
    assertThat(new String(measurePackage.getExportPackage()), is(equalTo("publish package")));
    verify(bundleService)
        .getMeasureExport(any(Measure.class), eq(PUBLISH), eq("Error"), eq("token"));
  }

  @Test
  void getMeasurePackageWithExportBundleTypeAndElmWarnings() {
    // given
    PackageDto packageDto =
        PackageDto.builder().fromStorage(false).exportPackage("export package".getBytes()).build();
    when(bundleService.getMeasureExport(any(Measure.class), eq(EXPORT), eq("Info"), anyString()))
        .thenReturn(packageDto);

    // when
    PackageDto measurePackage =
        qicorePackageService.getMeasurePackage(new Measure(), EXPORT, true, "token");

    // then
    assertThat(new String(measurePackage.getExportPackage()), is(equalTo("export package")));
    verify(bundleService).getMeasureExport(any(Measure.class), eq(EXPORT), eq("Info"), eq("token"));
  }

  @Test
  void testGetQRDA() {
    Exception ex =
        assertThrows(
            UnsupportedOperationException.class,
            () ->
                qicorePackageService.getQRDA(
                    QrdaRequestDTO.builder().measure(new Measure()).build(), "token"));
    assertThat(ex.getMessage(), containsString("method not yet implemented"));
  }

  @Test
  void testGetHumanReadableThrowsInstantiationException() {
    when(fhirServicesClient.getMeasureBundle(any(), anyString(), anyString(), anyString()))
        .thenReturn(MEASURE_BUNDLE_JSON);

    factory
        .when(() -> PackagingUtilityFactory.getInstance(MODEL_QI_CORE))
        .thenThrow(
            new InstantiationException("Unexpected error while getting human readable with CSS"));

    Exception ex =
        assertThrows(
            BundleOperationException.class,
            () ->
                qicorePackageService.getHumanReadable(
                    existingMeasure, TEST_USER, TEST_ACCESS_TOKEN));
    assertThat(ex.getMessage(), is(equalTo(BUNDLE_OPERATION_ERROR)));
  }

  @Test
  void testGetHumanReadableThrowsIllegalAccessException() {
    when(fhirServicesClient.getMeasureBundle(any(), anyString(), anyString(), anyString()))
        .thenReturn(MEASURE_BUNDLE_JSON);

    factory
        .when(() -> PackagingUtilityFactory.getInstance(MODEL_QI_CORE))
        .thenThrow(
            new IllegalAccessException("Unexpected error while getting human readable with CSS"));

    Exception ex =
        assertThrows(
            BundleOperationException.class,
            () ->
                qicorePackageService.getHumanReadable(
                    existingMeasure, TEST_USER, TEST_ACCESS_TOKEN));
    assertThat(ex.getMessage(), is(equalTo(BUNDLE_OPERATION_ERROR)));
  }

  @Test
  void testGetHumanReadableThrowsInvocationTargetException() {
    when(fhirServicesClient.getMeasureBundle(any(), anyString(), anyString(), anyString()))
        .thenReturn(MEASURE_BUNDLE_JSON);

    factory
        .when(() -> PackagingUtilityFactory.getInstance(MODEL_QI_CORE))
        .thenThrow(
            new InvocationTargetException(
                new Throwable("Unexpected error while getting human readable with CSS")));

    Exception ex =
        assertThrows(
            BundleOperationException.class,
            () ->
                qicorePackageService.getHumanReadable(
                    existingMeasure, TEST_USER, TEST_ACCESS_TOKEN));
    assertThat(ex.getMessage(), is(equalTo(BUNDLE_OPERATION_ERROR)));
  }

  @Test
  public void testGetHumanReadableSuccessForQiCoreMeasureDraftStatus() {
    MeasureMetaData meta = MeasureMetaData.builder().draft(true).build();
    existingMeasure.setMeasureMetaData(meta);

    when(fhirServicesClient.getMeasureBundle(any(), anyString(), anyString(), anyString()))
        .thenReturn(MEASURE_BUNDLE_JSON);

    PackagingUtilityImpl utility = mock(PackagingUtilityImpl.class);
    factory.when(() -> PackagingUtilityFactory.getInstance(MODEL_QI_CORE)).thenReturn(utility);
    when(utility.getHumanReadableWithCSS(anyString())).thenReturn(TEST_HUMAN_READABLE);

    String result =
        qicorePackageService.getHumanReadable(existingMeasure, TEST_USER, TEST_ACCESS_TOKEN);
    assertEquals(result, TEST_HUMAN_READABLE);
  }

  @Test
  public void testGetHRFromDBForQiCoreVersionedMeasure() {
    MeasureMetaData meta = MeasureMetaData.builder().draft(false).build();
    existingMeasure.setMeasureMetaData(meta);

    Export export =
        Export.builder().id(TEST_ACCESS_TOKEN).humanReadable(TEST_HUMAN_READABLE).build();
    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.of(export));

    String result =
        qicorePackageService.getHumanReadableForVersionedMeasure(
            existingMeasure, TEST_USER, TEST_ACCESS_TOKEN);
    assertEquals(result, TEST_HUMAN_READABLE);
  }

  @Test
  public void testGetHRForVersionedMeasureNoExport() {
    MeasureMetaData meta = MeasureMetaData.builder().draft(false).build();
    existingMeasure.setMeasureMetaData(meta);

    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.empty());

    String result =
        qicorePackageService.getHumanReadableForVersionedMeasure(
            existingMeasure, TEST_USER, TEST_ACCESS_TOKEN);
    assertNull(result);
  }

  @Test
  public void testGetHRForVersionedMeasureNoMetadata() {
    existingMeasure.setMeasureMetaData(null);

    String result =
        qicorePackageService.getHumanReadableForVersionedMeasure(
            existingMeasure, TEST_USER, TEST_ACCESS_TOKEN);
    assertNull(result);
  }

  @Test
  public void testGetHRForVersionedMeasureNotDraft() {
    existingMeasure.setMeasureMetaData(MeasureMetaData.builder().draft(true).build());

    String result =
        qicorePackageService.getHumanReadableForVersionedMeasure(
            existingMeasure, TEST_USER, TEST_ACCESS_TOKEN);
    assertNull(result);
  }

  @Test
  public void testSaveHRForQiCoreVersionedMeasureNoHR() {
    MeasureMetaData meta = MeasureMetaData.builder().draft(false).build();
    existingMeasure.setMeasureMetaData(meta);

    Export export = Export.builder().id(TEST_MEASURE_ID).build();
    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.of(export));

    when(fhirServicesClient.getMeasureBundle(any(), anyString(), anyString(), anyString()))
        .thenReturn(MEASURE_BUNDLE_JSON);

    PackagingUtilityImpl utility = mock(PackagingUtilityImpl.class);
    factory.when(() -> PackagingUtilityFactory.getInstance(MODEL_QI_CORE)).thenReturn(utility);
    when(utility.getHumanReadableWithCSS(anyString())).thenReturn(TEST_HUMAN_READABLE);

    Export savedExport =
        Export.builder().id(TEST_MEASURE_ID).humanReadable(TEST_HUMAN_READABLE).build();
    when(exportRepository.save(any(Export.class))).thenReturn(savedExport);

    String result =
        qicorePackageService.getHumanReadableForVersionedMeasure(
            existingMeasure, TEST_USER, TEST_ACCESS_TOKEN);
    assertEquals(result, TEST_HUMAN_READABLE);
    verify(exportRepository, times(1)).save(exportArgumentCaptor.capture());
  }
}
