package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Getter
@Slf4j
@ChangeUnit(id = "remove_qicore_measure_group_stratifications", order = "1", author = "madie_dev")
public class RemoveQICoreMeasureGroupStratificationsChangeUnit {

  @Setter private List<Measure> tempMeasures;

  @Execution
  public void removeQICoreMeasureGroupStratifications(MeasureRepository measureRepository)
      throws Exception {
    log.info("Removing legacy QI-Core Stratifications.()");

    // add all QICore to a list and blank strats
    List<Measure> measureList =
        new ArrayList<>(measureRepository.findAllByModel(ModelType.QI_CORE.getValue()));
    measureList.addAll(measureRepository.findAllByModel(ModelType.QI_CORE_6_0_0.getValue()));

    log.info("Modifying [{}] QI-Core measures", measureList.size());

    tempMeasures = new ArrayList<>();

    measureList.forEach(
        measure -> {
          if (CollectionUtils.isEmpty(measure.getGroups())) {
            return;
          }

          Measure modifiedMeasure =
              measure.toBuilder()
                  .groups(
                      measure.getGroups().stream()
                          .map(
                              group -> group.toBuilder().stratifications(new ArrayList<>()).build())
                          .toList())
                  .build();

          tempMeasures.add(modifiedMeasure);
          measureRepository.save(modifiedMeasure);
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
