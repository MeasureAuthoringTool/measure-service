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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
            .additionalActionMessage(String.join(", ", additionalActionMessage))
            // TODO replace Action's additionalActionMessage with List<String> and remove this join.
            //  Will require a migration of existing Action logs.
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
            .additionalActionMessage(String.join(", ", additionalActionMessage))
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
            .additionalActionMessage(String.join(", ", additionalActionMessage))
            .build(),
        collection);
  }

  public List<Action> findMeasureHistory(String measureId, String measureSetId) {
    List<ActionLog> measureActionLogs = measureActionLogRepository.findByTargetId(measureId);
    Optional<MeasureSetActionLog> measureSetActionLogs =
        measureSetActionLogRepository.findByTargetId(measureSetId);

    List<Action> combinedActionLog = new ArrayList<>();

    if (!CollectionUtils.isEmpty(measureActionLogs)) {
      measureActionLogs.forEach(
          log -> {
            if (!CollectionUtils.isEmpty(log.getActions())) {
              combinedActionLog.addAll(log.getActions());
            }
          });
    }

    measureSetActionLogs.ifPresent(
        log -> {
          if (!CollectionUtils.isEmpty(log.getActions())) {
            combinedActionLog.addAll(log.getActions());
          }
        });

    return combinedActionLog;
  }
}
