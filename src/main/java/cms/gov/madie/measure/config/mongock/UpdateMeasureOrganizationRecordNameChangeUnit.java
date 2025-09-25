package cms.gov.madie.measure.config.mongock;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@ChangeUnit(id = "measure_orgs_update_record_name", order = "1", author = "madie_dev")
public class UpdateMeasureOrganizationRecordNameChangeUnit {

  // this method with updated filter conditions and change unit ID can be used for future updates
  @Execution
  public void updateMeasureOrganizationRecordName(MongoTemplate mongoTemplate) {
    log.info("Entering the organizations for updating the The Joint Commission name");

    Query query = new Query(Criteria.where("name").is("The Joint Commission"));
    Update update = new Update().set("name", "Joint Commission");
    mongoTemplate.updateFirst(query, update, "organization");

    log.info("Completed updateMeasureOrganizationRecordNameChangeUnit()");
  }

  @RollbackExecution
  public void rollbackExecution() {
    log.debug("Entering rollbackExecution()");
  }
}
