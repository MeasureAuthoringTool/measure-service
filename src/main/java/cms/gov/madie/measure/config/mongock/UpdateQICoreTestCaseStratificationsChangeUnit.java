package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.Stratification;
import gov.cms.madie.models.measure.TestCasePopulationValue;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@ChangeUnit(id = "qi_core_testcase_strat_update", order = "1", author = "madie_dev")
public class UpdateQICoreTestCaseStratificationsChangeUnit {
  @Setter @Getter private List<Measure> tempMeasures;

  @Execution
  public void updateQICoreTestCaseStratifications(MeasureRepository measureRepository)
      throws Exception {
    log.debug("Entering updateQICoreTestCaseStratifications()");
    List<Measure> measureList = measureRepository.findAllByModel(ModelType.QI_CORE.getValue());
    log.info("found QI Core measures: {}", measureList.size());

    measureList.forEach(
        measure -> {
          if (!measure.isActive()
              || CollectionUtils.isEmpty(measure.getGroups())
              || CollectionUtils.isEmpty(measure.getTestCases())) {
            return;
          }
          for (var testCase : measure.getTestCases()) {
            if (CollectionUtils.isEmpty(testCase.getGroupPopulations())) {
              continue;
            }
            for (var groupPopulation : testCase.getGroupPopulations()) {
              if (CollectionUtils.isEmpty(groupPopulation.getStratificationValues())) {
                continue;
              }
              for (var stratificationValue : groupPopulation.getStratificationValues()) {
                Optional<Stratification> matchingGroupStratification =
                    measure.getGroups().stream()
                        .flatMap(group -> group.getStratifications().stream())
                        .filter(strat -> strat.getId().equals(stratificationValue.getId()))
                        .findFirst();

                if (matchingGroupStratification.isPresent()
                    && stratificationValue.getPopulationValues() != null) {
                  List<TestCasePopulationValue> filteredStratifiedPopulationValues =
                      stratificationValue.getPopulationValues().stream()
                          .filter(
                              populationValue ->
                                  matchingGroupStratification.get().getAssociations().stream()
                                      .anyMatch(
                                          validAssociation ->
                                              validAssociation
                                                  .getDisplay()
                                                  .equalsIgnoreCase(
                                                      populationValue.getName().getDisplay())))
                          .collect(Collectors.toList());
                  stratificationValue.setPopulationValues(filteredStratifiedPopulationValues);
                }
              }
            }
          }

          measure.setTestCases(measure.getTestCases());
          measureRepository.save(measure);
        });
  }

  @RollbackExecution
  public void rollbackExecution(MeasureRepository measureRepository) throws Exception {
    log.debug("Entering rollbackExecution() ");
    if (CollectionUtils.isNotEmpty(tempMeasures)) {
      tempMeasures.forEach(measureRepository::save);
    }
  }
}
