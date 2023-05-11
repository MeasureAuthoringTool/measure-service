package cms.gov.madie.measure.config;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;

@ChangeUnit(id = "add_measure_set", order = "1", author = "madie_dev")
public class AddMeasureSetChangeUnit {

  @Execution
  public void addMeasureSetValues(
      MeasureSetRepository measureSetRepository, MeasureRepository measureRepository) {
    List<Measure> distinctMeasures = measureRepository.findDistinctByMeasureSetField();
    distinctMeasures.forEach(
        distinctMeasure -> {
          if (!measureSetRepository.existsByMeasureSetId(distinctMeasure.getMeasureSetId())) {
            MeasureSet measureSet =
                MeasureSet.builder()
                    .measureSetId(distinctMeasure.getMeasureSetId())
                    .owner(distinctMeasure.getCreatedBy())
                    .build();
            measureSetRepository.save(measureSet);
          }
        });
  }

  @RollbackExecution
  public void rollbackExecution(MeasureSetRepository measureSetRepository) {
    measureSetRepository.deleteAll();
  }
}
