package cms.gov.madie.measure.services;

import cms.gov.madie.measure.repositories.MeasureActionLogRepository;
import cms.gov.madie.measure.repositories.MeasureSetActionLogRepository;
import cms.gov.madie.measure.utils.ActionLogCollectionType;
import gov.cms.madie.models.common.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionLogService {

  private final MeasureActionLogRepository measureActionLogRepository;
  private final MeasureSetActionLogRepository measureSetActionLogRepository;

  public boolean logAction(
      final String targetId,
      Class targetClass,
      final ActionType actionType,
      final String userId,
      final String... additionalActionMessage) {
    final String collection = ActionLogCollectionType.getCollectionNameForClazz(targetClass);

    return measureActionLogRepository.pushEvent(
        targetId,
        Action.builder()
            .actionType(actionType)
            .performedBy(userId)
            .performedAt(Instant.now())
            .additionalActionMessage(Arrays.toString(additionalActionMessage))
            .build(),
        collection);
  }

  public boolean logShareAccessControlAction(
      final String targetId,
      Class targetClass,
      final ActionType actionType,
      final String userId,
      final String sharedWith,
      final String... additionalActionMessage) {
    final String collection = ActionLogCollectionType.getCollectionNameForClazz(targetClass);

    return measureSetActionLogRepository.pushEvent(
        targetId,
        AccessControlAction.builder()
            .actionType(actionType)
            .performedBy(userId)
            .performedAt(Instant.now())
            .sharedWith(sharedWith)
            .additionalActionMessage(Arrays.toString(additionalActionMessage))
            .build(),
        collection);
  }

  public MeasureSetActionLog findMeasureSetActionLogByTargetId(final String targetId) {
    return measureSetActionLogRepository.findByTargetId(targetId).orElse(null);
  }

  public boolean logMeasureSetAction(
      final String targetId,
      Class targetClass,
      final ActionType actionType,
      final String userId,
      final String... additionalActionMessage) {
    final String collection = ActionLogCollectionType.getCollectionNameForClazz(targetClass);

    return measureSetActionLogRepository.pushEvent(
        targetId,
        Action.builder()
            .actionType(actionType)
            .performedBy(userId)
            .performedAt(Instant.now())
            .additionalActionMessage(Arrays.toString(additionalActionMessage))
            .build(),
        collection);
  }

  public List<Action> findMeasureHistory(String targetId, String measureSetId, String direction) {
    List<Action> measureHistory = new ArrayList<>();

    // Get measure specific actions
    List<ActionLog> measureActionLogs = measureActionLogRepository.findByTargetId(targetId);
    if (!CollectionUtils.isEmpty(measureActionLogs)) {
      measureActionLogs.forEach(
          log -> {
            if (!CollectionUtils.isEmpty(log.getActions())) {
              measureHistory.addAll(log.getActions());
            }
          });
    }

    // Get measure-set actions
    Optional<MeasureSetActionLog> measureSetActionLog =
        measureSetActionLogRepository.findByTargetId(measureSetId);
    if (measureSetActionLog.isPresent()
        && !CollectionUtils.isEmpty(measureSetActionLog.get().getActions())) {
      measureHistory.addAll(measureSetActionLog.get().getActions());
    }

    // Sort performedAt based on direction
    if ("desc".equalsIgnoreCase(direction)) {
      measureHistory.sort(
          Comparator.comparing(
              Action::getPerformedAt, Comparator.nullsLast(Comparator.reverseOrder())));
    } else {
      measureHistory.sort(
          Comparator.comparing(
              Action::getPerformedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    log.debug("Found {} total actions for measure ID: {}", measureHistory.size(), targetId);
    return measureHistory;
  }

  public List<Action> getPaginatedMeasureHistory(
      String targetId, String measureSetId, int limit, int page, String direction) {
    List<Action> measureHistory = findMeasureHistory(targetId, measureSetId, direction);

    // Apply pagination
    int startIndex = page * limit;
    int endIndex = Math.min(startIndex + limit, measureHistory.size());

    if (startIndex >= measureHistory.size()) {
      return new ArrayList<>();
    }

    return measureHistory.subList(startIndex, endIndex);
  }
}
