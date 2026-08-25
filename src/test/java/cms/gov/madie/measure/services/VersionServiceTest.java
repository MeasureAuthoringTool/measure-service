package cms.gov.madie.measure.services;

import static cms.gov.madie.measure.constants.BundleTypeConstants.EXPORT;
import static cms.gov.madie.measure.constants.BundleTypeConstants.PUBLISH;

import cms.gov.madie.measure.dto.CompositeVersionArtifacts;
import cms.gov.madie.measure.dto.MadieFeatureFlag;
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
import cms.gov.madie.measure.utils.TestCaseServiceUtil;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.cqm.CqmMeasure;
import gov.cms.madie.packaging.utils.PackagingUtilityFactory;
import gov.cms.madie.packaging.utils.qicore411.PackagingUtilityImpl;
import gov.cms.madie.models.measure.*;

import org.bson.types.ObjectId;
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
import org.springframework.util.CollectionUtils;

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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VersionServiceTest {

  @Mock private MeasureRepository measureRepository;
  @Mock private CqmMeasureRepository cqmMeasureRepository;
  @Mock private ExportRepository exportRepository;

  @Mock ActionLogService actionLogService;
  @Mock MeasureService measureService;
  @Mock TestCaseSequenceService sequenceService;
  @Mock QdmPackageService qdmPackageService;
  @Mock AppConfigService appConfigService;
  @Mock ElmToJsonService elmToJsonService;

  @Mock private MongoGridFsService mongoGridFsService;
  @Mock ElmTranslatorClient elmTranslatorClient;
  @Mock FhirServicesClient fhirServicesClient;

  @Mock private TestCaseValidationService testCaseValidationService;

  @Mock private MeasureLockService measureLockService;

  @Mock private BundleService bundleService;

  @Captor private ArgumentCaptor<Measure> measureCaptor;
  @Captor private ArgumentCaptor<CqmMeasure> cqmMeasureCaptor;
  @Captor private ArgumentCaptor<Export> exportArgumentCaptor;

  @InjectMocks VersionService versionService;

  private final String json =
      """
  		{
  			"entry":[
  				{
  			      "resource":{
  			         "period":{
  			            "start":"2024-10-10T20:30:10.123-05:00",
  			            "end":"2024-10-10T07:31:20.456+06:00"
  			         }
  					}
  				}
  			]
  		}
  		""";

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
          .json(json)
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
  TestCase testCase3 =
      TestCase.builder()
          .id("testId2")
          .caseNumber(1)
          .name("IPPPass")
          .series("BloodPressure>124")
          .createdAt(today.minus(300, ChronoUnit.SECONDS))
          .createdBy("TestUser")
          .lastModifiedBy("TestUser2")
          .json("")
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

  MeasureSet measureSet =
      MeasureSet.builder().measureSetId("MS123").cmsId(144).owner("testUser").build();

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
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
            .measureSet(measureSet)
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    Exception ex =
        assertThrows(
            BadVersionRequestException.class,
            () ->
                versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken"));
    assertTrue(ex.getMessage().contains("Measure has CQL errors."));
  }

  @Test
  public void testCheckValidVersioningThrowsBadVersionRequestExceptionForCqlErrors() {
    Measure existingMeasure =
        Measure.builder()
            .id("testMeasureId")
            .createdBy("testUser")
            .cqlErrors(true)
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    Exception ex =
        assertThrows(
            BadVersionRequestException.class,
            () ->
                versionService.checkValidVersioning(
                    "testMeasureId", "MAJOR", "testUser", "accesstoken"));
    assertTrue(ex.getMessage().contains("Measure has CQL errors."));
  }

  @Test
  public void testCreateVersionThrowsBadVersionRequestExceptionForEmptyCQL() {
    Measure existingMeasure =
        Measure.builder()
            .id("testMeasureId")
            .createdBy("testUser")
            .cqlErrors(false)
            .cql("")
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
            .measureSet(measureSet)
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    Exception ex =
        assertThrows(
            BadVersionRequestException.class,
            () ->
                versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken"));
    assertTrue(ex.getMessage().contains("Measure has no CQL."));
  }

  @Test
  public void testCreateVersionThrowsBadVersionRequestExceptionForNoGroup() {
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
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
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
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
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
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
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
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
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
  public void testGetNextVersionOtherException() {
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
  public void testCreateVersionThrowsInstantiationExceptionWhenSavingToExport() {
    FhirMeasure existingMeasure =
        FhirMeasure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001'")
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
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

    when(fhirServicesClient.getMeasureBundle(any(), anyString(), anyString(), anyString()))
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
  public void testCreateVersionMajorSuccess() {
    FhirMeasure existingMeasure =
        FhirMeasure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001'")
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
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
    when(fhirServicesClient.getMeasureBundle(any(), anyString(), anyString(), anyString()))
        .thenReturn(measureBundleJson);
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), anyString(), anyString()))
        .thenReturn(measureBundleJson);
    // mock bundle and hex
    ObjectId measureBundleId = mock(ObjectId.class);
    when(measureBundleId.toHexString()).thenReturn("hex1");
    ObjectId measureBundleWithoutWarningsId = mock(ObjectId.class);
    when(measureBundleWithoutWarningsId.toHexString()).thenReturn("hex2");

    when(mongoGridFsService.save(
            any(ByteArrayInputStream.class),
            eq(existingMeasure.getEcqmTitle() + "-v" + updatedMeasure.getVersion().toString()),
            eq("application/json")))
        .thenReturn(measureBundleId);
    when(mongoGridFsService.save(
            any(ByteArrayInputStream.class),
            eq(
                existingMeasure.getEcqmTitle()
                    + "-v"
                    + updatedMeasure.getVersion().toString()
                    + "-withoutWarnings"),
            eq("application/json")))
        .thenReturn(measureBundleWithoutWarningsId);

    versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    Measure savedValue = measureCaptor.getValue();
    assertEquals(savedValue.getVersion().getMajor(), 3);
    assertEquals(savedValue.getVersion().getMinor(), 0);
    assertEquals(savedValue.getVersion().getRevisionNumber(), 0);
    assertFalse(savedValue.getMeasureMetaData().isDraft());
    verify(fhirServicesClient)
        .getMeasureBundle(any(Measure.class), anyString(), eq(EXPORT), eq("Info"));
    verify(fhirServicesClient)
        .getMeasureBundle(any(Measure.class), anyString(), eq(PUBLISH), eq("Error"));

    verify(exportRepository, times(1)).save(exportArgumentCaptor.capture());
    Export capturedExport = exportArgumentCaptor.getValue();
    assertEquals(savedValue.getId(), capturedExport.getMeasureId());
    assertEquals("hex1", capturedExport.getMeasureBundleGridFsId());
    assertEquals("hex2", capturedExport.getMeasureBundleWithoutWarningsGridFsId());
  }

  @Test
  public void testCreateVersionCompositeMeasureSuccessWithNoCql() {
    FhirMeasure existingMeasure =
        FhirMeasure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .createdBy("testUser")
            .cql(null)
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
            .model(ModelType.QI_CORE.getValue())
            .measureSet(measureSet)
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    metaData.setComposite(true);
    existingMeasure.setMeasureMetaData(metaData);
    existingMeasure.setVersion(Version.builder().major(2).minor(3).revisionNumber(1).build());

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);
    when(measureRepository.findMaxVersionByMeasureSetId(anyString()))
        .thenReturn(Optional.of(Version.builder().major(2).minor(3).revisionNumber(1).build()));

    Measure updatedMeasure = existingMeasure.toBuilder().build();
    updatedMeasure.setVersion(Version.builder().major(3).minor(0).revisionNumber(0).build());
    MeasureMetaData updatedMetaData = new MeasureMetaData();
    updatedMetaData.setDraft(false);
    updatedMetaData.setComposite(true);
    updatedMeasure.setMeasureMetaData(updatedMetaData);
    when(measureRepository.save(any(Measure.class))).thenReturn(updatedMeasure);

    factory.when(() -> PackagingUtilityFactory.getInstance(MODEL_QI_CORE)).thenReturn(utility);

    String compositeBundleJson =
        """
            {"resourceType": "Bundle","entry": [ {
                "resource": {
                  "resourceType": "Measure","text":{"div":"humanReadable"}}}]}""";
    List<Export.ComponentHumanReadable> componentHumanReadables =
        List.of(
            Export.ComponentHumanReadable.builder()
                .componentId("component-measure-id")
                .fileName("ComponentMeasure-v1.0.000-FHIR")
                .humanReadable("<html>component human readable</html>")
                .build());
    when(bundleService.buildCompositeVersionArtifacts(any(Measure.class), anyString()))
        .thenReturn(
            new CompositeVersionArtifacts(
                compositeBundleJson, compositeBundleJson, componentHumanReadables));

    Export measureExport =
        Export.builder()
            .id("testId")
            .measureId("testMeasureId")
            .measureBundleJson(compositeBundleJson)
            .build();
    when(exportRepository.save(any(Export.class))).thenReturn(measureExport);

    ObjectId measureBundleId = mock(ObjectId.class);
    when(measureBundleId.toHexString()).thenReturn("hex1");
    ObjectId measureBundleWithoutWarningsId = mock(ObjectId.class);
    when(measureBundleWithoutWarningsId.toHexString()).thenReturn("hex2");
    when(mongoGridFsService.save(any(ByteArrayInputStream.class), anyString(), anyString()))
        .thenReturn(measureBundleId, measureBundleWithoutWarningsId);

    versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken");

    verify(bundleService, times(1)).buildCompositeVersionArtifacts(any(Measure.class), anyString());
    // composites skip CQL/ELM validation
    verify(elmTranslatorClient, never()).getElmJson(anyString(), anyString(), anyString());
    verify(elmToJsonService, never()).retrieveElmJson(any(Measure.class), anyString(), anyString());

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    Measure savedValue = measureCaptor.getValue();
    assertEquals(3, savedValue.getVersion().getMajor());
    assertFalse(savedValue.getMeasureMetaData().isDraft());

    verify(exportRepository, times(1)).save(exportArgumentCaptor.capture());
    Export capturedExport = exportArgumentCaptor.getValue();
    assertEquals(savedValue.getId(), capturedExport.getMeasureId());
    assertEquals("hex1", capturedExport.getMeasureBundleGridFsId());
    assertEquals("hex2", capturedExport.getMeasureBundleWithoutWarningsGridFsId());
    assertEquals(componentHumanReadables, capturedExport.getComponentHumanReadables());
  }

  @Test
  public void testCreateVersionCompositeMeasureFailsWithNoPopulationCriteria() {
    FhirMeasure existingMeasure =
        FhirMeasure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .createdBy("testUser")
            .cql(null)
            .groups(List.of())
            .model(ModelType.QI_CORE.getValue())
            .measureSet(measureSet)
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    metaData.setComposite(true);
    existingMeasure.setMeasureMetaData(metaData);
    existingMeasure.setVersion(Version.builder().major(1).minor(0).revisionNumber(0).build());

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    BadVersionRequestException ex =
        assertThrows(
            BadVersionRequestException.class,
            () ->
                versionService.createVersion("testMeasureId", "MAJOR", "testUser", "accesstoken"));
    assertTrue(ex.getMessage().contains("Population Criteria"));
    verify(bundleService, never()).buildCompositeVersionArtifacts(any(Measure.class), anyString());
  }

  @Test
  public void testCreateQdmVersionMinorSuccess() {
    QdmMeasure existingMeasure =
        QdmMeasure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001'")
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
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
    when(qdmPackageService.createNewMeasurePackage(any(Measure.class), anyString(), anyBoolean()))
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
  public void testCreateFhirVersionPatchSuccess() {
    FhirMeasure existingMeasure =
        FhirMeasure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001'")
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
            .ecqmTitle("testMsr")
            .version(Version.builder().major(2).minor(3).revisionNumber(1).build())
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
    Export measureExport = Export.builder().id("testId").measureId("testMeasureId").build();
    when(exportRepository.save(any(Export.class))).thenReturn(measureExport);
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), anyString(), eq("Info")))
        .thenReturn(measureBundleJson);

    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), anyString(), eq("Error")))
        .thenReturn(measureBundleJson);

    ObjectId measureBundleWithWarningsId = mock(ObjectId.class);
    ObjectId measureBundleWithoutWarningsId = mock(ObjectId.class);
    when(mongoGridFsService.save(
            any(ByteArrayInputStream.class),
            eq(existingMeasure.getEcqmTitle() + "-v" + updatedMeasure.getVersion().toString()),
            eq("application/json")))
        .thenReturn(measureBundleWithWarningsId);
    when(mongoGridFsService.save(
            any(ByteArrayInputStream.class),
            eq(
                existingMeasure.getEcqmTitle()
                    + "-v"
                    + updatedMeasure.getVersion().toString()
                    + "-withoutWarnings"),
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
    assertNull(measureExport.getMeasureBundleJson());
    assertEquals(measureBundleWithWarningsId.toHexString(), savedExport.getMeasureBundleGridFsId());
    assertEquals(
        measureBundleWithoutWarningsId.toHexString(),
        savedExport.getMeasureBundleWithoutWarningsGridFsId());
  }

  @Test
  public void testCreateDraftSuccessfullyForQiCoreJsonInvalid() {
    TestCaseGroupPopulation clonedTestCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("clonedGroupId1")
            .scoring("Cohort")
            .populationBasis("boolean")
            .build();
    Measure versionedMeasure = buildBasicMeasure();
    testCase.setJson(json.replace("}", ""));
    versionedMeasure.setTestCases(List.of(testCase));
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
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

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
    assertFalse(draft.getTestCases().get(0).getJson().contains("2024-10-11T01:30:10.123+00:00"));
    assertFalse(draft.getTestCases().get(0).getJson().contains("2024-10-10T01:31:20.456+00:00"));
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
                        .json(
                            json.replace(
                                    "2024-10-10T20:30:10.123-05:00",
                                    "2024-10-11T01:30:10.123+00:00")
                                .replace(
                                    "2024-10-10T07:31:20.456+06:00",
                                    "2024-10-10T01:31:20.456+00:00"))
                        .build()))
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(versionedCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

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
    assertTrue(draft.getTestCases().get(0).getJson().contains("2024-10-11T01:30:10.123+00:00"));
    assertTrue(draft.getTestCases().get(0).getJson().contains("2024-10-10T01:31:20.456+00:00"));
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
    testCase.setJson(null);
    versionedMeasure.setTestCases(List.of(testCase));
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
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

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
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

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
  public void testCreateDraftWithUpdatedModelSuccessfullyUsCore() {
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
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

    versionService.createDraft(
        versionedMeasure.getId(), "Test", "US Quality Core v0.5.0", "test-user", TEST_ACCESS_TOKEN);
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
    assertThat(draft.getModel(), is(equalTo(ModelType.US_QUALITY_CORE_0_5_0.getValue())));
    assertThat(draft.getCql(), containsStringIgnoringCase("using USQualityCore version '0.5.0'"));
  }

  @Test
  public void testCreateDraftDropsExtraGroupPopulationsWhenTestCaseHasMoreGroupsThanMeasure() {
    // Test case has 2 group populations but target measure only has 1 group.
    // The extra group population should be silently dropped to prevent IndexOutOfBoundsException.
    TestCaseGroupPopulation groupPop1 =
        TestCaseGroupPopulation.builder()
            .groupId("groupId1")
            .scoring("Cohort")
            .populationBasis("boolean")
            .build();
    TestCaseGroupPopulation groupPop2 =
        TestCaseGroupPopulation.builder()
            .groupId("groupId2")
            .scoring("Cohort")
            .populationBasis("boolean")
            .build();

    TestCase testCaseWithTwoGroups =
        testCase.toBuilder().groupPopulations(List.of(groupPop1, groupPop2)).build();

    Measure versionedMeasure = buildBasicMeasure(); // has 1 group (cvGroup)
    versionedMeasure.setTestCases(List.of(testCaseWithTwoGroups));

    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    Measure versionedCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(metaData)
            .groups(List.of(cvGroup.toBuilder().id("clonedGroupId1").build()))
            .testCases(List.of())
            .build();

    ArgumentCaptor<Measure> measureArgumentCaptor = ArgumentCaptor.forClass(Measure.class);
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(versionedCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

    // Should not throw IndexOutOfBoundsException
    assertDoesNotThrow(
        () ->
            versionService.createDraft(
                versionedMeasure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN));

    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure captured = measureArgumentCaptor.getValue();
    // The extra group population (groupId2) should have been dropped
    assertThat(captured.getTestCases().size(), is(equalTo(1)));
    assertThat(captured.getTestCases().get(0).getGroupPopulations().size(), is(equalTo(1)));
    // The remaining group population should be mapped to the target group
    assertThat(
        captured.getTestCases().get(0).getGroupPopulations().get(0).getGroupId(),
        not(equalTo("groupId1")));
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
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

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

  @Test
  public void testCreateDraftClearsCompositeMeasureIds() {
    Measure versionedMeasure = buildBasicMeasure();
    versionedMeasure.setCompositeMeasureIds(List.of("composite-id-1"));
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    Measure versionedCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureMetaData(metaData)
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(versionedCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

    versionService.createDraft(
        versionedMeasure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN);

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    Measure draft = measureCaptor.getValue();
    assertNull(draft.getCompositeMeasureIds());
    assertThat(versionedMeasure.getCompositeMeasureIds(), is(equalTo(List.of("composite-id-1"))));
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
        .reviewMetaData(
            new ReviewMetaData().toBuilder().lastReviewDate(today).approvalDate(today).build())
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
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);
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
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);
    when(sequenceService.generateSequence(anyString())).thenReturn(1);
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

  @Test
  public void testCreateDraftCopyCaseNumberFromExistingInvalidTestCase() {
    TestCaseGroupPopulation clonedTestCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("clonedGroupId1")
            .scoring("Cohort")
            .populationBasis("boolean")
            .build();
    Measure versionedMeasure = buildBasicMeasure();
    versionedMeasure.setTestCases(List.of(testCase3));

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
                    testCase2.toBuilder()
                        .id(ObjectId.get().toString())
                        .groupPopulations(List.of(clonedTestCaseGroupPopulation))
                        .hapiOperationOutcome(null)
                        .build()))
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(versionedCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);
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
    assertThat(draft.getTestCases().get(0).getCaseNumber(), is(equalTo(1)));
    assertThat(draft.getTestCases().get(0).getCaseNumber(), is(equalTo(1)));
    assertThat(draft.getTestCases().get(0).getHapiOperationOutcome(), is(equalTo(null)));
  }

  @Test
  public void testCreateDraftWhenQiCore411HasQiCore600() {
    Measure measure = buildBasicMeasure();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.findByMeasureSetIdAndModelInAndMeasureMetaDataDraft(
            anyString(), anyList(), anyBoolean()))
        .thenReturn(List.of(measure));

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
                "Can not create a draft for the measure \"Test\". You cannot draft a QI-Core v4.1.1 measure when a newer version is available.")));
  }

  @Test
  public void testCreateDraftWhenQiCore600HasQiCore700() {
    Measure measure = buildBasicMeasure();
    measure.setModel(ModelType.QI_CORE_6_0_0.getValue());
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.findByMeasureSetIdAndModelInAndMeasureMetaDataDraft(
            anyString(), anyList(), anyBoolean()))
        .thenReturn(List.of(measure));

    Exception ex =
        assertThrows(
            MeasureNotDraftableException.class,
            () ->
                versionService.createDraft(
                    measure.getId(), "Test", "QI-Core v6.0.0", "test-user", TEST_ACCESS_TOKEN));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "Can not create a draft for the measure \"Test\". You cannot draft a QI-Core v6.0.0 measure when a newer version is available.")));
  }

  @Test
  public void testCreateQiCore600DraftWithQiCore411Model() {
    Measure measure = buildBasicMeasure();
    measure.setModel(ModelType.QI_CORE_6_0_0.getValue());
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);

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
                "Can not create a draft for the measure \"Test\". You cannot draft a QI-Core v6.0.0 measure to a QI-Core v4.1.1 measure.")));
  }

  @Test
  public void testCreateQiCore600DraftSuccessfully() {
    Measure versionedMeasure = buildBasicMeasure();
    versionedMeasure.setModel(ModelType.QI_CORE_6_0_0.getValue());

    Measure versionedCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .groups(List.of())
            .testCases(List.of())
            .reviewMetaData(new ReviewMetaData())
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(versionedCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

    Measure draft =
        versionService.createDraft(
            versionedMeasure.getId(),
            "Test",
            ModelType.QDM_5_6.getValue(),
            "test-user",
            TEST_ACCESS_TOKEN);

    assertThat(draft.getMeasureName(), is(equalTo("Test")));
    // draft flag to true
    assertThat(draft.getMeasureMetaData().isDraft(), is(equalTo(true)));
    assertThat(draft.getReviewMetaData().getLastReviewDate(), is(equalTo(null)));
    assertThat(draft.getReviewMetaData().getApprovalDate(), is(equalTo(null)));
    // version remains same
    assertThat(draft.getVersion().getMajor(), is(equalTo(2)));
    assertThat(draft.getVersion().getMinor(), is(equalTo(3)));
    assertThat(draft.getVersion().getRevisionNumber(), is(equalTo(1)));
    assertThat(draft.getGroups().size(), is(equalTo(0)));
    assertThat(draft.getTestCases().size(), is(equalTo(0)));
  }

  @Test
  public void testCreateQiCore700DraftWithQiCore600Model() {
    Measure measure = buildBasicMeasure();
    measure.setModel(ModelType.QI_CORE_7_0_0.getValue());
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);

    Exception ex =
        assertThrows(
            MeasureNotDraftableException.class,
            () ->
                versionService.createDraft(
                    measure.getId(), "Test", "QI-Core v6.0.0", "test-user", TEST_ACCESS_TOKEN));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "Can not create a draft for the measure \"Test\". You cannot draft a QI-Core v7.0.0 measure to a QI-Core v6.0.0 measure.")));
  }

  @Test
  public void testCreateQiCore700DraftSuccessfully() {
    Measure versionedMeasure = buildBasicMeasure();
    versionedMeasure.setModel(ModelType.QI_CORE_7_0_0.getValue());

    Measure versionedCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .groups(List.of())
            .testCases(List.of())
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(versionedCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

    Measure draft =
        versionService.createDraft(
            versionedMeasure.getId(),
            "Test",
            ModelType.QDM_5_6.getValue(),
            "test-user",
            TEST_ACCESS_TOKEN);

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
  public void testCreateDraftWhenMeasureIsDraft() {
    Measure measure = buildBasicMeasure();
    measure.getMeasureMetaData().setDraft(true);
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));

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
                "Can not create a draft for the measure \"Test\". Only versioned measure can be drafted.")));
  }

  @Test
  public void testCreateDraftWhenMeasureMetaDataIsNull() {
    Measure measure = buildBasicMeasure();
    measure.setMeasureMetaData(null);
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));

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
                "Can not create a draft for the measure \"Test\". Only versioned measure can be drafted.")));
  }

  @Test
  public void testCreateDraftSuccessfullyWhenModelIsChanged() {
    TestCaseGroupPopulation clonedTestCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("clonedGroupId1")
            .scoring("Cohort")
            .populationBasis("boolean")
            .build();
    Measure versionedMeasure = buildBasicMeasure();
    testCase.setJson(json.replace("}", ""));
    versionedMeasure.setTestCases(List.of(testCase));
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    Measure versionedCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .model(MODEL_QI_CORE)
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
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

    // Mocks a validation request awaiting execution.
    when(testCaseValidationService.validateResourceAsynchronously(
            any(), any(TestCase.class), eq(TestCaseServiceUtil.IMPORT), anyString()))
        .thenAnswer(
            invocation ->
                invocation.getArgument(1, TestCase.class).toBuilder()
                    .validationStatus(TestCaseValidationStatus.PENDING.toString())
                    .build());

    Measure draft =
        versionService.createDraft(
            versionedMeasure.getId(),
            "Test",
            ModelType.QI_CORE_6_0_0.getValue(),
            "test-user",
            TEST_ACCESS_TOKEN);

    assertThat(draft.getMeasureName(), is(equalTo("Test")));
    assertThat(draft.getMeasureMetaData().isDraft(), is(equalTo(true)));
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
    assertFalse(draft.getTestCases().get(0).getJson().contains("2024-10-11T01:30:10.123+00:00"));
    assertFalse(draft.getTestCases().get(0).getJson().contains("2024-10-10T01:31:20.456+00:00"));
  }

  @Test
  public void testCreateVersion() {
    FhirMeasure existingMeasure =
        FhirMeasure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001'")
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
            .model(ModelType.QI_CORE.getValue())
            .measureSet(measureSet)
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);
    Version version = Version.builder().major(2).minor(3).revisionNumber(1).build();
    existingMeasure.setVersion(version);

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);
    when(measureLockService.checkMeasureAndTestCaseLock(
            anyString(), any(Measure.class), anyString()))
        .thenReturn(false);

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
    when(fhirServicesClient.getMeasureBundle(any(), anyString(), anyString(), anyString()))
        .thenReturn(measureBundleJson);
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), anyString(), anyString()))
        .thenReturn(measureBundleJson);
    // mock bundle and hex
    ObjectId measureBundleId = mock(ObjectId.class);
    when(measureBundleId.toHexString()).thenReturn("hex1");
    ObjectId measureBundleWithoutWarningsId = mock(ObjectId.class);
    when(measureBundleWithoutWarningsId.toHexString()).thenReturn("hex2");

    when(mongoGridFsService.save(
            any(ByteArrayInputStream.class),
            eq(existingMeasure.getEcqmTitle() + "-v" + updatedMeasure.getVersion().toString()),
            eq("application/json")))
        .thenReturn(measureBundleId);
    when(mongoGridFsService.save(
            any(ByteArrayInputStream.class),
            eq(
                existingMeasure.getEcqmTitle()
                    + "-v"
                    + updatedMeasure.getVersion().toString()
                    + "-withoutWarnings"),
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
  public void testCreateDraftSkipsTestCaseSetIdBackfillWhenFeatureFlagDisabled() {
    Measure versionedMeasure = buildBasicMeasure();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    Measure draftCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .model(MODEL_QI_CORE)
            .measureMetaData(metaData)
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.TEST_CASE_SET_ID)).thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(draftCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

    versionService.createDraft(
        versionedMeasure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN);

    // testCaseSetIdExistsInSet must never be consulted when the flag is off
    verify(measureRepository, never()).testCaseSetIdExistsInSet(anyString());
  }

  @Test
  public void testCreateDraftSkipsTestCaseSetIdBackfillForQDMMeasure() {
    String model = ModelType.QDM_5_6.getValue();
    Measure versionedMeasure = buildBasicMeasure();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    versionedMeasure.setModel(model);
    Measure draftCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(metaData)
            .model(model)
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.TEST_CASE_SET_ID)).thenReturn(true);
    when(measureRepository.save(any(Measure.class))).thenReturn(draftCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

    versionService.createDraft(
        versionedMeasure.getId(), "Test", model, "test-user", TEST_ACCESS_TOKEN);

    // testCaseSetIdExistsInSet must never be consulted when the flag is off
    verify(measureRepository, never()).testCaseSetIdExistsInSet(anyString());
  }

  @Test
  public void testCreateDraftSkipsTestCaseSetIdBackfillWhenSetIdsAlreadyExist() {
    Measure versionedMeasure = buildBasicMeasure();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    Measure draftCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .model(MODEL_QI_CORE)
            .measureMetaData(metaData)
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.TEST_CASE_SET_ID)).thenReturn(true);
    // set IDs already present — no backfill required
    when(measureRepository.testCaseSetIdExistsInSet(anyString())).thenReturn(true);
    when(measureRepository.save(any(Measure.class))).thenReturn(draftCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

    versionService.createDraft(
        versionedMeasure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN);

    // repository.save is called exactly once (for the draft itself, not for backfilling)
    verify(measureRepository, times(1)).save(any(Measure.class));
    // test cases on the original measure must not have been mutated with set ids
    versionedMeasure.getTestCases().forEach(tc -> assertNull(tc.getTestCaseSetId()));
  }

  @Test
  public void testCreateDraftBackfillsTestCaseSetIdsWhenFlagEnabledAndSetIdsAbsent() {
    // Use a fresh test case with no testCaseSetId set
    TestCase tcWithoutSetId =
        TestCase.builder()
            .id("tc-no-set-id")
            .caseNumber(1)
            .name("NoSetId")
            .groupPopulations(List.of(testCaseGroupPopulation))
            .build();
    Measure versionedMeasure = buildBasicMeasure();
    versionedMeasure.setTestCases(List.of(tcWithoutSetId));

    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    Measure draftCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(metaData)
            .model(MODEL_QI_CORE)
            .testCases(List.of(tcWithoutSetId))
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.TEST_CASE_SET_ID)).thenReturn(true);
    // no set IDs present yet — backfill should run
    when(measureRepository.testCaseSetIdExistsInSet(anyString())).thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(draftCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

    versionService.createDraft(
        versionedMeasure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN);

    // Each test case should now have a testCaseSetId assigned
    versionedMeasure
        .getTestCases()
        .forEach(tc -> assertNotNull(tc.getTestCaseSetId(), "testCaseSetId should have been set"));

    // save should be called twice: once for the backfill and once for the draft itself
    verify(measureRepository, times(2)).save(any(Measure.class));
  }

  @Test
  public void testCreateDraftDoesNotBackfillTestCaseSetIdsWhenTestCasesAreEmpty() {
    Measure versionedMeasure = buildBasicMeasure();
    versionedMeasure.setTestCases(List.of()); // no test cases

    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    Measure draftCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(metaData)
            .testCases(List.of())
            .model(MODEL_QI_CORE)
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(versionedMeasure));
    when(measureRepository.existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(false);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.TEST_CASE_SET_ID)).thenReturn(true);
    // set IDs not present, but there are no test cases to backfill
    when(measureRepository.testCaseSetIdExistsInSet(anyString())).thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(draftCopy);
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

    versionService.createDraft(
        versionedMeasure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN);

    // backfill save must not happen — only the draft save should occur
    verify(measureRepository, times(1)).save(any(Measure.class));
  }

  @Test
  public void testCreateDraftHtmlifiesRichTextContent() {
    ArgumentCaptor<Measure> measureArgumentCaptor = ArgumentCaptor.forClass(Measure.class);

    TestCaseGroupPopulation clonedTestCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("clonedGroupId1")
            .scoring("Cohort")
            .populationBasis("boolean")
            .build();

    // Create a measure with markdown-style content in metadata
    Measure versionedMeasure = buildBasicMeasure();
    MeasureMetaData metadata = new MeasureMetaData();
    metadata.setDescription("This is a <strong>bold</strong> description");
    metadata.setRationale("This is an _italic_ rationale");
    metadata.setPurpose("This is a purpose");
    versionedMeasure.setMeasureMetaData(metadata);

    // Set up a group with markdown description
    Group groupWithMarkdown =
        cvGroup.toBuilder().groupDescription("Group with **markdown** content").build();
    versionedMeasure.setGroups(List.of(groupWithMarkdown));

    MeasureMetaData draftMetadata = new MeasureMetaData();
    draftMetadata.setDraft(true);
    draftMetadata.setDescription("<p>This is a <strong>bold</strong> description</p>");
    draftMetadata.setRationale("<p>This is an <em>italic</em> rationale</p>");
    draftMetadata.setPurpose("<p>This is a purpose</p>");

    Measure versionedCopy =
        versionedMeasure.toBuilder()
            .id("2")
            .versionId("13-13-13-13")
            .measureName("Test")
            .measureMetaData(draftMetadata)
            .groups(
                List.of(
                    cvGroup.toBuilder()
                        .id(ObjectId.get().toString())
                        .groupDescription("<p>Group with <strong>markdown</strong> content</p>")
                        .build()))
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
    when(actionLogService.logAction(anyString(), any(), any(), anyString(), anyString()))
        .thenReturn(true);

    versionService.createDraft(
        versionedMeasure.getId(), "Test", MODEL_QI_CORE, "test-user", TEST_ACCESS_TOKEN);

    // Verify that measureRepository.save was called with htmlified content
    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure capturedMeasure = measureArgumentCaptor.getValue();

    // Verify that rich text content was htmlified (converted from markdown to HTML)
    assertThat(
        capturedMeasure.getMeasureMetaData().getDescription(),
        containsString("<strong>bold</strong>"));
    assertThat(
        capturedMeasure.getMeasureMetaData().getRationale(), containsString("<em>italic</em>"));
    assertThat(
        capturedMeasure.getMeasureMetaData().getPurpose(),
        containsString("<p>This is a purpose</p>"));
    assertThat(
        capturedMeasure.getGroups().get(0).getGroupDescription(),
        containsString("<strong>markdown</strong>"));
  }

  @Test
  public void testVersionBackfillsTestCaseSetIdsWhenFlagEnabledAndSetIdsAbsent() {
    TestCase tc = testCase.toBuilder().testCaseSetId(null).build();

    FhirMeasure existingMeasure =
        FhirMeasure.builder()
            .id("testMeasureId")
            .measureSetId("testMeasureSetId")
            .createdBy("testUser")
            .cql("library Test1CQLLib version '2.3.001'")
            .model(ModelType.QI_CORE.getValue())
            .groups(List.of(cvGroup.toBuilder().id(ObjectId.get().toString()).build()))
            .testCases(List.of(tc))
            .measureSet(measureSet)
            .build();

    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);
    existingMeasure.setMeasureMetaData(metaData);
    existingMeasure.setVersion(Version.builder().major(2).minor(3).revisionNumber(1).build());

    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    ElmJson elmJson = ElmJson.builder().json(ELMJON_NO_ERROR).build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString())).thenReturn(elmJson);
    when(elmTranslatorClient.hasErrors(any())).thenReturn(false);

    when(appConfigService.isFlagEnabled(MadieFeatureFlag.TEST_CASE_SET_ID)).thenReturn(true);
    when(measureRepository.testCaseSetIdExistsInSet("testMeasureSetId")).thenReturn(false);

    when(measureRepository.findMaxVersionByMeasureSetId(anyString()))
        .thenReturn(Optional.of(Version.builder().major(2).minor(3).revisionNumber(1).build()));

    String bundleJson = "{\"resourceType\":\"Bundle\"}";
    when(fhirServicesClient.getMeasureBundle(
            any(Measure.class), anyString(), anyString(), anyString()))
        .thenReturn(bundleJson);

    factory.when(() -> PackagingUtilityFactory.getInstance(anyString())).thenReturn(utility);
    when(utility.getHumanReadableWithCSS(anyString())).thenReturn("<html></html>");

    ObjectId objectId = new ObjectId();
    when(mongoGridFsService.save(any(), anyString(), anyString())).thenReturn(objectId);
    when(exportRepository.save(any(Export.class)))
        .thenReturn(Export.builder().id("exportId").build());
    when(measureRepository.save(any(Measure.class))).thenReturn(existingMeasure);

    versionService.createVersion("testMeasureId", "MAJOR", "testUser", TEST_ACCESS_TOKEN);

    // verify the repository was called to check for existing set ids
    verify(measureRepository, times(1)).testCaseSetIdExistsInSet("testMeasureSetId");
    assertNotNull(tc.getTestCaseSetId());

    // verify save was called to persist the backfilled ids
    verify(measureRepository, atLeastOnce()).save(measureCaptor.capture());
    List<Measure> savedMeasures = measureCaptor.getAllValues();
    assertTrue(
        savedMeasures.stream()
            .anyMatch(
                m ->
                    !CollectionUtils.isEmpty(m.getTestCases())
                        && m.getTestCases().stream().allMatch(t -> t.getTestCaseSetId() != null)));
  }

  @Test
  void testUpdateUsingStatementUSQualityCoreAddsUSCoreWhenNotExists() throws Exception {
    String cql = "using USQualityCore version '0.5.0'\ndefine x: 1";
    String result =
        (String) invokeUpdateUsingStatement(ModelType.US_QUALITY_CORE_0_5_0.getValue(), cql);

    assertTrue(result.contains("using USCore version '6.1.0-derived'"));
    assertTrue(result.contains("using FHIR version '4.0.1'"));
  }

  @Test
  void testUpdateUsingStatementUSQualityCoreReplacesUSCoreWhenExists() throws Exception {
    String cql = "using USQualityCore version '0.5.0'\nusing USCore version '3.0.0'\ndefine x: 1";
    String result =
        (String) invokeUpdateUsingStatement(ModelType.US_QUALITY_CORE_0_5_0.getValue(), cql);

    assertFalse(result.contains("using USCore version '3.0.0'"));
    assertTrue(result.contains("using USCore version '6.1.0-derived'"));
    assertTrue(result.contains("using FHIR version '4.0.1'"));
  }

  @Test
  void testUpdateUsingStatementUSQualityCoreDoesNotAddFHIRWhenExists() throws Exception {
    String cql = "using USQualityCore version '0.5.0'\nusing FHIR version '4.0.0'\ndefine x: 1";
    String result =
        (String) invokeUpdateUsingStatement(ModelType.US_QUALITY_CORE_0_5_0.getValue(), cql);

    assertTrue(result.contains("using USCore version '6.1.0-derived'"));
    assertTrue(result.contains("using FHIR version '4.0.0'"));
    assertFalse(result.contains("using FHIR version '4.0.1'\nusing FHIR"));
  }

  @Test
  void testUpdateUsingStatementUSQualityCoreWithBothUSCoreAndFHIRPresent() throws Exception {
    String cql =
        "using USQualityCore version '0.5.0'\nusing USCore version '5.0.0'\nusing FHIR version '4.0.0'\ndefine x: 1";
    String result =
        (String) invokeUpdateUsingStatement(ModelType.US_QUALITY_CORE_0_5_0.getValue(), cql);

    assertTrue(result.contains("using USCore version '6.1.0-derived'"));
    assertTrue(result.contains("using FHIR version '4.0.0'"));
    assertFalse(result.contains("using FHIR version '4.0.1'"));
  }

  @Test
  void testUpdateUsingStatementQICoreReplacesQICoreOnly() throws Exception {
    String cql = "using QICore version '4.1.1'\ndefine x: 1";
    String result = (String) invokeUpdateUsingStatement(ModelType.QI_CORE_6_0_0.getValue(), cql);

    assertTrue(result.contains("using QICore version '6.0.0'"));
    assertFalse(result.contains("using USCore"));
    assertFalse(result.contains("using FHIR"));
  }

  @Test
  void testUpdateUsingStatementUSQualityCoreMultipleUSCoreMatches() throws Exception {
    String cql =
        "using USQualityCore version '0.5.0'\nusing USCore version '3.0.0'\ndefine x: using USCore version '2.0.0'";
    String result =
        (String) invokeUpdateUsingStatement(ModelType.US_QUALITY_CORE_0_5_0.getValue(), cql);

    assertTrue(result.contains("using USCore version '6.1.0-derived'"));
    assertTrue(result.contains("using FHIR version '4.0.1'"));
  }

  private Object invokeUpdateUsingStatement(String model, String cql) throws Exception {
    var method =
        VersionService.class.getDeclaredMethod("updateUsingStatement", String.class, String.class);
    method.setAccessible(true);
    return method.invoke(versionService, model, cql);
  }
}
