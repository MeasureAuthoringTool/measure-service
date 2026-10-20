package cms.gov.madie.measure.validations;

import static cms.gov.madie.measure.validations.CompositeMeasureValidationService.COMPONENT_MEASURE_TYPES_INVALID;
import static cms.gov.madie.measure.validations.CompositeMeasureValidationService.COMPONENT_POPULATION_BASIS_INVALID;
import static cms.gov.madie.measure.validations.CompositeMeasureValidationService.COMPOSITE_SCORING_INVALID;
import static cms.gov.madie.measure.validations.CompositeMeasureValidationService.COMPOSITE_SCORING_REQUIRED;
import static cms.gov.madie.measure.validations.CompositeMeasureValidationService.SCORING_MUST_BE_COMPOSITE;
import static cms.gov.madie.measure.validations.CompositeMeasureValidationService.TWO_COMPONENTS_REQUIRED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.exceptions.InvalidCompositeMeasureException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Component;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.MeasureScoring;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompositeMeasureValidationServiceTest {

  private static final String COMPOSITE = MeasureScoring.COMPOSITE.toString();
  private static final String PROPORTION = MeasureScoring.PROPORTION.toString();
  private static final String RATIO = MeasureScoring.RATIO.toString();
  private static final String CONTINUOUS_VARIABLE = MeasureScoring.CONTINUOUS_VARIABLE.toString();
  private static final String COHORT = MeasureScoring.COHORT.toString();
  private static final String PATIENT = "boolean";

  private static final List<Component> TWO_COMPONENTS =
      List.of(component("m1", "g1"), component("m2", "g2"));

  @Mock private MeasureRepository measureRepository;
  @InjectMocks private CompositeMeasureValidationService compositeMeasureValidationService;

  private static Component component(String measureId, String groupId) {
    return Component.builder().measureId(measureId).groupId(groupId).build();
  }

  // builds a component measure with a single group
  private static Measure componentMeasure(
      String id, String groupId, String scoring, String populationBasis) {
    return Measure.builder()
        .id(id)
        .groups(
            List.of(
                Group.builder()
                    .id(groupId)
                    .scoring(scoring)
                    .populationBasis(populationBasis)
                    .build()))
        .build();
  }

  private static Measure compositeMeasure(
      String scoring, String compositeScoring, List<Component> components, String populationBasis) {
    return Measure.builder()
        .id("composite-1")
        .measureMetaData(MeasureMetaData.builder().composite(true).build())
        .groups(
            List.of(
                Group.builder()
                    .id("cg1")
                    .scoring(scoring)
                    .compositeScoring(compositeScoring)
                    .components(components)
                    .populationBasis(populationBasis)
                    .build()))
        .build();
  }

  private static Measure compositeMeasure() {
    return compositeMeasure(COMPOSITE, "Opportunity", TWO_COMPONENTS, PATIENT);
  }

  private void stubComponentMeasures(Measure... measures) {
    when(measureRepository.findAllById(List.of("m1", "m2"))).thenReturn(List.of(measures));
  }

  @Test
  void acceptsANonCompositeMeasureWithoutQueryingComponents() {
    Measure measure =
        Measure.builder()
            .id("1")
            .measureMetaData(MeasureMetaData.builder().composite(false).build())
            .groups(List.of(Group.builder().id("g1").scoring(PROPORTION).build()))
            .build();

    assertDoesNotThrow(() -> compositeMeasureValidationService.validate(measure));
    verify(measureRepository, never()).findAllById(any());
  }

  @Test
  void acceptsAMeasureWithoutMetadata() {
    assertDoesNotThrow(
        () -> compositeMeasureValidationService.validate(Measure.builder().id("1").build()));
    verify(measureRepository, never()).findAllById(any());
  }

  @Test
  void acceptsAValidCompositeMeasure() {
    stubComponentMeasures(
        componentMeasure("m1", "g1", PROPORTION, PATIENT),
        componentMeasure("m2", "g2", RATIO, PATIENT));

    assertDoesNotThrow(() -> compositeMeasureValidationService.validate(compositeMeasure()));
  }

  @Test
  void requiresTwoComponentsWhenNoComponentsAreSelected() {
    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () ->
                compositeMeasureValidationService.validate(
                    compositeMeasure(COMPOSITE, "Opportunity", List.of(), PATIENT)));

    assertThat(ex.getErrors(), contains(TWO_COMPONENTS_REQUIRED));
    verify(measureRepository, never()).findAllById(any());
  }

  @Test
  void requiresTwoComponentsWhenOneMeasureWithOneGroupIsSelected() {
    when(measureRepository.findAllById(List.of("m1")))
        .thenReturn(List.of(componentMeasure("m1", "g1", PROPORTION, PATIENT)));

    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () ->
                compositeMeasureValidationService.validate(
                    compositeMeasure(
                        COMPOSITE, "Opportunity", List.of(component("m1", "g1")), PATIENT)));

    assertThat(ex.getErrors(), contains(TWO_COMPONENTS_REQUIRED));
  }

  @Test
  void requiresTwoDistinctMeasuresNotOneMeasureContributingTwoGroups() {
    Measure m1 =
        Measure.builder()
            .id("m1")
            .groups(
                List.of(
                    Group.builder().id("g1").scoring(PROPORTION).populationBasis(PATIENT).build(),
                    Group.builder().id("g2").scoring(RATIO).populationBasis(PATIENT).build()))
            .build();
    // the same measure id is only fetched once
    when(measureRepository.findAllById(List.of("m1"))).thenReturn(List.of(m1));

    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () ->
                compositeMeasureValidationService.validate(
                    compositeMeasure(
                        COMPOSITE,
                        "Opportunity",
                        List.of(component("m1", "g1"), component("m1", "g2")),
                        PATIENT)));

    assertThat(ex.getErrors(), contains(TWO_COMPONENTS_REQUIRED));
  }

  @Test
  void ignoresDuplicateComponentEntriesWhenCountingComponents() {
    when(measureRepository.findAllById(List.of("m1")))
        .thenReturn(List.of(componentMeasure("m1", "g1", PROPORTION, PATIENT)));

    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () ->
                compositeMeasureValidationService.validate(
                    compositeMeasure(
                        COMPOSITE,
                        "Opportunity",
                        List.of(component("m1", "g1"), component("m1", "g1")),
                        PATIENT)));

    assertThat(ex.getErrors(), contains(TWO_COMPONENTS_REQUIRED));
  }

  @Test
  void requiresTheGroupScoringToBeComposite() {
    stubComponentMeasures(
        componentMeasure("m1", "g1", PROPORTION, PATIENT),
        componentMeasure("m2", "g2", RATIO, PATIENT));

    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () ->
                compositeMeasureValidationService.validate(
                    compositeMeasure(PROPORTION, "Opportunity", TWO_COMPONENTS, PATIENT)));

    assertThat(ex.getErrors(), hasItem(SCORING_MUST_BE_COMPOSITE));
  }

  @Test
  void requiresACompositeScoringMethod() {
    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () ->
                compositeMeasureValidationService.validate(
                    compositeMeasure(COMPOSITE, null, TWO_COMPONENTS, PATIENT)));

    assertThat(ex.getErrors(), contains(COMPOSITE_SCORING_REQUIRED));
    // components are not validated without a composite scoring method
    verify(measureRepository, never()).findAllById(any());
  }

  @Test
  void rejectsAnUnsupportedCompositeScoringMethod() {
    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () ->
                compositeMeasureValidationService.validate(
                    compositeMeasure(COMPOSITE, "Component-level", TWO_COMPONENTS, PATIENT)));

    assertThat(ex.getErrors(), contains(COMPOSITE_SCORING_INVALID));
    verify(measureRepository, never()).findAllById(any());
  }

  @Test
  void rejectsAContinuousVariableComponentUnderOpportunityScoring() {
    stubComponentMeasures(
        componentMeasure("m1", "g1", PROPORTION, PATIENT),
        componentMeasure("m2", "g2", CONTINUOUS_VARIABLE, PATIENT));

    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () -> compositeMeasureValidationService.validate(compositeMeasure()));

    assertThat(ex.getErrors(), contains(COMPONENT_MEASURE_TYPES_INVALID));
  }

  @Test
  void rejectsAContinuousVariableComponentUnderAllOrNothingScoring() {
    stubComponentMeasures(
        componentMeasure("m1", "g1", PROPORTION, PATIENT),
        componentMeasure("m2", "g2", CONTINUOUS_VARIABLE, PATIENT));

    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () ->
                compositeMeasureValidationService.validate(
                    compositeMeasure(COMPOSITE, "All-or-nothing", TWO_COMPONENTS, PATIENT)));

    assertThat(ex.getErrors(), contains(COMPONENT_MEASURE_TYPES_INVALID));
  }

  @Test
  void allowsAContinuousVariableComponentUnderLinearScoring() {
    stubComponentMeasures(
        componentMeasure("m1", "g1", PROPORTION, PATIENT),
        componentMeasure("m2", "g2", CONTINUOUS_VARIABLE, PATIENT));

    assertDoesNotThrow(
        () ->
            compositeMeasureValidationService.validate(
                compositeMeasure(COMPOSITE, "Linear", TWO_COMPONENTS, PATIENT)));
  }

  @Test
  void reportsTheMeasureTypesErrorOnlyOnceForMultipleBadComponents() {
    stubComponentMeasures(
        componentMeasure("m1", "g1", COHORT, PATIENT),
        componentMeasure("m2", "g2", COHORT, PATIENT));

    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () -> compositeMeasureValidationService.validate(compositeMeasure()));

    assertThat(ex.getErrors(), contains(COMPONENT_MEASURE_TYPES_INVALID));
  }

  @Test
  void rejectsANonPatientBasedComponentWhenTheCompositeIsPatientBased() {
    stubComponentMeasures(
        componentMeasure("m1", "g1", PROPORTION, PATIENT),
        componentMeasure("m2", "g2", RATIO, "Encounter"));

    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () -> compositeMeasureValidationService.validate(compositeMeasure()));

    assertThat(ex.getErrors(), contains(COMPONENT_POPULATION_BASIS_INVALID));
  }

  @Test
  void doesNotCheckPopulationBasisWhenTheCompositeIsNotPatientBased() {
    stubComponentMeasures(
        componentMeasure("m1", "g1", PROPORTION, "Encounter"),
        componentMeasure("m2", "g2", RATIO, "Encounter"));

    assertDoesNotThrow(
        () ->
            compositeMeasureValidationService.validate(
                compositeMeasure(COMPOSITE, "Opportunity", TWO_COMPONENTS, "Encounter")));
  }

  @Test
  void flagsAComponentWhoseMeasureCannotBeResolved() {
    stubComponentMeasures(componentMeasure("m1", "g1", PROPORTION, PATIENT));

    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () -> compositeMeasureValidationService.validate(compositeMeasure()));

    assertThat(ex.getErrors(), contains(COMPONENT_MEASURE_TYPES_INVALID));
  }

  @Test
  void flagsAComponentWithNoOrAnUnknownScoring() {
    stubComponentMeasures(
        componentMeasure("m1", "g1", null, PATIENT),
        componentMeasure("m2", "g2", "Bogus", PATIENT));

    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () -> compositeMeasureValidationService.validate(compositeMeasure()));

    assertThat(ex.getErrors(), contains(COMPONENT_MEASURE_TYPES_INVALID));
  }

  @Test
  void flagsAComponentWhoseGroupCannotBeResolved() {
    stubComponentMeasures(
        componentMeasure("m1", "g1", PROPORTION, PATIENT),
        componentMeasure("m2", "other-group", RATIO, PATIENT));

    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () -> compositeMeasureValidationService.validate(compositeMeasure()));

    assertThat(ex.getErrors(), contains(COMPONENT_MEASURE_TYPES_INVALID));
  }

  // ordered by the order the user must fix them in
  @Test
  void rejectsWithEveryFailureInTheOrderTheUserMustFixThem() {
    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () ->
                compositeMeasureValidationService.validate(
                    compositeMeasure(PROPORTION, null, List.of(), PATIENT)));

    assertThat(
        ex.getErrors(),
        contains(SCORING_MUST_BE_COMPOSITE, COMPOSITE_SCORING_REQUIRED, TWO_COMPONENTS_REQUIRED));
    assertThat(
        ex.getMessage(),
        is(
            "Composite measure with ID composite-1 is invalid: Measure score must be Composite, "
                + "Composite measure score is required, Two component measures must be selected"));
  }

  @Test
  void stillReportsTheComponentCountWhenTheCompositeScoringIsUnusable() {
    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () ->
                compositeMeasureValidationService.validate(
                    compositeMeasure(
                        COMPOSITE, "Component-level", List.of(component("m1", "g1")), PATIENT)));

    assertThat(ex.getErrors(), contains(COMPOSITE_SCORING_INVALID, TWO_COMPONENTS_REQUIRED));
    verify(measureRepository, never()).findAllById(any());
  }

  @Test
  void reportsComponentTypeAndPopulationBasisErrorsTogether() {
    stubComponentMeasures(
        componentMeasure("m1", "g1", PROPORTION, PATIENT),
        componentMeasure("m2", "g2", CONTINUOUS_VARIABLE, "Encounter"));

    InvalidCompositeMeasureException ex =
        assertThrows(
            InvalidCompositeMeasureException.class,
            () -> compositeMeasureValidationService.validate(compositeMeasure()));

    assertThat(
        ex.getErrors(),
        contains(COMPONENT_MEASURE_TYPES_INVALID, COMPONENT_POPULATION_BASIS_INVALID));
  }
}
