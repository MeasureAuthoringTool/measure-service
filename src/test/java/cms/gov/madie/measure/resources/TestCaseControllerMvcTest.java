package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.ValidList;
import cms.gov.madie.measure.exceptions.DuplicateTestCaseNameException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.MeasureService;
import cms.gov.madie.measure.services.QdmTestCaseShiftDatesService;
import cms.gov.madie.measure.services.TestCaseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.measure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({TestCaseController.class})
@ActiveProfiles("test")
public class TestCaseControllerMvcTest {

  @MockitoBean private TestCaseService testCaseService;
  @MockitoBean private MeasureRepository repository;
  @Autowired private MockMvc mockMvc;
  @MockitoBean private MeasureService measureService;
  @Captor ArgumentCaptor<TestCase> testCaseCaptor;
  @Captor ArgumentCaptor<String> measureIdCaptor;
  @Captor ArgumentCaptor<String> testCaseIdCaptor;
  @Captor ArgumentCaptor<String> usernameCaptor;
  @MockitoBean private QdmTestCaseShiftDatesService qdmTestCaseShiftDatesService;

  private TestCase testCase;
  private static final String TEST_ID = "TESTID";
  private static final String TEST_USER = "TestUser";
  private static final String TEST_USER_2 = "TestUser2";
  private static final String TEST_NAME = "TestName";
  private static final String TEST_TITLE = "TestTitle";
  private static final String TEST_DESCRIPTION = "Test Description";
  private static final String TEST_USER_ID = "test-okta-user-id-123";
  private static final String TEST_JSON = "{\"test\":\"test\"}";
  private static final String TEXT_251_CHARACTORS =
      "abcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyz";

  @BeforeEach
  public void setUp() {
    testCase = new TestCase();
    testCase.setId(TEST_ID);
    testCase.setDescription(TEST_DESCRIPTION);
    testCase.setCreatedBy(TEST_USER);
    testCase.setLastModifiedBy(TEST_USER_2);
    testCase.setName(TEST_NAME);
    testCase.setTitle(TEST_TITLE);
    testCase.setJson(TEST_JSON);
    testCase.setValidResource(false);
  }

  @Test
  public void testNewTestCase() throws Exception {
    when(testCaseService.persistTestCase(
            any(TestCase.class), any(String.class), any(String.class), anyString()))
        .thenReturn(testCase);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/measures/1234/test-cases")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(asJsonString(testCase))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(TEST_ID))
        .andExpect(jsonPath("$.createdBy").value(TEST_USER))
        .andExpect(jsonPath("$.lastModifiedBy").value(TEST_USER_2))
        .andExpect(jsonPath("$.name").value(TEST_NAME))
        .andExpect(jsonPath("$.json").value(TEST_JSON));
    verify(testCaseService, times(1))
        .persistTestCase(
            testCaseCaptor.capture(),
            measureIdCaptor.capture(),
            usernameCaptor.capture(),
            anyString());
    TestCase persistedTestCase = testCaseCaptor.getValue();
    assertEquals(TEST_DESCRIPTION, persistedTestCase.getDescription());
    assertEquals(TEST_JSON, persistedTestCase.getJson());
    assertEquals(TEST_USER_ID, usernameCaptor.getValue());
  }

