package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@ChangeUnit(id = "update_improvement_notation_qiCore", order = "1", author = "madie_dev")
public class UpdateImprovementNotationQiCoreChangeUnit {

  private static final String DECREASE_OLD = "decrease";
  private static final String DECREASE_NEW = "Decreased score indicates improvement";
  private static final String INCREASE_OLD = "increase";
  private static final String INCREASE_NEW = "Increased score indicates improvement";

  private final List<Measure> backupMeasures = new ArrayList<>();

  // If measure is QiCore and is in Draft state,
  // and 1 or more of its groups has incorrect improvement notation value, then update it to latest
  // value
  @Execution
  public void updateImprovementNotationQiCore(MeasureRepository measureRepository) {
    log.info("Entering updateImprovementNotationQiCore");
    List<Measure> measureList = measureRepository.findAllByModel(ModelType.QI_CORE.getValue());
    log.info("found {} Qi-Core measures", measureList.size());
    measureList.stream()
        .filter(
            measure ->
                measure.getMeasureMetaData().isDraft()
                    && CollectionUtils.isNotEmpty(measure.getGroups()))
        .forEach(
            measure -> {
              AtomicBoolean modified = new AtomicBoolean(false);
              measure.getGroups().stream()
                  .filter(group -> StringUtils.isNotBlank(group.getImprovementNotation()))
                  .forEach(
                      group -> {
                        String currentValue = group.getImprovementNotation();
                        if (DECREASE_OLD.equalsIgnoreCase(currentValue)) {
                          group.setImprovementNotation(DECREASE_NEW);
                          modified.set(true);
                        } else if (INCREASE_OLD.equalsIgnoreCase(currentValue)) {
                          group.setImprovementNotation(INCREASE_NEW);
                          modified.set(true);
                        }
                      });
              if (modified.get()) {
                backupMeasures.add(measure);
                measureRepository.save(measure);
              }
            });
  }

  @RollbackExecution
  public void rollbackExecution(MeasureRepository measureRepository) {
    log.debug("Something went wrong while executing updateImprovementNotationQiCore");
    measureRepository.saveAll(backupMeasures);
  }
}
