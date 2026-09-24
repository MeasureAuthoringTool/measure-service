package cms.gov.madie.measure.config.mongock;

import com.mongodb.client.model.Indexes;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Creates the indexes that back the measure search / user-export queries.
 *
 * <p>The measure search pipeline ({@code MeasureSearchServiceImpl}) filters the {@code measure}
 * collection on {@code active} and then narrows to a set of {@code measureSetId}s before the
 * measureSet {@code $lookup}. Without these indexes those stages are collection scans, which is the
 * dominant cost when many owner/shared searches run concurrently (e.g. the Full User Export).
 *
 * <ul>
 *   <li>{@code measure} : {@code {measureSetId: 1}} - the "restrict to matched sets" match in the
 *       paginated (facet) query, and the measureSet back-join.
 *   <li>{@code measure} : {@code {active: 1, measureSetId: 1}} - the first-stage match in the
 *       match-count query, plus the subsequent grouping by measureSetId.
 *   <li>{@code measureSet} : {@code {owner: 1}} and {@code {"acls.userId": 1}} - index-eligible
 *       ownership lookups (owner is stored lower-cased, so equality matches can use the index).
 * </ul>
 *
 * <p>Index names are left to MongoDB's defaults so that {@code ensureIndex} is idempotent against
 * any equivalent index that already exists (e.g. one created from a Spring {@code @Indexed}
 * annotation).
 */
@Slf4j
@ChangeUnit(id = "add_measure_search_indexes", order = "100", author = "madie_dev")
public class AddMeasureSearchIndexesChangeUnit {

  private static final String MEASURE_SET_ID_INDEX = "measureSetId_1";
  private static final String ACTIVE_MEASURE_SET_ID_INDEX = "active_1_measureSetId_1";
  private static final String OWNER_INDEX = "owner_1";
  private static final String ACLS_USER_ID_INDEX = "acls.userId_1";

  @Execution
  public void createIndexes(MongoTemplate mongoTemplate) {
    log.info("Creating measure search indexes");
    String measureCollection = mongoTemplate.getCollectionName(Measure.class);
    String measureSetCollection = mongoTemplate.getCollectionName(MeasureSet.class);

    mongoTemplate.getCollection(measureCollection).createIndex(Indexes.ascending("measureSetId"));
    mongoTemplate
        .getCollection(measureCollection)
        .createIndex(Indexes.ascending("active", "measureSetId"));
    mongoTemplate.getCollection(measureSetCollection).createIndex(Indexes.ascending("owner"));
    mongoTemplate.getCollection(measureSetCollection).createIndex(Indexes.ascending("acls.userId"));

    log.info("Finished creating measure search indexes");
  }

  @RollbackExecution
  public void rollback(MongoTemplate mongoTemplate) {
    String measureCollection = mongoTemplate.getCollectionName(Measure.class);
    String measureSetCollection = mongoTemplate.getCollectionName(MeasureSet.class);
    dropQuietly(mongoTemplate, measureCollection, MEASURE_SET_ID_INDEX);
    dropQuietly(mongoTemplate, measureCollection, ACTIVE_MEASURE_SET_ID_INDEX);
    dropQuietly(mongoTemplate, measureSetCollection, OWNER_INDEX);
    dropQuietly(mongoTemplate, measureSetCollection, ACLS_USER_ID_INDEX);
  }

  private void dropQuietly(MongoTemplate mongoTemplate, String collection, String indexName) {
    try {
      mongoTemplate.getCollection(collection).dropIndex(indexName);
    } catch (RuntimeException ex) {
      log.warn("Could not drop index [{}]; it may not exist: {}", indexName, ex.getMessage());
    }
  }
}
