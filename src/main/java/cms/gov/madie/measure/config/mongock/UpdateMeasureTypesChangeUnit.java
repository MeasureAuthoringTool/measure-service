package cms.gov.madie.measure.config.mongock;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.BaseConfigurationTypes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@Slf4j
@ChangeUnit(id = "measure_types_update", order = "1", author = "madie_dev")
public class UpdateMeasureTypesChangeUnit {

  @Execution
  public void updateMeasureTypes(MongoDatabase mongoDatabase) {

    MongoCollection<Document> collection = mongoDatabase.getCollection("measure");

    // Filter to match only documents where model is QDM
    Document filter = new Document("model", ModelType.QDM_5_6.getValue());

    collection.updateMany(
        Filters.and(filter, Filters.in("baseConfigurationTypes", "COST_OR_RESOURCE_USE")),
        Updates.set("baseConfigurationTypes.$", BaseConfigurationTypes.RESOURCE_USE));

    collection.updateMany(
        Filters.and(
            filter, Filters.in("baseConfigurationTypes", "PATIENT_ENGAGEMENT_OR_EXPERIENCE")),
        Updates.set("baseConfigurationTypes.$", BaseConfigurationTypes.EXPERIENCE));
  }

  @RollbackExecution
  public void rollbackExecution() {
    log.debug("Something went wrong while updating measure types.");
  }
}
