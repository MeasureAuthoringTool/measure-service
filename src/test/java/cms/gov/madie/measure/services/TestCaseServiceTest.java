package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.CopyTestCaseResult;
import cms.gov.madie.measure.dto.JobStatus;
import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.dto.MeasureTestCaseValidationReport;
import cms.gov.madie.measure.exceptions.DuplicateTestCaseNameException;
import cms.gov.madie.measure.exceptions.InvalidIdException;
import cms.gov.madie.measure.exceptions.LockNotObtainedException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import cms.gov.madie.measure.locks.TestCaseLock;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.JsonUtil;
import cms.gov.madie.measure.utils.ResourceUtil;

import cms.gov.madie.measure.utils.TestCaseServiceUtil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import gov.cms.madie.models.dto.TestCaseExportMetaData;
import gov.cms.madie.models.measure.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TestCaseServiceTest implements ResourceUtil {
  @Mock private MeasureRepository measureRepository;

  @Mock private ActionLogService actionLogService;
  @Mock private FhirServicesClient fhirServicesClient;
  @Mock private MeasureService measureService;
  @Mock private AppConfigService appConfigService;
  @Mock private TestCaseValidationService testCaseValidationService;
  @Mock private TestCaseSequenceService testCaseSequenceService;
  @Mock private TestCaseLockService testCaseLockService;

  @Spy @InjectMocks private TestCaseService testCaseService;

  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;
  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;
  @Captor private ArgumentCaptor<Class> targetClassArgumentCaptor;
  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor;
  @Captor private ArgumentCaptor<TestCase> testCaseCaptor;

  private TestCase testCase;
  private Measure measure;
  private Group group;
  private Population population1;
  private Population population2;
  private Population population3;
  private Population population4;
  private Population population5;

  String testCaseImportWithMeasureReport = getData("/test_case_exported_json.json");
  String testCaseImportQdm = getData("/test_case_exported_qdm_json.json");
  private static final String qdmTestCaseDescription =
      "Patient is seen in ED,  Decision to Admit order and assessment performed;patient does not have a psychiatric diagnosis; order should calculate, not assessment time; measure observation 50 minutes";

  @BeforeEach
  public void setUp() {
    testCase = new TestCase();
    testCase.setId("TESTID");
    testCase.setTitle("IPPPass");
    testCase.setSeries("BloodPressure bigger than 124");
    testCase.setCreatedBy("TestUser");
    testCase.setLastModifiedBy("TestUser2");
    testCase.setJson("{\n  \"resourceType\" : \"Patient\"\n}");
    testCase.setPatientId(UUID.randomUUID());
    testCase.setTestCaseSetId(UUID.randomUUID());

    measure = new Measure();
    measure.setModel(ModelType.QI_CORE.getValue());
    measure.setCreatedBy("test.user5");
    measure.setId(ObjectId.get().toString());
    measure.setMeasureSetId("IDIDID");
    measure.setMeasureName("MSR01");
    measure.setVersion(new Version(0, 0, 1));
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(true).build());
    measure.setActive(true);
  }

  @Test
  public void testPersistTestCase() {
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(measure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));

    TestCase persistTestCase =
        testCaseService.persistTestCase(testCase, measure.getId(), "test.user", "TOKEN");
    verify(measureRepository, times(1))
        .addOrUpdateTestCase(eq(measure.getId()), testCaseCaptor.capture());
    TestCase capturedTestCase = testCaseCaptor.getValue();
    int lastModCompareTo =
        capturedTestCase.getLastModifiedAt().compareTo(Instant.now().minus(60, ChronoUnit.SECONDS));
    assertEquals("test.user", capturedTestCase.getLastModifiedBy());
    assertEquals("test.user", capturedTestCase.getCreatedBy());
    assertEquals(1, lastModCompareTo);
    assertEquals(capturedTestCase.getLastModifiedAt(), capturedTestCase.getCreatedAt());
    assertEquals(
        UUID.fromString(capturedTestCase.getPatientId().toString()).toString(),
        capturedTestCase.getPatientId().toString());
    assertFalse(capturedTestCase.isCreatedBeforeVersioning());
    assertNotNull(persistTestCase.getPatientId());

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(),
            targetClassArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            anyString());
    assertEquals(persistTestCase.getId(), targetIdArgumentCaptor.getValue());
    assertEquals(TestCase.class, targetClassArgumentCaptor.getValue());
    assertEquals(ActionType.CREATED, actionTypeArgumentCaptor.getValue());
  }

  @Test
  public void testPersistTestCaseWithExistingTestCases() {
    List<TestCase> existingTestCases = new ArrayList<>();
    TestCase existingTestCase = TestCase.builder().id("Test1ID").title("Test0").build();
    existingTestCases.add(existingTestCase);
    measure.setTestCases(existingTestCases);
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(measure);

    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(200).successful(true).build())
                        .validResource(true)
                        .build());

    TestCase persistTestCase =
        testCaseService.persistTestCase(testCase, measure.getId(), "test.user", "TOKEN");
    assertThat(persistTestCase, is(notNullValue()));
    assertThat(persistTestCase.getId(), is(notNullValue()));
    assertThat(persistTestCase.getTitle(), is(equalTo(testCase.getTitle())));
    verify(measureRepository, times(1))
        .addOrUpdateTestCase(eq(measure.getId()), testCaseCaptor.capture());
    TestCase capturedTestCase = testCaseCaptor.getValue();
    int lastModCompareTo =
        capturedTestCase.getLastModifiedAt().compareTo(Instant.now().minus(60, ChronoUnit.SECONDS));
    assertEquals("test.user", capturedTestCase.getLastModifiedBy());
    assertEquals("test.user", capturedTestCase.getCreatedBy());
    assertEquals(1, lastModCompareTo);
    assertEquals(capturedTestCase.getLastModifiedAt(), capturedTestCase.getCreatedAt());
  }

  @Test
  public void testEnrichNewTestCase() {
    TestCase testCase = new TestCase();
    final String username = "user01";
    TestCase output =
        testCaseService.enrichNewTestCase(
            testCase, username, "measureId", ModelType.QI_CORE.getValue());
    assertThat(output, is(not(equalTo(testCase))));
    assertThat(output.getId(), is(notNullValue()));
    assertThat(output.getCreatedAt(), is(notNullValue()));
    assertThat(output.getCreatedBy(), is(equalTo(username)));
    assertThat(output.getLastModifiedAt(), is(notNullValue()));
    assertThat(output.getLastModifiedAt(), is(equalTo(output.getCreatedAt())));
    assertThat(output.getLastModifiedBy(), is(equalTo(username)));
    assertThat(output.getResourceUri(), is(nullValue()));
    assertThat(output.getHapiOperationOutcome(), is(nullValue()));
    assertThat(output.isValidResource(), is(false));
    assertNotNull(output.getTestCaseSetId());
    assertNotNull(output.getCaseNumber());
  }

  @Test
  public void testEnrichNewTestCaseWithTestCaseSequence() {
    when(testCaseSequenceService.generateSequence(anyString())).thenReturn(1);
    TestCase testCase = new TestCase();
    final String username = "user01";
    TestCase output =
        testCaseService.enrichNewTestCase(
            testCase, username, "measureId", ModelType.QI_CORE.getValue());
    assertThat(output, is(not(equalTo(testCase))));
    assertThat(output.getId(), is(notNullValue()));
    assertThat(output.getCreatedAt(), is(notNullValue()));
    assertThat(output.getCreatedBy(), is(equalTo(username)));
    assertThat(output.getLastModifiedAt(), is(notNullValue()));
    assertThat(output.getLastModifiedAt(), is(equalTo(output.getCreatedAt())));
    assertThat(output.getLastModifiedBy(), is(equalTo(username)));
    assertThat(output.getResourceUri(), is(nullValue()));
    assertThat(output.getHapiOperationOutcome(), is(nullValue()));
    assertThat(output.isValidResource(), is(false));
    assertNotNull(output.getTestCaseSetId());
    assertThat(output.getCaseNumber(), is(equalTo(1)));
  }

  @Test
  public void testEnrichNewTestCaseWhenModelIsQdm() {
    when(testCaseSequenceService.generateSequence(anyString())).thenReturn(1);
    TestCase testCase = new TestCase();
    final String username = "user01";
    TestCase output =
        testCaseService.enrichNewTestCase(
            testCase, username, "measureId", ModelType.QDM_5_6.getValue());
    assertThat(output, is(not(equalTo(testCase))));
    assertThat(output.getId(), is(notNullValue()));
    assertThat(output.getCreatedAt(), is(notNullValue()));
    assertThat(output.getCreatedBy(), is(equalTo(username)));
    assertThat(output.getLastModifiedAt(), is(notNullValue()));
    assertThat(output.getLastModifiedAt(), is(equalTo(output.getCreatedAt())));
    assertThat(output.getLastModifiedBy(), is(equalTo(username)));
    assertThat(output.getResourceUri(), is(nullValue()));
    assertThat(output.getHapiOperationOutcome(), is(nullValue()));
    assertThat(output.isValidResource(), is(false));
    assertNull(output.getTestCaseSetId());
    assertThat(output.getCaseNumber(), is(equalTo(1)));
  }

  @Test
  void resetCaseNumberSequenceWhenLastTestCaseIsDeleted() {
    List<TestCase> testCases =
        List.of(TestCase.builder().caseNumber(1).id("TC2_ID").title("TC2").build());

    Measure existingMeasure =
        Measure.builder()
            .id("measure-id")
            .createdBy("test.user")
            .testCases(testCases)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(existingMeasure);

    doReturn(existingMeasure).when(measureRepository).removeTestCase(anyString(), anyString());

    String output = testCaseService.deleteTestCases("measure-id", List.of("TC2_ID"), "test.user");
    verify(testCaseSequenceService, times(1)).resetSequence("measure-id");
  }

  @Test
  public void testUpdateTestCaseValidResourcesWithReportMeasureNotFound() {
    final String measureId = "M1234";
    final String accessToken = "Bearer Token";
    // updateTestCaseValidResourcesWithReport still uses findById (no active filter)
    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());

    MeasureTestCaseValidationReport output =
        testCaseService.updateTestCaseValidResourcesWithReport(measureId, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.getMeasureId(), is(equalTo(measureId)));
    assertThat(output.getJobStatus(), is(equalTo(JobStatus.SKIPPED)));
    assertThat(output.getMeasureSetId(), is(nullValue()));
    assertThat(output.getMeasureVersionId(), is(nullValue()));
    assertThat(output.getMeasureName(), is(nullValue()));
  }

  @Test
  public void testUpdateTestCaseValidResourcesWithReportMeasureNullTestCases() {
    final String measureId = "M1234";
    final String accessToken = "Bearer Token";
    final String measureSetId = UUID.randomUUID().toString();
    final String versionId = UUID.randomUUID().toString();
    Measure measure =
        Measure.builder()
            .id(measureId)
            .measureName("Measure 1234")
            .measureSetId(measureSetId)
            .versionId(versionId)
            .testCases(null)
            .model(ModelType.QI_CORE.getValue())
            .build();
    // Method under test uses findById
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));

    MeasureTestCaseValidationReport output =
        testCaseService.updateTestCaseValidResourcesWithReport(measureId, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.getMeasureId(), is(equalTo(measureId)));
    assertThat(output.getJobStatus(), is(equalTo(JobStatus.COMPLETED)));
    assertThat(output.getMeasureSetId(), is(equalTo(measureSetId)));
    assertThat(output.getMeasureVersionId(), is(equalTo(versionId)));
    assertThat(output.getMeasureName(), is(equalTo("Measure 1234")));
    assertThat(output.getTestCaseValidationReports(), is(notNullValue()));
    assertThat(output.getTestCaseValidationReports().isEmpty(), is(true));
  }

  @Test
  public void testUpdateTestCaseValidResourcesWithReportMeasureEmptyTestCases() {
    final String measureId = "M1234";
    final String accessToken = "Bearer Token";
    final String measureSetId = UUID.randomUUID().toString();
    final String versionId = UUID.randomUUID().toString();
    Measure measure =
        Measure.builder()
            .id(measureId)
            .measureName("Measure 1234")
            .measureSetId(measureSetId)
            .versionId(versionId)
            .testCases(List.of())
            .model(ModelType.QI_CORE.getValue())
            .build();
    // Method under test uses findById
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));

    MeasureTestCaseValidationReport output =
        testCaseService.updateTestCaseValidResourcesWithReport(measureId, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.getMeasureId(), is(equalTo(measureId)));
    assertThat(output.getJobStatus(), is(equalTo(JobStatus.COMPLETED)));
    assertThat(output.getMeasureSetId(), is(equalTo(measureSetId)));
    assertThat(output.getMeasureVersionId(), is(equalTo(versionId)));
    assertThat(output.getMeasureName(), is(equalTo("Measure 1234")));
    assertThat(output.getTestCaseValidationReports(), is(notNullValue()));
    assertThat(output.getTestCaseValidationReports().isEmpty(), is(true));
  }

  @Test
  public void testUpdateTestCaseValidResourcesWithReportMeasureWithTestCases() {
    final String measureId = "M1234";
    final String accessToken = "Bearer Token";
    final String measureSetId = UUID.randomUUID().toString();
    final String versionId = UUID.randomUUID().toString();
    List<TestCase> prevTestCases =
        List.of(
            TestCase.builder()
                .id("TC1")
                .name("TC1")
                .validResource(true)
                .patientId(UUID.randomUUID())
                .json("{}")
                .build(),
            TestCase.builder()
                .id("TC2")
                .name("TC2")
                .validResource(true)
                .patientId(UUID.randomUUID())
                .json("{}")
                .build(),
            TestCase.builder()
                .id("TC3")
                .name("TC3")
                .validResource(false)
                .patientId(UUID.randomUUID())
                .json("{}")
                .build());

    Measure measure =
        Measure.builder()
            .id(measureId)
            .measureName("Measure 1234")
            .measureSetId(measureSetId)
            .versionId(versionId)
            .testCases(prevTestCases)
            .model(ModelType.QI_CORE.getValue())
            .build();
    // Method under test uses findById
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));

    // TestCaseService spy = Mockito.spy(testCaseService);
    List<TestCase> validatedTestCases =
        List.of(
            TestCase.builder()
                .id("TC1")
                .name("TC1")
                .validResource(false)
                .patientId(UUID.randomUUID())
                .json("{}")
                .build(),
            TestCase.builder()
                .id("TC2")
                .name("TC2")
                .validResource(true)
                .patientId(UUID.randomUUID())
                .json("{}")
                .build(),
            TestCase.builder()
                .id("TC3")
                .name("TC3")
                .validResource(true)
                .patientId(UUID.randomUUID())
                .json("{}")
                .build());
    doReturn(validatedTestCases)
        .when(testCaseService)
        .updateTestCaseValidResourcesForMeasure(any(Measure.class), anyString());

    MeasureTestCaseValidationReport output =
        testCaseService.updateTestCaseValidResourcesWithReport(measureId, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.getMeasureId(), is(equalTo(measureId)));
    assertThat(output.getJobStatus(), is(equalTo(JobStatus.COMPLETED)));
    assertThat(output.getMeasureSetId(), is(equalTo(measureSetId)));
    assertThat(output.getMeasureVersionId(), is(equalTo(versionId)));
    assertThat(output.getMeasureName(), is(equalTo("Measure 1234")));
    assertThat(output.getTestCaseValidationReports(), is(notNullValue()));
    assertThat(output.getTestCaseValidationReports().size(), is(equalTo(3)));
    assertThat(output.getTestCaseValidationReports().get(0), is(notNullValue()));
    assertThat(output.getTestCaseValidationReports().get(0).getTestCaseId(), is(equalTo("TC1")));
    assertThat(output.getTestCaseValidationReports().get(0).isPreviousValidResource(), is(true));
    assertThat(output.getTestCaseValidationReports().get(0).isCurrentValidResource(), is(false));
    assertThat(output.getTestCaseValidationReports().get(1), is(notNullValue()));
    assertThat(output.getTestCaseValidationReports().get(1).getTestCaseId(), is(equalTo("TC2")));
    assertThat(output.getTestCaseValidationReports().get(1).isPreviousValidResource(), is(true));
    assertThat(output.getTestCaseValidationReports().get(1).isCurrentValidResource(), is(true));
    assertThat(output.getTestCaseValidationReports().get(2), is(notNullValue()));
    assertThat(output.getTestCaseValidationReports().get(2).getTestCaseId(), is(equalTo("TC3")));
    assertThat(output.getTestCaseValidationReports().get(2).isPreviousValidResource(), is(false));
    assertThat(output.getTestCaseValidationReports().get(2).isCurrentValidResource(), is(true));
  }

  @Test
  public void testUpdateTestCaseValidResourcesForMeasureValidList() {
    TestCase testCase =
        TestCase.builder()
            .id("TestID")
            .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
            .build();
    Measure measure =
        Measure.builder().testCases(List.of(testCase)).model(ModelType.QI_CORE.getValue()).build();
    final String accessToken = "Bearer Token";

    TestCase validatedTestCase =
        testCase.toBuilder()
            .hapiOperationOutcome(HapiOperationOutcome.builder().build())
            .validResource(true)
            .build();
    doReturn(List.of(validatedTestCase))
        .when(testCaseValidationService)
        .validateTestCasesAsResources(any(Measure.class), anyString());

    List<TestCase> output =
        testCaseService.updateTestCaseValidResourcesForMeasure(measure, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.size(), is(equalTo(1)));
    assertThat(output.get(0), is(equalTo(validatedTestCase)));
  }

  @Test
  public void testValidateResourceAsynchronouslyForSTU6MeasuresWhenUpdatingTestCase() {
    measure.setModel(ModelType.QI_CORE_6_0_0.getValue());
    TestCase testCase =
        TestCase.builder()
            .id("TestID")
            .title("test-title")
            .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
            .build();
    final String accessToken = "Bearer Token";

    measure.toBuilder()
        .model(ModelType.QI_CORE_6_0_0.getValue())
        .testCases(List.of(testCase))
        .build();
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(measure);
    // Mocks a validation request awaiting execution.
    when(testCaseValidationService.validateResourceAsynchronously(
            any(), any(TestCase.class), eq(TestCaseServiceUtil.SAVE), anyString()))
        .thenAnswer(
            invocation ->
                invocation.getArgument(1, TestCase.class).toBuilder()
                    .validationStatus(TestCaseValidationStatus.PENDING.toString())
                    .build());

    InOrder saveValidationOrder = inOrder(measureRepository, testCaseValidationService);
    TestCase output =
        testCaseService.updateTestCase(
            testCase, measure.getId(), "test-user", accessToken, TestCaseServiceUtil.SAVE);
    verify(measureRepository, times(1))
        .addOrUpdateTestCase(targetIdArgumentCaptor.capture(), testCaseCaptor.capture());
    saveValidationOrder.verify(measureRepository).addOrUpdateTestCase(measure.getId(), testCase);
    saveValidationOrder
        .verify(testCaseValidationService)
        .validateResourceAsynchronously(
            measureArgumentCaptor.capture(),
            any(TestCase.class),
            eq(TestCaseServiceUtil.SAVE),
            eq(accessToken));
    assertNotNull(output);
    assertEquals(TestCaseValidationStatus.PENDING.toString(), output.getValidationStatus());
  }

  @Test
  public void testPersistTestCasesThrowsResourceNotFoundExceptionForUnknownId() {
    List<TestCase> newTestCases = List.of(TestCase.builder().title("Test1").build());
    String measureId = measure.getId();
    String username = "user01";
    String accessToken = "Bearer Token";
    when(measureService.findActiveMeasureById(measureId))
        .thenThrow(new ResourceNotFoundException("Measure", measureId));

    assertThrows(
        ResourceNotFoundException.class,
        () -> testCaseService.persistTestCases(newTestCases, measureId, username, accessToken));
  }

  @Test
  public void testPersistTestCasesThrowsNoExceptionForNonDraftMeasure() {
    measure.setModel(ModelType.QI_CORE_6_0_0.getValue());
    TestCase testCase =
        TestCase.builder()
            .id("TestID")
            .title("test-title")
            .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
            .build();
    final String accessToken = "Bearer Token";

    measure.toBuilder()
        .model(ModelType.QI_CORE_6_0_0.getValue())
        .testCases(List.of(testCase))
        .build();
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(measure);
    when(testCaseValidationService.validateResourceAsynchronously(
            any(Measure.class), any(TestCase.class), anyString(), anyString()))
        .thenAnswer(
            invocation ->
                invocation.getArgument(1, TestCase.class).toBuilder()
                    .validationStatus(TestCaseValidationStatus.PENDING.toString())
                    .build());

    TestCase output =
        testCaseService.updateTestCase(
            testCase, measure.getId(), "test-user", accessToken, TestCaseServiceUtil.SAVE);
    assertNotNull(output);
    assertEquals(TestCaseValidationStatus.PENDING.toString(), output.getValidationStatus());
    assertEquals("test-user", output.getCreatedBy());
    assertEquals("test-title", output.getTitle());
  }

  @Test
  public void testPersistTestCasesSucceedsForNonDraftMeasure() {
    List<TestCase> newTestCases = List.of(TestCase.builder().title("Test1").build());
    String measureId = measure.getId();
    String username = "user01";
    String accessToken = "Bearer Token";
    measure.getMeasureMetaData().setDraft(false);
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Mockito.doAnswer((args) -> args.getArgument(0))
        .when(measureRepository)
        .save(measureCaptor.capture());

    // Should not throw exception - editing versioned measures is now always allowed
    testCaseService.persistTestCases(newTestCases, measureId, username, accessToken);

    // Verify the measure was saved
    verify(measureRepository).save(any(Measure.class));
  }

  @Test
  public void testPersistTestCasesHandlesNullList() {
    List<TestCase> newTestCases = null;
    String measureId = measure.getId();
    String username = "user01";
    String accessToken = "Bearer Token";

    List<TestCase> output =
        testCaseService.persistTestCases(newTestCases, measureId, username, accessToken);
    assertThat(output, is(nullValue()));
  }

  @Test
  public void testPersistTestCasesHandlesEmptyList() {
    List<TestCase> newTestCases = List.of();
    String measureId = measure.getId();
    String username = "user01";
    String accessToken = "Bearer Token";

    List<TestCase> output =
        testCaseService.persistTestCases(newTestCases, measureId, username, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  public void testPersistTestCasesHandlesListToMeasureNoExistingTestCases() {
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(200).successful(true).build())
                        .validResource(true)
                        .build())
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(400).successful(false).build())
                        .validResource(false)
                        .build());
    List<TestCase> newTestCases =
        List.of(
            TestCase.builder().title("Test1").json("test-json").build(),
            TestCase.builder().title("Test2").json("test-json").build());
    String measureId = measure.getId();
    String username = "user01";
    String accessToken = "Bearer Token";
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    List<TestCase> output =
        testCaseService.persistTestCases(newTestCases, measureId, username, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.size(), is(equalTo(2)));
    assertThat(output.get(0).getId(), is(notNullValue()));
    assertThat(output.get(0).getCreatedAt(), is(notNullValue()));
    assertThat(output.get(0).getCreatedBy(), is(equalTo("user01")));
    assertThat(output.get(0).getLastModifiedAt(), is(notNullValue()));
    assertThat(output.get(0).getLastModifiedBy(), is(equalTo("user01")));
    assertThat(output.get(0).getResourceUri(), is(nullValue()));
    assertTrue(output.get(0).getHapiOperationOutcome().isSuccessful());
    assertTrue(output.get(0).isValidResource());
    assertThat(output.get(1).getId(), is(notNullValue()));
    assertThat(output.get(1).getCreatedAt(), is(notNullValue()));
    assertThat(output.get(1).getCreatedBy(), is(equalTo("user01")));
    assertThat(output.get(1).getLastModifiedAt(), is(notNullValue()));
    assertThat(output.get(1).getLastModifiedBy(), is(equalTo("user01")));
    assertThat(output.get(1).getResourceUri(), is(nullValue()));
    assertNotNull(output.get(1).getHapiOperationOutcome());
    assertThat(output.get(1).isValidResource(), is(false));
  }

  @Test
  public void testPersistTestCasesHandlesListToMeasureWithExistingTestCases() {
    List<TestCase> existingTestCases = new ArrayList<>();
    existingTestCases.add(TestCase.builder().id("Test1ID").title("Test0").build());
    measure.setTestCases(existingTestCases);
    measure.setModel(ModelType.QDM_5_6.getValue());
    List<TestCase> newTestCases =
        List.of(
            TestCase.builder().title("Test1").series("series1").json("test-json").build(),
            TestCase.builder().title("Test2").series("series2").json("test-json").build());
    String measureId = measure.getId();
    String username = "user01";
    String accessToken = "Bearer Token";
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));

    List<TestCase> output =
        testCaseService.persistTestCases(newTestCases, measureId, username, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.size(), is(equalTo(2)));
    assertThat(output.get(0).getId(), is(notNullValue()));
    assertThat(output.get(0).getCreatedAt(), is(notNullValue()));
    assertThat(output.get(0).getCreatedBy(), is(equalTo("user01")));
    assertThat(output.get(0).getLastModifiedAt(), is(notNullValue()));
    assertThat(output.get(0).getLastModifiedBy(), is(equalTo("user01")));
    assertThat(output.get(0).getResourceUri(), is(nullValue()));
    assertThat(output.get(0).getHapiOperationOutcome(), is(nullValue()));
    assertThat(output.get(0).isValidResource(), is(false));
    assertThat(output.get(1).getId(), is(notNullValue()));
    assertThat(output.get(1).getCreatedAt(), is(notNullValue()));
    assertThat(output.get(1).getCreatedBy(), is(equalTo("user01")));
    assertThat(output.get(1).getLastModifiedAt(), is(notNullValue()));
    assertThat(output.get(1).getLastModifiedBy(), is(equalTo("user01")));
    assertThat(output.get(1).getResourceUri(), is(nullValue()));
    assertThat(output.get(1).getHapiOperationOutcome(), is(nullValue()));
    assertThat(output.get(1).isValidResource(), is(false));

    verifyNoInteractions(fhirServicesClient);
  }

  @Test
  public void testPersistTestCasesHandlesListToMeasureWithJson() {

    List<TestCase> newTestCases =
        List.of(
            TestCase.builder()
                .title("Test1")
                .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
                .build(),
            TestCase.builder()
                .title("Test2")
                .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
                .build());
    String measureId = measure.getId();
    String username = "user01";
    String accessToken = "Bearer Token";

    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());

    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(200).successful(true).build())
                        .validResource(true)
                        .build())
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(400).successful(false).build())
                        .validResource(false)
                        .build());

    List<TestCase> output =
        testCaseService.persistTestCases(newTestCases, measureId, username, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.size(), is(equalTo(2)));
    assertThat(output.get(0).getId(), is(notNullValue()));
    assertThat(output.get(0).getCreatedAt(), is(notNullValue()));
    assertThat(output.get(0).getCreatedBy(), is(equalTo("user01")));
    assertThat(output.get(0).getLastModifiedAt(), is(notNullValue()));
    assertThat(output.get(0).getLastModifiedBy(), is(equalTo("user01")));
    assertThat(output.get(0).getResourceUri(), is(nullValue()));
    assertThat(output.get(0).getHapiOperationOutcome(), is(notNullValue()));
    assertThat(output.get(0).getHapiOperationOutcome().getCode(), is(equalTo(200)));
    assertThat(output.get(0).isCreatedBeforeVersioning(), is(false));
    assertThat(output.get(0).isValidResource(), is(true));
    assertThat(output.get(1).getId(), is(notNullValue()));
    assertThat(output.get(1).getCreatedAt(), is(notNullValue()));
    assertThat(output.get(1).getCreatedBy(), is(equalTo("user01")));
    assertThat(output.get(1).getLastModifiedAt(), is(notNullValue()));
    assertThat(output.get(1).getLastModifiedBy(), is(equalTo("user01")));
    assertThat(output.get(1).getResourceUri(), is(nullValue()));
    assertThat(output.get(1).getHapiOperationOutcome(), is(notNullValue()));
    assertThat(output.get(1).getHapiOperationOutcome().getCode(), is(equalTo(400)));
    assertThat(output.get(1).isValidResource(), is(false));
    assertThat(output.get(1).isCreatedBeforeVersioning(), is(false));

    verify(testCaseValidationService, times(2))
        .validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean());
  }

  @Test
  public void testPersistTestCaseSucceedsForNonDraftMeasure() {
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(measure);

    // Should not throw exception - editing versioned measures is now always allowed
    testCaseService.persistTestCase(testCase, measure.getId(), "test.user", "TOKEN");

    // Verify the test case was pushed to the database
    verify(measureRepository).addOrUpdateTestCase(eq(measure.getId()), any(TestCase.class));
  }

  @Test
  public void testFindTestCasesByMeasureId() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    List<TestCase> persistTestCase =
        testCaseService.findTestCasesByMeasureId(measure.getId(), "test.user");
    assertEquals(1, persistTestCase.size());
    assertEquals(testCase.getId(), persistTestCase.get(0).getId());
  }

  @Test
  public void testFindTestCasesByMeasureIdWhenMeasureDoesNotExist() {
    when(measureService.findActiveMeasureById(measure.getId()))
        .thenThrow(new ResourceNotFoundException("Measure", measure.getId()));
    assertThrows(
        ResourceNotFoundException.class,
        () -> testCaseService.findTestCasesByMeasureId(measure.getId(), "test.user"));
  }

  @Test
  public void testFindTestCasesByMeasureIdSetsNullLockWhenNoLockExists() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    when(testCaseLockService.findByTestCaseId(testCase.getId())).thenReturn(null);

    List<TestCase> result = testCaseService.findTestCasesByMeasureId(measure.getId(), "test.user");

    assertEquals(1, result.size());
    assertNull(result.get(0).getTestCaseLock());
  }

  @Test
  public void testFindTestCasesByMeasureIdSetsNullLockWhenLockedByCurrentUser() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    TestCaseLock lock =
        TestCaseLock.builder()
            .measureId(measure.getId())
            .testCaseId(testCase.getId())
            .lockedBy("test.user")
            .build();
    when(testCaseLockService.findByTestCaseId(testCase.getId())).thenReturn(lock);

    List<TestCase> result = testCaseService.findTestCasesByMeasureId(measure.getId(), "test.user");

    assertEquals(1, result.size());
    assertNull(result.get(0).getTestCaseLock());
  }

  @Test
  public void testFindTestCasesByMeasureIdSetsLockInfoWhenLockedByAnotherUser() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    TestCaseLock lock =
        TestCaseLock.builder()
            .measureId(measure.getId())
            .testCaseId(testCase.getId())
            .lockedBy("another.user")
            .build();
    when(testCaseLockService.findByTestCaseId(testCase.getId())).thenReturn(lock);

    List<TestCase> result = testCaseService.findTestCasesByMeasureId(measure.getId(), "test.user");

    assertEquals(1, result.size());
    TestCaseLockInfo lockInfo = result.get(0).getTestCaseLock();
    assertNotNull(lockInfo);
    assertEquals(measure.getId(), lockInfo.getMeasureId());
    assertEquals(testCase.getId(), lockInfo.getTestCaseId());
    assertEquals("another.user", lockInfo.getLockedBy());
  }

  @Test
  public void testFindTestCaseSeriesByMeasureIdThrowsExceptionWhenMeasureDoesNotExist() {
    Optional<Measure> optional = Optional.empty();
    when(measureRepository.findAllTestCaseSeriesByMeasureId(anyString())).thenReturn(optional);
    assertThrows(
        ResourceNotFoundException.class,
        () -> testCaseService.findTestCaseSeriesByMeasureId(measure.getId()));
  }

  @Test
  public void testFindTestCaseSeriesByMeasureIdReturnsEmptyListWhenTestCasesNull() {
    Measure noTestCases = measure.toBuilder().build();
    measure.setTestCases(null);
    Optional<Measure> optional = Optional.of(noTestCases);
    when(measureRepository.findAllTestCaseSeriesByMeasureId(anyString())).thenReturn(optional);
    List<String> output = testCaseService.findTestCaseSeriesByMeasureId(measure.getId());
    assertEquals(List.of(), output);
  }

  @Test
  public void testFindTestCaseSeriesByMeasureIdReturnsEmptyListWhenTestCasesEmpty() {
    Measure noTestCases = measure.toBuilder().build();
    measure.setTestCases(new ArrayList<>());
    Optional<Measure> optional = Optional.of(noTestCases);
    when(measureRepository.findAllTestCaseSeriesByMeasureId(anyString())).thenReturn(optional);
    List<String> output = testCaseService.findTestCaseSeriesByMeasureId(measure.getId());
    assertEquals(List.of(), output);
  }

  @Test
  public void testFindTestCaseSeriesByMeasureIdReturnsDistinctList() {
    Measure withTestCases = measure.toBuilder().build();
    withTestCases.setTestCases(
        List.of(
            TestCase.builder().id(ObjectId.get().toString()).series("SeriesAAA").build(),
            TestCase.builder().id(ObjectId.get().toString()).series("SeriesAAA").build(),
            TestCase.builder().id(ObjectId.get().toString()).series("SeriesBBB").build()));
    Optional<Measure> optional = Optional.of(withTestCases);
    when(measureRepository.findAllTestCaseSeriesByMeasureId(anyString())).thenReturn(optional);
    List<String> output = testCaseService.findTestCaseSeriesByMeasureId(measure.getId());
    assertEquals(List.of("SeriesAAA", "SeriesBBB"), output);
  }

  @Test
  public void testFindTestCaseSeriesByMeasureIdReturnsListWithoutNullsAndEmptyStrings() {
    Measure withTestCases = measure.toBuilder().build();
    withTestCases.setTestCases(
        List.of(
            TestCase.builder().id(ObjectId.get().toString()).series("SeriesAAA").build(),
            TestCase.builder().id(ObjectId.get().toString()).series("").build(),
            TestCase.builder().id(ObjectId.get().toString()).series(null).build(),
            TestCase.builder().id(ObjectId.get().toString()).series("SeriesBBB").build()));
    Optional<Measure> optional = Optional.of(withTestCases);
    when(measureRepository.findAllTestCaseSeriesByMeasureId(anyString())).thenReturn(optional);
    List<String> output = testCaseService.findTestCaseSeriesByMeasureId(measure.getId());
    assertEquals(List.of("SeriesAAA", "SeriesBBB"), output);
  }

  @Test
  public void testUpdateTestCaseUpdatesLastModifiedFields() {
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Instant createdAt = Instant.now().minus(300, ChronoUnit.SECONDS);
    TestCase originalTestCase =
        testCase.toBuilder()
            .createdAt(createdAt)
            .createdBy("test.user5")
            .lastModifiedAt(createdAt)
            .lastModifiedBy("test.user5")
            .build();
    List<TestCase> testCases = new ArrayList<>();
    testCases.add(originalTestCase);
    Measure originalMeasure = measure.toBuilder().testCases(testCases).build();
    when(measureService.findMeasureById(anyString())).thenReturn(originalMeasure);

    TestCase updatingTestCase =
        testCase.toBuilder().title("UpdatedTitle").series("UpdatedSeries").build();
    Mockito.doAnswer((args) -> args.getArgument(0))
        .when(measureRepository)
        .save(any(Measure.class));
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));

    TestCase updatedTestCase =
        testCaseService.updateTestCase(
            updatingTestCase, measure.getId(), "test.user5", "TOKEN", TestCaseServiceUtil.SAVE);
    assertNotNull(updatedTestCase);

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    assertEquals(updatingTestCase.getId(), updatedTestCase.getId());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    assertNotNull(savedMeasure.getTestCases());
    assertEquals(1, savedMeasure.getTestCases().size());
    assertEquals(updatedTestCase, savedMeasure.getTestCases().get(0));

    int lastModCompareTo =
        updatedTestCase.getLastModifiedAt().compareTo(Instant.now().minus(60, ChronoUnit.SECONDS));
    assertEquals("test.user5", updatedTestCase.getLastModifiedBy());
    assertEquals(originalTestCase.getCreatedBy(), updatedTestCase.getCreatedBy());
    assertEquals(1, lastModCompareTo);
    assertNotEquals(updatedTestCase.getLastModifiedAt(), updatedTestCase.getCreatedAt());
    assertEquals("test.user5", updatedTestCase.getCreatedBy());
  }

  @Test
  public void testUpdateTestCaseWithEnforcedPatientIdSuccess() {
    String patientId = "3d2abb9d-c10a-4ab3-ae1a-1684ab61c07e";
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Instant createdAt = Instant.now().minus(300, ChronoUnit.SECONDS);
    String json =
        "{\"resourceType\": \"Bundle\", \"type\": \"collection\", \n"
            + "  \"entry\" : [ {\n"
            + "    \"fullUrl\" : \"http://local/Patient/1\",\n"
            + "    \"resource\" : {\n"
            + "      \"id\" : \"testUniqueId\",\n"
            + "      \"resourceType\" : \"Patient\"    \n"
            + "    }\n"
            + "  } ]             }";
    TestCase originalTestCase =
        testCase.toBuilder()
            .createdAt(createdAt)
            .createdBy("test.user5")
            .lastModifiedAt(createdAt)
            .lastModifiedBy("test.user5")
            .json(json)
            .patientId(UUID.fromString(patientId))
            .build();
    List<TestCase> testCases = new ArrayList<>();
    testCases.add(originalTestCase);
    Measure originalMeasure =
        measure.toBuilder()
            .model(ModelType.QI_CORE.getValue())
            .cqlLibraryName("Test1CQLLibraryName")
            .testCases(testCases)
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(originalMeasure);

    TestCase updatingTestCase =
        testCase.toBuilder().title("UpdatedTitle").series("UpdatedSeries").json(json).build();
    Mockito.doAnswer((args) -> args.getArgument(0))
        .when(measureRepository)
        .save(any(Measure.class));
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));

    TestCase updatedTestCase =
        testCaseService.updateTestCase(
            updatingTestCase, measure.getId(), "test.user5", "TOKEN", TestCaseServiceUtil.SAVE);
    assertNotNull(updatedTestCase);

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    assertEquals(updatingTestCase.getId(), updatedTestCase.getId());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    assertNotNull(savedMeasure.getTestCases());
    assertEquals(1, savedMeasure.getTestCases().size());

    assertTrue(savedMeasure.getTestCases().get(0).getJson().contains(patientId));

    int lastModCompareTo =
        updatedTestCase.getLastModifiedAt().compareTo(Instant.now().minus(60, ChronoUnit.SECONDS));
    assertEquals("test.user5", updatedTestCase.getLastModifiedBy());
    assertEquals(originalTestCase.getCreatedBy(), updatedTestCase.getCreatedBy());
    assertEquals(1, lastModCompareTo);
    assertNotEquals(updatedTestCase.getLastModifiedAt(), updatedTestCase.getCreatedAt());
    assertEquals("test.user5", updatedTestCase.getCreatedBy());
  }

  @Test
  public void testUpdateTestCaseEnforcingPatientIdFail() {
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Instant createdAt = Instant.now().minus(300, ChronoUnit.SECONDS);
    String json = "invalid test case json";
    TestCase originalTestCase =
        testCase.toBuilder()
            .createdAt(createdAt)
            .createdBy("test.user5")
            .lastModifiedAt(createdAt)
            .lastModifiedBy("test.user5")
            .json(json)
            .build();
    List<TestCase> testCases = new ArrayList<>();
    testCases.add(originalTestCase);
    Measure originalMeasure =
        measure.toBuilder()
            .model(ModelType.QI_CORE.getValue())
            .cqlLibraryName("Test1CQLLibraryName")
            .testCases(testCases)
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(originalMeasure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));

    TestCase updatingTestCase =
        testCase.toBuilder().title("UpdatedTitle").series("UpdatedSeries").json(json).build();
    Mockito.doAnswer((args) -> args.getArgument(0))
        .when(measureRepository)
        .save(any(Measure.class));
    TestCase updatedTestCase =
        testCaseService.updateTestCase(
            updatingTestCase, measure.getId(), "test.user5", "TOKEN", TestCaseServiceUtil.SAVE);

    assertNotNull(updatedTestCase);

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    assertEquals(updatingTestCase.getId(), updatedTestCase.getId());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    assertNotNull(savedMeasure.getTestCases());
    assertEquals(1, savedMeasure.getTestCases().size());

    assertFalse(
        savedMeasure
            .getTestCases()
            .get(0)
            .getJson()
            .contains("Updatedtitle-Updatedseries-Test1CQLLibraryName-0.0.1"));

    int lastModCompareTo =
        updatedTestCase.getLastModifiedAt().compareTo(Instant.now().minus(60, ChronoUnit.SECONDS));
    assertEquals("test.user5", updatedTestCase.getLastModifiedBy());
    assertEquals(originalTestCase.getCreatedBy(), updatedTestCase.getCreatedBy());
    assertEquals(1, lastModCompareTo);
    assertNotEquals(updatedTestCase.getLastModifiedAt(), updatedTestCase.getCreatedAt());
    assertEquals("test.user5", updatedTestCase.getCreatedBy());
  }

  @Test
  public void testUpdateTestCaseWhenMeasureIsNull() {
    when(measureService.findMeasureById(anyString())).thenReturn(null);
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            testCaseService.updateTestCase(
                testCase, measure.getId(), "test.user", "TOKEN", TestCaseServiceUtil.SAVE));
  }

  @Test
  public void testUpdateTestCasePreventsModificationOfCreatedByFields() {
    Instant createdAt = Instant.now().minus(300, ChronoUnit.SECONDS);
    TestCase originalTestCase =
        testCase.toBuilder()
            .createdAt(createdAt)
            .createdBy("test.user5")
            .lastModifiedAt(createdAt)
            .lastModifiedBy("test.user5")
            .build();
    List<TestCase> testCases = new ArrayList<>();
    testCases.add(originalTestCase);
    Measure originalMeasure =
        measure.toBuilder().model(ModelType.QDM_5_6.getValue()).testCases(testCases).build();
    when(measureService.findMeasureById(anyString())).thenReturn(originalMeasure);

    TestCase updatingTestCase =
        testCase.toBuilder()
            .createdBy("Nobody")
            .createdAt(Instant.now())
            .title("UpdatedTitle")
            .series("UpdatedSeries")
            .build();
    Mockito.doAnswer((args) -> args.getArgument(0))
        .when(measureRepository)
        .save(any(Measure.class));

    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));

    TestCase updatedTestCase =
        testCaseService.updateTestCase(updatingTestCase, measure.getId(), "test.user5", "TOKEN");
    assertNotNull(updatedTestCase);

    int lastModCompareTo =
        updatedTestCase.getLastModifiedAt().compareTo(Instant.now().minus(60, ChronoUnit.SECONDS));
    assertEquals("test.user5", updatedTestCase.getLastModifiedBy());
    assertEquals(1, lastModCompareTo);
    assertNotEquals(updatedTestCase.getLastModifiedAt(), updatedTestCase.getCreatedAt());
    assertEquals(originalTestCase.getCreatedAt(), updatedTestCase.getCreatedAt());
    assertEquals(originalTestCase.getCreatedBy(), updatedTestCase.getCreatedBy());
  }

  @Test
  public void testUpdateTestCaseSucceedsForNonDraftMeasure() {
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Mockito.doAnswer((args) -> args.getArgument(0))
        .when(measureRepository)
        .save(measureCaptor.capture());

    // Should not throw exception - editing versioned measures is now always allowed
    testCaseService.updateTestCase(
        testCase, measure.getId(), "test.user", "TOKEN", TestCaseServiceUtil.SAVE);

    // Verify the measure was saved
    verify(measureRepository).save(any(Measure.class));
  }

  @Test
  public void testThatUpdateTestCaseHandlesUpsertForNullTestCasesList() {
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Measure originalMeasure = measure.toBuilder().testCases(null).build();
    when(measureService.findMeasureById(anyString())).thenReturn(originalMeasure);
    Mockito.doAnswer((args) -> args.getArgument(0))
        .when(measureRepository)
        .save(any(Measure.class));
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));

    TestCase upsertingTestCase =
        testCase.toBuilder()
            .createdBy("Nobody")
            .createdAt(Instant.now())
            .title("UpdatedTitle")
            .series("UpdatedSeries")
            .build();

    TestCase updatedTestCase =
        testCaseService.updateTestCase(
            upsertingTestCase, measure.getId(), "test.user5", "TOKEN", TestCaseServiceUtil.SAVE);
    assertNotNull(updatedTestCase);

    int lastModCompareTo =
        updatedTestCase.getLastModifiedAt().compareTo(Instant.now().minus(60, ChronoUnit.SECONDS));
    assertEquals(1, lastModCompareTo);
    assertNotNull(updatedTestCase.getId());
    assertEquals(updatedTestCase.getLastModifiedAt(), updatedTestCase.getCreatedAt());
    assertEquals("test.user5", updatedTestCase.getCreatedBy());
    assertEquals("test.user5", updatedTestCase.getLastModifiedBy());

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    Measure savedMeasure = measureCaptor.getValue();
    assertNotNull(savedMeasure.getTestCases());
    assertEquals(1, savedMeasure.getTestCases().size());
    assertEquals(upsertingTestCase, savedMeasure.getTestCases().get(0));
  }

  @Test
  public void testThatUpdateTestCaseHandlesUpsertForEmptyTestCasesList() {
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Measure originalMeasure = measure.toBuilder().testCases(new ArrayList<>()).build();

    Mockito.doAnswer((args) -> args.getArgument(0))
        .when(measureRepository)
        .save(any(Measure.class));
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));

    TestCase upsertingTestCase =
        testCase.toBuilder()
            .createdBy("Nobody")
            .createdAt(Instant.now())
            .title("UpdatedTitle")
            .series("UpdatedSeries")
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(originalMeasure);

    TestCase updatedTestCase =
        testCaseService.updateTestCase(
            upsertingTestCase, measure.getId(), "test.user5", "TOKEN", TestCaseServiceUtil.SAVE);
    assertNotNull(updatedTestCase);

    int lastModCompareTo =
        updatedTestCase.getLastModifiedAt().compareTo(Instant.now().minus(60, ChronoUnit.SECONDS));
    assertEquals(1, lastModCompareTo);
    assertNotNull(updatedTestCase.getId());
    assertEquals(updatedTestCase.getLastModifiedAt(), updatedTestCase.getCreatedAt());
    assertEquals("test.user5", updatedTestCase.getCreatedBy());
    assertEquals("test.user5", updatedTestCase.getLastModifiedBy());

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    Measure savedMeasure = measureCaptor.getValue();
    assertNotNull(savedMeasure.getTestCases());
    assertEquals(1, savedMeasure.getTestCases().size());
    assertEquals(upsertingTestCase, savedMeasure.getTestCases().get(0));
  }

  @Test
  public void testThatUpdateTestCaseHandlesUpsertWithOtherExistingTestCases() {
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    TestCase otherExistingTC =
        TestCase.builder().id("TC1_ID").title("TC1").series("Series1").build();
    Measure originalMeasure =
        measure.toBuilder()
            .testCases(new ArrayList<>(Collections.singletonList(otherExistingTC)))
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(originalMeasure);
    Mockito.doAnswer((args) -> args.getArgument(0))
        .when(measureRepository)
        .save(any(Measure.class));
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));

    TestCase upsertingTestCase =
        testCase.toBuilder()
            .createdBy("Nobody")
            .createdAt(Instant.now())
            .title("UpdatedTitle")
            .series("UpdatedSeries")
            .patientId(null)
            .build();

    TestCase updatedTestCase =
        testCaseService.updateTestCase(
            upsertingTestCase, measure.getId(), "test.user5", "TOKEN", TestCaseServiceUtil.SAVE);
    assertNotNull(updatedTestCase);

    int lastModCompareTo =
        updatedTestCase.getLastModifiedAt().compareTo(Instant.now().minus(60, ChronoUnit.SECONDS));
    assertEquals(1, lastModCompareTo);
    assertNotNull(updatedTestCase.getId());
    assertEquals(updatedTestCase.getLastModifiedAt(), updatedTestCase.getCreatedAt());
    assertEquals("test.user5", updatedTestCase.getCreatedBy());
    assertEquals("test.user5", updatedTestCase.getLastModifiedBy());

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    Measure savedMeasure = measureCaptor.getValue();
    assertNotNull(savedMeasure.getTestCases());
    assertEquals(2, savedMeasure.getTestCases().size());
    assertEquals(otherExistingTC, savedMeasure.getTestCases().get(0));
    assertEquals(upsertingTestCase, savedMeasure.getTestCases().get(1));
  }

  @Test
  public void testUpdateTestCaseThrowsResourceNotFoundExceptionForUnknownMeasureId() {
    measure.setModel(ModelType.QI_CORE_6_0_0.getValue());
    TestCase testCase =
        TestCase.builder()
            .id("TestID")
            .title("test-title")
            .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
            .build();

    measure.toBuilder()
        .model(ModelType.QI_CORE_6_0_0.getValue())
        .testCases(List.of(testCase))
        .build();
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));
    doReturn(null).when(measureRepository).addOrUpdateTestCase(anyString(), any(TestCase.class));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            testCaseService.updateTestCase(
                testCase, measure.getId(), "test-user", "TOKEN", TestCaseServiceUtil.IMPORT));
  }

  @Test
  public void testGetTestCaseReturnsTestCaseById() {
    Measure mockMeasure =
        measure.toBuilder().testCases(Collections.singletonList(testCase)).build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(mockMeasure);
    TestCase output =
        testCaseService.getTestCase(measure.getId(), testCase.getId(), false, "TOKEN", "test-user");
    assertEquals(testCase, output);
  }

  @Test
  public void testGetTestCaseReturnsTestCaseByIdValidatesByUpsert() {
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenReturn(
            testCase.toBuilder()
                .hapiOperationOutcome(
                    HapiOperationOutcome.builder().code(200).successful(true).build())
                .validResource(true)
                .build());

    Measure mockMeasure =
        measure.toBuilder().testCases(Collections.singletonList(testCase)).build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(mockMeasure);
    TestCase output =
        testCaseService.getTestCase(measure.getId(), testCase.getId(), true, "TOKEN", "test-user");
    assertNotNull(output.getHapiOperationOutcome());
    assertEquals(200, output.getHapiOperationOutcome().getCode());
  }

  @Test
  public void testGetTestCaseThrowsNotFoundExceptionForMeasureWithEmptyListTestCases() {
    when(measureService.findActiveMeasureById(anyString()))
        .thenReturn(measure.toBuilder().testCases(List.of()).build());
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            testCaseService.getTestCase(
                measure.getId(), testCase.getId(), false, "TOKEN", "test-user"));
  }

  @Test
  public void testGetTestCaseThrowsNotFoundExceptionForMeasureWithNullTestCases() {
    doReturn(measure.toBuilder().testCases(null).build())
        .when(measureService)
        .findActiveMeasureById(any(String.class));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            testCaseService.getTestCase(
                measure.getId(), testCase.getId(), false, "TOKEN", "test-user"));
  }

  @Test
  public void testGetTestCaseThrowsNotFoundExceptionForMeasureWithOtherTestCases() {
    List<TestCase> testCases =
        List.of(
            TestCase.builder().id("TC1_ID").title("TC1").build(),
            TestCase.builder().id("TC2_ID").title("TC2").build());
    doReturn(measure.toBuilder().testCases(testCases).build())
        .when(measureService)
        .findActiveMeasureById(any(String.class));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            testCaseService.getTestCase(
                measure.getId(), testCase.getId(), false, "TOKEN", "test-user"));
  }

  @Test
  void testDeleteTestCase() {
    List<TestCase> testCases =
        List.of(
            TestCase.builder().id("TC1_ID").title("TC1").build(),
            TestCase.builder().id("TC2_ID").title("TC2").build());

    Measure existingMeasure =
        Measure.builder()
            .id("measure-id")
            .createdBy("test.user")
            .testCases(testCases)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();

    when(measureService.findActiveMeasureById(anyString())).thenReturn(existingMeasure);

    doReturn(existingMeasure).when(measureRepository).removeTestCase(anyString(), anyString());

    String output = testCaseService.deleteTestCases("measure-id", List.of("TC2_ID"), "test.user");
    assertThat(output, is(equalTo("Successfully deleted test cases: TC2_ID")));
  }

  @Test
  void testDeleteTestCaseCreatedAfterVersioning() {
    List<TestCase> testCases =
        List.of(
            TestCase.builder().id("TC1_ID").title("TC1").build(),
            TestCase.builder().id("TC2_ID").title("TC2").build());

    Measure existingMeasure =
        Measure.builder()
            .id("measure-id")
            .createdBy("test.user")
            .testCases(testCases)
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(existingMeasure);

    doReturn(existingMeasure).when(measureRepository).removeTestCase(anyString(), anyString());

    String output = testCaseService.deleteTestCases("measure-id", List.of("TC2_ID"), "test.user");
    assertThat(output, is(equalTo("Successfully deleted test cases: TC2_ID")));
  }

  @Test
  void testThrowErrorWhenDeletingTestCaseWhichIsCreatedBeforeVersioning() {
    List<TestCase> testCases =
        List.of(
            TestCase.builder().id("TC1_ID").title("TC1").createdBeforeVersioning(true).build(),
            TestCase.builder().id("TC2_ID").title("TC2").build());

    Measure existingMeasure =
        Measure.builder()
            .id("measure-id")
            .createdBy("test.user")
            .testCases(testCases)
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(existingMeasure);
    assertThrows(
        InvalidIdException.class,
        () -> testCaseService.deleteTestCases("measure-id", List.of("TC1_ID"), "test.user"));
  }

  @Test
  void testDeleteTestCaseReturnsExceptionForNullMeasureId() {
    assertThrows(
        InvalidIdException.class,
        () -> testCaseService.deleteTestCases("", List.of("testCaseId"), "OtherUser"));
  }

  @Test
  void testDeleteTestCaseReturnsExceptionForResourceNotFound() {
    doThrow(new ResourceNotFoundException("Measure", measure.getId()))
        .when(measureService)
        .findActiveMeasureById(anyString());
    assertThrows(
        ResourceNotFoundException.class,
        () -> testCaseService.deleteTestCases("testid", List.of("testCaseId"), "user2"));
  }

  @Test
  void testDeleteTestCaseReturnsExceptionForNullTestCaseId() {
    assertThrows(
        InvalidIdException.class,
        () -> testCaseService.deleteTestCases("measure-id", Collections.emptyList(), "OtherUser"));
  }

  @Test
  void testDeleteTestCaseReturnsExceptionThrowsAccessException() {
    final Measure measure =
        Measure.builder()
            .id("measure-id")
            .createdBy("OtherUser")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    doThrow(new UnauthorizedException("Measure", "measure-id", "user2"))
        .when(measureService)
        .verifyAuthorization(anyString(), any(Measure.class));
    assertThrows(
        UnauthorizedException.class,
        () -> testCaseService.deleteTestCases("measure-id", List.of("testCaseId"), "user2"));
  }

  @Test
  void testDeleteTestCaseReturnsExceptionForTestCaseNotFoundInMeasure() {
    doThrow(new ResourceNotFoundException("Measure", measure.getId()))
        .when(measureService)
        .findActiveMeasureById(anyString());
    assertThrows(
        ResourceNotFoundException.class,
        () -> testCaseService.deleteTestCases("measure-id", List.of("testCaseId"), "test.user"));
  }

  @Test
  void testDeleteTestCasReturnsExceptionForNullTestCasesinMeasure() {
    Measure existingMeasure =
        Measure.builder()
            .id("measure-id")
            .createdBy("test.user")
            .testCases(null)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(existingMeasure);

    assertThrows(
        InvalidIdException.class,
        () -> testCaseService.deleteTestCases("measure-id", List.of("testCaseId"), "test.user"));
  }

  @Test
  void testDeleteTestCasesThrowsInvalidIdExceptionIfMeasureIdIsNull() {
    measure.setId(null);
    assertThrows(
        InvalidIdException.class,
        () ->
            testCaseService.deleteTestCases(
                measure.getId(), List.of("TC1_ID", "TC2_ID"), "test.user"));
  }

  @Test
  void testDeleteTestCasesThrowsInvalidIdExceptionIfTestCaseIdsIsAnEmptyList() {
    assertThrows(
        InvalidIdException.class,
        () -> testCaseService.deleteTestCases(measure.getId(), List.of(), "test.user"));
  }

  @Test
  void testDeleteTestCasesShouldThrowResourceNotFoundExceptionWhenMeasureIsNotFound() {
    when(measureService.findActiveMeasureById(anyString()))
        .thenThrow(new ResourceNotFoundException("Measure", measure.getId()));

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            testCaseService.deleteTestCases(
                measure.getId(), List.of("TC1_ID", "TC2_ID"), "test.user"));
  }

  @Test
  void testDeleteTestCasesThrowsInvalidDraftStateException() {
    measure.getMeasureMetaData().setDraft(false);
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    assertThrows(
        InvalidIdException.class,
        () ->
            testCaseService.deleteTestCases(
                measure.getId(), List.of("TC1_ID", "TC2_ID"), "test.user"));
  }

  @Test
  void testDeleteTestCasesThrowsExceptionWhenMeasureDoesNotContainAnyTestCases() {
    measure.setTestCases(List.of());
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    assertThrows(
        InvalidIdException.class,
        () ->
            testCaseService.deleteTestCases(
                measure.getId(), List.of("TC1_ID", "TC2_ID"), "test.user"));
  }

  @Test
  void testDeleteTestCases() {
    List<TestCase> testCases =
        List.of(
            TestCase.builder().id("TC1_ID").title("TC1").build(),
            TestCase.builder().id("TC2_ID").title("TC2").build(),
            TestCase.builder().id("TC3_ID").title("TC3").build(),
            TestCase.builder().id("TC4_ID").title("TC4").build());

    measure.setTestCases(testCases);
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    doReturn(measure).when(measureRepository).removeTestCase(anyString(), anyString());

    String output =
        testCaseService.deleteTestCases(
            measure.getId(), testCases.stream().map(TestCase::getId).toList(), "test.user");
    assertThat(
        output,
        is(
            equalTo(
                "Successfully deleted test cases: "
                    + String.join(", ", testCases.stream().map(TestCase::getId).toList()))));
  }

  @Test
  void testDeleteTestCasesAndReturnNotFoundTestIds() {
    List<TestCase> testCases =
        List.of(
            TestCase.builder().id("TC1_ID").title("TC1").build(),
            TestCase.builder().id("TC2_ID").title("TC2").build(),
            TestCase.builder().id("TC3_ID").title("TC3").build(),
            TestCase.builder().id("TC4_ID").title("TC4").build());

    measure.setTestCases(testCases);
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    doReturn(measure).when(measureRepository).removeTestCase(anyString(), anyString());

    String output =
        testCaseService.deleteTestCases(
            measure.getId(), List.of("TC1_ID", "TC2_ID", "TC5_ID", "TC6_ID"), "test.user");
    assertThat(
        output,
        is(
            equalTo(
                "Successfully deleted test cases: TC1_ID, TC2_ID, unable to delete TC5_ID, TC6_ID")));
  }

  @Test
  void testDeleteTestCasesAndReturnsLockedIds() {
    List<TestCase> testCases =
        List.of(
            TestCase.builder().id("TC1_ID").title("TC1").build(),
            TestCase.builder().id("TC2_ID").title("TC2").build(),
            TestCase.builder().id("TC3_ID").title("TC3").build(),
            TestCase.builder().id("TC4_ID").title("TC4").build());

    measure.setTestCases(testCases);
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    doReturn(measure).when(measureRepository).removeTestCase(anyString(), anyString());
    doThrow(new LockNotObtainedException())
        .when(testCaseLockService)
        .lockTestCaseForUser(anyString(), eq("TC1_ID"), anyString());
    doThrow(new LockNotObtainedException())
        .when(testCaseLockService)
        .lockTestCaseForUser(anyString(), eq("TC2_ID"), anyString());

    LockNotObtainedException thrown =
        assertThrows(
            LockNotObtainedException.class,
            () ->
                testCaseService.deleteTestCases(
                    measure.getId(), List.of("TC1_ID", "TC2_ID", "TC3_ID"), "test.user"));

    assertThat(thrown.getMessage(), is("TC1_ID,TC2_ID"));
  }

  @Test
  void resetCaseNumberSequenceWhenDeleteAllTestCases() {
    testDeleteTestCases();
    verify(testCaseSequenceService, times(1)).resetSequence(anyString());
  }

  @Test
  void importTestCasesReturnValidOutcomes() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setJson(testCaseImportWithMeasureReport);

    doReturn(updatedTestCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertNotNull(testCase.getDescription());
    assertNotNull(testCase.getTestCaseSetId());
    assertEquals(
        testCase.getDescription(), JsonUtil.getTestDescription(testCaseImportWithMeasureReport));
    assertTrue(response.get(0).isSuccessful());
  }

  @Test
  void importTestCaseAddsNewSetIdForNewTestCases() {
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.COHORT.name())
            .populations(
                List.of(
                    Population.builder()
                        .name(PopulationType.INITIAL_POPULATION)
                        .definition("Initial Population")
                        .build()))
            .populationBasis("Boolean")
            .build();
    measure.setGroups(List.of(group));
    measure.setTestCases(List.of());
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setJson(testCaseImportWithMeasureReport);

    doReturn(updatedTestCase)
        .when(testCaseService)
        .updateTestCase(
            testCaseCaptor.capture(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertTrue(response.get(0).isSuccessful());

    TestCase capturedTestCase = testCaseCaptor.getValue();
    assertNotNull(capturedTestCase.getTestCaseSetId());
    assertNotNull(capturedTestCase.getDescription());
    assertEquals(
        capturedTestCase.getDescription(),
        JsonUtil.getTestDescription(testCaseImportWithMeasureReport));
  }

  @Test
  void importTestCasesExistingWithExportMetaDataReturnValidOutcomes() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setJson(testCaseImportWithMeasureReport);

    doReturn(updatedTestCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .testCaseMetaData(
                TestCaseExportMetaData.builder()
                    .description("metaDataDescription")
                    .patientId(testCase.getPatientId().toString())
                    .series("metaDataSeries")
                    .title("metaDataTitle")
                    .testCaseId(testCase.getId())
                    .build())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertNotNull(testCase.getDescription());
    assertEquals("metaDataDescription", testCase.getDescription());
    assertTrue(response.get(0).isSuccessful());
  }

  @Test
  void importTestCasesReturnValidOutcomeWithAnyExceptionsWhileUpdatingTestCases() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    doThrow(new ResourceNotFoundException("Measure", measure.getId()))
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertFalse(response.get(0).isSuccessful());
    assertEquals(
        "Could not find Measure with id: " + measure.getId(), response.get(0).getMessage());
  }

  @Test
  void importTestCasesReturnValidOutcomeWithAnyDefaultExceptionsWhileUpdatingTestCases() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    doThrow(new NullPointerException())
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertFalse(response.get(0).isSuccessful());
    assertEquals(
        "Unable to import test case, please try again. If the error persists, Please contact helpdesk.",
        response.get(0).getMessage());
  }

  @Test
  void importTestCasesReturnInvalidOutcomeWithSpecificExceptionMsgWhileUpdatingTestCases() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    doThrow(new DuplicateTestCaseNameException())
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertFalse(response.get(0).isSuccessful());
    assertEquals(
        "The Test Case Group and Title are already used in another test case on this measure. The combination must be unique (case insensitive, spaces ignored) across all test cases associated with the measure.",
        response.get(0).getMessage());
  }

  @Test
  void importTestCaseReturnValidOutComeWithJsonParseException() {
    var importedJson = "{\n" + "    \"resourceType\": \"Bundle\",\n" + "}";
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(importedJson)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertFalse(response.get(0).isSuccessful());
    assertEquals(
        "Error while processing Test Case JSON.  Please make sure Test Case JSON is valid.",
        response.get(0).getMessage());
  }

  @Test
  void importTestCaseReturnValidOutComeWithExceptionWhenJsonIsNull() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    var testCaseImportRequest =
        TestCaseImportRequest.builder().patientId(testCase.getPatientId()).json(null).build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertFalse(response.get(0).isSuccessful());
    assertEquals("Test Case file is missing.", response.get(0).getMessage());
  }

  @Test
  void importTestCaseReturnInvalidOutComeWithExceptionWhenJsonIsEmpty() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    var testCaseImportRequest =
        TestCaseImportRequest.builder().patientId(testCase.getPatientId()).json("").build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertFalse(response.get(0).isSuccessful());
    assertEquals("Test Case file is missing.", response.get(0).getMessage());
  }

  @Test
  void importTestCasesReturnValidOutcomesWithMultipleFilesPerPatient() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    TestCase updatedTestCase = testCase;
    updatedTestCase.setJson(testCaseImportWithMeasureReport);

    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest, testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertFalse(response.get(0).isSuccessful());
    assertEquals(
        "Multiple test case files are not supported. Please make sure only one JSON file is in the folder.",
        response.get(0).getMessage());
  }

  @Test
  void importTestCasesCreateNewAllCriteriaMatched() {
    population1 =
        Population.builder()
            .name(PopulationType.INITIAL_POPULATION)
            .definition("Initial Population")
            .build();
    population2 =
        Population.builder().name(PopulationType.DENOMINATOR).definition("Denominator").build();
    population3 =
        Population.builder()
            .name(PopulationType.DENOMINATOR_EXCLUSION)
            .definition("Denominator Exclusion")
            .build();
    population4 =
        Population.builder().name(PopulationType.NUMERATOR).definition("Numerator").build();
    population5 =
        Population.builder()
            .name(PopulationType.DENOMINATOR_EXCEPTION)
            .definition("Numerator Exception")
            .build();
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.COHORT.name())
            .populations(List.of(population1, population2, population3, population4, population5))
            .populationBasis("Encounter")
            .build();
    measure.setGroups(List.of(group));

    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setJson(testCaseImportWithMeasureReport);
    doReturn(updatedTestCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(UUID.randomUUID())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertTrue(response.get(0).isSuccessful());
  }

  @Test
  void importTestCasesCreateNewWithExportMetaDataAllCriteriaMatched() {
    population1 =
        Population.builder()
            .name(PopulationType.INITIAL_POPULATION)
            .definition("Initial Population")
            .build();
    population2 =
        Population.builder().name(PopulationType.DENOMINATOR).definition("Denominator").build();
    population3 =
        Population.builder()
            .name(PopulationType.DENOMINATOR_EXCLUSION)
            .definition("Denominator Exclusion")
            .build();
    population4 =
        Population.builder().name(PopulationType.NUMERATOR).definition("Numerator").build();
    population5 =
        Population.builder()
            .name(PopulationType.DENOMINATOR_EXCEPTION)
            .definition("Numerator Exception")
            .build();
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.COHORT.name())
            .populations(List.of(population1, population2, population3, population4, population5))
            .populationBasis("Encounter")
            .build();
    measure.setGroups(List.of(group));
    List<TestCase> testCases = new ArrayList<>();
    testCases.add(testCase);
    measure.setTestCases(testCases);
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(200).successful(true).build())
                        .validResource(true)
                        .build());

    TestCase updatedTestCase = testCase;
    updatedTestCase.setJson(testCaseImportWithMeasureReport);
    UUID patientId = UUID.randomUUID();
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(patientId)
            .json(testCaseImportWithMeasureReport)
            .testCaseMetaData(
                TestCaseExportMetaData.builder()
                    .description("metaDataDescription")
                    .patientId(patientId.toString())
                    .series("metaDataSeries")
                    .title("metaDataTitle")
                    .testCaseId("ObjectID123")
                    .build())
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());

    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure measureOutput = measureArgumentCaptor.getValue();
    assertThat(measureOutput, is(notNullValue()));
    assertThat(measureOutput.getTestCases(), is(notNullValue()));
    assertThat(measureOutput.getTestCases().size(), is(equalTo(2)));
    assertThat(measureOutput.getTestCases().get(0), is(equalTo(testCase)));
    assertThat(measureOutput.getTestCases().get(1), is(notNullValue()));
    assertThat(measureOutput.getTestCases().get(1).getPatientId(), is(equalTo(patientId)));
    assertTrue(response.get(0).isSuccessful());
  }

  @Test
  void importTestCasesCreateNewWhenMeasureHasNotTestCase() {
    population1 =
        Population.builder()
            .name(PopulationType.INITIAL_POPULATION)
            .definition("Initial Population")
            .build();
    population2 =
        Population.builder().name(PopulationType.DENOMINATOR).definition("Denominator").build();
    population3 =
        Population.builder()
            .name(PopulationType.DENOMINATOR_EXCLUSION)
            .definition("Denominator Exclusiob")
            .build();
    population4 =
        Population.builder().name(PopulationType.NUMERATOR).definition("Numerator").build();
    population5 =
        Population.builder()
            .name(PopulationType.DENOMINATOR_EXCEPTION)
            .definition("Numerator Exception")
            .build();
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.COHORT.name())
            .populations(List.of(population1, population2, population3, population4, population5))
            .populationBasis("Encounter")
            .build();
    measure.setGroups(List.of(group));

    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setJson(testCaseImportWithMeasureReport);
    doReturn(updatedTestCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(UUID.randomUUID())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertTrue(response.get(0).isSuccessful());
  }

  @Test
  void importTestCasesCreateNewCriteriaNotAllMatched() {
    population1 = Population.builder().name(PopulationType.INITIAL_POPULATION).build();
    population2 = Population.builder().name(PopulationType.DENOMINATOR).build();
    population3 = Population.builder().name(PopulationType.DENOMINATOR_EXCLUSION).build();
    population4 = Population.builder().name(PopulationType.NUMERATOR).build();
    population5 = Population.builder().name(PopulationType.NUMERATOR_EXCLUSION).build();
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.COHORT.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population3, population4, population5))
            .build();
    measure.setGroups(List.of(group));

    measure.setTestCases(List.of(testCase));
    measure.setActive(true);
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setJson(testCaseImportWithMeasureReport);

    doReturn(updatedTestCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(UUID.randomUUID())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertTrue(response.get(0).isSuccessful());
  }

  @Test
  void importTestCasesCreateNewInvalidImportJson() {
    population1 = Population.builder().name(PopulationType.INITIAL_POPULATION).build();
    population2 = Population.builder().name(PopulationType.DENOMINATOR).build();
    population3 = Population.builder().name(PopulationType.DENOMINATOR_EXCLUSION).build();
    population4 = Population.builder().name(PopulationType.NUMERATOR).build();
    population5 = Population.builder().name(PopulationType.DENOMINATOR_EXCEPTION).build();
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.COHORT.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population3, population4, population5))
            .build();
    measure.setGroups(List.of(group));

    measure.setTestCases(List.of(testCase));
    measure.setActive(true);
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setJson(testCaseImportWithMeasureReport);

    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(UUID.randomUUID())
            .json("testInvalidJson")
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertFalse(response.get(0).isSuccessful());
  }

  @Test
  void importTestCasesDoesNotCreateNewNoGivenName() throws IOException {
    measure.setActive(true);
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    String testCaseImportWithoutGivenName =
        removeGivenNameFromJson(testCaseImportWithMeasureReport);
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(UUID.randomUUID())
            .json(testCaseImportWithoutGivenName)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertFalse(response.get(0).isSuccessful());
    assertEquals("Test Case Title is required.", response.get(0).getMessage());
  }

  private String removeGivenNameFromJson(String testCaseJson) throws IOException {
    String modifiedjsonString = testCaseJson;
    if (!StringUtils.isEmpty(testCaseJson)) {
      ObjectMapper objectMapper = new ObjectMapper();

      JsonNode rootNode = objectMapper.readTree(testCaseJson);
      ArrayNode entryArray = (ArrayNode) rootNode.get("entry");

      for (JsonNode entryNode : entryArray) {
        if ("Patient".equalsIgnoreCase(entryNode.get("resource").get("resourceType").asText())) {

          JsonNode resourceNode = entryNode.get("resource");
          ObjectNode parent = (ObjectNode) resourceNode;

          parent.remove("name");

          ByteArrayOutputStream bout = new ByteArrayOutputStream();
          objectMapper.writerWithDefaultPrettyPrinter().writeValue(bout, rootNode);
          modifiedjsonString = bout.toString();
        }
      }
    }
    return modifiedjsonString;
  }

  @Test
  void testUniqueTestCaseName() {
    measure.setTestCases(List.of(testCase));
    TestCase anotherTestCase = testCase.toBuilder().id(null).build();
    assertThrows(
        DuplicateTestCaseNameException.class,
        () -> testCaseService.verifyUniqueTestCaseName(anotherTestCase, measure));
  }

  @Test
  void testUniqueNameCheckCoversNameOnlyCase() {
    TestCase nameOnly = testCase.toBuilder().series(null).build();
    measure.setTestCases(List.of(nameOnly));
    TestCase anotherTestCase = nameOnly.toBuilder().id(null).build();
    assertThrows(
        DuplicateTestCaseNameException.class,
        () -> testCaseService.verifyUniqueTestCaseName(anotherTestCase, measure));
  }

  @Test
  void testUniqueNameCheckIgnoredOnSelf() {
    measure.setTestCases(List.of(testCase));
    TestCase anotherTestCase = testCase.toBuilder().build();
    assertDoesNotThrow(() -> testCaseService.verifyUniqueTestCaseName(anotherTestCase, measure));
  }

  @Test
  void testAssumeUniqueNameOnEmptyList() {
    assertDoesNotThrow(() -> testCaseService.verifyUniqueTestCaseName(testCase, measure));
  }

  @Test
  void importQdmTestCasesReturnValidOutcomesForProportion() {
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id("testMeasureId")
            .model(ModelType.QDM_5_6.getValue())
            .scoring(MeasureScoring.PROPORTION.name())
            .build();

    population1 = Population.builder().name(PopulationType.INITIAL_POPULATION).build();
    population2 = Population.builder().name(PopulationType.DENOMINATOR).build();
    population3 = Population.builder().name(PopulationType.DENOMINATOR_EXCLUSION).build();
    population4 = Population.builder().name(PopulationType.NUMERATOR).build();
    population5 = Population.builder().name(PopulationType.DENOMINATOR_EXCEPTION).build();

    Stratification strat = new Stratification();
    strat.setId("testStratId");
    strat.setDescription("test desc");
    strat.setCqlDefinition("ipp");
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Encounter")
            .populations(List.of(population1, population2, population3, population4, population5))
            .stratifications(List.of(strat))
            .build();
    qdmMeasure.setGroups(List.of(group));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(qdmMeasure);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setDescription(qdmTestCaseDescription);
    String json = JsonUtil.getTestCaseJson(testCaseImportQdm);
    updatedTestCase.setJson(json);

    doReturn(updatedTestCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(testCaseImportQdm)
            .givenNames(Collections.singletonList("testGivenName"))
            .familyName("testFamilyName")
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QDM_5_6.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertNotNull(testCase.getDescription());
    assertEquals(testCase.getDescription(), JsonUtil.getTestDescriptionQdm(testCaseImportQdm));
    assertTrue(response.get(0).isSuccessful());
  }

  @Test
  void importQdmTestCasesReturnValidOutcomesForRatio() {
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id("testMeasureId")
            .model(ModelType.QDM_5_6.getValue())
            .scoring(MeasureScoring.RATIO.name())
            .build();

    population1 = Population.builder().name(PopulationType.INITIAL_POPULATION).build();
    population2 = Population.builder().name(PopulationType.DENOMINATOR).build();
    population3 = Population.builder().name(PopulationType.DENOMINATOR_EXCLUSION).build();
    population4 = Population.builder().name(PopulationType.NUMERATOR).build();
    population5 = Population.builder().name(PopulationType.DENOMINATOR_EXCEPTION).build();

    Stratification strat = new Stratification();
    strat.setId("testStratId");
    strat.setDescription("test desc");
    strat.setCqlDefinition("ipp");
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.RATIO.name())
            .populationBasis("Encounter")
            .populations(List.of(population1, population2, population3, population4, population5))
            .stratifications(List.of(strat))
            .build();
    qdmMeasure.setGroups(List.of(group));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(qdmMeasure);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setDescription(qdmTestCaseDescription);
    String json = JsonUtil.getTestCaseJson(testCaseImportQdm);
    updatedTestCase.setJson(json);

    doReturn(updatedTestCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(testCaseImportQdm)
            .givenNames(Collections.singletonList("testGivenName"))
            .familyName("testFamilyName")
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QDM_5_6.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertNotNull(testCase.getDescription());
    assertEquals(testCase.getDescription(), JsonUtil.getTestDescriptionQdm(testCaseImportQdm));
    assertTrue(response.get(0).isSuccessful());
  }

  @Test
  void importQdmTestCasesReturnValidOutcomes() {
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id("testMeasureId")
            .model(ModelType.QDM_5_6.getValue())
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.name())
            .build();

    population1 = Population.builder().name(PopulationType.INITIAL_POPULATION).build();
    population2 = Population.builder().name(PopulationType.MEASURE_POPULATION).build();
    var observation = MeasureObservation.builder().definition("test function").build();

    Stratification strat = new Stratification();
    strat.setId("testStratId");
    strat.setDescription("test desc");
    strat.setCqlDefinition("ipp");
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.name())
            .populationBasis("Encounter")
            .populations(List.of(population1, population2))
            .measureObservations(List.of(observation))
            .stratifications(List.of(strat))
            .build();
    qdmMeasure.setGroups(List.of(group));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(qdmMeasure);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setDescription(qdmTestCaseDescription);
    String json = JsonUtil.getTestCaseJson(testCaseImportQdm);
    updatedTestCase.setJson(json);

    doReturn(updatedTestCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(testCaseImportQdm)
            .givenNames(Collections.singletonList("testGivenName"))
            .familyName("testFamilyName")
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QDM_5_6.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertNotNull(testCase.getDescription());
    assertEquals(testCase.getDescription(), JsonUtil.getTestDescriptionQdm(testCaseImportQdm));
    assertTrue(response.get(0).isSuccessful());
  }

  @Test
  void importQdmTestCasesForCVMeasureWithMultipleGroups() {
    String testCaseData = getData("/cv_qdm_test_with_multiple_groups.json");
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id("testMeasureId")
            .model(ModelType.QDM_5_6.getValue())
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
            .build();

    population1 =
        Population.builder().name(PopulationType.INITIAL_POPULATION).definition("IP").build();
    population2 =
        Population.builder().name(PopulationType.MEASURE_POPULATION).definition("MSR POP").build();
    var observation = MeasureObservation.builder().definition("test function").build();
    Group group1 =
        Group.builder()
            .id("1")
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.name())
            .populationBasis("Encounter")
            .populations(List.of(population1, population2))
            .measureObservations(List.of(observation))
            .build();
    Group group2 = group1.toBuilder().id("2").build();
    qdmMeasure.setGroups(List.of(group1, group2));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(qdmMeasure);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setDescription(qdmTestCaseDescription);
    String json = JsonUtil.getTestCaseJson(testCaseData);
    updatedTestCase.setJson(json);

    doReturn(updatedTestCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .familyName("John")
            .givenNames(List.of("Doe"))
            .patientId(testCase.getPatientId())
            .json(testCaseData)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QDM_5_6.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertNotNull(testCase.getDescription());
    assertTrue(response.get(0).isSuccessful());
    assertThat(
        response.get(0).getMessage(),
        is(
            equalTo(
                "observation values were not imported. MADiE cannot import expected values for Continuous Variable measures with multiple population criteria.")));
  }

  @Test
  void testDefaultTestCaseJsonForQdmMeasureWhenJsonIsNull() {
    testCase.setJson(null);
    measure.setModel(ModelType.QDM_5_6.toString());
    measure.setTestCases(List.of(testCase));

    testCaseService.defaultTestCaseJsonForQdmMeasure(testCase, measure);
    assertNotNull(testCase.getJson());
    assertTrue(testCase.getJson().contains("qdmVersion"));
    assertTrue(testCase.getJson().contains("5.6"));
    assertTrue(testCase.getJson().contains("dataElements"));
    assertTrue(testCase.getJson().contains("_id"));
  }

  @Test
  void testDefaultTestCaseJsonForQdmMeasureWhenJsonIsNotNull() {
    measure.setModel(ModelType.QDM_5_6.toString());
    measure.setTestCases(List.of(testCase));

    testCaseService.defaultTestCaseJsonForQdmMeasure(testCase, measure);
    assertNotNull(testCase.getJson());
    assertEquals("{\n  \"resourceType\" : \"Patient\"\n}", testCase.getJson());
  }

  @Test
  public void testUpdateTestCaseForQdm() {
    String patientId = "66056973fc02b60000d076e9";
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Instant createdAt = Instant.now().minus(300, ChronoUnit.SECONDS);
    String json =
        "{\"qdmVersion\": \"5.6\",\n"
            + " \"dataElements\": [],\n"
            + " \"_id\": \"66056973fc02b60000d076e9\"\n"
            + "}";
    TestCase originalTestCase =
        testCase.toBuilder()
            .title("test title")
            .createdAt(createdAt)
            .createdBy("test.user5")
            .lastModifiedAt(createdAt)
            .lastModifiedBy("test.user5")
            .json(json)
            .build();
    List<TestCase> testCases = new ArrayList<>();
    testCases.add(originalTestCase);
    Measure originalMeasure =
        measure.toBuilder()
            .model(ModelType.QDM_5_6.getValue())
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .cqlLibraryName("Test1CQLLibraryName")
            .testCases(testCases)
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(originalMeasure);

    TestCase updatingTestCase =
        testCase.toBuilder().title("UpdatedTitle").series("UpdatedSeries").json(json).build();
    Mockito.doAnswer((args) -> args.getArgument(0))
        .when(measureRepository)
        .save(any(Measure.class));
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));

    TestCase updatedTestCase =
        testCaseService.updateTestCase(
            updatingTestCase, measure.getId(), "test.user5", "TOKEN", TestCaseServiceUtil.SAVE);
    assertNotNull(updatedTestCase);

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    assertEquals(updatingTestCase.getId(), updatedTestCase.getId());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    assertNotNull(savedMeasure.getTestCases());
    assertEquals(1, savedMeasure.getTestCases().size());

    assertTrue(savedMeasure.getTestCases().get(0).getJson().contains(patientId));

    int lastModCompareTo =
        updatedTestCase.getLastModifiedAt().compareTo(Instant.now().minus(60, ChronoUnit.SECONDS));
    assertEquals("test.user5", updatedTestCase.getLastModifiedBy());
    assertEquals(originalTestCase.getCreatedBy(), updatedTestCase.getCreatedBy());
    assertEquals(1, lastModCompareTo);
    assertNotEquals(updatedTestCase.getLastModifiedAt(), updatedTestCase.getCreatedAt());
    assertEquals("test.user5", updatedTestCase.getCreatedBy());
  }

  @Test
  public void testPersistTestCaseForQdm() {
    List<TestCase> existingTestCases = new ArrayList<>();
    TestCase existingTestCase =
        TestCase.builder().id("Test1ID").title("Test0").series("series").build();
    existingTestCases.add(existingTestCase);
    measure.setTestCases(existingTestCases);
    measure.setModel(ModelType.QDM_5_6.getValue());
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(measure);

    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));

    TestCase persistTestCase =
        testCaseService.persistTestCase(testCase, measure.getId(), "test.user", "TOKEN");
    assertThat(persistTestCase, is(notNullValue()));
    assertThat(persistTestCase.getId(), is(notNullValue()));
    assertThat(persistTestCase.getTitle(), is(equalTo(testCase.getTitle())));
    verify(measureRepository, times(1))
        .addOrUpdateTestCase(eq(measure.getId()), testCaseCaptor.capture());
    TestCase capturedTestCase = testCaseCaptor.getValue();
    int lastModCompareTo =
        capturedTestCase.getLastModifiedAt().compareTo(Instant.now().minus(60, ChronoUnit.SECONDS));
    assertEquals("test.user", capturedTestCase.getLastModifiedBy());
    assertEquals("test.user", capturedTestCase.getCreatedBy());
    assertEquals(1, lastModCompareTo);
    assertEquals(capturedTestCase.getLastModifiedAt(), capturedTestCase.getCreatedAt());
  }

  @Test
  void importTestCasesDoesNotCreateNewTitleOrGroupHasSpecialCharacters() {
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    String patientId = UUID.randomUUID().toString();
    String json =
        "{\"qdmVersion\": \"5.6\",\n"
            + " \"dataElements\": [],\n"
            + " \"_id\": "
            + patientId
            + "\n"
            + "}";
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(UUID.fromString(patientId))
            .json(json)
            .familyName("inavid ^&")
            .givenNames(Collections.singletonList("invalid ()"))
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QDM_5_6.getValue());
    assertFalse(response.get(0).isSuccessful());
    assertEquals(
        "Test Cases Group or Title cannot contain special characters.",
        response.get(0).getMessage());
  }

  @Test
  void importTestCasesDoesNotCreateNewTitleMissing() {
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    String patientId = UUID.randomUUID().toString();
    String json =
        "{\"qdmVersion\": \"5.6\",\n"
            + " \"dataElements\": [],\n"
            + " \"_id\": "
            + patientId
            + "\n"
            + "}";
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(UUID.fromString(patientId))
            .json(json)
            .familyName("testFamilyName")
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QDM_5_6.getValue());
    assertFalse(response.get(0).isSuccessful());
    assertEquals("Test Case title is required.", response.get(0).getMessage());
  }

  @Test
  void testGetDescriptionWithNullImportRequest() {
    final String bundleJson =
        """
        {
          "resourceType": "Bundle",
          "id": "IP-Pass-CVPatient",
          "meta": {
            "versionId": "1",
            "lastUpdated": "2022-09-14T15:14:42.152+00:00"
          },
          "type": "collection",
          "entry": [
          {
              "resource": {
                "resourceType": "MeasureReport",
                "id": "34c3e75a-c127-4236-8057-300a5ad5f8e3",
                "meta": {
                  "profile": [ "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/test-case-cqfm" ]
                },

                "extension": [ {
                  "url": "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-testCaseDescription",
                  "valueMarkdown": "tcdescription123"
                } ],
                "group": [],
                "evaluatedResource": [ ]
              }
            }
          ]
        }
        """;
    String output = testCaseService.getDescription(ModelType.QI_CORE.getValue(), bundleJson, null);
    assertThat(output, is(equalTo("tcdescription123")));
  }

  @Test
  void testGetDescriptionWithNullExportMetaData() {
    final String bundleJson =
        """
        {
          "resourceType": "Bundle",
          "id": "IP-Pass-CVPatient",
          "meta": {
            "versionId": "1",
            "lastUpdated": "2022-09-14T15:14:42.152+00:00"
          },
          "type": "collection",
          "entry": [
          {
              "resource": {
                "resourceType": "MeasureReport",
                "id": "34c3e75a-c127-4236-8057-300a5ad5f8e3",
                "meta": {
                  "profile": [ "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/test-case-cqfm" ]
                },

                "extension": [ {
                  "url": "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-testCaseDescription",
                  "valueMarkdown": "tcdescription123"
                } ],
                "group": [],
                "evaluatedResource": [ ]
              }
            }
          ]
        }
        """;
    TestCaseImportRequest importRequest =
        TestCaseImportRequest.builder().testCaseMetaData(null).build();
    String output =
        testCaseService.getDescription(ModelType.QI_CORE.getValue(), bundleJson, importRequest);
    assertThat(output, is(equalTo("tcdescription123")));
  }

  @Test
  void testGetDescriptionWithNullExportMetaDataDescription() {
    final String bundleJson =
        """
        {
          "resourceType": "Bundle",
          "id": "IP-Pass-CVPatient",
          "meta": {
            "versionId": "1",
            "lastUpdated": "2022-09-14T15:14:42.152+00:00"
          },
          "type": "collection",
          "entry": [
          {
              "resource": {
                "resourceType": "MeasureReport",
                "id": "34c3e75a-c127-4236-8057-300a5ad5f8e3",
                "meta": {
                  "profile": [ "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/test-case-cqfm" ]
                },

                "extension": [ {
                  "url": "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-testCaseDescription",
                  "valueMarkdown": "tcdescription123"
                } ],
                "group": [],
                "evaluatedResource": [ ]
              }
            }
          ]
        }
        """;
    TestCaseImportRequest importRequest =
        TestCaseImportRequest.builder()
            .testCaseMetaData(TestCaseExportMetaData.builder().description(null).build())
            .build();
    String output =
        testCaseService.getDescription(ModelType.QI_CORE.getValue(), bundleJson, importRequest);
    assertThat(output, is(equalTo("tcdescription123")));
  }

  @Test
  void testGetDescriptionWithValidExportMetaDataDescription() {
    final String bundleJson =
        """
        {
          "resourceType": "Bundle",
          "id": "IP-Pass-CVPatient",
          "meta": {
            "versionId": "1",
            "lastUpdated": "2022-09-14T15:14:42.152+00:00"
          },
          "type": "collection",
          "entry": [
          {
              "resource": {
                "resourceType": "MeasureReport",
                "id": "34c3e75a-c127-4236-8057-300a5ad5f8e3",
                "meta": {
                  "profile": [ "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/test-case-cqfm" ]
                },

                "extension": [ {
                  "url": "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-testCaseDescription",
                  "valueMarkdown": "tcdescription123"
                } ],
                "group": [],
                "evaluatedResource": [ ]
              }
            }
          ]
        }
        """;
    TestCaseImportRequest importRequest =
        TestCaseImportRequest.builder()
            .testCaseMetaData(
                TestCaseExportMetaData.builder().description("metaDataDescription").build())
            .build();
    String output =
        testCaseService.getDescription(ModelType.QI_CORE.getValue(), bundleJson, importRequest);
    assertThat(output, is(equalTo("metaDataDescription")));
  }

  @Test
  void testGetTitleWithNullImportRequest() {
    String output = testCaseService.getTitle(null, "theGivenName");
    assertThat(output, is(equalTo("theGivenName")));
  }

  @Test
  void testGetTitleWithNullExportMetaData() {
    TestCaseImportRequest importRequest =
        TestCaseImportRequest.builder().testCaseMetaData(null).build();
    String output = testCaseService.getTitle(importRequest, "theGivenName");
    assertThat(output, is(equalTo("theGivenName")));
  }

  @Test
  void testGetTitleWithValidExportMetaData() {
    TestCaseImportRequest importRequest =
        TestCaseImportRequest.builder()
            .testCaseMetaData(TestCaseExportMetaData.builder().title("metaDataTitle").build())
            .build();
    String output = testCaseService.getTitle(importRequest, "theGivenName");
    assertThat(output, is(equalTo("metaDataTitle")));
  }

  @Test
  void testGetSeriesWithNullImportRequest() {
    String output = testCaseService.getSeries(null, "theFamilyName");
    assertThat(output, is(equalTo("theFamilyName")));
  }

  @Test
  void testGetSeriesWithNullExportMetaData() {
    TestCaseImportRequest importRequest =
        TestCaseImportRequest.builder().testCaseMetaData(null).build();
    String output = testCaseService.getSeries(importRequest, "theFamilyName");
    assertThat(output, is(equalTo("theFamilyName")));
  }

  @Test
  void testGetSeriesWithValidExportMetaDataMissingSeries() {
    TestCaseImportRequest importRequest =
        TestCaseImportRequest.builder()
            .testCaseMetaData(TestCaseExportMetaData.builder().series(null).build())
            .build();
    String output = testCaseService.getSeries(importRequest, "theFamilyName");
    assertThat(output, is(nullValue()));
  }

  @Test
  void testGetSeriesWithValidExportMetaDataWithSeries() {
    TestCaseImportRequest importRequest =
        TestCaseImportRequest.builder()
            .testCaseMetaData(TestCaseExportMetaData.builder().series("metaDataSeries").build())
            .build();
    String output = testCaseService.getSeries(importRequest, "theFamilyName");
    assertThat(output, is(equalTo("metaDataSeries")));
  }

  @Test
  void testQiCoreTestCaseDateShift() {
    ResponseEntity<List<TestCase>> mockClientResponse = ResponseEntity.ok(List.of(testCase));
    doReturn(mockClientResponse)
        .when(fhirServicesClient)
        .shiftTestCaseDates(anyList(), anyInt(), anyString());
    String accessToken = "Bearer Token";

    List<TestCase> shiftedTestCase =
        testCaseService.shiftQiCoreTestCaseDates(
            List.of(testCase), 1, accessToken, "measureId", "userName");
    assertNotNull(shiftedTestCase);
  }

  @Test
  void testQiCoreTestCaseDateShiftFailed() {
    ResponseEntity<List<TestCase>> mockClientResponse = ResponseEntity.ok(Collections.emptyList());
    doReturn(mockClientResponse)
        .when(fhirServicesClient)
        .shiftTestCaseDates(anyList(), anyInt(), anyString());
    String accessToken = "Bearer Token";

    List<TestCase> shiftedTestCase =
        testCaseService.shiftQiCoreTestCaseDates(
            List.of(testCase), 1, accessToken, "measureId", "userName");
    assertTrue(CollectionUtils.isEmpty(shiftedTestCase));
  }

  @Test
  void testQiCoreMultiTestCaseDateShift() {
    ResponseEntity<List<TestCase>> mockClientResponse = ResponseEntity.ok(List.of(testCase));
    doReturn(mockClientResponse)
        .when(fhirServicesClient)
        .shiftTestCaseDates(anyList(), anyInt(), anyString());

    List<TestCase> shiftedTestCases =
        testCaseService.shiftQiCoreTestCaseDates(
            List.of(testCase), 1, "TOKEN", "measureId", "userName");
    assertThat(shiftedTestCases.size(), equalTo(1));
    assertTrue(shiftedTestCases.contains(testCase));
  }

  @Test
  void testCopyToAnotherMeasure() {
    // Set-up
    MeasureMetaData metaData = MeasureMetaData.builder().draft(false).build();
    TestCase source =
        testCase.deepCopy().toBuilder()
            .json(testCaseImportWithMeasureReport)
            .groupPopulations(
                List.of(
                    TestCaseGroupPopulation.builder()
                        .scoring(MeasureScoring.PROPORTION.toString())
                        .populationBasis("boolean")
                        .populationValues(
                            List.of(
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .expected(true)
                                    .build(),
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.DENOMINATOR)
                                    .expected(true)
                                    .build(),
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.NUMERATOR)
                                    .expected(true)
                                    .build()))
                        .build()))
            .build();

    Measure targetMeasure =
        measure.toBuilder()
            .groups(
                List.of(
                    Group.builder()
                        .scoring(MeasureScoring.PROPORTION.toString())
                        .populationBasis("boolean")
                        .populations(
                            List.of(
                                Population.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .definition("def")
                                    .build(),
                                Population.builder()
                                    .name(PopulationType.DENOMINATOR)
                                    .definition("def")
                                    .build(),
                                Population.builder()
                                    .name(PopulationType.NUMERATOR)
                                    .definition("def")
                                    .build()))
                        .build()))
            .measureMetaData(metaData)
            .build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureService.findMeasureById(anyString())).thenReturn(targetMeasure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(200).successful(true).build())
                        .validResource(true)
                        .build());
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(targetMeasure);

    // Start with empty Test Case list on target measure
    assertTrue(CollectionUtils.isEmpty(targetMeasure.getTestCases()));

    // Copy single Test Case to target measure
    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasure.getId(), List.of(source), "user.name", "accessToken");

    // Verify source Test Case wasn't modified
    assertTrue(
        (Boolean) source.getGroupPopulations().get(0).getPopulationValues().get(0).getExpected());

    // Matching Population Criteria - verify copied Test Case has source Population Expectations.
    assertThat(result.getCopiedTestCases().size(), equalTo(1));
    assertFalse(result.getDidClearExpectedValues());
    assertThat(
        (Boolean)
            result
                .getCopiedTestCases()
                .get(0)
                .getGroupPopulations()
                .get(0)
                .getPopulationValues()
                .get(0)
                .getExpected(),
        is(
            (Boolean)
                source.getGroupPopulations().get(0).getPopulationValues().get(0).getExpected()));

    assertThat(
        result.getCopiedTestCases().get(0).getJson(),
        containsString("2012-01-16T08:00:00.000+00:00"));
    assertFalse(result.getCopiedTestCases().get(0).isCreatedBeforeVersioning());
  }

  @Test
  void testCopyToAnotherMeasureWithDifferentPopCriteria() {
    // Set-up
    TestCase source =
        testCase.deepCopy().toBuilder()
            .groupPopulations(
                List.of(
                    TestCaseGroupPopulation.builder()
                        .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
                        .populationBasis("boolean")
                        .populationValues(
                            List.of(
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .expected(true)
                                    .build(),
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.MEASURE_POPULATION)
                                    .expected(true)
                                    .build(),
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.MEASURE_OBSERVATION)
                                    .expected(true)
                                    .build()))
                        .build()))
            .build();

    Measure targetMeasure =
        measure.toBuilder()
            .groups(
                List.of(
                    Group.builder()
                        .scoring(MeasureScoring.PROPORTION.toString())
                        .populationBasis("boolean")
                        .populations(
                            List.of(
                                Population.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .definition("def")
                                    .build(),
                                Population.builder()
                                    .name(PopulationType.DENOMINATOR)
                                    .definition("def")
                                    .build(),
                                Population.builder()
                                    .name(PopulationType.NUMERATOR)
                                    .definition("def")
                                    .build()))
                        .build()))
            .build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureService.findMeasureById(anyString())).thenReturn(targetMeasure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(200).successful(true).build())
                        .validResource(true)
                        .build());
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(targetMeasure);

    // Start with empty Test Case list on target measure
    assertTrue(CollectionUtils.isEmpty(targetMeasure.getTestCases()));

    // Copy single Test Case to target measure
    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasure.getId(), List.of(source), "user.name", "accessToken");

    // Verify source Test Case wasn't modified
    assertTrue(
        (Boolean) source.getGroupPopulations().get(0).getPopulationValues().get(0).getExpected());

    // Mismatched Population Criteria - verify copied Test Case have cleared Population
    // Expectations.
    assertThat(result.getCopiedTestCases().size(), equalTo(1));
    assertTrue(result.getDidClearExpectedValues());
    assertNull(
        result
            .getCopiedTestCases()
            .get(0)
            .getGroupPopulations()
            .get(0)
            .getPopulationValues()
            .get(0)
            .getExpected());
  }

  @Test
  void testCopyToAnotherMeasureDropsExtraGroupPopulationsWhenTargetHasFewerGroups() {
    // Source test case has 2 group populations; target measure has only 1 group.
    // The extra group population should be dropped to prevent stale/excess data.
    TestCase source =
        testCase.deepCopy().toBuilder()
            .groupPopulations(
                List.of(
                    TestCaseGroupPopulation.builder()
                        .scoring(MeasureScoring.PROPORTION.toString())
                        .populationBasis("boolean")
                        .populationValues(
                            List.of(
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .expected(true)
                                    .build(),
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.DENOMINATOR)
                                    .expected(true)
                                    .build()))
                        .build(),
                    TestCaseGroupPopulation.builder()
                        .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
                        .populationBasis("boolean")
                        .populationValues(
                            List.of(
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .expected(true)
                                    .build()))
                        .build()))
            .build();

    // Target measure has only 1 group
    Measure targetMeasure =
        measure.toBuilder()
            .groups(
                List.of(
                    Group.builder()
                        .scoring(MeasureScoring.PROPORTION.toString())
                        .populationBasis("boolean")
                        .populations(
                            List.of(
                                Population.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .definition("def")
                                    .build(),
                                Population.builder()
                                    .name(PopulationType.NUMERATOR)
                                    .definition("def")
                                    .build()))
                        .build()))
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureService.findMeasureById(anyString())).thenReturn(targetMeasure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(200).successful(true).build())
                        .validResource(true)
                        .build());
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(targetMeasure);

    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasure.getId(), List.of(source), "user.name", "accessToken");

    assertThat(result.getCopiedTestCases().size(), equalTo(1));
    assertTrue(result.getDidClearExpectedValues());
    // Extra group population should be dropped — only 1 group population remains
    assertThat(result.getCopiedTestCases().get(0).getGroupPopulations().size(), equalTo(1));
    // Expected values should be cleared
    assertNull(
        result
            .getCopiedTestCases()
            .get(0)
            .getGroupPopulations()
            .get(0)
            .getPopulationValues()
            .get(0)
            .getExpected());
  }

  @Test
  void testCopyToAnotherMeasureDoesNotTrimWhenTargetHasNoGroups() {
    // When target has no valid groups, targetGroups is null → isNotEmpty(targetGroups) is false
    // → trim is skipped even though pop criteria mismatch occurred.
    TestCase source =
        testCase.deepCopy().toBuilder()
            .groupPopulations(
                List.of(
                    TestCaseGroupPopulation.builder()
                        .scoring(MeasureScoring.PROPORTION.toString())
                        .populationBasis("boolean")
                        .populationValues(
                            List.of(
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .expected(true)
                                    .build()))
                        .build()))
            .build();

    // Target measure has no groups at all
    Measure targetMeasure =
        measure.toBuilder()
            .groups(List.of())
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureService.findMeasureById(anyString())).thenReturn(targetMeasure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(200).successful(true).build())
                        .validResource(true)
                        .build());
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(targetMeasure);

    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasure.getId(), List.of(source), "user.name", "accessToken");

    assertThat(result.getCopiedTestCases().size(), equalTo(1));
    assertTrue(result.getDidClearExpectedValues());
    // Group populations are NOT trimmed because targetGroups is null/empty
    assertThat(result.getCopiedTestCases().get(0).getGroupPopulations().size(), equalTo(1));
  }

  @Test
  void testCopyToAnotherMeasureDoesNotTrimWhenSourceHasFewerGroupsThanTarget() {
    // Source has 1 group population, target has 2 groups. Mismatch → expected values cleared.
    // But source.size() < target.size() so trim condition (size() > targetGroups.size()) is false.
    TestCase source =
        testCase.deepCopy().toBuilder()
            .groupPopulations(
                List.of(
                    TestCaseGroupPopulation.builder()
                        .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
                        .populationBasis("boolean")
                        .populationValues(
                            List.of(
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .expected(true)
                                    .build()))
                        .build()))
            .build();

    // Target measure has 2 groups — more than the source
    Measure targetMeasure =
        measure.toBuilder()
            .groups(
                List.of(
                    Group.builder()
                        .scoring(MeasureScoring.PROPORTION.toString())
                        .populationBasis("boolean")
                        .populations(
                            List.of(
                                Population.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .definition("def")
                                    .build(),
                                Population.builder()
                                    .name(PopulationType.DENOMINATOR)
                                    .definition("def")
                                    .build()))
                        .build(),
                    Group.builder()
                        .scoring(MeasureScoring.PROPORTION.toString())
                        .populationBasis("boolean")
                        .populations(
                            List.of(
                                Population.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .definition("def2")
                                    .build()))
                        .build()))
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureService.findMeasureById(anyString())).thenReturn(targetMeasure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(200).successful(true).build())
                        .validResource(true)
                        .build());
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(targetMeasure);

    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasure.getId(), List.of(source), "user.name", "accessToken");

    assertThat(result.getCopiedTestCases().size(), equalTo(1));
    assertTrue(result.getDidClearExpectedValues());
    // Source has fewer groups than target: no trimming — group populations count stays at 1
    assertThat(result.getCopiedTestCases().get(0).getGroupPopulations().size(), equalTo(1));
  }

  @Test
  void testCopyToAnotherMeasureWithExistingTestCase() {
    // Set-up
    Measure targetMeasure =
        measure.toBuilder().testCases(new ArrayList<>(List.of(testCase))).build();
    TestCase source = testCase.deepCopy().toBuilder().id(null).build();
    assertThat(targetMeasure.getTestCases().size(), is(1));

    when(measureService.findActiveMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureService.findMeasureById(anyString())).thenReturn(targetMeasure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(200).successful(true).build())
                        .validResource(true)
                        .build());
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(targetMeasure);

    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasure.getId(), List.of(source), "user.name", "accessToken");
    assertThat(result.getCopiedTestCases().size(), is(1));
    assertTrue(result.getCopiedTestCases().get(0).getTitle().contains("-"));
  }

  @Test
  void testCopyToAnotherMeasureNameTooLong() {
    // Set-up
    String longName = RandomStringUtils.insecure().next(240);
    Measure targetMeasure =
        measure.toBuilder()
            .testCases(new ArrayList<>(List.of(testCase.toBuilder().title(longName).build())))
            .build();
    TestCase source = testCase.deepCopy().toBuilder().id(null).title(longName).build();
    assertThat(targetMeasure.getTestCases().size(), is(1));

    when(measureService.findActiveMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureService.findMeasureById(anyString())).thenReturn(targetMeasure);

    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasure.getId(), List.of(source), "user.name", "accessToken");
    assertThat(result.getCopiedTestCases().size(), is(0));
    assertThat(result.getFailedTestCases().size(), is(1));
    assertThat(result.getFailedTestCases().get(0).getTitle(), is(longName));
    assertThat(targetMeasure.getTestCases().size(), is(1));
  }

  @Test
  void testCopyToAnotherMeasureWithMatchingStratifications() {
    // Set-up
    TestCase source =
        testCase.deepCopy().toBuilder()
            .groupPopulations(
                List.of(
                    TestCaseGroupPopulation.builder()
                        .scoring(MeasureScoring.PROPORTION.toString())
                        .populationBasis("boolean")
                        .populationValues(
                            List.of(
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .expected(true)
                                    .build(),
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.DENOMINATOR)
                                    .expected(true)
                                    .build(),
                                TestCasePopulationValue.builder()
                                    .name(PopulationType.NUMERATOR)
                                    .expected(true)
                                    .build()))
                        .stratificationValues(
                            List.of(
                                TestCaseStratificationValue.builder()
                                    .id("source-strat-id")
                                    .name("Strata 1")
                                    .expected(true)
                                    .build()))
                        .build()))
            .build();

    Measure targetMeasure =
        measure.toBuilder()
            .groups(
                List.of(
                    Group.builder()
                        .scoring(MeasureScoring.PROPORTION.toString())
                        .populationBasis("boolean")
                        .populations(
                            List.of(
                                Population.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .definition("def")
                                    .build(),
                                Population.builder()
                                    .name(PopulationType.DENOMINATOR)
                                    .definition("def")
                                    .build(),
                                Population.builder()
                                    .name(PopulationType.NUMERATOR)
                                    .definition("def")
                                    .build()))
                        .stratifications(
                            List.of(Stratification.builder().id("target-strat-id").build()))
                        .build()))
            .build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureService.findMeasureById(anyString())).thenReturn(targetMeasure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(200).successful(true).build())
                        .validResource(true)
                        .build());
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(targetMeasure);

    // Start with empty Test Case list on target measure
    assertTrue(CollectionUtils.isEmpty(targetMeasure.getTestCases()));

    // Copy single Test Case to target measure
    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasure.getId(), List.of(source), "user.name", "accessToken");

    // Verify source Test Case wasn't modified
    assertTrue(
        (Boolean) source.getGroupPopulations().get(0).getPopulationValues().get(0).getExpected());
    assertThat(
        source.getGroupPopulations().get(0).getStratificationValues().get(0).getId(),
        is("source-strat-id"));

    // Matching Population Criteria - verify copied Test Case has source Population Expectations.
    assertThat(result.getCopiedTestCases().size(), equalTo(1));
    assertFalse(result.getDidClearExpectedValues());
    assertThat(
        (Boolean)
            result
                .getCopiedTestCases()
                .get(0)
                .getGroupPopulations()
                .get(0)
                .getPopulationValues()
                .get(0)
                .getExpected(),
        is(
            (Boolean)
                source.getGroupPopulations().get(0).getPopulationValues().get(0).getExpected()));

    assertThat(
        result
            .getCopiedTestCases()
            .get(0)
            .getGroupPopulations()
            .get(0)
            .getStratificationValues()
            .size(),
        is(1));
    assertThat(
        result
            .getCopiedTestCases()
            .get(0)
            .getGroupPopulations()
            .get(0)
            .getStratificationValues()
            .get(0)
            .getId(),
        is("target-strat-id"));
  }

  @Test
  void testCopyEmptyTestCaseToAnotherMeasure() {
    // Set-up
    TestCase source = testCase.deepCopy().toBuilder().groupPopulations(new ArrayList<>()).build();

    Measure targetMeasure =
        measure.toBuilder()
            .groups(
                List.of(
                    Group.builder()
                        .scoring(MeasureScoring.PROPORTION.toString())
                        .populationBasis("boolean")
                        .populations(
                            List.of(
                                Population.builder()
                                    .name(PopulationType.INITIAL_POPULATION)
                                    .definition("def")
                                    .build(),
                                Population.builder()
                                    .name(PopulationType.DENOMINATOR)
                                    .definition("def")
                                    .build(),
                                Population.builder()
                                    .name(PopulationType.NUMERATOR)
                                    .definition("def")
                                    .build()))
                        .build()))
            .build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureService.findMeasureById(anyString())).thenReturn(targetMeasure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(
            invocation ->
                (invocation.getArgument(0, TestCase.class))
                    .toBuilder()
                        .hapiOperationOutcome(
                            HapiOperationOutcome.builder().code(200).successful(true).build())
                        .validResource(true)
                        .build());
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(targetMeasure);

    // Start with empty Test Case list on target measure
    assertTrue(CollectionUtils.isEmpty(targetMeasure.getTestCases()));

    // Copy single Test Case to target measure
    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasure.getId(), List.of(source), "user.name", "accessToken");

    // Verify source Test Case wasn't modified
    assertTrue(source.getGroupPopulations().isEmpty());

    // Verify expected values weren't "cleared". Technically, there weren't any to clear,
    // but this helps the UI display a more accurate toast message.
    assertThat(result.getCopiedTestCases().size(), equalTo(1));
    assertFalse(result.getDidClearExpectedValues());
  }

  @Test
  void testCopyToAnotherMeasureQDMNoUtcUpdate() {
    // Set-up
    TestCase source = testCase.deepCopy().toBuilder().json(testCaseImportQdm).build();

    Measure targetMeasure = measure.toBuilder().model(ModelType.QDM_5_6.getValue()).build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureService.findMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(targetMeasure);

    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));

    // Copy single Test Case to target measure
    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasure.getId(), List.of(source), "user.name", "accessToken");

    assertThat(
        result.getCopiedTestCases().get(0).getJson(),
        containsString("2024-12-30T09:00:00.000+04:00"));
  }

  @Test
  void testCopyToAnotherMeasureQiCoreSetsNewTestCaseSetId() {

    TestCase source = testCase.deepCopy();
    UUID sourceSetId = source.getTestCaseSetId();
    assertNotNull(sourceSetId);

    Measure targetMeasure = measure.toBuilder().build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureService.findMeasureById(anyString())).thenReturn(targetMeasure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(targetMeasure);

    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasure.getId(), List.of(source), "user.name", "accessToken");

    assertThat(result.getCopiedTestCases().size(), equalTo(1));
    UUID copiedSetId = result.getCopiedTestCases().get(0).getTestCaseSetId();
    assertNotNull(copiedSetId);
    assertNotEquals(sourceSetId, copiedSetId);
    // Original is unchanged
    assertEquals(sourceSetId, source.getTestCaseSetId());
  }

  @Test
  void testCopyMultipleToAnotherMeasureQiCoreEachGetsNewUniqueTestCaseSetId() {

    TestCase source1 = testCase.deepCopy().toBuilder().title("TC1").build();
    TestCase source2 =
        testCase.deepCopy().toBuilder().title("TC2").testCaseSetId(UUID.randomUUID()).build();
    UUID sourceSetId1 = source1.getTestCaseSetId();
    UUID sourceSetId2 = source2.getTestCaseSetId();
    assertNotNull(sourceSetId1);
    assertNotNull(sourceSetId2);
    assertNotEquals(sourceSetId1, sourceSetId2);

    Measure targetMeasure = measure.toBuilder().build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(targetMeasure);
    when(measureService.findMeasureById(anyString())).thenReturn(targetMeasure);
    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(targetMeasure);

    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasure.getId(), List.of(source1, source2), "user.name", "accessToken");

    assertThat(result.getCopiedTestCases().size(), equalTo(2));
    UUID copiedSetId1 = result.getCopiedTestCases().get(0).getTestCaseSetId();
    UUID copiedSetId2 = result.getCopiedTestCases().get(1).getTestCaseSetId();

    // Each copy has a non-null testCaseSetId
    assertNotNull(copiedSetId1);
    assertNotNull(copiedSetId2);
    // Each copy gets a different ID from its source
    assertNotEquals(sourceSetId1, copiedSetId1);
    assertNotEquals(sourceSetId2, copiedSetId2);
    // Each copy gets a unique ID (not shared across copies)
    assertNotEquals(copiedSetId1, copiedSetId2);
    // Sources are unchanged
    assertEquals(sourceSetId1, source1.getTestCaseSetId());
    assertEquals(sourceSetId2, source2.getTestCaseSetId());
  }

  @Test
  public void testValidateTestCaseAsynchronouslyForSTU6MeasuresWhenUpdatingTestCase() {
    measure.setModel(ModelType.QI_CORE_6_0_0.getValue());
    TestCase testCase =
        TestCase.builder()
            .id("TestID")
            .title("test-title")
            .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
            .build();
    final String accessToken = "Bearer Token";

    measure.toBuilder()
        .model(ModelType.QI_CORE_6_0_0.getValue())
        .testCases(List.of(testCase))
        .build();
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));
    when(measureRepository.addOrUpdateTestCase(anyString(), any(TestCase.class)))
        .thenReturn(measure);
    // Mocks a validation request awaiting execution.
    when(testCaseValidationService.validateResourceAsynchronously(
            measureArgumentCaptor.capture(),
            any(TestCase.class),
            eq(TestCaseServiceUtil.IMPORT),
            eq(accessToken)))
        .thenAnswer(
            invocation ->
                invocation.getArgument(1, TestCase.class).toBuilder()
                    .validationStatus(TestCaseValidationStatus.PENDING.toString())
                    .build());

    InOrder saveValidationOrder = inOrder(measureRepository, testCaseValidationService);
    TestCase output =
        testCaseService.updateTestCase(
            testCase, measure.getId(), "test-user", accessToken, TestCaseServiceUtil.IMPORT);
    verify(measureRepository, times(1))
        .addOrUpdateTestCase(targetIdArgumentCaptor.capture(), testCaseCaptor.capture());
    saveValidationOrder.verify(measureRepository).addOrUpdateTestCase(measure.getId(), testCase);
    saveValidationOrder
        .verify(testCaseValidationService)
        .validateResourceAsynchronously(
            measureArgumentCaptor.capture(),
            any(TestCase.class),
            eq(TestCaseServiceUtil.IMPORT),
            eq(accessToken));
    assertNotNull(output);
    assertEquals(TestCaseValidationStatus.PENDING.toString(), output.getValidationStatus());
  }

  @Test
  void importTestCasesReturnValidOutcomesWhenLockingSuccessful() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    LockInfo lock = LockInfo.builder().lockedId(testCase.getId()).lockedBy("test.user").build();
    when(testCaseLockService.lockTestCase(anyString(), anyString(), anyString())).thenReturn(lock);
    when(testCaseLockService.unlockTestCase(anyString(), anyString())).thenReturn(lock);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setJson(testCaseImportWithMeasureReport);

    doReturn(updatedTestCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertNotNull(testCase.getDescription());
    assertEquals(
        testCase.getDescription(), JsonUtil.getTestDescription(testCaseImportWithMeasureReport));
    assertTrue(response.get(0).isSuccessful());
  }

  @Test
  void importTestCasesReturnInvalidOutcomesWhenLockingFails() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    LockInfo lock = LockInfo.builder().lockedId(testCase.getId()).lockedBy("anotherUser").build();
    when(testCaseLockService.lockTestCase(anyString(), anyString(), anyString())).thenReturn(lock);

    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertNull(testCase.getDescription());
    assertFalse(response.get(0).isSuccessful());
    assertEquals(
        "Failed to import test case: "
            + testCase.getId()
            + ". The test case is locked by another user: "
            + lock.getLockedBy(),
        response.get(0).getMessage());
  }

  @Test
  void importTestCasesReturnValidOutcomesWhenLockedByIsSameUser() {
    measure.setTestCases(List.of(testCase));
    when(measureService.findActiveMeasureById(anyString())).thenReturn(measure);
    LockInfo lock = LockInfo.builder().lockedId(testCase.getId()).lockedBy("test.user").build();
    when(testCaseLockService.lockTestCase(anyString(), anyString(), anyString())).thenReturn(lock);
    when(testCaseLockService.unlockTestCase(anyString(), anyString())).thenReturn(lock);

    TestCase updatedTestCase = testCase;
    updatedTestCase.setJson(testCaseImportWithMeasureReport);

    doReturn(updatedTestCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testCase.getPatientId())
            .json(testCaseImportWithMeasureReport)
            .build();

    var response =
        testCaseService.importTestCases(
            List.of(testCaseImportRequest),
            measure.getId(),
            "test.user",
            "TOKEN",
            ModelType.QI_CORE.getValue());
    assertEquals(1, response.size());
    assertEquals(testCase.getPatientId(), response.get(0).getPatientId());
    assertNotNull(testCase.getDescription());
    assertEquals(
        testCase.getDescription(), JsonUtil.getTestDescription(testCaseImportWithMeasureReport));
    assertTrue(response.get(0).isSuccessful());
  }

  @Test
  void testShiftQiCoreTestCaseDatesTestCasesEmpty() {
    List<TestCase> shiftedTestCases =
        testCaseService.shiftQiCoreTestCaseDates(null, 1, "TOKEN", "measureId", "userName");
    assertTrue(CollectionUtils.isEmpty(shiftedTestCases));
  }

  @Test
  void testShiftQiCoreTestCaseDates() {
    when(testCaseLockService.lockAllTestCases(anyString(), any(List.class), anyString()))
        .thenReturn(null);
    ResponseEntity<List<TestCase>> mockClientResponse = ResponseEntity.ok(List.of(testCase));
    doReturn(mockClientResponse)
        .when(fhirServicesClient)
        .shiftTestCaseDates(anyList(), anyInt(), anyString());
    when(testCaseLockService.unlockAllTestCases(anyList(), anyString())).thenReturn(true);

    List<TestCase> shiftedTestCases =
        testCaseService.shiftQiCoreTestCaseDates(
            List.of(testCase), 1, "TOKEN", "measureId", "userName");
    assertThat(shiftedTestCases.size(), equalTo(1));
    assertTrue(shiftedTestCases.contains(testCase));
  }

  @Test
  void testShiftQiCoreTestCaseDatesThrowsLockNotObtainedException() {
    LockInfo lock = LockInfo.builder().lockedId("TESTID").lockedBy("anotherUser").build();
    when(testCaseLockService.lockAllTestCases(anyString(), any(List.class), anyString()))
        .thenReturn(List.of(lock));
    when(testCaseLockService.unlockAllTestCases(anyList(), anyString())).thenReturn(true);

    TestCase testCase2 = TestCase.builder().id("TESTID2").build();
    assertThrows(
        LockNotObtainedException.class,
        () ->
            testCaseService.shiftQiCoreTestCaseDates(
                List.of(testCase, testCase2), 1, "TOKEN", "measureId", "userName"));
  }

  @Test
  public void testGetTestCaseReturnsTestCaseWithLock() {
    testCase.setTestCaseLock(TestCaseLockInfo.builder().lockedBy("anotherUser").build());
    Measure mockMeasure =
        measure.toBuilder().testCases(Collections.singletonList(testCase)).build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(mockMeasure);
    when(testCaseLockService.findByTestCaseId(anyString()))
        .thenReturn(TestCaseLock.builder().lockedBy("anotherUser").build());
    TestCase output =
        testCaseService.getTestCase(measure.getId(), testCase.getId(), false, "TOKEN", "test-user");
    assertEquals(testCase, output);
    assertEquals("anotherUser", testCase.getTestCaseLock().getLockedBy());
  }

  @Test
  public void testGetTestCaseReturnsTestCaseNoLock() {
    Measure mockMeasure =
        measure.toBuilder().testCases(Collections.singletonList(testCase)).build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(mockMeasure);
    when(testCaseLockService.findByTestCaseId(anyString())).thenReturn(null);
    TestCase output =
        testCaseService.getTestCase(measure.getId(), testCase.getId(), false, "TOKEN", "test-user");
    assertEquals(testCase, output);
    assertNull(testCase.getTestCaseLock());
  }

  @Test
  public void testGetTestCaseReturnsTestCaseNoLockUserNameSame() {
    Measure mockMeasure =
        measure.toBuilder().testCases(Collections.singletonList(testCase)).build();
    when(measureService.findActiveMeasureById(anyString())).thenReturn(mockMeasure);
    when(testCaseLockService.findByTestCaseId(anyString()))
        .thenReturn(TestCaseLock.builder().lockedBy("test-user").build());
    TestCase output =
        testCaseService.getTestCase(measure.getId(), testCase.getId(), false, "TOKEN", "test-user");
    assertEquals(testCase, output);
    assertNull(testCase.getTestCaseLock());
  }

  @Test
  public void testUpdateTestCaseThrowsLockNotObtainedExceptionWhenTestCaseIsLocked() {
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    when(testCaseLockService.findByTestCaseId(anyString()))
        .thenReturn(TestCaseLock.builder().lockedBy("another.user").build());

    assertThrows(
        LockNotObtainedException.class,
        () -> testCaseService.updateTestCase(testCase, measure.getId(), "test.user", "TOKEN"));
  }

  @Test
  public void testUpdateTestCaseNoLock() {
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    when(testCaseLockService.findByTestCaseId(anyString())).thenReturn(null);

    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Mockito.doAnswer((args) -> args.getArgument(0))
        .when(measureRepository)
        .save(measureCaptor.capture());

    testCaseService.updateTestCase(testCase, measure.getId(), "test.user", "TOKEN");

    // Verify the measure was saved
    verify(measureRepository).save(any(Measure.class));
  }

  @Test
  public void testUpdateTestCaseSelfLock() {
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    when(testCaseLockService.findByTestCaseId(anyString()))
        .thenReturn(TestCaseLock.builder().lockedBy("test.user").build());

    when(testCaseValidationService.validateTestCaseAsResource(
            any(TestCase.class), any(ModelType.class), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(0, TestCase.class));
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Mockito.doAnswer((args) -> args.getArgument(0))
        .when(measureRepository)
        .save(measureCaptor.capture());

    testCaseService.updateTestCase(testCase, measure.getId(), "test.user", "TOKEN");

    // Verify the measure was saved
    verify(measureRepository).save(any(Measure.class));
  }

  @Test
  void testUpdateJsonWithGroupAndTitle_AllTestCasesUpdatedSuccessfully() throws Exception {
    List<TestCase> testCases = new ArrayList<>();
    TestCase testCase1 = new TestCase();
    testCase1.setId("1");
    testCase1.setJson(
        "{\"entry\": [{\"resource\": {\"resourceType\": \"Patient\", \"name\": [{\"family\": \"oldGroup\", \"given\": [\"oldTitle\"]}]}}]}");
    testCase1.setSeries("Group1");
    testCase1.setTitle("Title1");
    testCase1.setTestCaseLock(null);
    testCases.add(testCase1);

    TestCase updatedTestCase = new TestCase();
    updatedTestCase.setId("1");
    updatedTestCase.setJson(
        "{\"entry\": [{\"resource\": {\"resourceType\": \"Patient\", \"name\": [{\"family\": \"Group1\", \"given\": [\"Title1\"]}]}}]}");
    measure.toBuilder()
        .model(ModelType.QI_CORE_6_0_0.getValue())
        .testCases(List.of(testCase))
        .build();
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    when(testCaseService.updateTestCase(testCase1, "measure1", "user1", "accessToken"))
        .thenReturn(updatedTestCase);

    Map<String, Object> response =
        testCaseService.updateQiCoreJsonWithGroupAndTitle(
            testCases, "user1", "measure1", "accessToken");

    assertTrue(((List<String>) response.get("updated")).contains("Group1 - Title1"));
    assertTrue(((List<String>) response.get("failed")).isEmpty());
  }

  @Test
  void testUpdateJsonWithGroupAndTitle_TestCaseLockExists() {
    List<TestCase> testCases = new ArrayList<>();
    TestCase testCase1 = new TestCase();
    testCase1.setId("1");
    testCase1.setTestCaseLock(new TestCaseLockInfo());
    testCase1.setSeries("Group1");
    testCase1.setTitle("Title1");
    testCases.add(testCase1);

    Map<String, Object> response =
        testCaseService.updateQiCoreJsonWithGroupAndTitle(
            testCases, "user1", "measure1", "accessToken");

    assertTrue(((List<String>) response.get("failed")).contains("Group1 - Title1"));
    assertTrue(((List<String>) response.get("updated")).isEmpty());
    verify(testCaseLockService, never()).lockTestCase(anyString(), anyString(), anyString());
  }
}
