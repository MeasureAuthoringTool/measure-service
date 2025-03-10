package cms.gov.madie.measure.config;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.GroupPopulationUtil;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.Group;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "set_display_ids_groups", order = "1", author = "madie_dev")
public class GroupAndPopulationDisplayIdChangeUnit {
  @Setter private List<Measure> tempMeasures;

  @Execution
  public void setDisplayIds(MeasureRepository measureRepository) {
    log.info("Entering setDisplayIds()");

    List<Measure> allMeasures = measureRepository.findAll();
    if (CollectionUtils.isEmpty(allMeasures)) {
      log.error("No measures found! Exiting setDisplayIds()!");
      return;
    }

    setTempMeasures(allMeasures);
    allMeasures.stream()
        .forEach(
            measure -> {
              if (!ModelType.QDM_5_6.toString().equalsIgnoreCase(measure.getModel())) {
                List<Group> groups = measure.getGroups();
                if (CollectionUtils.isEmpty(groups)) {
                  return;
                }
                groups.stream()
                    .forEach(
                        group -> {
                          GroupPopulationUtil.setGroupAndPopulationsDisplayIds(measure, group);
                        });
                measureRepository.save(measure);
              }
            });
  }

  @RollbackExecution
  public void rollbackExecution(MeasureRepository measureRepository) throws Exception {
    log.debug("Entering rollbackExecution()");

    if (CollectionUtils.isNotEmpty(tempMeasures)) {
      tempMeasures.forEach(
          measure -> {
            measureRepository.save(measure);
          });
    }
  }
}
