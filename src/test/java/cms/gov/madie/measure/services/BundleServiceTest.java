package cms.gov.madie.measure.services;

import static cms.gov.madie.measure.constants.BundleTypeConstants.CALCULATION;
import static cms.gov.madie.measure.constants.BundleTypeConstants.EXPORT;
import static cms.gov.madie.measure.constants.BundleTypeConstants.PUBLISH;

import cms.gov.madie.measure.dto.CompositeVersionArtifacts;
import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.exceptions.BundleOperationException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.ResourceUtil;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Component;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureGroupTypes;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.common.Organization;
import gov.cms.madie.models.common.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cms.gov.madie.measure.utils.ServiceConstants.LEGACY_MEASURE_EXPORT_WARNING;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ExtendWith(MockitoExtension.class)
class BundleServiceTest implements ResourceUtil {

  @Mock private FhirServicesClient fhirServicesClient;
  @Mock private ExportRepository exportRepository;
  @Mock private ElmToJsonService elmToJsonService;
  @Mock private MongoGridFsService mongoGridFsService;
  @Mock private MeasureRepository measureRepository;
  @InjectMocks private BundleService bundleService;

  private Measure measure;

  @BeforeEach
  public void setUp() {
    Group group =
        Group.builder()
            .id("xyz-p12r-12ert")
            .populationBasis("Encounter")
            .measureGroupTypes(List.of(MeasureGroupTypes.PROCESS))
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "FactorialOfFive",
                        null,
                        null,
                        null)))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    List<Group> groups = new ArrayList<>();
    groups.add(group);
    String elmJson = getData("/test_elm.json");
    MeasureMetaData metaData = MeasureMetaData.builder().draft(true).build();
    measure =
        Measure.builder()
            .active(true)
            .id("xyz-p13r-13ert")
            .cql("test cql")
            .model(ModelType.QDM_5_6.getValue())
            .cqlErrors(false)
            .elmJson(elmJson)
            .measureSetId("IDIDID")
            .ecqmTitle("MEAS")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .groups(groups)
            .measureMetaData(metaData)
            .createdAt(Instant.now())
            .createdBy("test user")
            .lastModifiedAt(Instant.now())
            .lastModifiedBy("test user")
            .build();
  }

  @Test
  void testBundleMeasureReturnsNullForNullMeasure() {
    String output = bundleService.bundleMeasure(null, "Bearer TOKEN", CALCULATION, "Info");
    assertThat(output, is(nullValue()));
  }

  @Test
  void testBundleMeasureThrowsOperationException() {

    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), anyString(), anyString()))
        .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
    assertThrows(
        BundleOperationException.class,
        () -> bundleService.bundleMeasure(measure, "Bearer TOKEN", CALCULATION, "Info"));
  }

  @Test
  void testBundleMeasureReturnsBundleStringForDraftMeasure() {
    final String json = "{\"message\": \"GOOD JSON\"}";
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), anyString(), anyString()))
        .thenReturn(json);

    assertThat(measure.getMeasureMetaData().isDraft(), is(equalTo(true)));
    String output = bundleService.bundleMeasure(measure, "Bearer TOKEN", CALCULATION, "Info");
    assertThat(output, is(equalTo(json)));
  }

  @Test
  void testBundleMeasureReturnsBundleStringForCompositeDraftMeasure() {
    // Setup component measure
    Measure componentMeasure =
        Measure.builder()
            .id("comp-measure-id")
            .measureName("ComponentMeasure")
            .cqlLibraryName("ComponentLib")
            .version(new Version(1, 0, 0))
            .model("QI-Core v4.1.1")
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .groups(List.of(Group.builder().id("comp-group-1").displayId("Group 1").build()))
            .build();

    Component component =
        Component.builder().measureId("comp-measure-id").groupId("comp-group-1").build();

    Group compositeGroup =
        Group.builder()
            .id("composite-group-1")
            .components(List.of(component))
            .populationBasis("boolean")
            .measureGroupTypes(List.of(MeasureGroupTypes.PROCESS))
            .build();

    measure.setGroups(List.of(compositeGroup));
    measure.setModel("QI-Core v4.1.1");
    measure.setMeasureMetaData(
        MeasureMetaData.builder()
            .draft(true)
            .composite(true)
            .steward(Organization.builder().name("SemanticBits").build())
            .description("Composite measure")
            .developers(List.of(Organization.builder().name("ICF").build()))
            .build());

    final String compositeBundle = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    final String componentBundle = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;

    when(measureRepository.findById("comp-measure-id")).thenReturn(Optional.of(componentMeasure));
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), eq(CALCULATION), anyString()))
        .thenReturn(compositeBundle);

    Export componentExport =
        Export.builder()
            .measureId("comp-measure-id")
            .measureBundleGridFsId("comp-grid-fs-id")
            .build();
    when(exportRepository.findByMeasureId("comp-measure-id"))
        .thenReturn(Optional.of(componentExport));
    when(mongoGridFsService.findById("comp-grid-fs-id")).thenReturn(componentBundle);

    String output = bundleService.bundleMeasure(measure, "Bearer TOKEN", CALCULATION, "Info");

    assertNotNull(output);
    // verify component details were populated
    assertEquals("ComponentMeasure", component.getMeasureName());
    assertEquals("ComponentLib", component.getMeasureLibraryName());
    assertEquals("1.0.000", component.getMeasureVersion());
    assertFalse(component.isDraft());
    assertFalse(component.isMultiGroupComponent());
    assertEquals("Group 1", component.getGroupDisplayId());

    verify(fhirServicesClient)
        .getMeasureBundle(any(Measure.class), eq("Bearer TOKEN"), eq(CALCULATION), eq("Info"));
    verify(exportRepository).findByMeasureId("comp-measure-id");
    verify(mongoGridFsService).findById("comp-grid-fs-id");
  }

  @Test
  void testBuildCompositeVersionArtifactsSnapshotsComponentHumanReadables() {
    Measure componentMeasure =
        Measure.builder()
            .id("comp-measure-id")
            .measureName("ComponentMeasure")
            .ecqmTitle("ComponentMeasure")
            .cqlLibraryName("ComponentLib")
            .version(new Version(1, 0, 0))
            .model("QI-Core v4.1.1")
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .groups(List.of(Group.builder().id("comp-group-1").displayId("Group 1").build()))
            .build();

    Component component =
        Component.builder().measureId("comp-measure-id").groupId("comp-group-1").build();
    Group compositeGroup =
        Group.builder()
            .id("composite-group-1")
            .components(List.of(component))
            .populationBasis("boolean")
            .measureGroupTypes(List.of(MeasureGroupTypes.PROCESS))
            .build();

    measure.setGroups(List.of(compositeGroup));
    measure.setModel("QI-Core v4.1.1");
    measure.setMeasureMetaData(
        MeasureMetaData.builder()
            .draft(true)
            .composite(true)
            .steward(Organization.builder().name("SemanticBits").build())
            .description("Composite measure")
            .developers(List.of(Organization.builder().name("ICF").build()))
            .build());

    final String bundle = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    when(measureRepository.findById("comp-measure-id")).thenReturn(Optional.of(componentMeasure));
    when(measureRepository.findAllById(any())).thenReturn(List.of(componentMeasure));
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), eq(EXPORT), eq("Info")))
        .thenReturn(bundle);
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), eq(PUBLISH), eq("Error")))
        .thenReturn(bundle);

    Export componentExport =
        Export.builder()
            .measureId("comp-measure-id")
            .measureBundleGridFsId("comp-grid-fs-id")
            .measureBundleWithoutWarningsGridFsId("comp-grid-fs-id-nw")
            .humanReadable("<html>component HR</html>")
            .build();
    when(exportRepository.findByMeasureId("comp-measure-id"))
        .thenReturn(Optional.of(componentExport));
    when(mongoGridFsService.findById("comp-grid-fs-id")).thenReturn(bundle);
    when(mongoGridFsService.findById("comp-grid-fs-id-nw")).thenReturn(bundle);

    CompositeVersionArtifacts artifacts =
        bundleService.buildCompositeVersionArtifacts(measure, "Bearer TOKEN");

    assertNotNull(artifacts);
    assertNotNull(artifacts.bundleJson());
    assertNotNull(artifacts.bundleJsonWithoutWarnings());
    assertEquals(
        List.of(
            Export.ComponentHumanReadable.builder()
                .componentId("comp-measure-id")
                .fileName("ComponentMeasure-v1.0.000-FHIR")
                .humanReadable("<html>component HR</html>")
                .build()),
        artifacts.componentHumanReadables());
    verify(fhirServicesClient)
        .getMeasureBundle(any(Measure.class), anyString(), eq(EXPORT), eq("Info"));
    verify(fhirServicesClient)
        .getMeasureBundle(any(Measure.class), anyString(), eq(PUBLISH), eq("Error"));
  }

  @Test
  void testBundleMeasureReturnsBundleStringForVersionedMeasure() {
    final String json = "{\"message\": \"GOOD JSON\"}";
    Export export = Export.builder().measureId(measure.getId()).measureBundleJson(json).build();
    measure.getMeasureMetaData().setDraft(false);
    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.of(export));

    String output = bundleService.bundleMeasure(measure, "Bearer TOKEN", null, "Info");
    assertThat(output, is(equalTo(json)));
  }

  @Test
  void testBundleMeasureReturnsBundleStringForVersionedMeasureWithGridFS() {
    final String json = "{\"message\": \"GOOD JSON\"}";
    Export export =
        Export.builder().measureId(measure.getId()).measureBundleGridFsId("gridFsId").build();
    measure.getMeasureMetaData().setDraft(false);
    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.of(export));
    when(mongoGridFsService.findById(anyString())).thenReturn(json);

    String output = bundleService.bundleMeasure(measure, "Bearer TOKEN", null, "Info");
    assertThat(output, is(equalTo(json)));
  }

  @Test
  void testBundleMeasureReturnsPublishableBundleForVersionedMeasure() {
    final String publishableBundle = "{\"message\": \"PUBLISHABLE JSON\"}";
    Export export =
        Export.builder()
            .measureId(measure.getId())
            .measureBundleGridFsId("export-grid-fs-id")
            .measureBundleWithoutWarningsGridFsId("publish-grid-fs-id")
            .build();
    measure.getMeasureMetaData().setDraft(false);
    when(exportRepository.findByMeasureId(measure.getId())).thenReturn(Optional.of(export));
    when(mongoGridFsService.findById("publish-grid-fs-id")).thenReturn(publishableBundle);

    String output = bundleService.bundleMeasure(measure, "******", PUBLISH, "Info");

    assertThat(output, is(equalTo(publishableBundle)));
    verify(mongoGridFsService).findById("publish-grid-fs-id");
    verify(mongoGridFsService, never()).findById("export-grid-fs-id");
  }

  @Test
  void testBundleMeasureThrowsWhenVersionedPublishableBundleIsUnavailable() {
    // given
    Export export =
        Export.builder()
            .measureId(measure.getId())
            .measureBundleGridFsId("export-grid-fs-id")
            .build();
    measure.getMeasureMetaData().setDraft(false);
    when(exportRepository.findByMeasureId(measure.getId())).thenReturn(Optional.of(export));

    // when
    Exception exception =
        assertThrows(
            ResourceNotFoundException.class,
            () -> bundleService.bundleMeasure(measure, "******", PUBLISH, "Error"));

    // then
    assertThat(exception.getMessage(), is(equalTo(LEGACY_MEASURE_EXPORT_WARNING)));
    verifyNoInteractions(mongoGridFsService);
  }

  @Test
  void testBundleMeasureReturnsBundleStringForVersionedMeasureIfExportUnavailable() {
    measure.getMeasureMetaData().setDraft(false);
    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.empty());

    Exception ex =
        assertThrows(
            BundleOperationException.class,
            () -> bundleService.bundleMeasure(measure, "Bearer TOKEN", null, "Info"));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "An error occurred while bundling Measure with ID xyz-p13r-13ert."
                    + " Please try again later or contact a System Administrator if this continues to occur.")));
  }

  @Test
  void testExportWithElmWarningsBundleMeasureForVersionedMeasure() throws IOException {
    final String json = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    measure.getMeasureMetaData().setDraft(false);

    Export export =
        Export.builder()
            .measureId(measure.getId())
            .measureBundleGridFsId("id1")
            .measureBundleWithoutWarningsGridFsId("id2")
            .build();
    measure.setMeasureMetaData(
        MeasureMetaData.builder()
            .draft(false)
            .steward(Organization.builder().name("SemanticBits").build())
            .description("This is a description")
            .developers(List.of(Organization.builder().name("ICF").build()))
            .build());
    measure.setModel("QI-Core v4.1.1");
    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.of(export));
    when(mongoGridFsService.findById("id1")).thenReturn(json);
    PackageDto output = bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN");
    assertNotNull(output);
    ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(output.getExportPackage()));
    ZipEntry entry = z.getNextEntry();
    String fileName = entry.getName();
    assertEquals("resources/measure-TestCreateNewLibrary-1.0.000.json", fileName);
    verify(mongoGridFsService, times(1)).findById("id1");
  }

  @Test
  void testExportWithElmWarningsWhenBundleIsNotAvailableInGridFsButIsStoredAsBundleJson() {
    final String json = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    Export export =
        Export.builder()
            .measureId(measure.getId())
            .measureBundleGridFsId("grid-fs-id-1")
            .measureBundleWithoutWarningsGridFsId("id2")
            .measureBundleJson(json)
            .build();
    measure.getMeasureMetaData().setDraft(false);
    measure.setModel("QI-Core v4.1.1");
    when(mongoGridFsService.findById("grid-fs-id-1")).thenReturn(null);
    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.of(export));
    PackageDto output = bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN");
    assertNotNull(output.getExportPackage());
    verify(mongoGridFsService, times(1)).findById("grid-fs-id-1");
  }

  @Test
  void
      testExportWithElmWarningsThrowsExceptionWhenBundleIsNotAvailableInGridFsAndBundleJsonIsAlsoEmpty() {
    Export export =
        Export.builder()
            .measureId(measure.getId())
            .measureBundleGridFsId("grid-fs-id-1")
            .measureBundleWithoutWarningsGridFsId("id2")
            .measureBundleJson("") // no saved export
            .build();
    measure.getMeasureMetaData().setDraft(false);
    measure.setModel("QI-Core v4.1.1");
    when(mongoGridFsService.findById("grid-fs-id-1")).thenReturn(null);
    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.of(export));
    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN"));
    assertThat(
        ex.getMessage(),
        is(equalTo("Could not find saved export for Measure with id: xyz-p13r-13ert")));
  }

  @Test
  void testExportWithoutElmWarningsBundleMeasureForVersionedMeasure() throws IOException {
    final String json = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    measure.getMeasureMetaData().setDraft(false);

    Export export =
        Export.builder()
            .measureId(measure.getId())
            .measureBundleGridFsId("id1")
            .measureBundleWithoutWarningsGridFsId("id2")
            .build();
    measure.setEcqmTitle("MEAS");
    measure.setMeasureMetaData(
        MeasureMetaData.builder()
            .draft(false)
            .steward(Organization.builder().name("SemanticBits").build())
            .description("This is a description")
            .developers(List.of(Organization.builder().name("ICF").build()))
            .build());
    measure.setModel("QI-Core v4.1.1");
    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.of(export));
    when(mongoGridFsService.findById("id2")).thenReturn(json);
    PackageDto output = bundleService.getMeasureExport(measure, "Error", "Bearer TOKEN");
    assertNotNull(output);
    ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(output.getExportPackage()));
    ZipEntry entry = z.getNextEntry();
    String fileName = entry.getName();
    assertEquals("resources/measure-TestCreateNewLibrary-1.0.000.json", fileName);
    verify(mongoGridFsService, times(1)).findById("id2");
  }

  @Test
  void testExplicitExportBundleTypeOverridesErrorSeverityForVersionedMeasure() {
    final String json = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    measure.setModel("QI-Core v4.1.1");
    Export export =
        Export.builder()
            .measureId(measure.getId())
            .measureBundleGridFsId("export-grid-fs-id")
            .measureBundleWithoutWarningsGridFsId("publish-grid-fs-id")
            .build();
    when(exportRepository.findByMeasureId(measure.getId())).thenReturn(Optional.of(export));
    when(mongoGridFsService.findById("export-grid-fs-id")).thenReturn(json);

    PackageDto output = bundleService.getMeasureExport(measure, EXPORT, "Error", "******");

    assertNotNull(output.getExportPackage());
    verify(mongoGridFsService).findById("export-grid-fs-id");
    verify(mongoGridFsService, never()).findById("publish-grid-fs-id");
  }

  @Test
  void testExportWithoutElmWarningsThrowsExceptionWhenGridFsDoesNotContainSavedExport() {
    Export export =
        Export.builder()
            .measureId(measure.getId())
            .measureBundleGridFsId("grid-fs-id-1")
            .measureBundleWithoutWarningsGridFsId("grid-fs-id-2")
            .measureBundleJson("") // no saved export
            .build();
    measure.getMeasureMetaData().setDraft(false);
    measure.setModel("QI-Core v4.1.1");
    when(mongoGridFsService.findById("grid-fs-id-2")).thenReturn("");
    when(exportRepository.findByMeasureId(anyString())).thenReturn(Optional.of(export));
    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> bundleService.getMeasureExport(measure, "Error", "Bearer TOKEN"));
    assertThat(ex.getMessage(), is(equalTo(LEGACY_MEASURE_EXPORT_WARNING)));
  }

  @Test
  void testExportBundleMeasureForVersionedMeasureDoesntExistInMongo() {
    doThrow(
            new BundleOperationException(
                "Measure", "xyz-p13r-13ert", new RuntimeException("Failed to retrieve ELM JSON")))
        .when(elmToJsonService)
        .retrieveElmJson(any(), anyString());

    measure.setEcqmTitle("MEAS");
    measure.setMeasureMetaData(
        MeasureMetaData.builder()
            .draft(true)
            .steward(Organization.builder().name("SemanticBits").build())
            .description("This is a description")
            .developers(List.of(Organization.builder().name("ICF").build()))
            .build());
    measure.setModel("QI-Core v4.1.1");

    Exception ex =
        assertThrows(
            BundleOperationException.class,
            () -> bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN"));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "An error occurred while bundling Measure with ID xyz-p13r-13ert."
                    + " Please try again later or contact a System Administrator if this continues to occur.")));
  }

  @Test
  void testExportBundleMeasureForDraftMeasure() {
    measure.setEcqmTitle("MEAS");
    measure.setMeasureMetaData(
        MeasureMetaData.builder()
            .draft(true)
            .steward(Organization.builder().name("SemanticBits").build())
            .description("This is a description")
            .developers(List.of(Organization.builder().name("ICF").build()))
            .build());
    measure.setModel("QI-Core v4.1.1");

    byte[] exportBytes = "TEST".getBytes();
    doReturn(exportBytes)
        .when(fhirServicesClient)
        .getMeasureBundleExport(any(Measure.class), eq("Info"), eq("Bearer TOKEN"));

    PackageDto output = bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN");
    assertNotNull(output);
    assertArrayEquals("TEST".getBytes(), output.getExportPackage());
  }

  @Test
  void testPublishBundleMeasureForDraftMeasure() {
    // given
    measure.setEcqmTitle("MEAS");
    measure.setMeasureMetaData(
        MeasureMetaData.builder()
            .draft(true)
            .steward(Organization.builder().name("SemanticBits").build())
            .description("This is a description")
            .developers(List.of(Organization.builder().name("ICF").build()))
            .build());
    measure.setModel("QI-Core v4.1.1");
    byte[] exportBytes = "TEST".getBytes();
    when(fhirServicesClient.getMeasureBundleExport(
            any(Measure.class), eq(PUBLISH), eq("Error"), eq("******")))
        .thenReturn(exportBytes);

    // when
    PackageDto output = bundleService.getMeasureExport(measure, PUBLISH, "Error", "******");

    // then
    assertArrayEquals(exportBytes, output.getExportPackage());
    verify(fhirServicesClient).getMeasureBundleExport(measure, PUBLISH, "Error", "******");
  }

  @Test
  void testExportBundleMeasureForDraftMeasureThrowsException() {

    measure.setEcqmTitle("MEAS");
    measure.setMeasureMetaData(
        MeasureMetaData.builder()
            .draft(true)
            .steward(Organization.builder().name("SemanticBits").build())
            .description("This is a description")
            .developers(List.of(Organization.builder().name("ICF").build()))
            .build());
    measure.setModel("QI-Core v4.1.1");

    doThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN))
        .when(fhirServicesClient)
        .getMeasureBundleExport(any(Measure.class), eq("Info"), eq("Bearer TOKEN"));

    Exception ex =
        assertThrows(
            BundleOperationException.class,
            () -> bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN"));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "An error occurred while bundling Measure with ID xyz-p13r-13ert."
                    + " Please try again later or contact a System Administrator if this continues to occur.")));
  }

  @Test
  void testExportBundleMeasureForNullMeasureReturnsNull() {
    PackageDto output = bundleService.getMeasureExport(null, "Info", "Bearer TOKEN");
    assertNull(output);
  }

  @Test
  void testGetMeasureExportForCompositeDraftSuccess() {
    // Setup component measure
    Measure componentMeasure =
        Measure.builder()
            .id("comp-measure-id")
            .measureName("ComponentMeasure")
            .cqlLibraryName("ComponentLib")
            .version(new Version(1, 0, 0))
            .model("QI-Core v4.1.1")
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .groups(List.of(Group.builder().id("comp-group-1").displayId("Group 1").build()))
            .build();

    Component component =
        Component.builder().measureId("comp-measure-id").groupId("comp-group-1").build();

    Group compositeGroup =
        Group.builder()
            .id("composite-group-1")
            .components(List.of(component))
            .populationBasis("boolean")
            .measureGroupTypes(List.of(MeasureGroupTypes.PROCESS))
            .build();

    measure.setGroups(List.of(compositeGroup));
    measure.setModel("QI-Core v4.1.1");
    measure.setMeasureMetaData(
        MeasureMetaData.builder()
            .draft(true)
            .composite(true)
            .steward(Organization.builder().name("SemanticBits").build())
            .description("Composite measure")
            .developers(List.of(Organization.builder().name("ICF").build()))
            .build());

    final String compositeBundle = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    final String componentBundle = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;

    when(measureRepository.findById("comp-measure-id")).thenReturn(Optional.of(componentMeasure));
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), eq(EXPORT), anyString()))
        .thenReturn(compositeBundle);

    Export componentExport =
        Export.builder()
            .measureId("comp-measure-id")
            .measureBundleGridFsId("comp-grid-fs-id")
            .build();
    when(exportRepository.findByMeasureId("comp-measure-id"))
        .thenReturn(Optional.of(componentExport));
    when(mongoGridFsService.findById("comp-grid-fs-id")).thenReturn(componentBundle);

    PackageDto output = bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN");
    assertNotNull(output);
    assertNotNull(output.getExportPackage());
    assertFalse(output.isFromStorage());

    // verify component details were populated
    assertEquals("ComponentMeasure", component.getMeasureName());
    assertEquals("ComponentLib", component.getMeasureLibraryName());
    assertEquals("1.0.000", component.getMeasureVersion());
    assertFalse(component.isDraft());
    assertFalse(component.isMultiGroupComponent());
    assertEquals("Group 1", component.getGroupDisplayId());
  }

  @Test
  void testGetMeasureExportForCompositeDraftWithMultipleGroups() {
    Measure componentMeasure =
        Measure.builder()
            .id("comp-measure-id")
            .measureName("ComponentMeasure")
            .cqlLibraryName("ComponentLib")
            .version(new Version(2, 1, 0))
            .model("QI-Core v4.1.1")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .groups(
                List.of(
                    Group.builder().id("g1").displayId("Group 1").build(),
                    Group.builder().id("g2").displayId("Group 2").build()))
            .build();

    Component component = Component.builder().measureId("comp-measure-id").groupId("g2").build();

    Group compositeGroup =
        Group.builder()
            .id("composite-group-1")
            .components(List.of(component))
            .populationBasis("boolean")
            .measureGroupTypes(List.of(MeasureGroupTypes.PROCESS))
            .build();

    measure.setGroups(List.of(compositeGroup));
    measure.setModel("QI-Core v4.1.1");
    measure.setMeasureMetaData(
        MeasureMetaData.builder()
            .draft(true)
            .composite(true)
            .steward(Organization.builder().name("SemanticBits").build())
            .description("Composite measure")
            .developers(List.of(Organization.builder().name("ICF").build()))
            .build());

    final String bundle = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;

    when(measureRepository.findById("comp-measure-id")).thenReturn(Optional.of(componentMeasure));
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), eq(EXPORT), anyString()))
        .thenReturn(bundle);

    Export componentExport =
        Export.builder()
            .measureId("comp-measure-id")
            .measureBundleGridFsId("comp-grid-fs-id")
            .build();
    when(exportRepository.findByMeasureId("comp-measure-id"))
        .thenReturn(Optional.of(componentExport));
    when(mongoGridFsService.findById("comp-grid-fs-id")).thenReturn(bundle);

    PackageDto output = bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN");
    assertNotNull(output);

    // verify multi-group component
    assertTrue(component.isMultiGroupComponent());
    assertTrue(component.isDraft());
    assertEquals("Group 2", component.getGroupDisplayId());
  }

  @Test
  void testGetMeasureExportForCompositeDraftThrowsExceptionOnRestClientError() {
    Component component = Component.builder().measureId("comp-measure-id").build();

    Group compositeGroup =
        Group.builder().id("composite-group-1").components(List.of(component)).build();

    measure.setGroups(List.of(compositeGroup));
    measure.setModel("QI-Core v4.1.1");
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(true).composite(true).build());

    when(measureRepository.findById("comp-measure-id")).thenReturn(Optional.empty());
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), eq(EXPORT), anyString()))
        .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThrows(
        BundleOperationException.class,
        () -> bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN"));
  }

  @Test
  void testGetMeasureExportForCompositeDraftWithNoGroups() {
    measure.setGroups(null);
    measure.setModel("QI-Core v4.1.1");
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(true).composite(true).build());

    final String bundle = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), eq(EXPORT), anyString()))
        .thenReturn(bundle);

    PackageDto output = bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN");
    assertNotNull(output);
  }

  @Test
  void testGetMeasureExportForCompositeDraftWithEmptyComponents() {
    Group compositeGroup = Group.builder().id("composite-group-1").components(null).build();

    measure.setGroups(List.of(compositeGroup));
    measure.setModel("QI-Core v4.1.1");
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(true).composite(true).build());

    final String bundle = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), eq(EXPORT), anyString()))
        .thenReturn(bundle);

    PackageDto output = bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN");
    assertNotNull(output);
  }

  @Test
  void testGetMeasureExportForCompositeDraftComponentWithBlankMeasureId() {
    Component component = Component.builder().measureId("").build();

    Group compositeGroup =
        Group.builder().id("composite-group-1").components(List.of(component)).build();

    measure.setGroups(List.of(compositeGroup));
    measure.setModel("QI-Core v4.1.1");
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(true).composite(true).build());

    final String bundle = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), eq(EXPORT), anyString()))
        .thenReturn(bundle);

    PackageDto output = bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN");
    assertNotNull(output);
    // blank measureId should be skipped, no repository calls
    verify(measureRepository, never()).findById(anyString());
  }

  @Test
  void testGetMeasureExportForCompositeDraftComponentMeasureNotFound() {
    Component component = Component.builder().measureId("non-existent-id").groupId("g1").build();

    Group compositeGroup =
        Group.builder().id("composite-group-1").components(List.of(component)).build();

    measure.setGroups(List.of(compositeGroup));
    measure.setModel("QI-Core v4.1.1");
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(true).composite(true).build());

    final String bundle = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    when(measureRepository.findById("non-existent-id")).thenReturn(Optional.empty());
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), eq(EXPORT), anyString()))
        .thenReturn(bundle);

    // component export fetch will fail since export doesn't exist
    when(exportRepository.findByMeasureId("non-existent-id")).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> bundleService.getMeasureExport(measure, "Info", "Bearer TOKEN"));
  }
}
