package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.QdmMeasure;
import gov.cms.madie.models.validators.ImprovementNotationValidator;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@ChangeUnit(id = "qdm_improvement_notation_standardizer", order = "1", author = "madie_dev")
public class QdmImprovementNotationStandardizationChangeUnit {

  @Execution
  public void standardizeQdmImprovementNotationField(MeasureRepository measureRepository) {
    log.info("QdmImprovementNotationStandardizationChangeUnit started!");
    List<Measure> qdmMeasures = measureRepository.findAllByModel(ModelType.QDM_5_6.getValue());
    ImprovementNotationValidator validator = new ImprovementNotationValidator();
    AtomicInteger counter = new AtomicInteger(0);
    if (CollectionUtils.isNotEmpty(qdmMeasures)) {
      qdmMeasures.stream()
          .filter(
              (measure) ->
                  measure.getMeasureMetaData().isDraft()
                      && measure.isActive()
                      && StringUtils.isNotBlank(((QdmMeasure) measure).getImprovementNotation())
                      && !validator.isValid((QdmMeasure) measure, null))
          .forEach(
              measure -> {
                QdmMeasure qdmMeasure = (QdmMeasure) measure;
                log.info(
                    "Updating draft measure [{}] with invalid improvement notation: {} ",
                    measure.getId(),
                    ((QdmMeasure) measure).getImprovementNotation());
                counter.incrementAndGet();
                final String newDescription =
                    StringUtils.isNotBlank(qdmMeasure.getImprovementNotationDescription())
                        ? qdmMeasure.getImprovementNotation()
                            + " - "
                            + qdmMeasure.getImprovementNotationDescription()
                        : qdmMeasure.getImprovementNotation();
                qdmMeasure.setImprovementNotationDescription(newDescription);
                qdmMeasure.setImprovementNotation("Other");
                measureRepository.save(measure);
              });
    }
    log.info(
        "QdmImprovementNotationStandardizationChangeUnit completed, updated {} measures", counter);
  }

  @RollbackExecution
  public void rollbackExecution(MongoTemplate mongoTemplate) {
    // Not able to rollback - not able to differentiate between update
    // measures and measures that already had Other + description
    // Also, rolling back means these measures will be broken again
    log.error(
        "Error detected, but cannot roll back QdmImprovementNotationStandardizationChangeUnit!");
  }
}
