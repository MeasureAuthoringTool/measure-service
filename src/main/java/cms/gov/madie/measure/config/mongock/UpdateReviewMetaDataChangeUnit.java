package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Measure;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ChangeUnit(id = "review_metadata_update", order = "1", author = "madie_dev")
public class UpdateReviewMetaDataChangeUnit {

  @Execution
  public void updateReviewMetaData(MeasureRepository measureRepository) {
    log.info("Entering updateReviewMetaData()");

    List<Measure> measureList = measureRepository.findAll();
    measureList.forEach(
        measure -> {
          var reviewMetaData = measure.getReviewMetaData();
          if (reviewMetaData.getApprovalDate() != null
              && reviewMetaData.getLastReviewDate() != null) {
            var createdAt = measure.getCreatedAt();
            var approvalDate = reviewMetaData.getApprovalDate();
            var lastReviewDate = reviewMetaData.getLastReviewDate();

            boolean updateRequired = false;

            if (approvalDate != null && approvalDate.isBefore(createdAt)) {
              reviewMetaData.setApprovalDate(null);
              updateRequired = true;
            }

            if (lastReviewDate != null && lastReviewDate.isBefore(createdAt)) {
              reviewMetaData.setLastReviewDate(null);
              updateRequired = true;
            }

            if (updateRequired) {
              log.info("Updating reviewMetaData for measure with ID [{}]", measure.getId());
              measure.setReviewMetaData(reviewMetaData);
              measureRepository.save(measure);
            }
          }
        });
    log.info("Completed updateReviewMetaData()");
  }

  @RollbackExecution
  public void rollbackExecution() throws Exception {
    log.debug("Entering rollbackExecution()");
  }
}
