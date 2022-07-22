package cms.gov.madie.measure.validations;

import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.MeasureScoring;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.validators.GroupScoringPopulationValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import javax.validation.ConstraintValidatorContext;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupScoringPopulationValidatorTest {

  @Mock private ConstraintValidatorContext validatorContext;

  private final GroupScoringPopulationValidator validator = new GroupScoringPopulationValidator();

  @Test
  public void testValidatorReturnsTrueForNullGroup() {
    boolean output = validator.isValid(null, validatorContext);
    assertTrue(output);
  }

  @Test
  public void testValidatorReturnsFalseForNullScoring() {
    Group group = Group.builder().scoring(null).build();
    boolean output = validator.isValid(group, validatorContext);
    assertFalse(output);
  }

  @Test
  public void testValidatorReturnsFalseForEmptyScoring() {
    Group group = Group.builder().scoring("").build();
    boolean output = validator.isValid(group, validatorContext);
    assertFalse(output);
  }

  @Test
  public void testValidatorReturnsFalseForWhitespaceScoring() {
    Group group = Group.builder().scoring("   ").build();
    boolean output = validator.isValid(group, validatorContext);
    assertFalse(output);
  }

  @Test
  public void testValidatorReturnsFalseForNullPopulation() {
    Group group =
        Group.builder().scoring(MeasureScoring.COHORT.toString()).populations(null).build();
    boolean output = validator.isValid(group, validatorContext);
    assertFalse(output);
  }

  @Test
  public void testValidatorReturnsFalseForEmptyPopulation() {
    Group group =
        Group.builder().scoring(MeasureScoring.COHORT.toString()).populations(List.of()).build();
    boolean output = validator.isValid(group, validatorContext);
    assertFalse(output);
  }

  @Test
  public void testValidatorReturnsFalseForInvalidScoring() {
    Population population =
        Population.builder()
            .name(PopulationType.INITIAL_POPULATION)
            .definition("Advanced Illness")
            .build();
    Group group = Group.builder().scoring("Invalid").populations(List.of(population)).build();
    boolean output = validator.isValid(group, validatorContext);
    assertFalse(output);
  }

  @ParameterizedTest
  @MethodSource("groupScoringPopComboProvider")
  void isGroupScoringPopulationCombinationValid(Group group, boolean expected) {
    assertEquals(expected, validator.isValid(group, validatorContext));
  }

  // Note: cannot pass null arguments using a generator like this
  private static Stream<Arguments> groupScoringPopComboProvider() {
    return Stream.of(
            cohortProvider(), proportionProvider(), continuousVariableProvider(), ratioProvider())
        .flatMap(a -> a);
  }

  private static Stream<Arguments> cohortProvider() {
    return Stream.of(
        // correct Cohort population combination
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.COHORT.toString())
                .populations(
                    List.of(
                        new Population(
                            "id-11", PopulationType.INITIAL_POPULATION, "initial population")))
                .build(),
            true),
        // correct Cohort initial population but with an extra, invalid population
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.COHORT.toString())
                .populations(
                    List.of(
                        new Population("id-1", PopulationType.INITIAL_POPULATION, ""),
                        new Population("id-2", PopulationType.MEASURE_POPULATION, "Something")))
                .build(),
            false),
        // invalid, Cohort population
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.COHORT.toString())
                .populations(List.of(new Population("id-3", PopulationType.INITIAL_POPULATION, "")))
                .build(),
            false));
  }

  private static Stream<Arguments> proportionProvider() {
    return Stream.of( // valid Proportion definitions with no optional populations
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.PROPORTION.toString())
                .populations(
                    List.of(
                        new Population("id-1", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-2", PopulationType.NUMERATOR, "pop2"),
                        new Population("id-3", PopulationType.DENOMINATOR, "pop3")))
                .build(),
            true),
        // valid Proportion definitions with all optional populations
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.PROPORTION.toString())
                .populations(
                    List.of(
                        new Population("id-4", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-5", PopulationType.NUMERATOR, "pop2"),
                        new Population("id-6", PopulationType.NUMERATOR_EXCLUSION, "pop3"),
                        new Population("id-7", PopulationType.DENOMINATOR, "pop4"),
                        new Population("id-8", PopulationType.DENOMINATOR_EXCLUSION, "pop5"),
                        new Population("id-9", PopulationType.DENOMINATOR_EXCEPTION, "pop6")))
                .build(),
            true),
        // invalid Proportion definitions with missing value for optional population
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.PROPORTION.toString())
                .populations(
                    List.of(
                        new Population("id-15", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-10", PopulationType.NUMERATOR, "pop2"),
                        new Population("id-11", PopulationType.NUMERATOR_EXCLUSION, "pop3"),
                        new Population("id-12", PopulationType.DENOMINATOR, "pop4"),
                        new Population("id-13", PopulationType.DENOMINATOR_EXCLUSION, ""),
                        new Population("id-14", PopulationType.DENOMINATOR_EXCEPTION, "pop6")))
                .build(),
            false),
        // invalid Proportion definitions with missing value for required population
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.PROPORTION.toString())
                .populations(
                    List.of(
                        new Population("id-16", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-17", PopulationType.NUMERATOR, ""),
                        new Population("id-18", PopulationType.NUMERATOR_EXCLUSION, "pop3"),
                        new Population("id-19", PopulationType.DENOMINATOR, "pop4"),
                        new Population("id-20", PopulationType.DENOMINATOR_EXCLUSION, "pop5"),
                        new Population("id-21", PopulationType.DENOMINATOR_EXCEPTION, "pop6")))
                .build(),
            false),
        // invalid Proportion definitions with all required populations but extra population
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.PROPORTION.toString())
                .populations(
                    List.of(
                        new Population("id-22", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-23", PopulationType.NUMERATOR, "pop2"),
                        new Population("id-24", PopulationType.DENOMINATOR, "pop4"),
                        new Population("id-25", PopulationType.DENOMINATOR_EXCLUSION, "pop5"),
                        new Population("id-26", PopulationType.DENOMINATOR_EXCEPTION, "pop6"),
                        new Population("id-27", PopulationType.MEASURE_POPULATION, "pop9")))
                .build(),
            false));
  }

  private static Stream<Arguments> continuousVariableProvider() {
    return Stream.of(
        // invalid CV definitions with mismatched definitions
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
                .populations(
                    List.of(
                        new Population("id-28", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-29", PopulationType.NUMERATOR, "pop2"),
                        new Population("id-30", PopulationType.DENOMINATOR, "pop4")))
                .build(),
            false),
        // invalid CV definitions missing required populations
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
                .populations(
                    List.of(new Population("id-31", PopulationType.INITIAL_POPULATION, "pop1")))
                .build(),
            false),
        // invalid CV definitions with mismatched definitions
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
                .populations(
                    List.of(
                        new Population("id-32", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-34", PopulationType.NUMERATOR, "pop2"),
                        new Population("id-33", PopulationType.DENOMINATOR, "pop4")))
                .build(),
            false),
        // valid CV definitions with required populations
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
                .populations(
                    List.of(
                        new Population("id-35", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-36", PopulationType.MEASURE_POPULATION, "pop2")))
                .build(),
            true),
        // valid CV definitions with required and optional populations
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
                .populations(
                    List.of(
                        new Population("id-37", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-38", PopulationType.MEASURE_POPULATION, "pop2"),
                        new Population(
                            "id-39", PopulationType.MEASURE_POPULATION_EXCLUSION, "pop3")))
                .build(),
            true));
  }

  private static Stream<Arguments> ratioProvider() {
    return Stream.of( // valid Proportion definitions with no optional populations
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.RATIO.toString())
                .populations(
                    List.of(
                        new Population("id-40", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-41", PopulationType.NUMERATOR, "pop2"),
                        new Population("id-42", PopulationType.DENOMINATOR, "pop3")))
                .build(),
            true),
        // valid Proportion definitions with all optional populations
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.RATIO.toString())
                .populations(
                    List.of(
                        new Population("id-43", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-44", PopulationType.NUMERATOR, "pop2"),
                        new Population("id-45", PopulationType.NUMERATOR_EXCLUSION, "pop3"),
                        new Population("id-46", PopulationType.DENOMINATOR, "pop4"),
                        new Population("id-47", PopulationType.DENOMINATOR_EXCLUSION, "pop5")))
                .build(),
            true),
        // invalid Proportion definitions with missing value for optional population
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.RATIO.toString())
                .populations(
                    List.of(
                        new Population("id-48", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-49", PopulationType.NUMERATOR, "pop2"),
                        new Population("id-50", PopulationType.NUMERATOR_EXCLUSION, "pop3"),
                        new Population("id-51", PopulationType.DENOMINATOR, "pop4"),
                        new Population("id-52", PopulationType.DENOMINATOR_EXCLUSION, "")))
                .build(),
            false),
        // invalid Proportion definitions with missing value for required population
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.RATIO.toString())
                .populations(
                    List.of(
                        new Population("id-53", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-54", PopulationType.NUMERATOR, ""),
                        new Population("id-55", PopulationType.NUMERATOR_EXCLUSION, "pop3"),
                        new Population("id-56", PopulationType.DENOMINATOR, "pop4")))
                .build(),
            false),
        // invalid Proportion definitions with all required populations but extra population
        Arguments.of(
            Group.builder()
                .scoring(MeasureScoring.RATIO.toString())
                .populations(
                    List.of(
                        new Population("id-57", PopulationType.INITIAL_POPULATION, "pop1"),
                        new Population("id-58", PopulationType.NUMERATOR, "pop2"),
                        new Population("id-59", PopulationType.DENOMINATOR, "pop4"),
                        new Population("id-60", PopulationType.DENOMINATOR_EXCLUSION, "pop5"),
                        new Population("id-61", PopulationType.DENOMINATOR_EXCEPTION, "pop6")))
                .build(),
            false));
  }
}
