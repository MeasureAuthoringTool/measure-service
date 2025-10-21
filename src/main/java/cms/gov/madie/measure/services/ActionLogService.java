package cms.gov.madie.measure.services;

import cms.gov.madie.measure.repositories.MeasureActionLogRepository;
import cms.gov.madie.measure.repositories.MeasureSetActionLogRepository;
import cms.gov.madie.measure.utils.ActionLogCollectionType;
import gov.cms.madie.models.common.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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
    List<Action> combinedActionLogs =
        Stream.concat(
                measureActionLogRepository
                    .findByTargetId(measureId)
                    .map(ActionLog::getActions)
                    .stream()
                    .flatMap(List::stream),
                measureSetActionLogRepository
                    .findByTargetId(measureSetId)
                    .map(MeasureSetActionLog::getActions)
                    .stream()
                    .flatMap(List::stream)
                    // exclude CREATED from measure set
                    .filter(a -> !ActionType.CREATED.equals(a.getActionType())))
            // sort descending
            .sorted(Comparator.comparing(Action::getPerformedAt).reversed())
            .toList();

    return combinedActionLogs;
  }
}