  @Test
  public void testAddTestCasesTitle251Characters() throws Exception {
    testCase.setTitle(TEXT_251_CHARACTORS);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/measures/1234/test-cases/list")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .content(asJsonString(List.of(testCase)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.validationErrors.['list[0].title']")
                    .value("Test Case Title can not be more than 250 characters."))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Test Case Title can not be more than 250 characters."));
  }

  @Test
  public void testAddTestCasesNotEmpty() throws Exception {
    testCase.setTitle(TEXT_251_CHARACTORS);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/measures/1234/test-cases/list")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .content(asJsonString(List.of()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.['list']").value("must not be empty"))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("must not be empty"));
  }

  @Test
  public void testAddTestCases() throws Exception {
    doReturn(Optional.of(new Measure().toBuilder().createdBy(TEST_USER_ID).build()))
        .when(repository)
        .findById("1234");
    ArgumentCaptor<List> testCaseListCaptor = ArgumentCaptor.forClass(List.class);
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
                .json("{\"resourceType\": \"RANDOM\", \"type\": \"BAD\"}")
                .validResource(false)
                .build());

    when(testCaseService.persistTestCases(anyList(), anyString(), anyString(), anyString()))
        .thenReturn(savedTestCases);

    List<TestCase> testCases =
        List.of(
            TestCase.builder()
                .title("Test1")
                .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
                .build(),
            TestCase.builder()
                .title("Test2")
                .json("{\"resourceType\": \"RANDOM\", \"type\": \"BAD\"}")
                .build());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/measures/1234/test-cases/list")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(asJsonString(testCases))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].id").value("ID1"))
        .andExpect(jsonPath("$[0].title").value("Test1"))
        .andExpect(jsonPath("$[0].validResource").value(Boolean.TRUE))
        .andExpect(jsonPath("$[1].id").value("ID2"))
        .andExpect(jsonPath("$[1].title").value("Test2"))
        .andExpect(jsonPath("$[1].validResource").value(Boolean.FALSE));
    verify(testCaseService, times(1))
        .persistTestCases(
            testCaseListCaptor.capture(),
            measureIdCaptor.capture(),
            usernameCaptor.capture(),
            anyString());
    assertThat(testCaseListCaptor.getValue(), is(equalTo(new ValidList<>(testCases))));
    assertThat(usernameCaptor.getValue(), is(equalTo(TEST_USER_ID)));
  }

  @Test
  public void testGetTestCases() throws Exception {
    when(testCaseService.findTestCasesByMeasureId(any(String.class))).thenReturn(List.of(testCase));

    mockMvc
        .perform(get("/measures/1234/test-cases").with(user(TEST_USER_ID)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    "[{\"id\":\"TESTID\",\"caseNumber\":null,\"name\":\"TestName\",\"title\":\"TestTitle\",\"series\":null,"
                        + "\"description\":\"Test Description\",\"createdAt\":null,"
                        + "\"createdBy\":\"TestUser\",\"lastModifiedAt\":null,"
                        + "\"lastModifiedBy\":\"TestUser2\","
                        + "\"validResource\":false,"
                        + "\"createdBeforeVersioning\":false,"
                        + "\"json\":\"{\\\"test\\\":\\\"test\\\"}\",\"patientId\":null,\"hapiOperationOutcome\":null,"
                        + "\"groupPopulations\":null,"
                        + "\"validationStatus\":null,"
                        + "\"validationTaskId\":null"
                        + "}]"));
    verify(testCaseService, times(1)).findTestCasesByMeasureId(measureIdCaptor.capture());
    String measureId = measureIdCaptor.getValue();
    assertEquals("1234", measureId);
  }

  @Test
  public void testGetTestCasesWhenMeasureWithMeasureIdMissing() throws Exception {
    when(testCaseService.findTestCasesByMeasureId(any(String.class)))
        .thenThrow(new ResourceNotFoundException("Measure", "1234"));

    mockMvc
        .perform(get("/measures/1234/test-cases").with(user(TEST_USER_ID)).with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Could not find Measure with id: 1234"));
    verify(testCaseService, times(1)).findTestCasesByMeasureId(measureIdCaptor.capture());
    String measureId = measureIdCaptor.getValue();
    assertEquals("1234", measureId);
  }

  private String asJsonString(final Object obj) throws JsonProcessingException {
    return new ObjectMapper().writeValueAsString(obj);
  }

  @Test
  public void getTestCase() throws Exception {
    when(testCaseService.getTestCase(
            any(String.class), any(String.class), anyBoolean(), anyString()))
        .thenReturn(testCase, null);

    mockMvc
        .perform(
            get("/measures/1234/test-cases/TESTID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    "{\"id\":\"TESTID\",\"caseNumber\":null,\"name\":\"TestName\",\"title\":\"TestTitle\",\"series\":null,"
                        + "\"description\":\"Test Description\",\"createdAt\":null,"
                        + "\"createdBy\":\"TestUser\",\"lastModifiedAt\":null,"
                        + "\"lastModifiedBy\":\"TestUser2\","
                        + "\"validResource\":false,"
                        + "\"createdBeforeVersioning\":false,"
                        + "\"json\":\"{\\\"test\\\":\\\"test\\\"}\",\"patientId\":null,\"hapiOperationOutcome\":null,"
                        + "\"groupPopulations\":null,"
                        + "\"validationStatus\":null,"
                        + "\"validationTaskId\":null"
                        + "}"));
    verify(testCaseService, times(1))
        .getTestCase(
            measureIdCaptor.capture(), testCaseIdCaptor.capture(), anyBoolean(), anyString());
    assertEquals("1234", measureIdCaptor.getValue());
    assertEquals("TESTID", testCaseIdCaptor.getValue());
  }

  @Test
  public void updateTestCase() throws Exception {
    String modifiedDescription = "New Description";
    testCase.setDescription(modifiedDescription);
    testCase.setJson("{\"new\":\"json\"}");
    when(testCaseService.updateTestCase(
            any(TestCase.class), any(String.class), any(String.class), anyString(), anyString()))
        .thenReturn(testCase);

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/measures/1234/test-cases/TESTID")
                .content(
                    "{\"id\":\"TESTID\",\"name\":\"TestName\",\"title\":\"TestTitle\",\"series\":null,"
                        + "\"description\":\""
                        + modifiedDescription
                        + "\",\"createdAt\":null,"
                        + "\"createdBy\":\"TestUser\",\"lastModifiedAt\":null,"
                        + "\"lastModifiedBy\":\"TestUser2\","
                        + "\"json\":\"{\\\"new\\\":\\\"json\\\"}\"}")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "test-okta")
                .with(user(TEST_USER_ID))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    "{\"id\":\"TESTID\",\"caseNumber\":null,\"name\":\"TestName\",\"title\":\"TestTitle\",\"series\":null,"
                        + "\"description\":\""
                        + modifiedDescription
                        + "\",\"createdAt\":null,"
                        + "\"createdBy\":\"TestUser\",\"lastModifiedAt\":null,"
                        + "\"lastModifiedBy\":\"TestUser2\","
                        + "\"validResource\":false,"
                        + "\"createdBeforeVersioning\":false,"
                        + "\"json\":\"{\\\"new\\\":\\\"json\\\"}\",\"patientId\":null,\"hapiOperationOutcome\":null,"
                        + "\"groupPopulations\":null,"
                        + "\"validationStatus\":null,"
                        + "\"validationTaskId\":null"
                        + "}"));
    verify(testCaseService, times(1))
        .updateTestCase(
            testCaseCaptor.capture(),
            measureIdCaptor.capture(),
            usernameCaptor.capture(),
            anyString(),
            anyString());
    assertEquals("1234", measureIdCaptor.getValue());
    assertEquals("TESTID", testCaseCaptor.getValue().getId());
    assertEquals(modifiedDescription, testCaseCaptor.getValue().getDescription());
    assertEquals(TEST_USER_ID, usernameCaptor.getValue());
  }

  @Test
  public void testGetTestCaseSeriesByMeasureIdThrows404() throws Exception {
    when(testCaseService.findTestCaseSeriesByMeasureId(anyString()))
        .thenThrow(new ResourceNotFoundException("Measure", "1234"));
    mockMvc
        .perform(get("/measures/1234/test-cases/series").with(user(TEST_USER_ID)).with(csrf()))
        .andExpect(status().isNotFound());
    verify(testCaseService, times(1)).findTestCaseSeriesByMeasureId(measureIdCaptor.capture());
    assertEquals("1234", measureIdCaptor.getValue());
  }

  @Test
  public void testGetTestCaseSeriesByMeasureIdReturnsEmptyList() throws Exception {
    when(testCaseService.findTestCaseSeriesByMeasureId(anyString())).thenReturn(List.of());
    mockMvc
        .perform(get("/measures/1234/test-cases/series").with(user(TEST_USER_ID)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string("[]"));
    verify(testCaseService, times(1)).findTestCaseSeriesByMeasureId(measureIdCaptor.capture());
    assertEquals("1234", measureIdCaptor.getValue());
  }

  @Test
  public void testAddListThrowsResourceNotFoundException() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/measures/1234/test-cases/list")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .content(asJsonString(List.of(testCase)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Could not find Measure with id: 1234"));
  }

  @Test
  public void testAddListThrowsUserUnauthorized() throws Exception {
    doReturn(Optional.of(Measure.builder().createdBy("good.user").id("1234").build()))
        .when(repository)
        .findById("1234");
    doThrow(new UnauthorizedException("Measure", "1234", TEST_USER_ID))
        .when(measureService)
        .verifyAuthorization(anyString(), any(Measure.class));
    MvcResult result =
        mockMvc
            .perform(
                post("/measures/1234/test-cases/list")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .content(asJsonString(List.of(testCase)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(
        response.contains("User " + TEST_USER_ID + " is not authorized for Measure with ID 1234"));
  }

  @Test
  public void testGetTestCaseSeriesByMeasureIdReturnsSeries() throws Exception {
    when(testCaseService.findTestCaseSeriesByMeasureId(anyString()))
        .thenReturn(List.of("SeriesAAA", "SeriesBBB"));
    mockMvc
        .perform(get("/measures/1234/test-cases/series").with(user(TEST_USER_ID)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string("[\"SeriesAAA\",\"SeriesBBB\"]"));
    verify(testCaseService, times(1)).findTestCaseSeriesByMeasureId(measureIdCaptor.capture());
    assertEquals("1234", measureIdCaptor.getValue());
  }

  @Test
  public void testNewTestCaseDescription251Characters() throws Exception {
    testCase.setDescription(TEXT_251_CHARACTORS);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/measures/1234/test-cases")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .content(asJsonString(testCase))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.validationErrors.description")
                    .value("Test Case Description can not be more than 250 characters."))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Test Case Description can not be more than 250 characters."));
  }

  @Test
  public void testNewTestCaseTitle251Characters() throws Exception {
    testCase.setTitle(TEXT_251_CHARACTORS);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/measures/1234/test-cases")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .content(asJsonString(testCase))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.validationErrors.title")
                    .value("Test Case Title can not be more than 250 characters."))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Test Case Title can not be more than 250 characters."));
  }

  @Test
  public void testNewTestCaseTitleRequiredNull() throws Exception {
    testCase.setTitle(null);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/measures/1234/test-cases")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .content(asJsonString(testCase))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.title").value("Test Case Title is required."))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Test Case Title is required."));
  }

  @Test
  public void testNewTestCaseTitleNotUnique() throws Exception {
    when(testCaseService.persistTestCase(
            any(TestCase.class), any(String.class), any(String.class), anyString()))
        .thenThrow(new DuplicateTestCaseNameException());

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/measures/1234/test-cases")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .content(asJsonString(testCase))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "The Test Case Group and Title combination is not unique. "
                            + "The combination must be unique (case insensitive, spaces ignored) across all test cases associated with the measure."))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("must be unique"));
  }

  @Test
  public void testNewTestCaseTitleRequiredBlank() throws Exception {
    testCase.setTitle("");

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/measures/1234/test-cases")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .content(asJsonString(testCase))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.title").value("Test Case Title is required."))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Test Case Title is required."));
  }

  @Test
  public void testNewTestCaseSeries251Characters() throws Exception {
    testCase.setSeries(TEXT_251_CHARACTORS);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/measures/1234/test-cases")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .content(asJsonString(testCase))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.validationErrors.series")
                    .value("Test Case Series can not be more than 250 characters."))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Test Case Series can not be more than 250 characters."));
  }

  @Test
  public void testUpdateTestCaseDescription251Characters() throws Exception {
    testCase.setDescription(TEXT_251_CHARACTORS);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/measures/1234/test-cases/TESTID")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .content(asJsonString(testCase))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.validationErrors.description")
                    .value("Test Case Description can not be more than 250 characters."))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Test Case Description can not be more than 250 characters."));
  }

  @Test
  public void testUpdateTestCaseTitle251Characters() throws Exception {
    testCase.setTitle(TEXT_251_CHARACTORS);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/measures/1234/test-cases/TESTID")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .content(asJsonString(testCase))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.validationErrors.title")
                    .value("Test Case Title can not be more than 250 characters."))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Test Case Title can not be more than 250 characters."));
  }

  @Test
  public void testUpdateTestCaseTitleRequiredNull() throws Exception {
    testCase.setTitle(null);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/measures/1234/test-cases/TESTID")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .content(asJsonString(testCase))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.title").value("Test Case Title is required."))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Test Case Title is required."));
  }

  @Test
  public void testUpdateTestCaseTitleRequiredBlank() throws Exception {
    testCase.setTitle("");

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/measures/1234/test-cases/TESTID")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .content(asJsonString(testCase))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.title").value("Test Case Title is required."))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Test Case Title is required."));
  }

  @Test
  public void testNewTestCaseAcceptsBooleanExpectedValues() throws Exception {
    testCase.setTitle("TC1");

    testCase.setGroupPopulations(
        List.of(
            TestCaseGroupPopulation.builder()
                .scoring(MeasureScoring.COHORT.toString())
                .populationBasis("boolean")
                .groupId("G123")
                .populationValues(
                    List.of(
                        TestCasePopulationValue.builder()
                            .name(PopulationType.INITIAL_POPULATION)
                            .expected(Boolean.TRUE)
                            .build()))
                .build()));

    when(testCaseService.persistTestCase(
            any(TestCase.class), any(String.class), any(String.class), anyString()))
        .thenReturn(testCase);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/measures/1234/test-cases")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(asJsonString(testCase))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();
    verify(testCaseService, times(1))
        .persistTestCase(
            testCaseCaptor.capture(),
            measureIdCaptor.capture(),
            usernameCaptor.capture(),
            anyString());
    TestCase persistedTestCase = testCaseCaptor.getValue();
    assertEquals(TEST_DESCRIPTION, persistedTestCase.getDescription());
    assertEquals(TEST_JSON, persistedTestCase.getJson());
    assertEquals(TEST_USER_ID, usernameCaptor.getValue());
  }

  @Test
  public void testNewTestCaseAcceptsStringExpectedValues() throws Exception {
    testCase.setTitle("TC1");

    testCase.setGroupPopulations(
        List.of(
            TestCaseGroupPopulation.builder()
                .scoring(MeasureScoring.COHORT.toString())
                .populationBasis("Encounter")
                .groupId("G123")
                .populationValues(
                    List.of(
                        TestCasePopulationValue.builder()
                            .name(PopulationType.INITIAL_POPULATION)
                            .expected("3")
                            .build()))
                .build()));

    when(testCaseService.persistTestCase(
            any(TestCase.class), any(String.class), any(String.class), anyString()))
        .thenReturn(testCase);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/measures/1234/test-cases")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(asJsonString(testCase))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();
    verify(testCaseService, times(1))
        .persistTestCase(
            testCaseCaptor.capture(),
            measureIdCaptor.capture(),
            usernameCaptor.capture(),
            anyString());
    TestCase persistedTestCase = testCaseCaptor.getValue();
    assertEquals(TEST_DESCRIPTION, persistedTestCase.getDescription());
    assertEquals(TEST_JSON, persistedTestCase.getJson());
    assertEquals(TEST_USER_ID, usernameCaptor.getValue());
  }

  @Test
  public void testUpdateTestCaseSeries251Characters() throws Exception {
    testCase.setSeries(TEXT_251_CHARACTORS);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/measures/1234/test-cases/TESTID")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .content(asJsonString(testCase))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.validationErrors.series")
                    .value("Test Case Series can not be more than 250 characters."))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Test Case Series can not be more than 250 characters."));
  }

  //  Should be enabled after testCasePopulationValidations are updated
  //  @Test
  public void testUpdateTestCaseReturnsBadRequestForMismatchExpectedValuesAgainstPopulationBasis()
      throws Exception {
    String modifiedDescription = "New Description";
    testCase.setDescription(modifiedDescription);
    testCase.setJson("{\"new\":\"json\"}");
    when(testCaseService.updateTestCase(
            any(TestCase.class), any(String.class), any(String.class), anyString(), anyString()))
        .thenReturn(testCase);

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/measures/1234/test-cases/TESTID")
                .content(
                    "{\"id\":\"TESTID\",\"name\":\"TestName\",\"title\":\"TestTitle\",\"series\":null,"
                        + "\"description\":\""
                        + modifiedDescription
                        + "\",\"createdAt\":null,"
                        + "\"createdBy\":\"TestUser\",\"lastModifiedAt\":null,"
                        + "\"lastModifiedBy\":\"TestUser2\","
                        + "\"groupPopulations\": ["
                        + "{"
                        + "  \"groupId\": \"631f64812c5b7a4c2554b1c4\","
                        + "  \"scoring\": \"Cohort\","
                        + "  \"populationBasis\": \"Encounter\","
                        + "  \"populationValues\": ["
                        + "    {"
                        + "      \"id\": null,"
                        + "      \"name\": \"initialPopulation\","
                        + "      \"expected\": \"BAD\""
                        + "    }"
                        + "  ]"
                        + "}"
                        + "],"
                        + "\"json\":\"{\\\"new\\\":\\\"json\\\"}\"}")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "test-okta")
                .with(user(TEST_USER_ID))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    verify(testCaseService, never())
        .updateTestCase(any(TestCase.class), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  public void testDeleteTestCases() throws Exception {
    List<String> testCaseIds = List.of("testCaseId1", "testCaseId1");
    when(testCaseService.deleteTestCases(anyString(), any(), any(String.class)))
        .thenReturn("mock text");

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.delete("/measures/1234/test-cases")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .content(asJsonString(testCaseIds))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string("mock text"))
            .andReturn();

    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("mock text"));
  }
}
