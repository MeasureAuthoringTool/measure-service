package cms.gov.madie.measure.validations;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureScoring;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.TestCaseGroupPopulation;
import gov.cms.madie.models.measure.TestCasePopulationValue;
import gov.cms.madie.models.validators.ValidScoringPopulation;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ScoringPopulationValidator
    implements ConstraintValidator<ValidScoringPopulation, TestCaseGroupPopulation> {

  @Autowired MeasureRepository measureRepository;

  /**
   * Validates based on measureGroupPopulations If testCaseGroupPopulations contains all
   * measureGroupPopulations return true
   *
   * @param testCaseGroupPopulation
   * @param context
   * @return
   */
  @Override
  public boolean isValid(
      TestCaseGroupPopulation testCaseGroupPopulation, ConstraintValidatorContext context) {
    if (testCaseGroupPopulation == null) {
      return true;
    }
    if (testCaseGroupPopulation.getScoring() == null
        || testCaseGroupPopulation.getScoring().trim().isEmpty()) {
      return false;
    }
    MeasureScoring scoring = MeasureScoring.valueOfText(testCaseGroupPopulation.getScoring());
    List<TestCasePopulationValue> populationValues = testCaseGroupPopulation.getPopulationValues();

    if (scoring == null || populationValues == null || populationValues.isEmpty()) {
      return false;
    }

    List<PopulationType> receivedPopulations =
        populationValues.stream()
            .map(TestCasePopulationValue::getName)
            .distinct()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    Optional<Measure> measure =
        measureRepository.findGroupById(testCaseGroupPopulation.getGroupId());

    if (measure.isPresent()) {
      Optional<Group> measureGroup =
          measure.get().getGroups().stream()
              .filter(group -> group.getId().equals(testCaseGroupPopulation.getGroupId()))
              .findFirst();
      if (measureGroup.isPresent()) {
        List<Population> groupPopulations = measureGroup.get().getPopulations();
        return receivedPopulations.size() == groupPopulations.size()
            && receivedPopulations.stream()
                .allMatch(
                    population ->
                        groupPopulations.stream()
                            .anyMatch(gp -> Objects.equals(gp.getName(), population)));
      }
    }
    return false;
  }
}
