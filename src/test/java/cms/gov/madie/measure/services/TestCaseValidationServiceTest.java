package cms.gov.madie.measure.services;

import cms.gov.madie.measure.repositories.MeasureRepository;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TestCaseValidationServiceTest {

  @Spy private ObjectMapper mapper;
  @Mock private FhirServicesClient fhirServicesClient;
  @Mock private MeasureRepository measureRepository;
  @Mock private ThreadPoolTaskExecutor validationExecutor;

  @InjectMocks private TestCaseValidationService testCaseValidationService;

  private Measure measure;
  private TestCase testCase;
  private ResponseEntity<HapiOperationOutcome> hapiValidOutcome;

  @BeforeEach
  void setUp() {
    testCase =
        TestCase.builder()
            .id("testCaseId")
            .title("testCaseTitle")
            .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
            .build();

    List<TestCase> testCases = new ArrayList<>();
    testCases.add(testCase);

    measure =
        Measure.builder()
            .id("measureId")
            .model(ModelType.QI_CORE_6_0_0.toString())
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .ecqmTitle("testTitle")
            .version(new Version(0, 0, 1))
            .testCases(testCases)
            .build();

    hapiValidOutcome =
        ResponseEntity.ok(
            HapiOperationOutcome.builder()
                .code(200)
                .successful(true)
                .outcomeResponse(
                    "{\n"
                        + "        \"resourceType\": \"OperationOutcome\",\n"
                        + "        \"issue\": [\n"
                        + "            {\n"
                        + "                \"severity\": \"information\",\n"
                        + "                \"code\": \"informational\",\n"
                        + "                \"diagnostics\": \"No issues detected during validation\"\n"
                        + "            }\n"
                        + "        ]\n"
                        + "    }\n")
                .build());
  }

  @Test
  public void testAsyncValidationStu6UpdatesValidationStatus() {
    Measure validingTestCaseMeasure =
        measure.toBuilder()
            .testCases(
                List.of(
                    testCase.toBuilder()
                        .testCaseValidationStatus(TestCaseValidationStatus.PENDING)
                        .build()))
            .build();

    when(measureRepository.findAndUpdateValidationStatus(
            anyString(), anyString(), any(TestCaseValidationStatus.class)))
        .thenReturn(validingTestCaseMeasure);

    TestCase output =
        testCaseValidationService.validateResourceAsynchronously(measure, testCase, "Bearer Token");

    assertEquals(TestCaseValidationStatus.PENDING, output.getTestCaseValidationStatus());
  }

  @Test
  public void testValidationUpdatesValidationStatus() {
    Measure validingTestCaseMeasure =
        measure.toBuilder()
            .testCases(
                List.of(
                    testCase.toBuilder()
                        .testCaseValidationStatus(TestCaseValidationStatus.VALIDATING)
                        .build()))
            .build();

    when(measureRepository.findAndUpdateValidationStatus(
            anyString(), anyString(), any(TestCaseValidationStatus.class)))
        .thenReturn(validingTestCaseMeasure);

    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenReturn(hapiValidOutcome);

    TestCase validatedTestCase =
        testCaseValidationService.validate(
            UUID.randomUUID(), measure.getId(), testCase, ModelType.QI_CORE_6_0_0, "Bearer Token");

    assertThat(
        validatedTestCase.getTestCaseValidationStatus(),
        equalTo(TestCaseValidationStatus.VALIDATING));
  }

  @Test
  public void testValidateTestCaseAsResource() {
    TestCase testCase =
        TestCase.builder()
            .id("TestID")
            .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
            .build();
    final String accessToken = "Bearer Token";

    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenReturn(
            ResponseEntity.ok(HapiOperationOutcome.builder().code(200).successful(true).build()));

    TestCase output =
        testCaseValidationService.validateTestCaseAsResource(
            testCase, ModelType.QI_CORE, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.getJson(), is(notNullValue()));
    assertThat(output.getHapiOperationOutcome(), is(notNullValue()));
    assertThat(output.getHapiOperationOutcome().getCode(), is(equalTo(200)));
  }

  @Test
  public void testValidateTestCaseAsResourceWithHapiError() {
    TestCase testCase =
        TestCase.builder()
            .createdBy("Nobody")
            .createdAt(Instant.now())
            .title("UpdatedTitle")
            .series("UpdatedSeries")
            .json("{\n  \"resourceType\" : \"Patient\"\n}")
            .build();

    doThrow(new RuntimeException())
        .when(fhirServicesClient)
        .validateBundle(anyString(), any(ModelType.class), anyString());

    TestCase output =
        testCaseValidationService.validateTestCaseAsResource(
            testCase, ModelType.QI_CORE, "Bearer Token");
    assertThat(output, is(notNullValue()));
    assertThat(output.getHapiOperationOutcome(), is(notNullValue()));
    assertThat(output.getHapiOperationOutcome().getCode(), is(equalTo(500)));
    assertThat(
        output.getHapiOperationOutcome().getMessage(),
        is(equalTo("An unknown exception occurred while validating the test case JSON.")));
  }

  @Test
  public void testValidateTestCaseAsResourceForQDM() {
    final String qdmJson = "{\n \"qdmVersion\": \"5.6\",\n \"dataElements\": []\n }";
    TestCase testCase = TestCase.builder().id("TestID").json(qdmJson).build();
    final String accessToken = "Bearer Token";
    TestCase output =
        testCaseValidationService.validateTestCaseAsResource(
            testCase, ModelType.QDM_5_6, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.getJson(), is(notNullValue()));
    assertThat(output.isValidResource(), is(true));
  }

  @Test
  public void testValidateTestCaseAsResourceMalformedJsonForQDM() {
    final String qdmJson = "{\n BADTHINGHERE \"qdmVersion\": \"5.6\",\n \"dataElements\": []\n }";
    TestCase testCase = TestCase.builder().id("TestID").json(qdmJson).build();
    final String accessToken = "Bearer Token";
    TestCase output =
        testCaseValidationService.validateTestCaseAsResource(
            testCase, ModelType.QDM_5_6, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.getJson(), is(notNullValue()));
    assertThat(output.isValidResource(), is(false));
  }

  @Test
  public void testValidateTestCaseAsResourceWhenJsonIsNull() {
    final String accessToken = "Bearer Token";
    TestCase testCase = TestCase.builder().id("TestID").build();
    TestCase output =
        testCaseValidationService.validateTestCaseAsResource(
            testCase, ModelType.QDM_5_6, accessToken);
    assertEquals(testCase, output);
  }

  @Test
  public void testValidateTestCaseAsResourceHandlesNullTestCase() {
    TestCase testCase = null;
    final String accessToken = "Bearer Token";

    TestCase output =
        testCaseValidationService.validateTestCaseAsResource(
            testCase, ModelType.QI_CORE, accessToken);
    assertThat(output, is(nullValue()));
  }

  @Test
  public void testValidateTestCaseJsonHandlesNullTestCase() {
    HapiOperationOutcome output =
        testCaseValidationService.validateTestCaseJson(null, ModelType.QI_CORE, "TOKEN");
    assertThat(output, is(nullValue()));
  }

  @Test
  public void testValidateTestCaseJsonHandlesNullJson() {
    TestCase tc = new TestCase();
    tc.setJson(null);
    HapiOperationOutcome output =
        testCaseValidationService.validateTestCaseJson(tc, ModelType.QI_CORE, "TOKEN");
    assertThat(output, is(nullValue()));
  }

  @Test
  public void testValidateTestCaseJsonHandlesEmptyJson() {
    HapiOperationOutcome output =
        testCaseValidationService.validateTestCaseJson(
            TestCase.builder().json("").build(), ModelType.QI_CORE, "TOKEN");
    assertThat(output, is(nullValue()));
  }

  @Test
  public void testValidateTestCaseJsonHandlesWhitespaceJson() {
    HapiOperationOutcome output =
        testCaseValidationService.validateTestCaseJson(
            TestCase.builder().json("   ").build(), ModelType.QI_CORE, "TOKEN");
    assertThat(output, is(nullValue()));
  }

  @Test
  public void testValidateTestCaseJsonHandlesGenericException() {
    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenThrow(new RuntimeException("something bad happened!"));

    HapiOperationOutcome output =
        testCaseValidationService.validateTestCaseJson(
            TestCase.builder().json("{}").build(), ModelType.QI_CORE, "TOKEN");
    assertThat(output, is(notNullValue()));
    assertThat(output.getCode(), is(equalTo(500)));
  }

  @Test
  public void testValidateTestCaseJsonHandlesNotFound() {
    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.NOT_FOUND, "path not found", "{}".getBytes(), Charset.defaultCharset()));

    HapiOperationOutcome output =
        testCaseValidationService.validateTestCaseJson(
            TestCase.builder().json("{}").build(), ModelType.QI_CORE, "TOKEN");
    assertThat(output, is(notNullValue()));
    assertThat(output.getCode(), is(equalTo(404)));
  }

  @Test
  public void testValidateTestCaseJsonHandlesUnsupportedMediaType() {
    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported Media Type",
                "{}".getBytes(),
                Charset.defaultCharset()));

    HapiOperationOutcome output =
        testCaseValidationService.validateTestCaseJson(
            TestCase.builder().json("{}").build(), ModelType.QI_CORE, "TOKEN");
    assertThat(output, is(notNullValue()));
    assertThat(output.getCode(), is(equalTo(415)));
  }

  @Test
  public void testValidateTestCaseJsonHandlesInternalServerError() {
    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unsupported Media Type"));

    HapiOperationOutcome output =
        testCaseValidationService.validateTestCaseJson(
            TestCase.builder().json("{}").build(), ModelType.QI_CORE, "TOKEN");
    assertThat(output, is(notNullValue()));
    assertThat(output.getCode(), is(equalTo(500)));
  }

  @Test
  public void testValidateTestCaseJsonHandlesProcessingErrorDuringHttpClientException()
      throws JsonProcessingException {
    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unsupported Media Type"));
    doThrow(
            new JsonParseException(
                mapper.getDeserializationContext().getParser(), "Something bad happened!"))
        .when(mapper)
        .readValue(anyString(), any(Class.class));

    HapiOperationOutcome output =
        testCaseValidationService.validateTestCaseJson(
            TestCase.builder().json("{}").build(), ModelType.QI_CORE, "TOKEN");
    assertThat(output, is(notNullValue()));
    assertThat(output.getCode(), is(equalTo(500)));
    assertThat(
        output.getMessage(),
        is(
            equalTo(
                "Unable to validate test case JSON due to errors, but outcome not able to be interpreted!")));
  }

  @Test
  public void testValidateTestCaseJsonHandlesGoodResponse() {
    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenReturn(hapiValidOutcome);

    HapiOperationOutcome output =
        testCaseValidationService.validateTestCaseJson(
            TestCase.builder().json("{}").build(), ModelType.QI_CORE, "TOKEN");
    assertThat(output, is(notNullValue()));
    assertThat(output.getCode(), is(equalTo(200)));
    assertThat(output.getMessage(), is(nullValue()));
    assertThat(output.isSuccessful(), is(true));
  }

  @Test
  public void testValidateTestCasesAsResourcesNullList() {
    final String accessToken = "Bearer Token";
    final ModelType model = ModelType.QI_CORE;
    List<TestCase> output =
        testCaseValidationService.validateTestCasesAsResources(null, model, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  public void testValidateTestCasesAsResourcesEmptyList() {
    final String accessToken = "Bearer Token";
    final ModelType model = ModelType.QI_CORE;
    final List<TestCase> testCases = List.of();
    List<TestCase> output =
        testCaseValidationService.validateTestCasesAsResources(testCases, model, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  public void testValidateTestCasesAsResourcesWithEntries() {
    TestCase testCase =
        TestCase.builder()
            .id("TestID")
            .json("{\"resourceType\": \"Bundle\", \"type\": \"collection\"}")
            .build();
    final String accessToken = "Bearer Token";

    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenReturn(
            ResponseEntity.ok(HapiOperationOutcome.builder().code(200).successful(true).build()));
    final ModelType model = ModelType.QI_CORE;
    final List<TestCase> testCases = List.of(testCase);
    List<TestCase> output =
        testCaseValidationService.validateTestCasesAsResources(testCases, model, accessToken);
    assertThat(output, is(notNullValue()));
    assertThat(output.isEmpty(), is(false));
    assertThat(output.get(0), is(notNullValue()));
    assertThat(output.get(0).isValidResource(), is(true));
    assertThat(output.get(0).getHapiOperationOutcome(), is(notNullValue()));
    assertThat(output.get(0).getHapiOperationOutcome().getCode(), is(equalTo(200)));
  }
}
