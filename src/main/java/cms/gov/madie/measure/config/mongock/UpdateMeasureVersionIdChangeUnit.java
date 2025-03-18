package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Measure;
import org.apache.commons.lang3.StringUtils;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@ChangeUnit(id = "update_measure_version_id", order = "1", author = "madie_dev")
public class UpdateMeasureVersionIdChangeUnit {
  @Execution
  public void updateMeasureVersionId(MeasureRepository measureRepository) {
    log.info("updating the measure version ids");
    List<Measure> measures = measureRepository.findAll();
    List<String> updatedMeasures = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(measures)) {
      measures.forEach(
          measure -> {
            try {
              UUID.fromString(measure.getVersionId());
            } catch (IllegalArgumentException exception) {
              measure.setVersionId(UUID.randomUUID().toString());
              measureRepository.save(measure);
              updatedMeasures.add(measure.getId());
            }
          });
      log.info(
          "Version id updated for the following measures: "
              + StringUtils.join(updatedMeasures, ", "));
    }
  }

  @RollbackExecution
  public void rollbackExecution() {
    log.debug("Something went wrong while updating measure version ids.");
  }
}
