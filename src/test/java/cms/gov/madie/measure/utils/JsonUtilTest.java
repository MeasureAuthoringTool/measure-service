package cms.gov.madie.measure.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureObservation;
import gov.cms.madie.models.measure.MeasureScoring;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.QdmMeasure;
import gov.cms.madie.models.measure.TestCaseGroupPopulation;
import gov.cms.madie.models.measure.TestCasePopulationValue;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class JsonUtilTest implements ResourceUtil {

  private String baseUrl = "https://myorg.com";
  private TestCase testCase =
      TestCase.builder().patientId(UUID.fromString("3d2abb9d-c10a-4ab3-ae1a-1684ab61c07e")).build();
  final String json = getData("/bundles/qicore_json_util_testjson1.json");
  final String json2 = getData("/bundles/qicore_json_util_testjson2.json");
  final String malformedJson =
      "{ \"resourceType\": \"Bundle\", \"type\": \"collection\", \"entry\": [{ \"fullUrl\": \"633c9d020968f8012250fc60 }]}"; // intentional - missing quotes around fullUrl ID
  final String measureReportJson = getData("/bundles/qicore_json_util_measurereport.json");
  final String json_noEntries =
      "{\n"
          + "   \"resourceType\":\"Bundle\",\n"
          + "   \"id\":\"62c880eb0111a60120dc21eb\",\n"
          + "   \"type\":\"collection\"\n"
          + "}";
  final String json_noResource =
      "{\n"
          + "  \"id\": \"62c880eb0111a60120dc21eb\",\n"
          + "  \"type\": \"collection\",\n"
          + "  \"entry\": [ {\n"
          + "    \"fullUrl\": \"62c880eb0111a60120dc21eb\"\n"
          + "  }]\n"
          + "}";
  final String json_noResourceType =
      "{\n"
          + "  \"id\": \"62c880eb0111a60120dc21eb\",\n"
          + "  \"entry\": [ {\n"
          + "    \"resource\": {\n"
          + "      \"id\": \"62c880eb0111a60120dc21eb\"\n"
          + "    }\n"
          + "  }]\n"
          + "}";
  final String json_noName =
      "{\n"
          + "  \"id\": \"62c880eb0111a60120dc21eb\",\n"
          + "  \"entry\": [ {\n"
          + "    \"resource\": {\n"
          + "      \"resourceType\": \"Patient\",\n"
          + "      \"id\": \"62c880eb0111a60120dc21eb\"\n"
          + "    }\n"
          + "  }]\n"
          + "}";
  final String json_noGivenName =
      "{\n"
          + "  \"id\": \"62c880eb0111a60120dc21eb\",\n"
          + "  \"entry\": [ {\n"
          + "    \"resource\": {\n"
          + "      \"resourceType\": \"Patient\",\n"
          + "      \"id\": \"62c880eb0111a60120dc21eb\",\n"
          + "      \"name\": [ {\n"
          + "        \"family\": \"TestFamilyName\"\n"
          + "      } ]\n"
          + "    }\n"
          + "  }]\n"
          + "}";
  final String json_noGroup =
      "{\n"
          + "  \"id\": \"62c880eb0111a60120dc21eb\",\n"
          + "  \"entry\": [ {\n"
          + "    \"resource\": {\n"
          + "      \"resourceType\": \"MeasureReport\",\n"
          + "      \"id\": \"62c880eb0111a60120dc21eb\"\n"
          + "    }\n"
          + "  }]\n"
          + "}";
  final String json_noPopulation =
      "{\n"
          + "  \"id\": \"62c880eb0111a60120dc21eb\",\n"
          + "  \"entry\": [ {\n"
          + "    \"resource\": {\n"
          + "      \"resourceType\": \"MeasureReport\",\n"
          + "      \"id\": \"62c880eb0111a60120dc21eb\",\n"
          + "      \"group\": [ {\n"
          + "      } ]\n"
          + "    }\n"
          + "  }]\n"
          + "}";
  final String json_noCode =
      "{\n"
          + "  \"id\": \"62c880eb0111a60120dc21eb\",\n"
          + "  \"entry\": [ {\n"
          + "    \"resource\": {\n"
          + "      \"resourceType\": \"MeasureReport\",\n"
          + "      \"id\": \"62c880eb0111a60120dc21eb\",\n"
          + "      \"group\": [ {\n"
          + "          \"population\" : [{\n"
          + "          }]\n"
          + "      } ]\n"
          + "    }\n"
          + "  }]\n"
          + "}";
  final String json_noCount =
      "{\n"
          + "  \"id\": \"62c880eb0111a60120dc21eb\",\n"
          + "  \"entry\": [ {\n"
          + "    \"resource\": {\n"
          + "      \"resourceType\": \"MeasureReport\",\n"
          + "      \"id\": \"62c880eb0111a60120dc21eb\",\n"
          + "      \"group\": [ {\n"
          + "          \"population\" : [{\n"
          + "              \"code\" : {}\n"
          + "          }]\n"
          + "      } ]\n"
          + "    }\n"
          + "  }]\n"
          + "}";

  final String qdmImportedJson = getData("/test_case_exported_qdm_json.json");
  final String testCasePopulationValueJsonNode =
      "{\n" + "\"population_index\":0,\n" + "\"IPP\":1\n" + "}";

  Population population1 = Population.builder().name(PopulationType.INITIAL_POPULATION).build();
  Population population2 =
      Population.builder()
          .name(PopulationType.DENOMINATOR)
          .id("ref1")
          .definition("Denominator")
          .build();
  Population population3 =
      Population.builder()
          .name(PopulationType.NUMERATOR)
          .id("ref2")
          .definition("Numerator")
          .build();
  Group group = Group.builder().populations(List.of(population1, population2, population3)).build();
  final Measure measure = Measure.builder().groups(List.of(group)).build();

  @Test
  public void testIsValidJsonSuccess() {
    boolean output = JsonUtil.isValidJson(json);
    assertThat(output, is(true));
  }

  @Test
  public void testIsValidJsonFalse() {
    boolean output = JsonUtil.isValidJson(malformedJson);
    assertThat(output, is(false));
  }

  @Test
  public void testIsValidJsonFalseForNull() {
    boolean output = JsonUtil.isValidJson(null);
    assertThat(output, is(false));
  }

  @Test
  public void testGetPatientId() throws JsonProcessingException {
    String output = JsonUtil.getPatientId(json);
    assertThat(output, is(equalTo("1")));
  }

  @Test
  public void testUpdateFullUrlNoChange() {
    final String fullUrl = "https://something/Patient/foo";
    final String output =
        JsonUtil.updateFullUrl(fullUrl, "patient1", "a64561f9-5654-4e45-ac06-1c168f411345");
    assertThat(output, is(equalTo(fullUrl)));
  }

  @Test
  public void testUpdateFullUrlUpdatesSuccessfully() {
    final String fullUrl = "https://something/Patient/patient1";
    final String output =
        JsonUtil.updateFullUrl(fullUrl, "patient1", "a64561f9-5654-4e45-ac06-1c168f411345");
    assertThat(
        output, is(equalTo("https://something/Patient/a64561f9-5654-4e45-ac06-1c168f411345")));
  }

  @Test
  public void testUpdateFullUrlUpdatesOnlyLastInstanceSuccessfully() {
    final String fullUrl = "https://something/patient1/patient1/Patient/patient1/patient1";
    final String output =
        JsonUtil.updateFullUrl(fullUrl, "patient1", "a64561f9-5654-4e45-ac06-1c168f411345");
    assertThat(
        output,
        is(
            equalTo(
                "https://something/patient1/patient1/Patient/patient1/a64561f9-5654-4e45-ac06-1c168f411345")));
  }

  @Test
  public void testUpdateFullUrlUpdatesStringWithOnlyId() {
    final String fullUrl = "patient1";
    final String output =
        JsonUtil.updateFullUrl(fullUrl, "patient1", "a64561f9-5654-4e45-ac06-1c168f411345");
    assertThat(output, is(equalTo("a64561f9-5654-4e45-ac06-1c168f411345")));
  }

  @Test
  public void testReplaceReferencesDoesNothing() {
    String output = JsonUtil.replacePatientRefs(json, "FOO12344", "BillyBob");
    assertThat(output, is(equalTo(json)));
  }

  @Test
  public void testReplaceReference() {
    // make sure it's there to start with
    assertThat(json.contains("\"Patient/1\""), is(true));
    String output = JsonUtil.replacePatientRefs(json, "1", "a64561f9-5654-4e45-ac06-1c168f411345");
    assertThat(output, is(not(equalTo(json))));
    assertThat(output.contains("\"Patient/1\""), is(false));
  }

  @Test
  public void testReplaceReferenceWithoutOldId() {
    // make sure it's there to start with
    assertThat(json.contains("\"Patient/1\""), is(true));
    String output = JsonUtil.replacePatientRefs(json, "a64561f9-5654-4e45-ac06-1c168f411345");
    assertThat(output, is(not(equalTo(json))));
    assertThat(output.contains("\"Patient/1\""), is(false));
    assertThat(output.contains("\"Patient/a64561f9-5654-4e45-ac06-1c168f411345\""), is(true));
  }

  @Test
  public void testReplaceFullUrlRefsWorks() {
    String output =
        JsonUtil.replaceFullUrlRefs(
            "{ \"reference\" : \"http://local/Patient/1\" }",
            "http://local/Patient/1",
            "a64561f9-5654-4e45-ac06-1c168f411345");
    assertThat(
        output, is(equalTo("{ \"reference\": \"Patient/a64561f9-5654-4e45-ac06-1c168f411345\" }")));
  }

  @Test
  public void testReplaceFullUrlRefsHandlesFullJson() {
    assertThat(json2.contains("reference\":\"http://local/Patient/Patient-7"), is(true));
    String output =
        JsonUtil.replaceFullUrlRefs(
            json2, "http://local/Patient/Patient-7", "a64561f9-5654-4e45-ac06-1c168f411345");
    assertThat(output.contains("\"reference\": \"http://local/Patient/Patient-7\""), is(false));
    assertThat(
        output.contains("\"reference\": \"Patient/a64561f9-5654-4e45-ac06-1c168f411345\""),
        is(true));
  }

  @Test
  public void testIsUuiReturnsFalseForNull() {
    assertThat(JsonUtil.isUuid(null), is(false));
  }

  @Test
  public void testIsUuiReturnsFalseForEmptyString() {
    assertThat(JsonUtil.isUuid(""), is(false));
  }

  @Test
  public void testIsUuiReturnsFalseForObjectId() {
    assertThat(JsonUtil.isUuid("63bc5891ee2e584d9c7d819b"), is(false));
  }

  @Test
  public void testIsUuiReturnsFalseForRandomString() {
    assertThat(JsonUtil.isUuid("RandomStringHere"), is(false));
  }

  @Test
  public void testIsUuiReturnsFalseForAlmostUuid() {
    assertThat(JsonUtil.isUuid("a500cba-353-050-9a7"), is(false));
  }

  @Test
  public void testIsUuiReturnsTrueForUuid() {
    assertThat(JsonUtil.isUuid("a500ccba-a353-4050-94a7-50f4eac4e59f"), is(true));
  }

  @Test
  public void testGetPatientFamilyName() throws JsonProcessingException {
    String output = JsonUtil.getPatientName(json, "family");
    assertThat(output, is(equalTo("Health")));
  }

  @Test
  public void testGetPatientGivenName() throws JsonProcessingException {
    String output = JsonUtil.getPatientName(json, "given");
    assertThat(output, is(equalTo("Lizzy")));
  }

  @Test
  public void testGetPatientFamilyNameNoEntries() throws JsonProcessingException {
    String output = JsonUtil.getPatientName(json_noEntries, "family");
    assertThat(output, is(equalTo(null)));
  }

  @Test
  public void testGetPatientFamilyNameNoResource() throws JsonProcessingException {
    String output = JsonUtil.getPatientName(json_noResource, "family");
    assertThat(output, is(equalTo(null)));
  }

  @Test
  public void testGetPatientFamilyNameNoResourceType() throws JsonProcessingException {
    String output = JsonUtil.getPatientName(json_noResourceType, "family");
    assertThat(output, is(equalTo(null)));
  }

  @Test
  public void testGetPatientFamilyNameWrongtype() throws JsonProcessingException {
    String output = JsonUtil.getPatientName(json, "wrongType");
    assertThat(output, is(equalTo(null)));
  }

  @Test
  public void testGetPatientFamilyNameNoName() throws JsonProcessingException {
    String output = JsonUtil.getPatientName(json_noName, "family");
    assertThat(output, is(equalTo(null)));
  }

  @Test
  public void testGetPatientFamilyNameNoGivenName() throws JsonProcessingException {
    String output = JsonUtil.getPatientName(json_noGivenName, "given");
    assertThat(output, is(equalTo(null)));
  }

  @Test
  public void testGetTestCaseGroupPopulationsFromMeasureReport() throws JsonProcessingException {

    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(measureReportJson, true, measure);
    assertThat(testCaseGroupPopulations.size(), is(equalTo(2)));
    log.debug("testCaseGroupPopulations size  = " + testCaseGroupPopulations.size());

    assertThat(
        testCaseGroupPopulations.get(0).getPopulationValues().get(0).getExpected(),
        is(equalTo("1")));
    assertThat(
        testCaseGroupPopulations.get(0).getPopulationValues().get(1).getExpected(),
        is(equalTo("2")));
    assertThat(
        testCaseGroupPopulations.get(0).getPopulationValues().get(2).getExpected(),
        is(equalTo("3")));

    assertThat(
        testCaseGroupPopulations.get(1).getPopulationValues().get(0).getExpected(),
        is(equalTo("4")));
    assertThat(
        testCaseGroupPopulations.get(1).getPopulationValues().get(1).getExpected(),
        is(equalTo("5")));
    assertThat(
        testCaseGroupPopulations.get(1).getPopulationValues().get(2).getExpected(),
        is(equalTo("6")));
  }

  @Test
  public void testGetTestCaseGroupPopulationsFromMeasureReportNoEntries()
      throws JsonProcessingException {
    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(json_noEntries, true, measure);
    assertThat(testCaseGroupPopulations.size(), is(equalTo(0)));
  }

  @Test
  public void testGetTestCaseGroupPopulationsFromMeasureReportNoResource()
      throws JsonProcessingException {
    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(json_noResource, true, measure);
    assertThat(testCaseGroupPopulations.size(), is(equalTo(0)));
  }

  @Test
  public void testGetTestCaseGroupPopulationsFromMeasureReportNoResourceType()
      throws JsonProcessingException {
    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(json_noResourceType, true, measure);
    assertThat(testCaseGroupPopulations.size(), is(equalTo(0)));
  }

  @Test
  public void testGetTestCaseGroupPopulationsFromMeasureReportNoGroup()
      throws JsonProcessingException {
    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(json_noGroup, true, measure);
    assertThat(testCaseGroupPopulations.size(), is(equalTo(0)));
  }

  @Test
  public void testGetTestCaseGroupPopulationsFromMeasureReportNoPopulation()
      throws JsonProcessingException {
    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(json_noPopulation, true, measure);
    assertThat(testCaseGroupPopulations.size(), is(equalTo(0)));
  }

  @Test
  public void testGetTestCaseGroupPopulationsFromMeasureReportNoCode()
      throws JsonProcessingException {
    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(json_noCode, true, measure);
    assertThat(testCaseGroupPopulations.size(), is(equalTo(0)));
  }

  @Test
  public void testGetTestCaseGroupPopulationsFromMeasureReportNoCount()
      throws JsonProcessingException {
    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(json_noCount, true, measure);
    assertThat(testCaseGroupPopulations.size(), is(equalTo(0)));
  }

  @Test
  public void testGetTestCaseGroupPopulationsFromMeasureReportStratifications()
      throws JsonProcessingException {

    String jsonWithStrat = getData("/test_case_export_w_stratification.json");
    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(jsonWithStrat, true, measure);
    assertThat(testCaseGroupPopulations.size(), is(equalTo(1)));
    assertThat(testCaseGroupPopulations.get(0).getStratificationValues().size(), is(equalTo(2)));
    assertThat(
        testCaseGroupPopulations.get(0).getStratificationValues().get(0).getName(),
        is(equalTo("Strata 1")));
    assertThat(
        testCaseGroupPopulations.get(0).getStratificationValues().get(1).getName(),
        is(equalTo("Strata 2")));
  }

  @Test
  public void testGetTestCaseGroupPopulationsFromMeasureReportStratificationsNonBoolean()
      throws JsonProcessingException {

    String jsonWithStrat = getData("/test_case_export_w_stratification.json");
    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(jsonWithStrat, true, measure);
    assertThat(testCaseGroupPopulations.size(), is(equalTo(1)));
    assertThat(testCaseGroupPopulations.get(0).getStratificationValues().size(), is(equalTo(2)));
    assertThat(
        testCaseGroupPopulations.get(0).getStratificationValues().get(0).getName(),
        is(equalTo("Strata 1")));
    assertThat(
        testCaseGroupPopulations.get(0).getStratificationValues().get(1).getName(),
        is(equalTo("Strata 2")));
  }

  @Test
  public void testEnforcePatientIdEmptyJson() {
    testCase.setJson(null);
    String modifiedJson = JsonUtil.enforcePatientId(testCase, baseUrl);
    assertNull(modifiedJson);
  }

  @Test
  public void testEnforcePatientIdNoEntry() {
    String json = "{\"resourceType\": \"Bundle\", \"type\": \"collection\"}";
    testCase.setJson(json);
    String modifiedJson = JsonUtil.enforcePatientId(testCase, baseUrl);
    assertEquals(modifiedJson, json);
  }

  @Test
  public void testEnforcePatientIdNoResource() {
    String json =
        "{\"resourceType\": \"Bundle\", \"type\": \"collection\", \n"
            + "  \"entry\" : [ {\n"
            + "    \"fullUrl\" : \"http://local/Patient/1\"\n"
            + "  } ]             }";
    testCase.setJson(json);
    String modifiedJson = JsonUtil.enforcePatientId(testCase, baseUrl);
    assertEquals(modifiedJson, json);
  }

  @Test
  public void testEnforcePatientIdNoResourceType() {
    String json =
        "{\"resourceType\": \"Bundle\", \"type\": \"collection\", \n"
            + "  \"entry\" : [ {\n"
            + "    \"fullUrl\" : \"http://local/Patient/1\",\n"
            + "    \"resource\" : {\n"
            + "      \"id\" : \"testUniqueId\"\n"
            + "    }\n"
            + "  } ]             }";
    testCase.setJson(json);
    String modifiedJson = JsonUtil.enforcePatientId(testCase, baseUrl);
    assertEquals(modifiedJson, json);
  }

  @Test
  public void testEnforcePatientIdNoPatientResourceType() {
    String json =
        "{\"resourceType\": \"Bundle\", \"type\": \"collection\", \n"
            + "  \"entry\" : [ {\n"
            + "    \"fullUrl\" : \"http://local/Patient/1\",\n"
            + "    \"resource\" : {\n"
            + "      \"id\" : \"testUniqueId\",\n"
            + "      \"resourceType\" : \"NOTPatient\"    \n"
            + "    }\n"
            + "  } ]             }";
    testCase.setJson(json);
    String modifiedJson = JsonUtil.enforcePatientId(testCase, baseUrl);
    assertEquals(modifiedJson, json);
  }

  @Test
  public void testJsonNodeToString() {
    String str = JsonUtil.jsonNodeToString(null, null);
    assertTrue(StringUtils.isAllBlank(str));
  }

  @Test
  void updateResourceFullUrlsIfTestResourcesAvailable() {
    final String json = getData("/bundles/qicore_json_util_fullurl.json");
    TestCase tc1 =
        TestCase.builder().id("TC1").name("TC1").patientId(UUID.randomUUID()).json(json).build();
    String updatedTc1 = JsonUtil.updateResourceFullUrls(tc1, baseUrl);
    assertNotEquals(updatedTc1, json);
    assertTrue(updatedTc1.contains(baseUrl));
  }

  @Test
  void updateResourceFullUrlsIfNoTestResourceAvailable() {
    final String json =
        "{\"id\":\"6323489059967e30c06d0774\",\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[]}";
    TestCase tc1 =
        TestCase.builder().id("TC1").name("TC1").patientId(UUID.randomUUID()).json(json).build();
    String baseUrl = "https://myorg.com";
    String updatedTc1 = JsonUtil.updateResourceFullUrls(tc1, baseUrl);
    assertFalse(updatedTc1.contains(baseUrl));
  }

  @Test
  void updateResourceFullUrlsIfJsonInvalid() {
    final String json = getData("/bundles/qicore_json_util_fullurl.json");
    // remove the opening { to make json invalid
    String invalidJson = json.substring(1, json.length());
    TestCase tc1 =
        TestCase.builder()
            .id("TC1")
            .name("TC1")
            .patientId(UUID.randomUUID())
            .json(invalidJson)
            .build();
    String updatedTc1 = JsonUtil.updateResourceFullUrls(tc1, baseUrl);
    // original json is returned when json is invalid
    assertEquals(updatedTc1, invalidJson);
  }

  @Test
  void updateResourceFullUrlsIfEntryNodeNotAvailable() {
    final String json =
        "{\"id\":\"6323489059967e30c06d0774\",\"resourceType\":\"Bundle\",\"type\":\"collection\"}";
    TestCase tc1 =
        TestCase.builder().id("TC1").name("TC1").patientId(UUID.randomUUID()).json(json).build();
    String baseUrl = "https://myorg.com";
    String updatedTc1 = JsonUtil.updateResourceFullUrls(tc1, baseUrl);
    assertFalse(updatedTc1.contains(baseUrl));
  }

  @Test
  void testGetTestcaseDescriptionIfMeasureReportMissing() throws JsonProcessingException {
    final String json =
        "{\"id\":\"6323489059967e30c06d0774\",\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[]}";
    String description = JsonUtil.getTestDescription(json);
    assertNull(description);
  }

  @Test
  void testGetTestcaseDescriptionIfNoExtension() throws JsonProcessingException {
    final String json =
        "{\"id\":\"6323489059967e30c06d0774\",\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[{\"resource\": {\"resourceType\": \"MeasureReport\"}}]}";
    String description = JsonUtil.getTestDescription(json);
    assertNull(description);
  }

  @Test
  void testGetTestcaseDescriptionIfNoTestcaseDescriptionExtension() throws JsonProcessingException {
    final String json =
        "{\"id\":\"6323489059967e30c06d0774\",\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[{\"resource\": {\"resourceType\": \"MeasureReport\",\"extension\":[{\"url\":\"http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-inputParameters\",\"valueReference\":{\"reference\":\"#IPPass-parameters\"}}]}}]}";
    String description = JsonUtil.getTestDescription(json);
    assertNull(description);
  }

  @Test
  void testGetTestcaseDescription() throws JsonProcessingException {
    final String json =
        "{\"id\":\"6323489059967e30c06d0774\",\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[{\"resource\": {\"resourceType\": \"MeasureReport\", \"extension\":[{\"url\":\"http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-testCaseDescription\",\"valueMarkdown\":\"test case description\"}]}}]}";
    String description = JsonUtil.getTestDescription(json);
    assertEquals(description, "test case description");
  }

  @Test
  void testGetPatientNameQdmWrongNodeType() throws JsonProcessingException {
    String result = JsonUtil.getPatientNameQdm(qdmImportedJson, "wrongNode");
    assertNull(result);
  }

  @Test
  void testGetTestDescriptionQdmNotFound() throws JsonProcessingException {
    String result = JsonUtil.getTestDescriptionQdm("{\"id\":\"test\"}");
    assertNull(result);
  }

  @Test
  void testGGetTestCaseJsonNotFound() throws JsonProcessingException {
    String result = JsonUtil.getTestCaseJson("{\"id\":\"test\"}");
    assertNull(result);
  }

  @Test
  void testHandleStratificationValuesGroupsNull() throws JsonProcessingException {
    QdmMeasure qdmMeasure =
        QdmMeasure.builder().scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString()).build();

    List<TestCaseGroupPopulation> groupPopulations =
        JsonUtil.getTestCaseGroupPopulationsQdm(qdmImportedJson, qdmMeasure);
    assertTrue(CollectionUtils.isEmpty(groupPopulations.get(0).getStratificationValues()));
  }

  @Test
  void testHandleStratificationValuesStratificationsNull() throws JsonProcessingException {
    Group group = Group.builder().build();
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
            .groups(List.of(group))
            .build();

    List<TestCaseGroupPopulation> groupPopulations =
        JsonUtil.getTestCaseGroupPopulationsQdm(qdmImportedJson, qdmMeasure);
    assertTrue(CollectionUtils.isEmpty(groupPopulations.get(0).getStratificationValues()));
  }

  @Test
  void testGetTestCaseGroupPopulationsQdmEmptyExpectedValues() throws JsonProcessingException {
    Group group = Group.builder().build();
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
            .groups(List.of(group))
            .build();

    List<TestCaseGroupPopulation> groupPopulations =
        JsonUtil.getTestCaseGroupPopulationsQdm("{\"expectedValues\":[]}", qdmMeasure);
    assertTrue(CollectionUtils.isEmpty(groupPopulations));
  }

  @Test
  void testGetTestCaseGroupPopulationsQdmForRatio() throws JsonProcessingException {
    QdmMeasure qdmMeasure = QdmMeasure.builder().scoring(MeasureScoring.RATIO.toString()).build();

    List<TestCaseGroupPopulation> groupPopulations =
        JsonUtil.getTestCaseGroupPopulationsQdm(qdmImportedJson, qdmMeasure);
    assertTrue(CollectionUtils.isNotEmpty(groupPopulations.get(0).getPopulationValues()));
  }

  @Test
  void testGetTestCaseGroupPopulationsQdmForCVWithStratificationValues()
      throws JsonProcessingException {
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
            .patientBasis(false)
            .build();

    List<TestCaseGroupPopulation> groupPopulations =
        JsonUtil.getTestCaseGroupPopulationsQdm(qdmImportedJson, qdmMeasure);
    assertTrue(CollectionUtils.isNotEmpty(groupPopulations.get(0).getPopulationValues()));
    assertTrue(CollectionUtils.isNotEmpty(groupPopulations.get(1).getStratificationValues()));
    assertTrue(CollectionUtils.isNotEmpty(groupPopulations.get(2).getStratificationValues()));
  }

  @Test
  void testSetObservationValuesForCV() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode ippNode = mapper.readTree(testCasePopulationValueJsonNode);

    List<TestCasePopulationValue> populationValues = List.of();

    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
            .patientBasis(false)
            .build();

    JsonUtil.setObservationValuesForCV(ippNode, populationValues, qdmMeasure);
    assertTrue(CollectionUtils.isEmpty(populationValues));
  }

  @Test
  void testSetDenominatorValues() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode ippNode = mapper.readTree(testCasePopulationValueJsonNode);

    List<TestCasePopulationValue> populationValues = List.of();

    QdmMeasure qdmMeasure =
        QdmMeasure.builder().scoring(MeasureScoring.RATIO.toString()).patientBasis(false).build();

    JsonUtil.setDenominatorValues(ippNode, populationValues, qdmMeasure);
    assertTrue(CollectionUtils.isEmpty(populationValues));
  }

  @Test
  void testSetNumeratorValues() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode ippNode = mapper.readTree(testCasePopulationValueJsonNode);

    List<TestCasePopulationValue> populationValues = List.of();

    QdmMeasure qdmMeasure =
        QdmMeasure.builder().scoring(MeasureScoring.RATIO.toString()).patientBasis(false).build();

    JsonUtil.setNumeratorValues(ippNode, populationValues, qdmMeasure);
    assertTrue(CollectionUtils.isEmpty(populationValues));
  }

  @Test
  void testReplaceNestedDateTimeStringValue() throws IOException {

    ObjectMapper mapper = new ObjectMapper();
    JsonNode rootNode = mapper.readTree(json);
    assertTrue(json.contains("2022-09-06T20:47:21-05:00"));
    assertTrue(json.contains("2021-10-13T03:34:10.160+02:00"));
    JsonUtil.replaceNestedDateTimeStringValue(rootNode);
    String modifiedJsonString = mapper.writeValueAsString(rootNode);

    // 2022-09-06T20:47:21-05:00 -> 2022-09-07T01:47:21.000+00:00
    assertTrue(modifiedJsonString.contains("2022-09-07T01:47:21.000+00:00"));
    // 2021-10-13T03:34:10.160+02:00 -> 2021-10-13T01:34:10.160+00:00
    assertTrue(modifiedJsonString.contains("2021-10-13T01:34:10.160+00:00"));
    // 2023-08-10T03:34:10.054Z -> 2023-08-10T03:34:10.054+00:00
    assertTrue(modifiedJsonString.contains("2023-08-10T03:34:10.054+00:00"));
    // 2023-08-15T03:34:10.054Z -> 2023-08-15T03:34:10.054+00:00
    assertTrue(modifiedJsonString.contains("2023-08-15T03:34:10.054+00:00"));
    // 2021-10-13T03:34:10.160+03:00 -> 2021-10-13T03:34:10.160+00:00 <- invalid timezone test
    assertTrue(modifiedJsonString.contains("2021-10-13T03:34:10.160+00:00"));
    // 2023-09-12T03:34:10.054Z -> 2023-09-12T03:34:10.054+00:00
    assertTrue(modifiedJsonString.contains("2023-09-12T03:34:10.054+00:00"));
    // 2023-09-13T09:34:10.054Z -> 2023-09-13T09:34:10.054+00:00
    assertTrue(modifiedJsonString.contains("2023-09-13T09:34:10.054+00:00"));
  }

  @Test
  void testConvertDateTimeToUTCForTestCase() {
    testCase.setJson(json);
    String result = JsonUtil.convertDateTimeToUTC(testCase.getJson());
    assertNotEquals(json, result);
    // 2022-09-06T20:47:21-05:00 -> 2022-09-07T01:47:21.000+00:00
    assertTrue(result.contains("2022-09-07T01:47:21.000+00:00"));
    // 2021-10-13T03:34:10.160+02:00 -> 2021-10-13T01:34:10.160+00:00
    assertTrue(result.contains("2021-10-13T01:34:10.160+00:00"));
    // 2023-08-10T03:34:10.054Z -> 2023-08-10T03:34:10.054+00:00
    assertTrue(result.contains("2023-08-10T03:34:10.054+00:00"));
    // 2023-08-15T03:34:10.054Z -> 2023-08-15T03:34:10.054+00:00
    assertTrue(result.contains("2023-08-15T03:34:10.054+00:00"));
    // 2021-10-13T03:34:10.160+99:00 -> 2021-10-13T03:34:10.160+00:00 <- invalid timezone test
    assertTrue(result.contains("2021-10-13T03:34:10.160+00:00"));
    // 2023-09-12T03:34:10.054Z -> 2023-09-12T03:34:10.054+00:00
    assertTrue(result.contains("2023-09-12T03:34:10.054+00:00"));
    // 2023-09-13T09:34:10.054Z -> 2023-09-13T09:34:10.054+00:00
    assertTrue(result.contains("2023-09-13T09:34:10.054+00:00"));
  }

  @Test
  void testGetConvertedDateTimeWrongFormat() {
    String oldValue = "2025-03-29T29:54:74";
    String newValue = JsonUtil.getNewValue("2025-03-29T29:54:74");
    assertEquals(oldValue, newValue);
  }

  @Test
  void testgetTestCaseGroupPopulationsFromMeasureReportWithMeasureObservation()
      throws JsonProcessingException {
    MeasureObservation observation1 =
        MeasureObservation.builder()
            .id("obsId1")
            .definition("Denominator Observations")
            .criteriaReference("ref1")
            .displayId("MeasureObservation_1_1")
            .build();
    MeasureObservation observation2 =
        MeasureObservation.builder()
            .id("obsId2")
            .definition("Numberator Observations")
            .criteriaReference("ref2")
            .displayId("MeasureObservation_1_2")
            .build();
    group.setPopulationBasis("boolean");
    group.setMeasureObservations(List.of(observation1, observation2));
    measure.setGroups(List.of(group));

    String jsonWithObserv = getData("/test_case_export_w_measure-observation.json");

    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(jsonWithObserv, true, measure);

    assertTrue(testCaseGroupPopulations.get(0).getPopulationValues().size() == 5);
  }

  @Test
  void testFindMeasureObservationNoObservation() {
    MeasureObservation result = JsonUtil.findMeasureObservation(group, "MeasureObservation_1_1");

    assertNull(result);
  }

  @Test
  void testFindMeasureObservationNotFound() {
    MeasureObservation obs = MeasureObservation.builder().displayId("MeasureObservation_1").build();
    group.setMeasureObservations(List.of(obs));
    MeasureObservation result = JsonUtil.findMeasureObservation(group, "MeasureObservation_1_1");

    assertNull(result);
  }

  @Test
  void testgetTestCaseGroupPopulationsFromMeasureReportWithMeasureObservationEpisodeBased()
      throws JsonProcessingException {
    MeasureObservation observation1 =
        MeasureObservation.builder()
            .id("obsId1")
            .definition("Denominator Observation")
            .criteriaReference("ref")
            .displayId("MeasureObservation_1_1")
            .build();
    MeasureObservation observation2 =
        MeasureObservation.builder()
            .id("obsId2")
            .definition("Numberator Observation")
            .criteriaReference("ref")
            .displayId("MeasureObservation_1_2")
            .build();
    group.setPopulationBasis("Encounter");
    group.setMeasureObservations(List.of(observation1, observation2));
    measure.setGroups(List.of(group));

    String jsonWithObserv = getData("/test_case_export_w_measure-observation_multiple.json");

    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(jsonWithObserv, true, measure);

    assertTrue(testCaseGroupPopulations.get(0).getPopulationValues().size() == 8);
  }

  @Test
  public void testGetTestCaseGroupPopulationsFromMeasureReportTwoGroups()
      throws JsonProcessingException {
    String jsonWith2Groups = getData("/test_case_export_w_two_groups.json");
    List<TestCaseGroupPopulation> testCaseGroupPopulations =
        JsonUtil.getTestCaseGroupPopulationsFromMeasureReport(jsonWith2Groups, false, measure);
    assertThat(testCaseGroupPopulations.size(), is(equalTo(1)));
    assertThat(testCaseGroupPopulations.get(0).getPopulationValues().size(), is(equalTo(4)));
    assertThat(
        testCaseGroupPopulations.get(0).getPopulationValues().get(0).getExpected(),
        is(equalTo("1")));
    assertThat(
        testCaseGroupPopulations.get(0).getPopulationValues().get(1).getExpected(),
        is(equalTo("2")));
    assertThat(
        testCaseGroupPopulations.get(0).getPopulationValues().get(2).getExpected(),
        is(equalTo("3")));
    assertThat(
        testCaseGroupPopulations.get(0).getPopulationValues().get(3).getExpected(),
        is(equalTo("4")));
  }

  @Test
  public void testRemoveMeasureReportFromJsonThrowsException() {
    assertThrows(RuntimeException.class, () -> JsonUtil.removeMeasureReportEntries(null));
  }

  @Test
  public void removeMeasureReportEntriesThrowsExceptionForEmptyJson() {
    assertThrows(RuntimeException.class, () -> JsonUtil.removeMeasureReportEntries(""));
  }

  @Test
  public void removeMeasureReportEntriesHandlesInvalidJson() {
    String invalidJson =
        "{ \"type\": \"transaction\", \"entry\": [ { \"resource\": { \"resourceType\": \"Patient\" } ";
    assertThrows(
        JsonProcessingException.class, () -> JsonUtil.removeMeasureReportEntries(invalidJson));
  }

  @Test
  public void removeMeasureReportEntriesSuccessfullyRemovesMeasureReport()
      throws JsonProcessingException {
    String json =
        "{ \"entry\": ["
            + "{ \"resource\": { \"resourceType\": \"MeasureReport\", \"id\": \"mr1\" } },"
            + "{ \"resource\": { \"resourceType\": \"Patient\", \"id\": \"p1\" } },"
            + "{ \"resource\": { \"resourceType\": \"Observation\", \"id\": \"obs1\" } }"
            + "] }";
    String result = JsonUtil.removeMeasureReportEntries(json);
    assertFalse(result.contains("\"MeasureReport\""));
  }

  @Test
  public void updateBundleTypeAndRemoveRequestUpdatesTypeToCollection()
      throws JsonProcessingException {
    String json = "{ \"type\": \"transaction\", \"entry\": [] }";
    TestCase testCase = new TestCase();
    testCase.setJson(json);
    String result = JsonUtil.updateBundleTypeAndRemoveRequest(testCase);
    assertTrue(result.contains("\"type\":\"collection\""));
    assertTrue(testCase.isBundleTypeUpdated());
  }

  @Test
  public void updateBundleTypeAndRemoveRequestRemovesRequestEntries()
      throws JsonProcessingException {
    String json =
        "{ \"entry\": [ { \"request\": { \"method\": \"POST\" }, \"resource\": { \"resourceType\": \"Patient\" } } ] }";
    TestCase testCase = new TestCase();
    testCase.setJson(json);
    String result = JsonUtil.updateBundleTypeAndRemoveRequest(testCase);
    assertFalse(result.contains("\"request\""));
    // type is not updated, so flag should be false
    assertFalse(testCase.isBundleTypeUpdated());
  }

  @Test
  public void updateBundleTypeAndRemoveRequestThrowsExceptionForEmptyJson() {
    TestCase testCase = new TestCase();
    testCase.setJson("");
    assertThrows(RuntimeException.class, () -> JsonUtil.updateBundleTypeAndRemoveRequest(testCase));
  }

  @Test
  public void processJsonWithoutMeasureReportRemovalHandlesInvalidJson() {
    String invalidJson =
        "{ \"type\": \"transaction\", \"entry\": [ { \"resource\": { \"resourceType\": \"Patient\" } ";
    TestCase testCase = new TestCase();
    testCase.setJson(invalidJson);
    assertThrows(
        JsonProcessingException.class, () -> JsonUtil.updateBundleTypeAndRemoveRequest(testCase));
  }

  @Test
  public void testUpdateResourceProfile() {
    String json =
        """
	      {
            "entry": [
              {
                "fullUrl": "https://madie.cms.gov/Practitioner/aa12aa5b-e428-419f-b486-748fd20852b5",
                "resource": {
                "resourceType": "Practitioner",
                "id": "aa12aa5b-e428-419f-b486-748fd20852b5",
                "meta": {
                  "profile": [
                    "http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-patient"
                  ]
                }
              }
            }
          ]
        }
	  """;
    String expectedProfile =
        "http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-practitioner";
    TestCase testCase = TestCase.builder().json(json).build();
    String result = JsonUtil.updateResourceProfile(testCase);
    assertTrue(result.contains(expectedProfile));
  }

  @Test
  public void testUpdateResourceProfileWhenEntryIsNull() {
    TestCase testCase = TestCase.builder().json(json_noEntries).build();
    String result = JsonUtil.updateResourceProfile(testCase);
    assertEquals(json_noEntries, result);
  }

  @Test
  public void testUpdateResourceProfileWhenResourceNodeIsMissing() {
    TestCase testCase = TestCase.builder().json(json_noResource).build();
    String result = JsonUtil.updateResourceProfile(testCase);
    assertEquals(json_noResource, result);
  }

  @Test
  public void testUpdateResourceProfileWhenResourceTypeIsNull() {
    TestCase testCase = TestCase.builder().json(json_noResourceType).build();
    String result = JsonUtil.updateResourceProfile(testCase);
    assertEquals(json_noResourceType, result);
  }

  @Test
  public void testUpdateResourceProfileWhenMetaIsNull() {
    String json =
        """
		  {
		    "entry": [
		      {
		        "fullUrl": "https://madie.cms.gov/patient/aa12aa5b-e428-419f-b486-748fd20852b5",
		        "resource": {
		          "resourceType": "patient",
		          "id": "aa12aa5b-e428-419f-b486-748fd20852b5"
		          }
		        }
		      ]
		    }
		  """;
    TestCase testCase = TestCase.builder().json(json).build();
    String result = JsonUtil.updateResourceProfile(testCase);
    assertEquals(json, result);
  }

  @Test
  public void testUpdateResourceProfileWhenMetaDoesNotHaveProfile() {
    String json =
        """
		  {
		    "entry": [
		      {
		        "fullUrl": "https://madie.cms.gov/patient/aa12aa5b-e428-419f-b486-748fd20852b5",
		        "resource": {
		          "resourceType": "patient",
		          "id": "aa12aa5b-e428-419f-b486-748fd20852b5",
		          "meta": {
                    "wrong": [
                      "http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-patient"
                    ]
                  }
		        }
		      }
		    ]
		  }
		  """;
    TestCase testCase = TestCase.builder().json(json).build();
    String result = JsonUtil.updateResourceProfile(testCase);
    assertEquals(json, result);
  }

  @Test
  public void testUpdateResourceProfileWhenResourceTypeIsPatient() {
    String json =
        """
          {
            "entry": [
              {
                "fullUrl": "https://madie.cms.gov/patient/aa12aa5b-e428-419f-b486-748fd20852b5",
                "resource": {
                  "resourceType": "patient",
                  "id": "aa12aa5b-e428-419f-b486-748fd20852b5",
                  "meta": {
                    "profile": [
                      "http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-patient"
                    ]
                  }
                }
              }
            ]
          }
        """;
    TestCase testCase = TestCase.builder().json(json).build();
    String result = JsonUtil.updateResourceProfile(testCase);
    assertEquals(json, result);
  }

  @Test
  public void testUpdateResourceProfileWhenJsonIsInvalid() {
    TestCase testCase = TestCase.builder().json(malformedJson).build();
    String result = JsonUtil.updateResourceProfile(testCase);
    assertEquals(malformedJson, result);
  }

  @Test
  public void testUpdateResourceProfileWhenProfileHasResourceType() {
    String json =
        """
          {
            "entry": [
              {
                "fullUrl": "https://madie.cms.gov/Condition/non-behavioral-health-dx-without-SUD-8a64",
                "resource": {
                  "resourceType": "Condition",
                  "id": "non-behavioral-health-dx-without-SUD-8a64",
                  "meta": {
                    "profile": [
                      "http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-condition-encounter-diagnosis"
                    ]
                  }
                }
              }
            ]
          }
        """;
    TestCase testCase = TestCase.builder().json(json).build();
    String result = JsonUtil.updateResourceProfile(testCase);
    assertEquals(json, result);
  }
}
