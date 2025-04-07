package cms.gov.madie.measure.config.mongock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.nimbusds.oauth2.sdk.util.CollectionUtils;

import cms.gov.madie.measure.repositories.MeasureActionLogRepository;
import cms.gov.madie.measure.repositories.MeasureSetActionLogRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.ActionLog;
import gov.cms.madie.models.common.MeasureSetActionLog;
import gov.cms.madie.models.measure.MeasureSet;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "data_migration_measureset_action_log", order = "1", author = "madie_dev")
public class MeasureSetActionLogMigrationChangeUnit {

  List<ActionLog> actionLogsToBeMigrated = new ArrayList<>();
  List<String> measureSetActionLogIds = new ArrayList<>();

  @Execution
  public void migrateMeasureSetActionLog(
      MeasureSetRepository measureSetRepository,
      MeasureActionLogRepository measureActionLogRepository,
      MeasureSetActionLogRepository measureSetActionLogRepository) {
    log.info("Entering migrateMeasureSetActionLog()");

    List<ActionLog> actionsLogs = measureActionLogRepository.findAll();
    List<String> actionLogIdsToBeMigrated = new ArrayList<>();
    List<MeasureSetActionLog> measureSetActionLogs = new ArrayList<>();

    if (CollectionUtils.isNotEmpty(actionsLogs)) {
      actionsLogs.stream()
          .forEach(
              actionLog -> {
                String targetId = actionLog.getTargetId();
                Optional<MeasureSet> measureSetOpt = measureSetRepository.findById(targetId);
                if (measureSetOpt.isPresent()) {
                  // if the ActionLog should be in MeasureSetActionLog instead:
                  actionLogsToBeMigrated.add(actionLog);
                  actionLogIdsToBeMigrated.add(actionLog.getId());

                  // get the migrated data ready:
                  List<AccessControlAction> accessControlActions =
                      actionLog.getActions().stream()
                          .map(
                              action -> {
                                return AccessControlAction.builder()
                                    .actionType(action.getActionType())
                                    .additionalActionMessage(action.getAdditionalActionMessage())
                                    .performedAt(action.getPerformedAt())
                                    .performedBy(action.getPerformedBy())
                                    .build();
                              })
                          .collect(Collectors.toList());

                  MeasureSetActionLog measureSetActionLog =
                      MeasureSetActionLog.builder()
                          .id(actionLog.getId())
                          .targetId(targetId)
                          .actions(accessControlActions)
                          .build();

                  measureSetActionLogs.add(measureSetActionLog);
                  measureSetActionLogIds.add(actionLog.getId());
                }
              });

      // add MeasureSetActionLog first
      if (CollectionUtils.isNotEmpty(measureSetActionLogs)) {
        measureSetActionLogRepository.saveAll(measureSetActionLogs);
      }
      // delete from MeasureActionLog
      if (CollectionUtils.isNotEmpty(actionLogIdsToBeMigrated)) {
        measureActionLogRepository.deleteAllById(actionLogIdsToBeMigrated);
      }
    }
  }

  @RollbackExecution
  public void rollbackExecution(
      MeasureActionLogRepository measureActionLogRepository,
      MeasureSetActionLogRepository measureSetActionLogRepository)
      throws Exception {
    log.debug("Entering rollbackExecution()");

    if (CollectionUtils.isNotEmpty(actionLogsToBeMigrated)) {
      measureActionLogRepository.saveAll(actionLogsToBeMigrated);
    }

    if (CollectionUtils.isNotEmpty(measureSetActionLogIds)) {
      measureSetActionLogRepository.deleteAllById(measureSetActionLogIds);
    }
  }
}
