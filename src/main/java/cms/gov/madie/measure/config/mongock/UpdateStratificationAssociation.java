package cms.gov.madie.measure.config.mongock;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.Stratification;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@ChangeUnit(id = "update_stratification-assocation_20240614", order = "1", author = "madie_dev")
@Slf4j
public class UpdateStratificationAssociation {

  @Setter private List<Measure> tempMeasures;

  @Execution
  public void removeAssociationFromStratification(MeasureRepository measureRepository) {
    List<Measure> measures = measureRepository.findAllByModel(ModelType.QDM_5_6.getValue());
    if (CollectionUtils.isNotEmpty(measures)) {
      setTempMeasures(measures);
      measures.forEach(
          measure -> {
            List<Group> groups = measure.getGroups();
            if (CollectionUtils.isNotEmpty(groups)) {
              groups.forEach(
                  group -> {
                    List<Stratification> strats = group.getStratifications();
                    if (CollectionUtils.isNotEmpty((strats))) {
                      strats.forEach(
                          strat -> {
                            log.info("Stratification...", strat);
                            strat.setAssociation(null);
                          });
                    }
                    group.setStratifications(strats);
                  });
              measure.setGroups(groups);
              measureRepository.save(measure);
            }
          });
    }
  }

  @RollbackExecution
  public void rollbackExecution(MeasureRepository measureRepository) {
    if (CollectionUtils.isNotEmpty(tempMeasures)) {
      tempMeasures.forEach(
          measure -> {
            measureRepository.save(measure);
          });
    }
  }
}
