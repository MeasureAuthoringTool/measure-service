package cms.gov.madie.measure.repositories;

import java.util.Collection;
import java.util.List;

import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionLog;

public interface ActionLogRepository {

  /**
   * Performs a MongoDB Upsert operation based on the targetId. If a document with the given
   * targetId is found, the provided action will be pushed onto a list on the document. If no
   * document with the given targetId is found, a new one will be created with the provided action
   * as the sole item in the list.
   *
   * @param targetId field to search on
   * @param action action to push into the list of actions for the given targetId
   * @param collection name of collection to write to
   * @return true if upsert is successful, false otherwise
   */
  boolean pushEvent(String targetId, Action action, String collection);

  List<ActionLog> findAllActionLogs(Class<?> targetClass);

  Collection<ActionLog> saveAllActionLogs(List<ActionLog> actionLogs, Class<?> targetClass);

  void removeActionsByUsers(List<String> users, Class<?> clazz);

  Collection<ActionLog> updateAllActionLogs(List<ActionLog> actionLogs, Class<?> targetClass);
}
