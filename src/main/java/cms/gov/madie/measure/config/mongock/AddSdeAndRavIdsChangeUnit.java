package cms.gov.madie.measure.config.mongock;

import com.mongodb.client.result.UpdateResult;
import gov.cms.madie.models.measure.Measure;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;

/**
 * Assigns a unique identifier to every existing SDE (supplementalData) and RAV (riskAdjustments)
 * that does not have one. Only the identifier is added; no other SDE/RAV data is modified.
 */
@Slf4j
@ChangeUnit(id = "add_sde_rav_ids", order = "1", author = "madie_dev")
public class AddSdeAndRavIdsChangeUnit {
  static final String SUPPLEMENTAL_DATA = "supplementalData";
  static final String RISK_ADJUSTMENTS = "riskAdjustments";
  static final String ID = "_id";

  // identifiers added by this change unit, so they can be removed on rollback
  @Getter private final List<Pair<Query, Update>> rollbackUpdates = new ArrayList<>();

  @Execution
  public void addSdeAndRavIds(MongoTemplate mongoTemplate) {
    log.info("Starting - add_sde_rav_ids");
    String collectionName = mongoTemplate.getCollectionName(Measure.class);
    Query query =
        new Query(
            new Criteria()
                .orOperator(
                    Criteria.where(SUPPLEMENTAL_DATA).elemMatch(Criteria.where(ID).is(null)),
                    Criteria.where(RISK_ADJUSTMENTS).elemMatch(Criteria.where(ID).is(null))));
    query.fields().include(SUPPLEMENTAL_DATA).include(RISK_ADJUSTMENTS);

    MigrationStats stats = new MigrationStats();
    try (Stream<Document> measures = mongoTemplate.stream(query, Document.class, collectionName)) {
      measures.forEach(measure -> addIds(mongoTemplate, collectionName, measure, stats));
    }

    log.info(
        "COMPLETED - add_sde_rav_ids. Measures processed: {}, updated: {}, skipped: {}, failed: {}."
            + " SDE ids added: {}, RAV ids added: {}",
        stats.measuresProcessed,
        stats.measuresUpdated,
        stats.measuresSkipped,
        stats.measuresFailed,
        stats.sdeIdsAdded,
        stats.ravIdsAdded);
    if (stats.measuresSkipped > 0 || stats.measuresFailed > 0) {
      log.warn(
          "add_sde_rav_ids: {} measure(s) were not updated. "
              + "Ids for their SDEs/RAVs will be assigned the next time the measure is saved.",
          stats.measuresSkipped + stats.measuresFailed);
    }
  }

  private void addIds(
      MongoTemplate mongoTemplate, String collectionName, Document measure, MigrationStats stats) {
    stats.measuresProcessed++;
    Object measureId = measure.get(ID);
    Criteria criteria = Criteria.where(ID).is(measureId);
    Update update = new Update();
    Criteria rollbackCriteria = Criteria.where(ID).is(measureId);
    Update rollbackUpdate = new Update();

    int sdeIds =
        addMissingIds(
            measure, SUPPLEMENTAL_DATA, criteria, update, rollbackCriteria, rollbackUpdate);
    int ravIds =
        addMissingIds(
            measure, RISK_ADJUSTMENTS, criteria, update, rollbackCriteria, rollbackUpdate);
    if (sdeIds + ravIds == 0) {
      log.warn("add_sde_rav_ids: no SDE/RAV ids to add for measure [{}]", measureId);
      stats.measuresSkipped++;
      return;
    }

    try {
      UpdateResult result = mongoTemplate.updateFirst(new Query(criteria), update, collectionName);
      if (result.getModifiedCount() == 1) {
        stats.measuresUpdated++;
        stats.sdeIdsAdded += sdeIds;
        stats.ravIdsAdded += ravIds;
        rollbackUpdates.add(Pair.of(new Query(rollbackCriteria), rollbackUpdate));
      } else {
        // the measure's SDEs/RAVs changed after they were read, so nothing was updated
        log.warn(
            "add_sde_rav_ids: measure [{}] was modified during the migration, skipping it",
            measureId);
        stats.measuresSkipped++;
      }
    } catch (Exception ex) {
      log.error("add_sde_rav_ids: failed to add SDE/RAV ids to measure [{}]", measureId, ex);
      stats.measuresFailed++;
    }
  }

  /**
   * Adds a $set for each element of the given list that has no identifier. Each $set is guarded by
   * the criteria so that it only applies if that element still exists without an identifier.
   *
   * @return the number of identifiers that will be added
   */
  private int addMissingIds(
      Document measure,
      String field,
      Criteria criteria,
      Update update,
      Criteria rollbackCriteria,
      Update rollbackUpdate) {
    List<?> elements = measure.get(field, List.class);
    if (elements == null) {
      return 0;
    }
    int count = 0;
    for (int i = 0; i < elements.size(); i++) {
      if (!(elements.get(i) instanceof Document element)) {
        log.warn(
            "add_sde_rav_ids: skipping invalid {} entry at index {} on measure [{}]",
            field,
            i,
            measure.get(ID));
        continue;
      }
      if (element.get(ID) != null) {
        continue;
      }
      String elementPath = field + "." + i;
      String idPath = elementPath + "." + ID;
      ObjectId id = ObjectId.get();
      criteria.and(elementPath).exists(true).and(idPath).is(null);
      update.set(idPath, id);
      rollbackCriteria.and(idPath).is(id);
      rollbackUpdate.unset(idPath);
      count++;
    }
    return count;
  }

  @RollbackExecution
  public void rollbackExecution(MongoTemplate mongoTemplate) {
    log.info("Starting rollback - add_sde_rav_ids");
    String collectionName = mongoTemplate.getCollectionName(Measure.class);
    rollbackUpdates.forEach(
        rollback ->
            mongoTemplate.updateFirst(rollback.getFirst(), rollback.getSecond(), collectionName));
    log.info(
        "COMPLETED rollback - add_sde_rav_ids. Measures rolled back: {}", rollbackUpdates.size());
  }

  private static class MigrationStats {
    private int measuresProcessed;
    private int measuresUpdated;
    private int measuresSkipped;
    private int measuresFailed;
    private int sdeIdsAdded;
    private int ravIdsAdded;
  }
}
