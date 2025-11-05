package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.ValidList;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.MeasureService;
import gov.cms.madie.models.measure.*;
import gov.cms.madie.models.common.Version;
import cms.gov.madie.measure.services.TestCaseService;
import cms.gov.madie.measure.services.QdmTestCaseShiftDatesService;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class TestCaseControllerTest {
  @Mock private TestCaseService testCaseService;
  @Mock private MeasureRepository repository;
  @Mock private MeasureService measureService;
  @Mock private QdmTestCaseShiftDatesService qdmTestCaseShiftDatesService;

  @Mock
  private cms.gov.madie.measure.services.TestCaseLockEnrichmentService
      testCaseLockEnrichmentService;

  @InjectMocks private TestCaseController controller;

  private TestCase testCase;
  private Measure measure;

  @BeforeEach
  public void setUp() {
    testCase = new TestCase();
    testCase.setId("TESTID");
    testCase.setName("IPPPass");
    testCase.setSeries("BloodPressure>124");
    testCase.setTitle("title");
    testCase.setCreatedBy("TestUser");
    testCase.setLastModifiedBy("TestUser2");
    testCase.setDescription("TESTCASEDESCRIPTION");
    testCase.setJson("date1");

    measure = new Measure();
    measure.setId(ObjectId.get().toString());
    measure.setMeasureSetId("IDIDID");
    measure.setMeasureName("MSR01");
    measure.setVersion(new Version(0, 0, 1));
    measure.setCreatedBy("test.user");
  }

  @Test
  void saveTestCase() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    doReturn(testCase)
        .when(testCaseService)
        .persistTestCase(any(TestCase.class), any(String.class), any(String.class), anyString());

    TestCase newTestCase = new TestCase();

    ResponseEntity<TestCase> response =
        controller.addTestCase(newTestCase, measure.getId(), "TOKEN", principal);
    assertNotNull(response.getBody());
    assertNotNull(response.getBody());
    assertEquals("TESTID", response.getBody().getId());
  }

  @Test
  void setTestCaseList() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    doReturn(Optional.of(measure)).when(repository).findById("MeasureID");

    List<TestCase> savedTestCases =
        List.of(
            TestCase.builder()
                .id("ID1")
                .title("Test1")
                .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
                .validResource(true)
                .build(),
            TestCase.builder()
                .id("ID2")
                .title("Test2")
                .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
                .validResource(true)
                .build());

    when(testCaseService.persistTestCases(anyList(), anyString(), anyString(), anyString()))
        .thenReturn(savedTestCases);

    ValidList<TestCase> testCases =
        ValidList.<TestCase>builder()
            .list(
                List.of(
                    TestCase.builder()
                        .title("Test1")
                        .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
                        .build(),
                    TestCase.builder()
                        .title("Test2")
                        .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
                        .build()))
            .build();

    ResponseEntity<List<TestCase>> output =
        controller.addTestCases(testCases, "MeasureID", "Bearer Token", principal);
    assertThat(output, is(notNullValue()));
    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.CREATED)));
    assertThat(output.getBody(), is(equalTo(savedTestCases)));
  }

  @Test
  void testAddTestCasesThrowWhenUserIsUnauthorized() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("evil.user");

    doReturn(Optional.of(measure)).when(repository).findById("MeasureID");
    doThrow(new UnauthorizedException("Measure", "MeasureID", "evil.user"))
        .when(measureService)
        .verifyAuthorization(anyString(), any(Measure.class));
    assertThrows(
        UnauthorizedException.class,
        () -> controller.addTestCases(new ValidList<>(), "MeasureID", "Bearer Token", principal));
  }

  @Test
  void testAddTestCasesThrowsWhenMeasureNotFound() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    assertThrows(
        ResourceNotFoundException.class,
        () -> controller.addTestCases(new ValidList<>(), "1234", "Bearer Token", principal));
  }

  @Test
  void getTestCases() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    doReturn(List.of(testCase))
        .when(testCaseService)
        .findTestCasesByMeasureId(any(String.class), anyString());

    ResponseEntity<List<TestCase>> response =
        controller.getTestCasesByMeasureId(measure.getId(), principal);
    assertEquals(1, Objects.requireNonNull(response.getBody()).size());
    assertEquals("IPPPass", response.getBody().get(0).getName());
    assertEquals("BloodPressure>124", response.getBody().get(0).getSeries());
  }

  @Test
  void getTestCase() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    doReturn(testCase)
        .when(testCaseService)
        .getTestCase(any(String.class), any(String.class), anyBoolean(), anyString(), anyString());
    ResponseEntity<TestCase> response =
        controller.getTestCase(principal, measure.getId(), testCase.getId(), true, "TOKEN");
    assertNotNull(response.getBody());
    assertNotNull(response.getBody());
    assertEquals("IPPPass", response.getBody().getName());
    assertEquals("BloodPressure>124", response.getBody().getSeries());
  }

  @Test
  void updateTestCase() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user2");

    doReturn(testCase)
        .when(testCaseService)
        .updateTestCase(
            any(TestCase.class), any(String.class), any(String.class), anyString(), anyString());

    ResponseEntity<TestCase> response =
        controller.updateTestCase(testCase, measure.getId(), testCase.getId(), "TOKEN", principal);
    assertNotNull(response.getBody());
    assertNotNull(response.getBody());
    assertEquals("IPPPass", response.getBody().getName());
    assertEquals("BloodPressure>124", response.getBody().getSeries());

    ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
    verify(testCaseService, times(1))
        .updateTestCase(
            any(TestCase.class), anyString(), usernameCaptor.capture(), anyString(), anyString());
    assertEquals("test.user2", usernameCaptor.getValue());
  }

  @Test
  void testSuccessfulDeleteTestCase() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    String returnOutput = "Test case deleted successfully: TC1_ID";
    doReturn(returnOutput)
        .when(testCaseService)
        .deleteTestCases(any(String.class), anyList(), any(String.class));

    List<String> testCaseIds = new ArrayList<>();
    testCaseIds.add("TC1_ID");
    ResponseEntity<String> output =
        controller.deleteTestCases("measure-id", testCaseIds, principal);

    assertThat(output.getBody(), is(equalTo("Test case deleted successfully: TC1_ID")));
    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.OK)));
  }

  @Test
  void testDeleteTestCases() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    String mockedServiceResponse = "Succesfully deleted provided test cases";
    doReturn(mockedServiceResponse)
        .when(testCaseService)
        .deleteTestCases(any(String.class), any(), any(String.class));

    List<String> testCaseIds = new ArrayList<>();
    testCaseIds.add("TC1_ID");
    ResponseEntity<String> output =
        controller.deleteTestCases("measure.id", testCaseIds, principal);

    assertEquals(mockedServiceResponse, output.getBody());
    assertEquals(HttpStatus.OK, output.getStatusCode());
  }

  @Test
  public void testGetTestCaseSeriesByMeasureIdReturnsEmptyList() {
    when(testCaseService.findTestCaseSeriesByMeasureId(anyString())).thenReturn(List.of());
    ResponseEntity<List<String>> output = controller.getTestCaseSeriesByMeasureId(measure.getId());
    assertNotNull(output.getBody());
    assertEquals(List.of(), output.getBody());
  }

  @Test
  public void testGetTestCaseSeriesByMeasureIdReturnsSeries() {
    when(testCaseService.findTestCaseSeriesByMeasureId(anyString()))
        .thenReturn(List.of("SeriesAAA", "SeriesBBB"));
    ResponseEntity<List<String>> output = controller.getTestCaseSeriesByMeasureId(measure.getId());
    assertNotNull(output.getBody());
    assertEquals(List.of("SeriesAAA", "SeriesBBB"), output.getBody());
  }

  @Test
  public void testGetTestCaseSeriesByMeasureIdBubblesUpExceptions() {
    when(testCaseService.findTestCaseSeriesByMeasureId(anyString()))
        .thenThrow(new ResourceNotFoundException("Measure", measure.getId()));
    assertThrows(
        ResourceNotFoundException.class,
        () -> controller.getTestCaseSeriesByMeasureId(measure.getId()));
  }

  @Test
  void saveTestCaseWithSanitizedDescription() {

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    doReturn(testCase)
        .when(testCaseService)
        .persistTestCase(any(TestCase.class), any(String.class), any(String.class), anyString());

    TestCase newTestCase = new TestCase();
    newTestCase.setDescription("TESTCASEDESCRIPTION<script>alert('Wufff!')</script>");

    ResponseEntity<TestCase> response =
        controller.addTestCase(newTestCase, measure.getId(), "TOKEN", principal);
    assertEquals("TESTID", response.getBody().getId());
    assertEquals("TESTCASEDESCRIPTION", response.getBody().getDescription());
  }

  @Test
  void updateTestCaseWithSanitizedDescription() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user2");

    doReturn(testCase)
        .when(testCaseService)
        .updateTestCase(
            any(TestCase.class), any(String.class), any(String.class), anyString(), anyString());

    testCase.setDescription("TESTCASEDESCRIPTION<script>alert('Wufff!')</script>");

    ResponseEntity<TestCase> response =
        controller.updateTestCase(testCase, measure.getId(), testCase.getId(), "TOKEN", principal);
    assertNotNull(response.getBody());
    assertEquals("IPPPass", response.getBody().getName());
    assertEquals("BloodPressure>124", response.getBody().getSeries());
    assertEquals("TESTCASEDESCRIPTION", response.getBody().getDescription());

    ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
    verify(testCaseService, times(1))
        .updateTestCase(
            any(TestCase.class), anyString(), usernameCaptor.capture(), anyString(), anyString());
    assertEquals("test.user2", usernameCaptor.getValue());
  }

  @Test
  void importTestCasesSuccesfullyUpdatesAllTestCases() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    UUID testPatientId = UUID.randomUUID();

    var testCaseImportOutcome =
        TestCaseImportOutcome.builder().successful(true).patientId(testPatientId).build();
    var testCaseImportRequest =
        TestCaseImportRequest.builder()
            .patientId(testPatientId)
            .json("test case import json")
            .build();

    when(testCaseService.importTestCases(any(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(List.of(testCaseImportOutcome));
    var responseEntity =
        controller.importTestCases(
            List.of(testCaseImportRequest), measure.getId(), "TOKEN", principal);
    assertEquals(1, Objects.requireNonNull(responseEntity.getBody()).size());
    assertEquals(
        testPatientId, Objects.requireNonNull(responseEntity.getBody()).get(0).getPatientId());
  }

  @Test
  void updateTestCaseNullId() {
    Principal principal = mock(Principal.class);
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controller.updateTestCase(
                TestCase.builder().build(), "testMeasureId", "testTestCaseId", "TOKEN", principal));
  }

  @Test
  void updateTestCaseIdNotMatch() {
    Principal principal = mock(Principal.class);
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controller.updateTestCase(
                TestCase.builder().id("differentId").build(),
                "testMeasureId",
                "testTestCaseId",
                "TOKEN",
                principal));
  }

  @Test
  void shiftQdmTestCaseDates() {
    Principal principal = mock(Principal.class);

    doReturn(List.of(testCase.getId()))
        .when(qdmTestCaseShiftDatesService)
        .shiftTestCaseDates(
            any(String.class), anyList(), any(Integer.class), any(String.class), any());
    ResponseEntity<List<String>> response =
        controller.shiftQdmTestCaseDates(
            measure.getId(), List.of(testCase.getId()), 1, "TOKEN", principal);

    assertNotNull(response.getBody());
    assertEquals(testCase.getId(), response.getBody().get(0));
  }

  @Test
  void shiftDatesForAllTestCasesOnQdmMeasure() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    testCase.setJson("Date2");
    TestCase testCase2 = TestCase.builder().json("Date3").build();
    doReturn(List.of(testCase, testCase2))
        .when(qdmTestCaseShiftDatesService)
        .shiftAllTestCaseDates(
            any(String.class), any(Integer.class), any(String.class), anyString());
    ResponseEntity<List<TestCase>> response =
        controller.shiftAllQdmTestCaseDates(measure.getId(), 1, "TOKEN", principal);

    assertNotNull(response.getBody());
    assertEquals(response.getBody().size(), 2);
    assertEquals("Date2", response.getBody().get(0).getJson());
    assertEquals("Date3", response.getBody().get(1).getJson());
  }

  @Test
  void shiftTestCaseDatesForQiCoreMeasure() {
    FhirMeasure fhirMeasure =
        FhirMeasure.builder()
            .id(measure.getId())
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .createdBy("test.user")
            .build();
    fhirMeasure.setTestCases(List.of(testCase));
    doReturn(fhirMeasure).when(measureService).findMeasureById(fhirMeasure.getId());

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    doReturn(testCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    doReturn(fhirMeasure.getTestCases())
        .when(testCaseService)
        .shiftQiCoreTestCaseDates(anyList(), anyInt(), anyString(), anyString(), anyString());

    ResponseEntity<List<String>> response =
        controller.shiftQiCoreTestCaseDates(
            fhirMeasure.getId(),
            List.of(fhirMeasure.getTestCases().get(0).getId()),
            1,
            "TOKEN",
            principal);
    assertThat(response.getStatusCode(), equalTo(HttpStatusCode.valueOf(200)));
    assertTrue(CollectionUtils.isEmpty(response.getBody()));
  }

  @Test
  void shiftTestCaseDatesForQiCoreMeasurePartialFailure() {
    FhirMeasure fhirMeasure =
        FhirMeasure.builder()
            .id(measure.getId())
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .createdBy("test.user")
            .build();

    TestCase testCase2 = new TestCase();
    testCase2.setId("TESTID2");
    testCase2.setName("IPPPass");
    testCase2.setTitle("title");
    testCase2.setCreatedBy("TestUser");
    testCase2.setLastModifiedBy("TestUser2");
    testCase2.setDescription("TESTCASEDESCRIPTION");
    testCase2.setJson("date2");

    fhirMeasure.setTestCases(List.of(testCase, testCase2));
    doReturn(fhirMeasure).when(measureService).findMeasureById(fhirMeasure.getId());

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    doReturn(testCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    doReturn(List.of(testCase2))
        .when(testCaseService)
        .shiftQiCoreTestCaseDates(anyList(), anyInt(), anyString(), anyString(), anyString());

    ResponseEntity<List<String>> response =
        controller.shiftQiCoreTestCaseDates(
            fhirMeasure.getId(),
            List.of(
                fhirMeasure.getTestCases().get(0).getId(),
                fhirMeasure.getTestCases().get(1).getId()),
            1,
            "TOKEN",
            principal);
    assertThat(response.getStatusCode(), equalTo(HttpStatusCode.valueOf(200)));
    assertThat(response.getBody().size(), equalTo(1));
    assertThat(response.getBody().get(0), equalTo("title"));
  }

  @Test
  void shiftAllQiCoreTestCaseDatesInvalidModelType() {
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id(measure.getId())
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .createdBy("test.user")
            .build();
    qdmMeasure.setTestCases(List.of(testCase));
    doReturn(qdmMeasure).when(measureService).findMeasureById(qdmMeasure.getId());

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    assertThrows(
        ResourceNotFoundException.class,
        () -> controller.shiftAllQiCoreTestCaseDates(qdmMeasure.getId(), 1, principal, "TOKEN"));
  }

  @Test
  void shiftAllQiCoreTestCaseDates() {
    FhirMeasure fhirMeasure =
        FhirMeasure.builder()
            .id(measure.getId())
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .createdBy("test.user")
            .build();
    fhirMeasure.setTestCases(List.of(testCase));
    doReturn(fhirMeasure).when(measureService).findMeasureById(fhirMeasure.getId());
    doReturn(fhirMeasure.getTestCases())
        .when(testCaseService)
        .findTestCasesByMeasureId(anyString(), anyString());

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    doReturn(testCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    doReturn(fhirMeasure.getTestCases())
        .when(testCaseService)
        .shiftQiCoreTestCaseDates(anyList(), anyInt(), anyString(), anyString(), anyString());

    ResponseEntity<List<String>> response =
        controller.shiftAllQiCoreTestCaseDates(fhirMeasure.getId(), 1, principal, "TOKEN");
    assertThat(response.getStatusCode(), equalTo(HttpStatusCode.valueOf(200)));
    assertTrue(CollectionUtils.isEmpty(response.getBody()));
  }

  @Test
  void shiftAllQiCoreTestCaseDatesPartialFailure() {
    FhirMeasure fhirMeasure =
        FhirMeasure.builder()
            .id(measure.getId())
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .createdBy("test.user")
            .build();
    fhirMeasure.setTestCases(
        List.of(
            testCase,
            TestCase.builder().id("7890").title("bad").series("testCase").json("").build()));
    doReturn(fhirMeasure).when(measureService).findMeasureById(fhirMeasure.getId());
    doReturn(fhirMeasure.getTestCases())
        .when(testCaseService)
        .findTestCasesByMeasureId(anyString(), anyString());

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    doReturn(testCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString(), anyString());
    doReturn(List.of(testCase))
        .when(testCaseService)
        .shiftQiCoreTestCaseDates(anyList(), anyInt(), anyString(), anyString(), anyString());

    ResponseEntity<List<String>> response =
        controller.shiftAllQiCoreTestCaseDates(fhirMeasure.getId(), 1, principal, "TOKEN");
    assertThat(response.getStatusCode(), equalTo(HttpStatusCode.valueOf(200)));
    assertTrue(CollectionUtils.isNotEmpty(response.getBody()));
    assertThat(response.getBody().size(), equalTo(1));
    assertThat(response.getBody().get(0), equalTo("testCase - bad"));
  }

  @Test
  void shiftQiCoreTestCaseDatesInvalidModelType() {
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id(measure.getId())
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .createdBy("test.user")
            .build();
    qdmMeasure.setTestCases(List.of(testCase));
    doReturn(qdmMeasure).when(measureService).findMeasureById(qdmMeasure.getId());

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controller.shiftQiCoreTestCaseDates(
                qdmMeasure.getId(), List.of(testCase.getId()), 1, "TOKEN", principal));
  }

  @Test
  void shiftQiCoreTestCaseDatesNoTestCaseFound() {
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id(measure.getId())
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .createdBy("test.user")
            .build();
    doReturn(qdmMeasure).when(measureService).findMeasureById(qdmMeasure.getId());

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controller.shiftQiCoreTestCaseDates(
                qdmMeasure.getId(), List.of(testCase.getId()), 1, "TOKEN", principal));
  }
}
