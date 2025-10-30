package cms.gov.madie.measure.repositories;

import com.mongodb.client.result.UpdateResult;

import cms.gov.madie.measure.utils.ActionLogCollectionType;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionLog;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Repository
public class ActionLogRepositoryImpl implements ActionLogRepository {
  private final MongoTemplate mongoTemplate;

  @Override
  public boolean pushEvent(String targetId, Action action, String collection) {
    if (targetId == null || targetId.isEmpty() || action == null) {
      return false;
    }
    Update update = new Update();
    UpdateResult upsert =
        mongoTemplate.upsert(
            new Query(Criteria.where("targetId").is(targetId)),
            update.push("actions").value(action),
            collection);
    return upsert.getUpsertedId() != null || upsert.getModifiedCount() == 1;
  }

  @Override
  public List<ActionLog> findAllActionLogs(Class<?> targetClass) {
    final String collection = ActionLogCollectionType.getCollectionNameForClazz(targetClass);
    return mongoTemplate.findAll(ActionLog.class, collection);
  }

  @Override
  public Collection<ActionLog> saveAllActionLogs(List<ActionLog> actionLogs, Class<?> targetClass) {
    final String collection = ActionLogCollectionType.getCollectionNameForClazz(targetClass);
    return mongoTemplate.insert(actionLogs, collection);
  }

  @Override
  @Transactional
  public void removeActionsByUsers(List<String> users, Class<?> targetClass) {
    final String collection = ActionLogCollectionType.getCollectionNameForClazz(targetClass);
    log.debug("Removing Actions performed by users: [{}] from collection: [{}]", users, collection);
    Query query = new Query(Criteria.where("actions.performedBy").in(users));
    Update update =
        new Update().pull("actions", Query.query(Criteria.where("performedBy").in(users)));

    UpdateResult result = mongoTemplate.updateMulti(query, update, collection);
    log.info(
        "removeActionsByUsers: UpdateResult: matchedAcount: {} modifiedCount: {}",
        result.getMatchedCount(),
        result.getModifiedCount());

    Query emptyActionsQuery = new Query(Criteria.where("actions").size(0));
    mongoTemplate.remove(emptyActionsQuery, collection);
  }
}
