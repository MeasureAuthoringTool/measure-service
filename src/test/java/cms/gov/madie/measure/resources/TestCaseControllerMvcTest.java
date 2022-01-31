package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.models.TestCase;
import cms.gov.madie.measure.services.TestCaseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({TestCaseController.class})
public class TestCaseControllerMvcTest {

  @MockBean private TestCaseService testCaseService;
  @Autowired private MockMvc mockMvc;
  @Captor ArgumentCaptor<TestCase> testCaseCaptor;
  @Captor ArgumentCaptor<String> measureIdCaptor;
  @Captor ArgumentCaptor<String> testCaseIdCaptor;

  private TestCase testCase;
  private static final String TEST_ID = "TESTID";
  private static final String TEST_USER = "TestUser";
  private static final String TEST_USER_2 = "TestUser2";
  private static final String TEST_NAME = "TestName";
  private static final String TEST_DESCRIPTION = "Test Description";
  private static final String TEST_USER_ID = "test-okta-user-id-123";

  @BeforeEach
  public void setUp() {
    testCase = new TestCase();
    testCase.setId(TEST_ID);
    testCase.setDescription(TEST_DESCRIPTION);
    testCase.setCreatedBy(TEST_USER);
    testCase.setLastModifiedBy(TEST_USER_2);
    testCase.setName(TEST_NAME);
  }

  @Test
  public void testNewTestCase() throws Exception {
    when(testCaseService.persistTestCase(any(TestCase.class), any(String.class)))
        .thenReturn(testCase);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/measures/1234/test-cases")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(asJsonString(testCase))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(TEST_ID))
        .andExpect(jsonPath("$.createdBy").value(TEST_USER))
        .andExpect(jsonPath("$.lastModifiedBy").value(TEST_USER_2))
        .andExpect(jsonPath("$.name").value(TEST_NAME));
    verify(testCaseService, times(1))
        .persistTestCase(testCaseCaptor.capture(), measureIdCaptor.capture());
    TestCase persistedTestCase = testCaseCaptor.getValue();
    assertEquals(TEST_DESCRIPTION, persistedTestCase.getDescription());
  }

  @Test
  public void testGetTestCases() throws Exception {
    when(testCaseService.findTestCasesByMeasureId(any(String.class))).thenReturn(List.of(testCase));

    mockMvc
        .perform(
            get("/measures/1234/test-cases")
                .with(user(TEST_USER_ID))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    "[{\"id\":\"TESTID\",\"name\":\"TestName\",\"series\":null,"
                        + "\"description\":\"Test Description\",\"createdAt\":null,"
                        + "\"createdBy\":\"TestUser\",\"lastModifiedAt\":null,"
                        + "\"lastModifiedBy\":\"TestUser2\"}]"));
    verify(testCaseService, times(1)).findTestCasesByMeasureId(measureIdCaptor.capture());
    String measureId = measureIdCaptor.getValue();
    assertEquals("1234", measureId);
  }

  @Test
  public void testGetTestCasesWhenMeasureWithMeasureIdMissing() throws Exception {
    when(testCaseService.findTestCasesByMeasureId(any(String.class)))
        .thenThrow(new ResourceNotFoundException("Measure", "1234"));

    mockMvc
        .perform(
            get("/measures/1234/test-cases")
                .with(user(TEST_USER_ID))
                .with(csrf()))
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
    when(testCaseService.getTestCase(any(String.class), any(String.class))).thenReturn(testCase);

    mockMvc
        .perform(
            get("/measures/1234/test-cases/TESTID")
                .with(user(TEST_USER_ID))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    "{\"id\":\"TESTID\",\"name\":\"TestName\",\"series\":null,"
                        + "\"description\":\"Test Description\",\"createdAt\":null,"
                        + "\"createdBy\":\"TestUser\",\"lastModifiedAt\":null,"
                        + "\"lastModifiedBy\":\"TestUser2\"}"));
    verify(testCaseService, times(1))
        .getTestCase(measureIdCaptor.capture(), testCaseIdCaptor.capture());
    assertEquals("1234", measureIdCaptor.getValue());
    assertEquals("TESTID", testCaseIdCaptor.getValue());
  }

  @Test
  public void updateTestCase() throws Exception {
    String modifiedDescription = "New Description";
    testCase.setDescription(modifiedDescription);
    when(testCaseService.updateTestCase(any(TestCase.class), any(String.class)))
        .thenReturn(testCase);

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/measures/1234/test-cases/TESTID")
                .content(
                    "{\"id\":\"TESTID\",\"name\":\"TestName\",\"series\":null,"
                        + "\"description\":\""
                        + modifiedDescription
                        + "\",\"createdAt\":null,"
                        + "\"createdBy\":\"TestUser\",\"lastModifiedAt\":null,"
                        + "\"lastModifiedBy\":\"TestUser2\"}")
                .contentType(MediaType.APPLICATION_JSON)
                .with(user(TEST_USER_ID))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    "{\"id\":\"TESTID\",\"name\":\"TestName\",\"series\":null,"
                        + "\"description\":\""
                        + modifiedDescription
                        + "\",\"createdAt\":null,"
                        + "\"createdBy\":\"TestUser\",\"lastModifiedAt\":null,"
                        + "\"lastModifiedBy\":\"TestUser2\"}"));
    verify(testCaseService, times(1))
        .updateTestCase(testCaseCaptor.capture(), measureIdCaptor.capture());
    assertEquals("1234", measureIdCaptor.getValue());
    assertEquals("TESTID", testCaseCaptor.getValue().getId());
    assertEquals(modifiedDescription, testCaseCaptor.getValue().getDescription());
  }

  @Test
  public void testGetTestCaseSeriesByMeasureIdThrows404() throws Exception {
    final String measureId = "TESTID";
    when(testCaseService.findTestCaseSeriesByMeasureId(anyString()))
            .thenThrow(new ResourceNotFoundException("Measure", measureId));
    mockMvc
        .perform(
            get("/measures/1234/test-cases/series")
                .with(user(TEST_USER_ID))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  public void testGetTestCaseSeriesByMeasureIdReturnsEmptyList() throws Exception {
    when(testCaseService.findTestCaseSeriesByMeasureId(anyString()))
            .thenReturn(List.of());
    mockMvc
        .perform(
            get("/measures/1234/test-cases/series")
                .with(user(TEST_USER_ID))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string("[]"));
  }

  @Test
  public void testGetTestCaseSeriesByMeasureIdReturnsSeries() throws Exception {
    when(testCaseService.findTestCaseSeriesByMeasureId(anyString()))
            .thenReturn(List.of("SeriesAAA", "SeriesBBB"));
    mockMvc
        .perform(
            get("/measures/1234/test-cases/series")
                .with(user(TEST_USER_ID))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string("[\"SeriesAAA\",\"SeriesBBB\"]"));
  }
}
