package cms.gov.madie.measure.config.mongock;

import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@ChangeUnit(id = "delete_experimental_from_qdm_measuremetadata", order = "1", author = "madie_dev")
public class DeleteExperimentalFromQDMMeasuresChangeUnit {
  @Execution
  public void deleteExperimentalFieldForQDMMeasuresFromMeasureMetaData(
      MongoOperations mongoOperations) {
    log.info("deleting experimental data from measure meta data for QDM measures");
    Query query = new Query(Criteria.where("model").is(ModelType.QDM_5_6.getValue()));
    Update update = new Update().unset("measureMetaData.experimental");

    BulkOperations bulkOperations =
        mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED, Measure.class);
    bulkOperations.updateMulti(query, update);
    bulkOperations.execute();
  }

  @RollbackExecution
  public void rollbackExecution() {
    log.debug("Entering rollbackExecution()");
  }
}
