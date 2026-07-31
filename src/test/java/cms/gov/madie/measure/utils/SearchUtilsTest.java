package cms.gov.madie.measure.utils;

import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class SearchUtilsTest {

  @Test
  void testAppendTestCaseSetIdCriteriaAllowsNoTestCasesOrAllValidTestCaseSetIds() {
    Criteria base = new Criteria();

    SearchUtils.appendTestCaseSetIdCriteria(base);

    String json = base.getCriteriaObject().toJson();
    assertThat(json).contains("testCases");
    assertThat(json).contains("$or");
    assertThat(json).contains("$nor");
    assertThat(json).contains("$elemMatch");
    assertThat(json).contains("$exists");
    assertThat(json).contains("testCaseSetId");
  }

  @Test
  void testAppendVersionSearchCriteriaThreePartVersion() {
    Criteria base = new Criteria();
    MeasureSearchCriteria input =
        MeasureSearchCriteria.builder()
            .searchField("1.2.3")
            .optionalSearchProperties(List.of("version"))
            .build();

    SearchUtils.appendAdditionalSearchCriteria(base, input);

    String json = base.getCriteriaObject().toString();
    assertThat(json).contains("version=1.2.003");
  }

  @Test
  void testAppendVersionSearchCriteriaTwoPartVersion() {
    Criteria base = new Criteria();
    MeasureSearchCriteria input =
        MeasureSearchCriteria.builder()
            .searchField("2.5")
            .optionalSearchProperties(List.of("version"))
            .build();

    SearchUtils.appendAdditionalSearchCriteria(base, input);

    String json = base.getCriteriaObject().toString();
    assertThat(json).contains("version.major");
    assertThat(json).contains("version.minor");
    assertThat(json).contains("2");
    assertThat(json).contains("5");
  }

  @Test
  void testAppendVersionSearchCriteriaSinglePartVersion() {
    Criteria base = new Criteria();
    MeasureSearchCriteria input =
        MeasureSearchCriteria.builder()
            .searchField("7")
            .optionalSearchProperties(List.of("version"))
            .build();

    SearchUtils.appendAdditionalSearchCriteria(base, input);

    String json = base.getCriteriaObject().toString();
    assertThat(json).contains("version.major");
    assertThat(json).contains("version.minor");
    assertThat(json).contains("version.revisionNumber");
    assertThat(json).contains("7");
  }

  @Test
  void testAppendInvalidVersionStringFallsBackToNoMatch() {
    Criteria base = new Criteria();
    MeasureSearchCriteria input =
        MeasureSearchCriteria.builder()
            .searchField("abc.def")
            .optionalSearchProperties(List.of("version"))
            .build();

    SearchUtils.appendAdditionalSearchCriteria(base, input);

    String json = base.getCriteriaObject().toString();
    assertThat(json).contains("__NO_MATCH__");
  }

  @Test
  void testAppendCmsIdSearchCriteria() {
    Criteria base = new Criteria();

    MeasureSearchCriteria input =
        MeasureSearchCriteria.builder()
            .searchField("1028FHIR")
            .optionalSearchProperties(List.of("cmsId"))
            .build();
    SearchUtils.appendAdditionalSearchCriteria(base, input);

    String json = base.getCriteriaObject().toString();
    assertThat(json).contains("cmsId");
    assertThat(json).contains("1028FHIR");
  }

  @Test
  void testAppendModelSearchCriteria() {
    Criteria base = new Criteria();

    MeasureSearchCriteria input =
        MeasureSearchCriteria.builder()
            .searchField("FHIR")
            .optionalSearchProperties(List.of("model"))
            .build();
    SearchUtils.appendAdditionalSearchCriteria(base, input);

    String json = base.getCriteriaObject().toString();
    assertThat(json).contains("model");
    assertThat(json).contains("FHIR");
  }

  @Test
  void testAppendMeasureSearchCriteria() {
    Criteria base = new Criteria();
    MeasureSearchCriteria input =
        MeasureSearchCriteria.builder()
            .searchField("TestMeasure")
            .optionalSearchProperties(List.of("measure"))
            .build();

    SearchUtils.appendAdditionalSearchCriteria(base, input);

    String json = base.getCriteriaObject().toString();
    assertThat(json).contains("measureName");
    assertThat(json).contains("TestMeasure");
  }

  @Test
  void testAppendReviewSearchCriteria() {
    Criteria base = new Criteria();
    MeasureSearchCriteria input =
        MeasureSearchCriteria.builder()
            .searchField("Ready")
            .optionalSearchProperties(List.of("review"))
            .build();

    SearchUtils.appendAdditionalSearchCriteria(base, input);

    String json = base.getCriteriaObject().toString();
    assertThat(json).contains("reviewStatus");
    assertThat(json).contains("Ready");
  }

  @Test
  void testAppendWithNullOptionalPropertiesDefaultsToAll() {
    Criteria base = new Criteria();

    MeasureSearchCriteria input = MeasureSearchCriteria.builder().searchField("v1").build();

    SearchUtils.appendAdditionalSearchCriteria(base, input);

    String json = base.getCriteriaObject().toString();
    assertThat(json).contains("version").contains("model").contains("measureName");
    assertThat(json).doesNotContain("reviewStatus");
  }

  @Test
  void testAppendWithUnknownOptionalPropertyFallsBackToRegex() {
    Criteria base = new Criteria();
    MeasureSearchCriteria input =
        MeasureSearchCriteria.builder()
            .searchField("test-field")
            .optionalSearchProperties(List.of("unknownProp"))
            .build();

    SearchUtils.appendAdditionalSearchCriteria(base, input);

    String json = base.getCriteriaObject().toString();
    assertThat(json).contains("unknownProp").contains("test-field");
  }
}
