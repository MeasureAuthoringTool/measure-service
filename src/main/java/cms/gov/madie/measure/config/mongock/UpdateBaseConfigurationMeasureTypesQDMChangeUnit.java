package cms.gov.madie.measure.config.mongock;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import gov.cms.madie.models.common.ModelType;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@Slf4j
@ChangeUnit(id = "qdm_base_configuration_types_update", order = "1", author = "madie_dev")
public class UpdateBaseConfigurationMeasureTypesQDMChangeUnit {
  @Execution
  public void updateMeasureTypes(MongoDatabase mongoDatabase) {

    MongoCollection<Document> collection = mongoDatabase.getCollection("measure");

    // Filter all documents that are model QDM
    Document filter =
        new Document("model", ModelType.QDM_5_6.getValue()).append("measureMetaData.draft", true);

    // PATIENT_REPORTED_OUTCOME_PERFORMANCE -> PATIENT_REPORTED_OUTCOME
    collection.updateMany(
        Filters.and(
            filter, Filters.in("baseConfigurationTypes", "PATIENT_REPORTED_OUTCOME_PERFORMANCE")),
        Updates.set("baseConfigurationTypes.$", "PATIENT_REPORTED_OUTCOME"));

    // INTERMEDIATE_CLINICAL_OUTCOME -> INTERMEDIATE_OUTCOME
    collection.updateMany(
        Filters.and(filter, Filters.in("baseConfigurationTypes", "INTERMEDIATE_CLINICAL_OUTCOME")),
        Updates.set("baseConfigurationTypes.$", "INTERMEDIATE_OUTCOME"));
  }

  @RollbackExecution
  public void rollbackExecution() {
    log.debug("Something went wrong while updating measure types.");
  }
}
