package cms.gov.madie.measure.validations;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureScoring;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.TestCaseGroupPopulation;
import gov.cms.madie.models.measure.TestCasePopulationValue;
import gov.cms.madie.models.common.Version;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringPopulationValidatorTest {

  @Mock private ConstraintValidatorContext validatorContext;
  @Mock private MeasureRepository measureRepository;
  private final ScoringPopulationValidator validator = new ScoringPopulationValidator();

  private Group group1;
  private Measure measure;

  @BeforeEach
  public void setUp() {
    ReflectionTestUtils.setField(validator, "measureRepository", measureRepository);

    group1 =
        Group.builder()
            .id("GroupId")
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1")))
            .groupDescription("Description")
            .build();
    List<Group> groups = new ArrayList<>();
    groups.add(group1);
    measure =
        Measure.builder()
            .active(true)
            .id("xyz-p13r-13ert")
            .cql("test cql")
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .groups(groups)
            .createdAt(Instant.now())
            .createdBy("test user")
            .lastModifiedAt(Instant.now())
            .lastModifiedBy("test user")
            .build();
  }

  @Test
  public void testValidatorReturnsTrueForNull() {
    boolean output = validator.isValid(null, validatorContext);
    assertTrue(output);
  }

  @Test
  public void testValidatorReturnsFalseForMissingScoring() {
    var testCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .scoring(null)
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder()
                        .name(PopulationType.INITIAL_POPULATION)
                        .build()))
            .build();
    boolean output = validator.isValid(testCaseGroupPopulation, validatorContext);
    assertFalse(output);
  }

  @Test
  public void testValidatorReturnsFalseForNullPopulationsList() {
    var testCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .populationValues(null)
            .scoring(MeasureScoring.COHORT.toString())
            .build();
    boolean output = validator.isValid(testCaseGroupPopulation, validatorContext);
    assertFalse(output);
  }

  @Test
  public void testValidatorReturnsFalseForEmptyPopulationsList() {
    var testCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .populationValues(List.of())
            .scoring(MeasureScoring.COHORT.toString())
            .build();
    boolean output = validator.isValid(testCaseGroupPopulation, validatorContext);
    assertFalse(output);
  }

  @Test
  public void testValidatorReturnsFalseForMissingPopulation() {
    when(measureRepository.findGroupById(anyString())).thenReturn(Optional.of(measure));
    var testCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("GroupId")
            .populationValues(List.of(TestCasePopulationValue.builder().build()))
            .scoring(MeasureScoring.COHORT.toString())
            .build();
    boolean output = validator.isValid(testCaseGroupPopulation, validatorContext);
    assertFalse(output);
  }

  @Test
  public void testValidatorReturnsFalseForIncorrectPopulation() {
    when(measureRepository.findGroupById(anyString())).thenReturn(Optional.of(measure));
    var testCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("GroupId")
            .populationValues(
                List.of(TestCasePopulationValue.builder().name(PopulationType.DENOMINATOR).build()))
            .scoring(MeasureScoring.COHORT.toString())
            .build();
    boolean output = validator.isValid(testCaseGroupPopulation, validatorContext);
    assertFalse(output);
  }

  @Test
  public void testValidatorReturnsTrueForCorrectPopulation() {
    when(measureRepository.findGroupById(anyString())).thenReturn(Optional.of(measure));
    var testCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("GroupId")
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder()
                        .name(PopulationType.INITIAL_POPULATION)
                        .build()))
            .scoring(MeasureScoring.COHORT.toString())
            .build();
    boolean output = validator.isValid(testCaseGroupPopulation, validatorContext);
    assertTrue(output);
  }
}
