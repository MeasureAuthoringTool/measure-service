package cms.gov.madie.measure.config.mongock;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import gov.cms.madie.models.measure.Measure;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "delete_sde_description_from_defDescPair", order = "2", author = "madie_dev")
public class DeleteSupplementalDataDescriptionChangeUnit {

  @Execution
  public void deleteSdeDescriptionFromDefDescPair(MongoOperations mongoOperations) {
    Query query = new Query(Criteria.where("supplementalData").exists(true));
    Update update = new Update().unset("supplementalData.$[].description");

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
