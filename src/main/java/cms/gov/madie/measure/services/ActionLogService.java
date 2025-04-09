package cms.gov.madie.measure.services;

import cms.gov.madie.measure.repositories.MeasureActionLogRepository;
import cms.gov.madie.measure.repositories.MeasureSetActionLogRepository;
import cms.gov.madie.measure.utils.ActionLogCollectionType;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.MeasureSetActionLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;

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
}
