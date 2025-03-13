package cms.gov.madie.measure.config.mongock;

import gov.cms.madie.models.measure.Measure;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.data.util.Pair;

import java.util.List;

@Slf4j
@ChangeUnit(id = "update_cms_id", order = "1", author = "madie_dev")
public class UpdateCmsIdChangeUnit {
  @Execution
  public void updateCmsId(MongoTemplate mongoTemplate) {
    // aggregation to fetch one measure per measure set instead of fetching all measures in a
    // measure set
    TypedAggregation<Measure> measureAggregation =
        Aggregation.newAggregation(
            Measure.class,
            Aggregation.match(
                new Criteria()
                    .andOperator(
                        Criteria.where("cmsId").ne(null),
                        Criteria.where("cmsId").ne("0"),
                        Criteria.where("cmsId").ne(""))),
            Aggregation.group("measureSetId").first("$$ROOT").as("measure"),
            Aggregation.project(
                "measure._id", "measure.cmsId", "measure.measureSetId", "measure.measureName"));

    AggregationResults<MeasureMeta> aggregationResults =
        mongoTemplate.aggregate(measureAggregation, MeasureMeta.class);
    List<MeasureMeta> measureMetas = aggregationResults.getMappedResults();
    if (CollectionUtils.isEmpty(measureMetas)) {
      return;
    }

    List<Pair<Query, UpdateDefinition>> updatePairs =
        measureMetas.stream()
            .map(
                measureMeta -> {
                  // prepare measure set update queries
                  Query query =
                      Query.query(Criteria.where("measureSetId").is(measureMeta.getMeasureSetId()));
                  // make sure there is no FHIR appended to the cms id
                  String cmsId = measureMeta.getCmsId().trim().replace("FHIR", "");
                  // Update update = Update.update("cmsId", Integer.parseInt(cmsId));
                  UpdateDefinition update = Update.update("cmsId", Integer.parseInt(cmsId));
                  return Pair.of(query, update);
                })
            .toList();

    BulkOperations bulkOperations =
        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "measureSet");
    // BulOperations.updateMulti(List<Pair<Query, UpdateDefinition>> updates)
    bulkOperations.updateMulti(updatePairs);
    bulkOperations.execute();
  }

  @RollbackExecution
  public void rollbackExecution(MongoTemplate mongoTemplate) {
    log.debug("Something went wrong while updating the CMS IDs. rolling back the updates.");
    BulkOperations bulkOperations =
        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "measureSet");
    Query query = new Query();
    Update update = new Update().unset("cmsId");
    bulkOperations.updateMulti(query, update);
    bulkOperations.execute();
  }

  @Data
  @Builder
  static class MeasureMeta {
    private String id;
    private String cmsId;
    private String measureSetId;
    private String measureName;
  }
}
