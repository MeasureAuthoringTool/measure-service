package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.InvalidGroupException;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import gov.cms.madie.models.measure.*;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service(ServiceConstants.QICORE_VALIDATOR)
public class QiCoreModelValidator extends ModelValidator {

  // Groups no longer get validated based on association. Can have 0 to many
  @Override
  public void validateGroupAssociations(Group group) {
    if (group.getStratifications() != null) {
      List<Stratification> list =
          new ArrayList<>(
              group.getStratifications().stream()
                  .filter(
                      test ->
                          (StringUtils.isBlank(test.getCqlDefinition()))
                              && (!StringUtils.isBlank(test.getDescription())
                                  || !CollectionUtils.isEmpty(test.getAssociations())))
                  .toList());
      if (group.getScoring().equals(MeasureScoring.RATIO.toString())
          && group.getPopulations().stream()
                  .filter(
                      population -> population.getName().equals(PopulationType.INITIAL_POPULATION))
                  .toList()
                  .size()
              > 1) {
        list.addAll(
            group.getStratifications().stream()
                .filter(stratification -> stratification.getAssociations().size() > 1)
                .toList());
      }
      if (!CollectionUtils.isEmpty(list)) {
        throw new InvalidGroupException("QiCore group stratifications cannot be associated.");
      }
    }
  }

  @Override
  public void validateGroups(Measure measure) {
    if (CollectionUtils.isEmpty(measure.getGroups())) {
      throw new InvalidResourceStateException(
          "Measure", measure.getId(), "since there is no population criteria on the measure.");
    }

    if (measure.getMeasureMetaData() != null
        && measure.getMeasureMetaData().isComposite()
        && measure.getGroups().stream()
            .anyMatch(
                group ->
                    !CollectionUtils.isEmpty(group.getPopulations())
                        || !CollectionUtils.isEmpty(group.getStratifications()))) {
      throw new InvalidResourceStateException(
          "Measure",
          measure.getId(),
          "since composite measures cannot have populations or stratifications in groups at this time.");
    }
    if (measure.getGroups().stream()
        .anyMatch(g -> CollectionUtils.isEmpty(g.getMeasureGroupTypes()))) {
      throw new InvalidResourceStateException(
          "Measure",
          measure.getId(),
          "since there is at least one Population Criteria with no type.");
    }

    if (measure.getMeasureMetaData() != null && measure.getMeasureMetaData().isDraft()) {
      measure
          .getGroups()
          .forEach(
              group -> {
                if (StringUtils.isBlank(group.getImprovementNotation())
                    && !StringUtils.equals(group.getScoring(), MeasureScoring.COHORT.toString())) {
                  throw new InvalidResourceStateException(
                      "Measure",
                      measure.getId(),
                      "since there is at least one Population Criteria "
                          + "with no improvement notation.");
                }
              });
    }
  }
}
