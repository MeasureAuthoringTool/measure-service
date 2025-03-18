package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.Stratification;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@ChangeUnit(id = "measure_group_qdm_strat_id_update", order = "1", author = "madie_dev")
public class UpdateQdmMeasureGroupStratificationsChangeUnit {

  @Setter @Getter private List<Measure> tempMeasures;

  @Execution
  public void updateQdmMeasureGroupStratifications(MeasureRepository measureRepository)
      throws Exception {
    log.debug("Entering updateQdmMeasureGroupStratifications()");

    List<Measure> measureList = measureRepository.findAllByModel(ModelType.QDM_5_6.getValue());
    log.info("found QDM measures: {}", measureList.size());
    final AtomicBoolean changed = new AtomicBoolean();
    tempMeasures = new ArrayList<>();
    measureList.forEach(
        measure -> {
          if (!measure.isActive() || CollectionUtils.isEmpty(measure.getGroups())) {
            return;
          }
          changed.set(false);
          Measure m =
              measure.toBuilder()
                  .groups(
                      measure.getGroups().stream()
                          .map(
                              g -> {
                                if (CollectionUtils.isNotEmpty(g.getStratifications())) {
                                  return g.toBuilder()
                                      .stratifications(
                                          g.getStratifications().stream()
                                              .map(
                                                  s ->
                                                      new Stratification(
                                                          s.getId(),
                                                          s.getDescription(),
                                                          s.getCqlDefinition(),
                                                          s.getAssociation(),
                                                          s.getAssociations(),
                                                          s.getDisplayId()))
                                              .toList())
                                      .build();
                                } else {
                                  return g;
                                }
                              })
                          .toList())
                  .build();

          measure
              .getGroups()
              .forEach(
                  group -> {
                    if (CollectionUtils.isEmpty(group.getStratifications())) {
                      return;
                    }

                    group
                        .getStratifications()
                        .forEach(
                            stratification -> {
                              if (stratification != null
                                  && StringUtils.isBlank(stratification.getId())) {
                                stratification.setId(UUID.randomUUID().toString());
                                changed.set(true);
                              }
                            });
                  });

          if (changed.get()) {
            tempMeasures.add(m);
            measureRepository.save(measure);
          }
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
