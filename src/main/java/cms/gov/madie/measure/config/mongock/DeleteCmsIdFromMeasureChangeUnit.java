package cms.gov.madie.measure.config.mongock;

import com.mongodb.bulk.BulkWriteResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@ChangeUnit(id = "delete_cms_id_field", order = "1", author = "madie_dev")
public class DeleteCmsIdFromMeasureChangeUnit {

  @Execution
  public void deleteCmsId(MongoTemplate mongoTemplate) {
    Query query = new Query();
    Update update = new Update().unset("cmsId");
    BulkOperations bulkOperations =
        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "measure");
    bulkOperations.updateMulti(query, update);
    BulkWriteResult result = bulkOperations.execute();
    log.info("CMS Id removed for: {} measures", result.getModifiedCount());
  }

  @RollbackExecution
  public void rollbackExecution() {
    log.debug("Nothing to rollback");
  }
}
