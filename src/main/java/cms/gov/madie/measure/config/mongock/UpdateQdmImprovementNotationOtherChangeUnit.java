package cms.gov.madie.measure.config.mongock;

import com.mongodb.bulk.BulkWriteResult;
import gov.cms.madie.models.common.ModelType;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@ChangeUnit(id = "update_qdm_improvement_notation_other_field", order = "1", author = "madie_dev")
public class UpdateQdmImprovementNotationOtherChangeUnit {

  @Execution
  public void updateImprovementNotationOtherField(MongoTemplate mongoTemplate) {
    BulkWriteResult result =
        renameDocumentField(
            mongoTemplate, "improvementNotationOther", "improvementNotationDescription");
    log.info("Reporting Updated for QDM measures: {}", result.getModifiedCount());
  }

  @RollbackExecution
  public void rollbackExecution(MongoTemplate mongoTemplate) {
    log.debug(
        "Something went wrong while updating the measure improvement notation description."
            + "Rolling back the updates.");
    BulkWriteResult result =
        renameDocumentField(
            mongoTemplate, "improvementNotationDescription", "improvementNotationOther");
    log.info("Rolling back count: {}", result.getModifiedCount());
  }

  private BulkWriteResult renameDocumentField(
      MongoTemplate mongoTemplate, String originalField, String newField) {
    Query query = new Query(Criteria.where("model").is(ModelType.QDM_5_6.getValue()));
    BulkOperations bulkOperations =
        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "measure");
    Update update = new Update().rename(originalField, newField);
    bulkOperations.updateMulti(query, update);
    return bulkOperations.execute();
  }
}
