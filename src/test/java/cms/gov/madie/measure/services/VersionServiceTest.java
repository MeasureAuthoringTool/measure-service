package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.exceptions.BadVersionRequestException;
import cms.gov.madie.measure.exceptions.BundleOperationException;
import cms.gov.madie.measure.exceptions.CqlElmTranslationErrorException;
import cms.gov.madie.measure.exceptions.MeasureNotDraftableException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import cms.gov.madie.measure.repositories.CqmMeasureRepository;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.cqm.CqmMeasure;
import gov.cms.madie.models.measure.ElmJson;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.packaging.utils.PackagingUtilityFactory;
import gov.cms.madie.packaging.utils.qicore411.PackagingUtilityImpl;
import gov.cms.madie.models.measure.*;

import org.bson.types.ObjectId;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static cms.gov.madie.measure.services.VersionService.VersionValidationResult.TEST_CASE_ERROR;
import static cms.gov.madie.measure.services.VersionService.VersionValidationResult.VALID;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VersionServiceTest {

  @Mock private MeasureRepository measureRepository;
  @Mock private CqmMeasureRepository cqmMeasureRepository;
  @Mock private ExportRepository exportRepository;

  @Mock ActionLogService actionLogService;
  @Mock MeasureService measureService;
  @Mock ExportService exportService;
  @Mock TestCaseSequenceService sequenceService;
  @Mock QdmPackageService qdmPackageService;
  @Mock AppConfigService appConfigService;
  @Mock ElmToJsonService elmToJsonService;

  @Mock private GridFsTemplate gridFsTemplate;
  @Mock ElmTranslatorClient elmTranslatorClient;
  @Mock FhirServicesClient fhirServicesClient;

  @Captor private ArgumentCaptor<Measure> measureCaptor;
  @Captor private ArgumentCaptor<CqmMeasure> cqmMeasureCaptor;
  @Captor private ArgumentCaptor<Export> exportArgumentCaptor;

  @InjectMocks VersionService versionService;

  private final String ELMJON_ERROR =
      "{\n" + "\"errorExceptions\" : \n" + "[ {\"error\":\"error translating cql\" } ]\n" + "}";
  private final String ELMJON_NO_ERROR = "{\n" + "\"errorExceptions\" : \n" + "[]\n" + "}";

  private final Instant today = Instant.now();

  private static final String TEST_ACCESS_TOKEN = "test-user-access-token";

  private final HapiOperationOutcome validTestCaseHapiOperationOutcome =
      HapiOperationOutcome.builder().code(2).message("No issues").successful(true).build();
  private final HapiOperationOutcome invalidTestCaseHapiOperationOutcome =
      HapiOperationOutcome.builder().code(42).message("invalid json").successful(false).build();

  TestCaseGroupPopulation testCaseGroupPopulation =
      TestCaseGroupPopulation.builder()
          .groupId("groupId1")
          .scoring("Cohort")
          .populationBasis("boolean")
          .build();

  TestCase testCase =
      TestCase.builder()
          .id("testId1")
          .caseNumber(2)
          .name("IPPPass")
          .series("BloodPressure>124")
          .createdAt(today)
          .createdBy("TestUser")
          .lastModifiedBy("TestUser2")
          .json("{\"resourceType\":\"Patient\"}")
          .title("Test1")
          .groupPopulations(List.of(testCaseGroupPopulation))
          .build();
  TestCase testCase2 =
      TestCase.builder()
          .id("testId2")
          .caseNumber(1)
          .name("IPPPass")
          .series("BloodPressure>124")
          .createdAt(today.minus(300, ChronoUnit.SECONDS))
          .createdBy("TestUser")
          .lastModifiedBy("TestUser2")
          .json("{\"resourceType\":\"Patient\"}")
          .title("Test2")
          .groupPopulations(List.of(testCaseGroupPopulation))
          .build();
  Group cvGroup =
      Group.builder()
          .id("xyz-p12r-12ert")
          .populationBasis("Encounter")
          .scoring("Continuous Variable")
          .populations(
              List.of(
                  new Population(
                      "id-1",
                      PopulationType.INITIAL_POPULATION,
                      "FactorialOfFive",
                      null,
                      null,
                      "IntialPopulation_1"),
                  new Population(
                      "id-2",
                      PopulationType.MEASURE_POPULATION,
                      "Measure Population",
                      null,
                      null,
                      "MeasurePopulation_1")))
          .measureObservations(
              List.of(
                  new MeasureObservation(
                      "id-1",
                      "fun",
                      "a description of fun",
                      "id-2",
                      AggregateMethodType.MAXIMUM.getValue(),
                      "MeasureObservation_1")))
          .stratifications(List.of())
          .groupDescription("Description")
          .scoringUnit("test-scoring-unit")
          .build();

  MeasureSet measureSet = MeasureSet.builder().measureSetId("MS123").cmsId(144).build();

  private static final String MODEL_QI_CORE = "QI-Core v4.1.1";
  private static MockedStatic<PackagingUtilityFactory> factory;
  private static PackagingUtilityImpl utility = mock(PackagingUtilityImpl.class);

  @BeforeAll
  public static void staticSetup() {
    factory = mockStatic(PackagingUtilityFactory.class);
  }

  @AfterAll
  public static void close() {
    factory.close();
  }

  @Test
  public void testCheckValidVersioningThrowsResourceNotFoundException() {
    when(measureService.findMeasureById(anyString())).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            versionService.checkValidVersioning(
                "testMeasureId", "MAJOR", "testUser", "accesstoken"));
  }

  @Test
  public void testCreateVersionThrowsResourceNotFoundException() {
    when(measureService.findMeasureById(anyString())).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () -> versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken"));
  }

  @Test
  public void testCreateVersionThrowsBadVersionRequestExceptionForInvalidVersionType() {
    Measure existingMeasure =
        Measure.builder().id("testMeasureId").createdBy("testUser").measureSet(measureSet).build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    assertThrows(
        BadVersionRequestException.class,
        () ->
            versionService.createVersion(
                "testMeasureId", "NOTVALIDVERSIONTYPE", "testUser", "accesstoken"));
  }

  @Test
  public void testCheckValidVersioningThrowsBadVersionRequestExceptionForInvalidVersionType() {
    Measure existingMeasure = Measure.builder().id("testMeasureId").createdBy("testUser").build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    assertThrows(
        BadVersionRequestException.class,
        () ->
            versionService.checkValidVersioning(
                "testMeasureId", "NOTVALIDVERSIONTYPE", "testUser", "accesstoken"));
  }

  @Test
  public void testCheckValidVersioningThrowsUnauthorizedExceptionForNonOwner() {
    Measure existingMeasure =
        Measure.builder().id("testMeasureId").createdBy("anotherUser").build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);
    doThrow(new UnauthorizedException("Measure", "testMeasureId", "testUser"))
        .when(measureService)
        .verifyAuthorization(anyString(), any(Measure.class));

    assertThrows(
        UnauthorizedException.class,
        () ->
            versionService.checkValidVersioning(
                "testMeasureId", "MAJOR", "testUser", "accesstoken"));
  }

  @Test
  public void testCreateVersionThrowsUnauthorizedExceptionForNonOwner() {
    Measure existingMeasure =
        Measure.builder()
            .id("testMeasureId")
            .createdBy("anotherUser")
            .measureSet(measureSet)
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);
    doThrow(new UnauthorizedException("Measure", "testMeasureId", "testUser"))
        .when(measureService)
        .verifyAuthorization(anyString(), any(Measure.class));

    assertThrows(
        UnauthorizedException.class,
        () -> versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken"));
  }

  @Test
  public void testCreateVersionThrowsBadVersionRequestExceptionForNonDraftMeasure() {
    Measure existingMeasure =
        Measure.builder().id("testMeasureId").createdBy("testUser").measureSet(measureSet).build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(false);
    existingMeasure.setMeasureMetaData(metaData);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    assertThrows(
        BadVersionRequestException.class,
        () -> versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken"));
  }

  @Test
  public void testCheckValidVersioningThrowsBadVersionRequestExceptionForNonDraftMeasure() {
    Measure existingMeasure = Measure.builder().id("testMeasureId").createdBy("testUser").build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(false);
    existingMeasure.setMeasureMetaData(metaData);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    assertThrows(
        BadVersionRequestException.class,
        () ->
            versionService.checkValidVersioning(
                "testMeasureId", "MAJOR", "testUser", "accesstoken"));
  }

  @Test
  public void testCreateVersionThrowsBadVersionRequestExceptionForCqlErrors() {
    Measure existingMeasure =
        Measure.builder()
            .id("testMeasureId")
            .createdBy("testUser")
            .cqlErrors(true)
            .measureSet(measureSet)
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    assertThrows(
        BadVersionRequestException.class,
        () -> versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken"));
  }

  @Test
  public void testCheckValidVersioningThrowsBadVersionRequestExceptionForCqlErrors() {
    Measure existingMeasure =
        Measure.builder().id("testMeasureId").createdBy("testUser").cqlErrors(true).build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    assertThrows(
        BadVersionRequestException.class,
        () ->
            versionService.checkValidVersioning(
                "testMeasureId", "MAJOR", "testUser", "accesstoken"));
  }

  @Test
  public void testCreateVersionThrowsBadVersionRequestExceptionForEmptyCQL() {
    Measure existingMeasure =
        Measure.builder()
            .id("testMeasureId")
            .createdBy("testUser")
            .cqlErrors(false)
            .cql("")
            .measureSet(measureSet)
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    assertThrows(
        BadVersionRequestException.class,
        () -> versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken"));
  }

  @Test
  public void testCreateVersionThrowsCqlElmTranslationErrorExceptionForInvalidCQL() {
    Measure existingMeasure =
        Measure.builder()
            .id("testMeasureId")
            .measureName("test measure")
            .createdBy("testUser")
            .cqlErrors(false)
            .model(ModelType.QDM_5_6.getValue())
            .cql("test cql")
            .measureSet(measureSet)
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    ElmJson elmJson = ElmJson.builder().json(ELMJON_ERROR).build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString())).thenReturn(elmJson);
    when(elmTranslatorClient.hasErrors(any())).thenReturn(true);

    assertThrows(
        CqlElmTranslationErrorException.class,
        () -> versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken"));
  }

  @Test
  public void testCheckValidVersioningThrowsCqlElmTranslationErrorExceptionForInvalidCQL() {
    Measure existingMeasure =
        Measure.builder()
            .id("testMeasureId")
            .measureName("test measure")
            .createdBy("testUser")
            .cqlErrors(false)
            .cql("test cql")
            .model(ModelType.QDM_5_6.getValue())
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    ElmJson elmJson = ElmJson.builder().json(ELMJON_ERROR).build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString())).thenReturn(elmJson);
    when(elmTranslatorClient.hasErrors(any())).thenReturn(true);

    assertThrows(
        CqlElmTranslationErrorException.class,
        () ->
            versionService.checkValidVersioning(
                "testMeasureId", "MAJOR", "testUser", "accesstoken"));
  }

  @Test
  public void testCheckVersionIdentifiesTestCaseErrors() {
    Measure existingMeasure =
        Measure.builder()
            .id("testMeasureId")
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001")
            .model(ModelType.QDM_5_6.getValue())
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);
    List<TestCase> testCases = List.of(TestCase.builder().validResource(false).build());
    existingMeasure.setTestCases(testCases);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    ElmJson elmJson = ElmJson.builder().json(ELMJON_NO_ERROR).build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString())).thenReturn(elmJson);
    when(elmTranslatorClient.hasErrors(any())).thenReturn(false);
    var validationResult =
        versionService.checkValidVersioning("testMeasureId", "MAJOR", "testUser", "accesstoken");
    assertEquals(TEST_CASE_ERROR, validationResult);
  }

  @Test
  public void testCheckVersion() {
    Measure existingMeasure =
        Measure.builder()
            .id("testMeasureId")
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001")
            .model(ModelType.QDM_5_6.getValue())
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    ElmJson elmJson = ElmJson.builder().json(ELMJON_NO_ERROR).build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString())).thenReturn(elmJson);
    when(elmTranslatorClient.hasErrors(any())).thenReturn(false);
    var validationResult =
        versionService.checkValidVersioning("testMeasureId", "MAJOR", "testUser", "accesstoken");
    assertEquals(VALID, validationResult);
  }

  @Test
  public void testGetNextVersionOtherException() throws Exception {
    Measure existingMeasure =
        Measure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001'")
            .build();
    Version version = versionService.getNextVersion(existingMeasure, "InvalidVersionType");
    assertEquals(version.getMajor(), 0);
    assertEquals(version.getMinor(), 0);
    assertEquals(version.getRevisionNumber(), 0);
  }

  @Test
  public void testCreateVersionThrowsInstantiationExceptionWhenSavingToExport() throws Exception {
    FhirMeasure existingMeasure =
        FhirMeasure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001'")
            .model(ModelType.QI_CORE.getValue())
            .measureSet(measureSet)
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);
    Version version = Version.builder().major(2).minor(3).revisionNumber(1).build();
    existingMeasure.setVersion(version);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    ElmJson elmJson = ElmJson.builder().json(ELMJON_NO_ERROR).build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString())).thenReturn(elmJson);
    when(elmTranslatorClient.hasErrors(any())).thenReturn(false);

    Version newVersion = Version.builder().major(2).minor(2).revisionNumber(2).build();
    when(measureRepository.findMaxVersionByMeasureSetId(anyString()))
        .thenReturn(Optional.of(newVersion));

    String measureBundleJson =
        """
            {"resourceType": "Bundle","entry": [ {
                "resource": {
                  "resourceType": "Measure","text":{"div":"humanReadable"}}}]}""";

    when(fhirServicesClient.getMeasureBundle(any(), anyString(), anyString()))
        .thenReturn(measureBundleJson);

    factory
        .when(() -> PackagingUtilityFactory.getInstance(MODEL_QI_CORE))
        .thenThrow(
            new InstantiationException("Unexpected error while getting human readable with CSS"));

    Exception ex =
        assertThrows(
            BundleOperationException.class,
            () ->
                versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken"));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "An error occurred while bundling Measure with ID testMeasureId. Please try again later or contact a System Administrator if this continues to occur.")));
  }

  @Test
  public void testCreateVersionMajorSuccess() throws Exception {
    FhirMeasure existingMeasure =
        FhirMeasure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001'")
            .model(ModelType.QI_CORE.getValue())
            .measureSet(measureSet)
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);
    Version version = Version.builder().major(2).minor(3).revisionNumber(1).build();
    existingMeasure.setVersion(version);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    ElmJson elmJson = ElmJson.builder().json(ELMJON_NO_ERROR).build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString())).thenReturn(elmJson);
    when(elmTranslatorClient.hasErrors(any())).thenReturn(false);

    Version newVersion = Version.builder().major(2).minor(2).revisionNumber(2).build();
    when(measureRepository.findMaxVersionByMeasureSetId(anyString()))
        .thenReturn(Optional.of(newVersion));

    Measure updatedMeasure = existingMeasure.toBuilder().build();
    Version updatedVersion = Version.builder().major(3).minor(0).revisionNumber(0).build();
    updatedMeasure.setVersion(updatedVersion);
    MeasureMetaData updatedMetaData = new MeasureMetaData();
    updatedMetaData.setDraft(false);
    updatedMeasure.setMeasureMetaData(updatedMetaData);
    when(measureRepository.save(any(Measure.class))).thenReturn(updatedMeasure);

    factory.when(() -> PackagingUtilityFactory.getInstance(MODEL_QI_CORE)).thenReturn(utility);

    String measureBundleJson =
        """
            {"resourceType": "Bundle","entry": [ {
                "resource": {
                  "resourceType": "Measure","text":{"div":"humanReadable"}}}]}""";
    Export measureExport =
        Export.builder()
            .id("testId")
            .measureId("testMeasureId")
            .measureBundleJson(measureBundleJson)
            .measureBundleGridFsId("id1")
            .measureBundleWithoutWarningsGridFsId("id2")
            .build();
    when(exportRepository.save(any(Export.class))).thenReturn(measureExport);
    when(fhirServicesClient.getMeasureBundle(any(), anyString(), anyString()))
        .thenReturn(measureBundleJson);
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class),
            anyString(),
            anyString(),
            any(CqlCompilerException.ErrorSeverity.class)))
        .thenReturn(measureBundleJson);
    // mock bundle andhex?
    ObjectId measureBundleId = mock(ObjectId.class);
    when(measureBundleId.toHexString()).thenReturn("hex1");
    ObjectId measureBundleWithoutWarningsId = mock(ObjectId.class);
    when(measureBundleWithoutWarningsId.toHexString()).thenReturn("hex2");

    when(gridFsTemplate.store(
            any(ByteArrayInputStream.class), eq("measureBundle.json"), eq("application/json")))
        .thenReturn(measureBundleId);
    when(gridFsTemplate.store(
            any(ByteArrayInputStream.class),
            eq("measureBundleWithoutWarnings.json"),
            eq("application/json")))
        .thenReturn(measureBundleWithoutWarningsId);

    versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    Measure savedValue = measureCaptor.getValue();
    assertEquals(savedValue.getVersion().getMajor(), 3);
    assertEquals(savedValue.getVersion().getMinor(), 0);
    assertEquals(savedValue.getVersion().getRevisionNumber(), 0);
    assertFalse(savedValue.getMeasureMetaData().isDraft());

    verify(exportRepository, times(1)).save(exportArgumentCaptor.capture());
    Export capturedExport = exportArgumentCaptor.getValue();
    assertEquals(savedValue.getId(), capturedExport.getMeasureId());
    assertEquals("hex1", capturedExport.getMeasureBundleGridFsId());
    assertEquals("hex2", capturedExport.getMeasureBundleWithoutWarningsGridFsId());
  }

  @Test
  public void testCreateQdmVersionMinorSuccess() throws Exception {
    QdmMeasure existingMeasure =
        QdmMeasure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001'")
            .model(ModelType.QDM_5_6.getValue())
            .measureSet(measureSet)
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);
    Version version = Version.builder().major(2).minor(3).revisionNumber(1).build();
    existingMeasure.setVersion(version);
    List<TestCase> testCases = List.of(TestCase.builder().validResource(true).build());
    existingMeasure.setTestCases(testCases);
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    ElmJson elmJson = ElmJson.builder().json(ELMJON_NO_ERROR).build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString())).thenReturn(elmJson);
    when(elmTranslatorClient.hasErrors(any())).thenReturn(false);

    Version newVersion = Version.builder().major(2).minor(3).revisionNumber(2).build();
    when(measureRepository.findMaxMinorVersionByMeasureSetIdAndVersionMajor(anyString(), anyInt()))
        .thenReturn(Optional.of(newVersion));

    Measure updatedMeasure = existingMeasure.toBuilder().build();
    Version updatedVersion = Version.builder().major(2).minor(4).revisionNumber(0).build();
    updatedMeasure.setVersion(updatedVersion);
    MeasureMetaData updatedMetaData = new MeasureMetaData();
    updatedMetaData.setDraft(false);
    updatedMeasure.setMeasureMetaData(updatedMetaData);
    when(measureRepository.save(any(Measure.class))).thenReturn(updatedMeasure);

    byte[] exportPackage = "Look, I'm a measure package".getBytes();
    when(exportService.getMeasureExport(any(Measure.class), anyString()))
        .thenReturn(PackageDto.builder().fromStorage(false).exportPackage(exportPackage).build());
    when(qdmPackageService.getHumanReadable(any(Measure.class), anyString(), anyString()))
        .thenReturn("test human readable");

    when(exportRepository.save(any(Export.class)))
        .thenAnswer(
            invocationOnMock -> {
              Export ex = invocationOnMock.getArgument(0);
              ex.setId("ID123");
              return ex;
            });

    versionService.createVersion("testMeasureId", "MINOR", "testUser", "accesstoken");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    verify(cqmMeasureRepository, times(1)).save(cqmMeasureCaptor.capture());
    Measure savedValue = measureCaptor.getValue();
    assertEquals(savedValue.getVersion().getMajor(), 2);
    assertEquals(savedValue.getVersion().getMinor(), 4);
    assertEquals(savedValue.getVersion().getRevisionNumber(), 0);
    assertFalse(savedValue.getMeasureMetaData().isDraft());
    verify(exportRepository, times(1)).save(exportArgumentCaptor.capture());
    Export export = exportArgumentCaptor.getValue();
    assertThat(export.getMeasureId(), is(equalTo(updatedMeasure.getId())));
    assertThat(export.getPackageData(), is(equalTo(exportPackage)));
    assertThat(export.getHumanReadable(), is(equalTo("test human readable")));
  }

  @Test
  public void testCreateFhirVersionPatchSuccess() throws Exception {
    FhirMeasure existingMeasure =
        FhirMeasure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001'")
            .measureSet(measureSet)
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);
    Version version = Version.builder().major(2).minor(3).revisionNumber(1).build();
    existingMeasure.setVersion(version);
    List<TestCase> testCases = List.of(TestCase.builder().validResource(true).build());
    existingMeasure.setTestCases(testCases);
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    ElmJson elmJson = ElmJson.builder().json(ELMJON_NO_ERROR).build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString())).thenReturn(elmJson);
    when(elmTranslatorClient.hasErrors(any())).thenReturn(false);

    Version newVersion = Version.builder().major(2).minor(3).revisionNumber(1).build();
    when(measureRepository.findMaxRevisionNumberByMeasureSetIdAndVersionMajorAndMinor(
            anyString(), anyInt(), anyInt()))
        .thenReturn(Optional.of(newVersion));

    Measure updatedMeasure = existingMeasure.toBuilder().build();
    Version updatedVersion = Version.builder().major(2).minor(3).revisionNumber(2).build();
    updatedMeasure.setVersion(updatedVersion);
    MeasureMetaData updatedMetaData = new MeasureMetaData();
    updatedMetaData.setDraft(false);
    updatedMeasure.setMeasureMetaData(updatedMetaData);
    when(measureRepository.save(any(Measure.class))).thenReturn(updatedMeasure);

    factory.when(() -> PackagingUtilityFactory.getInstance("QI-Core v6.0.0")).thenReturn(utility);

    String measureBundleJson =
        """
            {"resourceType": "Bundle","entry": [ {
                "resource": {
                  "resourceType": "Measure","text":{"div":"humanReadable"}}}]}""";
    Export measureExport =
        Export.builder()
            .id("testId")
            .measureId("testMeasureId")
            .measureBundleJson(measureBundleJson)
            .measureBundleGridFsId("id1")
            .measureBundleWithoutWarningsGridFsId("id2")
            .build();
    when(exportRepository.save(any(Export.class))).thenReturn(measureExport);
    when(fhirServicesClient.getMeasureBundle(any(Measure.class), anyString(), anyString()))
        .thenReturn(measureBundleJson);

    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class),
            anyString(),
            anyString(),
            any(CqlCompilerException.ErrorSeverity.class)))
        .thenReturn(measureBundleJson);
    ObjectId measureBundleId = mock(ObjectId.class);
    when(measureBundleId.toHexString()).thenReturn("hex1");
    ObjectId measureBundleWithoutWarningsId = mock(ObjectId.class);
    when(measureBundleWithoutWarningsId.toHexString()).thenReturn("hex2");

    when(gridFsTemplate.store(
            any(ByteArrayInputStream.class), eq("measureBundle.json"), eq("application/json")))
        .thenReturn(measureBundleId);
    when(gridFsTemplate.store(
            any(ByteArrayInputStream.class),
            eq("measureBundleWithoutWarnings.json"),
            eq("application/json")))
        .thenReturn(measureBundleWithoutWarningsId);

    versionService.createVersion("testMeasureId", "PATCH", "testUser", "accesstoken");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    Measure savedValue = measureCaptor.getValue();
    assertEquals(savedValue.getVersion().getMajor(), 2);
    assertEquals(savedValue.getVersion().getMinor(), 3);
    assertEquals(savedValue.getVersion().getRevisionNumber(), 2);
    assertFalse(savedValue.getMeasureMetaData().isDraft());

    verify(exportRepository, times(1)).save(exportArgumentCaptor.capture());
    Export savedExport = exportArgumentCaptor.getValue();
    assertEquals(savedValue.getId(), savedExport.getMeasureId());
    // no longer save to measureBundle,  we want to make sure there's a hex appended
    assertEquals("hex1", savedExport.getMeasureBundleGridFsId());
    assertEquals("hex2", savedExport.getMeasureBundleWithoutWarningsGridFsId());
  }

  @Test
  public void testCreateDraftSuccessfullyForQiCore() {
    TestCaseGroupPopulation clonedTestCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("clonedGroupId1")
            .scoring("Cohort")
            .populationBasis("boolean")
            .build();
    Measure versionedMeasure = buildBasicMeasure();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    Measure versionedCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(metaData)
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
            .testCases(
                List.of(
                    testCase.toBuilder()
                        .id(ObjectId.get().toString())
                        .groupPopulations(List.of(clonedTestCaseGroupPopulation))
                        .hapiOperationOutcome(validTestCaseHapiOperationOutcome)
                        .build()))
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(versionedCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString())).thenReturn(true);
    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenReturn(ResponseEntity.ok(validTestCaseHapiOperationOutcome));

    Measure draft =
        versionService.createDraft(
            versionedMeasure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN);

    assertThat(draft.getMeasureName(), is(equalTo("Test")));
    // draft flag to true
    assertThat(draft.getMeasureMetaData().isDraft(), is(equalTo(true)));
    // version remains same
    assertThat(draft.getVersion().getMajor(), is(equalTo(2)));
    assertThat(draft.getVersion().getMinor(), is(equalTo(3)));
    assertThat(draft.getVersion().getRevisionNumber(), is(equalTo(1)));
    assertThat(draft.getGroups().size(), is(equalTo(1)));
    assertFalse(draft.getGroups().stream().anyMatch(item -> "xyz-p12r-12ert".equals(item.getId())));
    assertThat(draft.getTestCases().size(), is(equalTo(1)));
    assertFalse(draft.getGroups().stream().anyMatch(item -> "testId1".equals(item.getId())));
    assertThat(
        draft.getTestCases().get(0).getGroupPopulations().get(0).getGroupId(),
        is(equalTo("clonedGroupId1")));
    assertTrue(draft.getTestCases().get(0).getHapiOperationOutcome().isSuccessful());
  }

  @Test
  public void testCreateDraftSuccessfullyForQdm() {
    TestCaseGroupPopulation clonedTestCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("clonedGroupId1")
            .scoring("Cohort")
            .populationBasis("boolean")
            .build();
    Measure versionedMeasure = buildBasicMeasure();
    versionedMeasure.setModel(ModelType.QDM_5_6.getValue());
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    Measure versionedCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(metaData)
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
            .testCases(
                List.of(
                    testCase.toBuilder()
                        .id(ObjectId.get().toString())
                        .groupPopulations(List.of(clonedTestCaseGroupPopulation))
                        .build()))
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(versionedCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString())).thenReturn(true);

    Measure draft =
        versionService.createDraft(
            versionedMeasure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN);

    assertThat(draft.getMeasureName(), is(equalTo("Test")));
    // draft flag to true
    assertThat(draft.getMeasureMetaData().isDraft(), is(equalTo(true)));
    // version remains same
    assertThat(draft.getVersion().getMajor(), is(equalTo(2)));
    assertThat(draft.getVersion().getMinor(), is(equalTo(3)));
    assertThat(draft.getVersion().getRevisionNumber(), is(equalTo(1)));
    assertThat(draft.getGroups().size(), is(equalTo(1)));
    assertFalse(draft.getGroups().stream().anyMatch(item -> "xyz-p12r-12ert".equals(item.getId())));
    assertThat(draft.getTestCases().size(), is(equalTo(1)));
    assertFalse(draft.getGroups().stream().anyMatch(item -> "testId1".equals(item.getId())));
    assertThat(
        draft.getTestCases().get(0).getGroupPopulations().get(0).getGroupId(),
        is(equalTo("clonedGroupId1")));
  }

  @Test
  public void testCreateDraftWithUpdatedModelSuccessfully() {
    ArgumentCaptor<Measure> measureArgumentCaptor = ArgumentCaptor.forClass(Measure.class);

    TestCaseGroupPopulation clonedTestCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("clonedGroupId1")
            .scoring("Cohort")
            .populationBasis("boolean")
            .build();
    Measure versionedMeasure = buildBasicMeasure();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    Measure versionedCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(metaData)
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .cql("library TestCQLLib version '2.3.001'\nusing QICore version '6.0.0'\n")
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
            .testCases(
                List.of(
                    testCase.toBuilder()
                        .id(ObjectId.get().toString())
                        .groupPopulations(List.of(clonedTestCaseGroupPopulation))
                        .build()))
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(versionedCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString())).thenReturn(true);
    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenReturn(ResponseEntity.ok(validTestCaseHapiOperationOutcome));

    versionService.createDraft(
        versionedMeasure.getId(), "Test", "QI-Core v6.0.0", "test-user", TEST_ACCESS_TOKEN);
    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure draft = measureArgumentCaptor.getValue();

    assertThat(draft.getMeasureName(), is(equalTo("Test")));
    // draft flag to true
    assertThat(draft.getMeasureMetaData().isDraft(), is(equalTo(true)));
    // version remains same
    assertThat(draft.getVersion().getMajor(), is(equalTo(2)));
    assertThat(draft.getVersion().getMinor(), is(equalTo(3)));
    assertThat(draft.getVersion().getRevisionNumber(), is(equalTo(1)));
    assertThat(draft.getGroups().size(), is(equalTo(1)));
    assertFalse(draft.getGroups().stream().anyMatch(item -> "xyz-p12r-12ert".equals(item.getId())));
    assertThat(draft.getTestCases().size(), is(equalTo(1)));
    assertFalse(draft.getGroups().stream().anyMatch(item -> "testId1".equals(item.getId())));
    assertThat(
        draft.getTestCases().get(0).getGroupPopulations().get(0).getGroupId(), notNullValue());
    assertThat(draft.getModel(), is(equalTo(ModelType.QI_CORE_6_0_0.getValue())));
    assertThat(draft.getCql(), containsStringIgnoringCase("using QICore version '6.0.0'"));
  }

  @Test
  public void testCreateDraftSuccessfullyWithoutGroups() {

    Measure versionedMeasure =
        Measure.builder()
            .id("1")
            .measureSetId("1-1-1-1")
            .measureName("Test")
            .createdBy("test-user")
            .cql("library TestCQLLib version '2.3.001'")
            .versionId("12-12-12-12")
            .version(Version.builder().major(2).minor(3).revisionNumber(1).build())
            .measureMetaData(new MeasureMetaData())
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);

    Measure versionedCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(metaData)
            .groups(List.of())
            .testCases(List.of())
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(versionedCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString())).thenReturn(true);

    Measure draft =
        versionService.createDraft(
            versionedMeasure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN);

    assertThat(draft.getMeasureName(), is(equalTo("Test")));
    // draft flag to true
    assertThat(draft.getMeasureMetaData().isDraft(), is(equalTo(true)));
    // version remains same
    assertThat(draft.getVersion().getMajor(), is(equalTo(2)));
    assertThat(draft.getVersion().getMinor(), is(equalTo(3)));
    assertThat(draft.getVersion().getRevisionNumber(), is(equalTo(1)));
    assertThat(draft.getGroups().size(), is(equalTo(0)));
    assertThat(draft.getTestCases().size(), is(equalTo(0)));
  }

  @Test
  public void testCreateDraftWhenMeasureDoesNotExists() {
    String measureId = "nonExistent";
    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());
    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                versionService.createDraft(
                    measureId, "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN));
    assertThat(ex.getMessage(), is(equalTo("Could not find Measure with id: " + measureId)));
  }

  @Test
  public void testCreateDraftWhenDraftUserUnAuthorized() {
    String user = "bad guy";
    Measure measure = buildBasicMeasure();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    doThrow(new UnauthorizedException("Measure", "1", user))
        .when(measureService)
        .verifyAuthorization(anyString(), any(Measure.class));

    Exception ex =
        assertThrows(
            UnauthorizedException.class,
            () ->
                versionService.createDraft(
                    measure.getId(), "Test", MODEL_QI_CORE, user, TEST_ACCESS_TOKEN));
    assertThat(
        ex.getMessage(), is(equalTo("User " + user + " is not authorized for Measure with ID 1")));
  }

  @Test
  public void testCreateDraftWhenDraftAlreadyExists() {
    Measure measure = buildBasicMeasure();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(true);

    Exception ex =
        assertThrows(
            MeasureNotDraftableException.class,
            () ->
                versionService.createDraft(
                    measure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "Can not create a draft for the measure \"Test\". Only one draft is permitted per measure.")));
  }

  private Measure buildBasicMeasure() {
    return Measure.builder()
        .id("1")
        .measureSetId("1-1-1-1")
        .measureName("Test")
        .model(ModelType.QI_CORE.getValue())
        .createdBy("test-user")
        .cql("library TestCQLLib version '2.3.001'\nusing QICore version '4.1.1'\n")
        .versionId("12-12-12-12")
        .version(Version.builder().major(2).minor(3).revisionNumber(1).build())
        .measureMetaData(new MeasureMetaData())
        .groups(List.of(cvGroup))
        .testCases(List.of(testCase))
        .build();
  }

  @Test
  public void testCreateDraftCopyCaseNumberFromExistingTestCase() {
    TestCaseGroupPopulation clonedTestCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("clonedGroupId1")
            .scoring("Cohort")
            .populationBasis("boolean")
            .build();
    Measure versionedMeasure = buildBasicMeasure();
    versionedMeasure.setTestCases(List.of(testCase, testCase2));

    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    Measure versionedCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(metaData)
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
            .testCases(
                List.of(
                    testCase.toBuilder()
                        .id(ObjectId.get().toString())
                        .groupPopulations(List.of(clonedTestCaseGroupPopulation))
                        .hapiOperationOutcome(invalidTestCaseHapiOperationOutcome)
                        .build(),
                    testCase2.toBuilder()
                        .id(ObjectId.get().toString())
                        .groupPopulations(List.of(clonedTestCaseGroupPopulation))
                        .hapiOperationOutcome(invalidTestCaseHapiOperationOutcome)
                        .build()))
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(versionedCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString())).thenReturn(true);
    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenReturn(ResponseEntity.ok(invalidTestCaseHapiOperationOutcome));
    Measure draft =
        versionService.createDraft(
            versionedMeasure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN);

    assertThat(draft.getMeasureName(), is(equalTo("Test")));
    // draft flag to true
    assertThat(draft.getMeasureMetaData().isDraft(), is(equalTo(true)));
    // version remains same
    assertThat(draft.getVersion().getMajor(), is(equalTo(2)));
    assertThat(draft.getVersion().getMinor(), is(equalTo(3)));
    assertThat(draft.getVersion().getRevisionNumber(), is(equalTo(1)));
    assertThat(draft.getGroups().size(), is(equalTo(1)));
    assertFalse(draft.getGroups().stream().anyMatch(item -> "xyz-p12r-12ert".equals(item.getId())));
    assertThat(draft.getTestCases().size(), is(equalTo(2)));
    assertFalse(draft.getGroups().stream().anyMatch(item -> "testId1".equals(item.getId())));
    assertThat(
        draft.getTestCases().get(0).getGroupPopulations().get(0).getGroupId(),
        is(equalTo("clonedGroupId1")));
    assertThat(draft.getTestCases().get(0).getCaseNumber(), is(equalTo(2)));
    assertThat(draft.getTestCases().get(1).getCaseNumber(), is(equalTo(1)));
    assertFalse(draft.getTestCases().get(0).getHapiOperationOutcome().isSuccessful());
    assertEquals(
        "invalid json", draft.getTestCases().get(0).getHapiOperationOutcome().getMessage());
    ;
  }

  @Test
  public void testCreateDraftCopyCaseNumberFromSequenceGenerator() {
    TestCaseGroupPopulation clonedTestCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("clonedGroupId1")
            .scoring("Cohort")
            .populationBasis("boolean")
            .build();
    Measure versionedMeasure = buildBasicMeasure();
    testCase.setCaseNumber(null);
    testCase.setCreatedAt(null);
    testCase2.setCaseNumber(0);
    versionedMeasure.setTestCases(List.of(testCase, testCase2));

    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    Measure versionedCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(metaData)
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
            .testCases(
                List.of(
                    testCase.toBuilder()
                        .id(ObjectId.get().toString())
                        .groupPopulations(List.of(clonedTestCaseGroupPopulation))
                        .caseNumber(1)
                        .build(),
                    testCase2.toBuilder()
                        .id(ObjectId.get().toString())
                        .groupPopulations(List.of(clonedTestCaseGroupPopulation))
                        .caseNumber(2)
                        .build()))
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(versionedCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString())).thenReturn(true);
    when(sequenceService.generateSequence(anyString())).thenReturn(1);
    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenReturn(ResponseEntity.ok(validTestCaseHapiOperationOutcome));
    Measure draft =
        versionService.createDraft(
            versionedMeasure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN);

    assertThat(draft.getMeasureName(), is(equalTo("Test")));
    // draft flag to true
    assertThat(draft.getMeasureMetaData().isDraft(), is(equalTo(true)));
    // version remains same
    assertThat(draft.getVersion().getMajor(), is(equalTo(2)));
    assertThat(draft.getVersion().getMinor(), is(equalTo(3)));
    assertThat(draft.getVersion().getRevisionNumber(), is(equalTo(1)));
    assertThat(draft.getGroups().size(), is(equalTo(1)));
    assertFalse(draft.getGroups().stream().anyMatch(item -> "xyz-p12r-12ert".equals(item.getId())));
    assertThat(draft.getTestCases().size(), is(equalTo(2)));
    assertFalse(draft.getGroups().stream().anyMatch(item -> "testId1".equals(item.getId())));
    assertThat(
        draft.getTestCases().get(0).getGroupPopulations().get(0).getGroupId(),
        is(equalTo("clonedGroupId1")));
    assertThat(draft.getTestCases().get(0).getCaseNumber(), is(equalTo(1)));
  }
}
