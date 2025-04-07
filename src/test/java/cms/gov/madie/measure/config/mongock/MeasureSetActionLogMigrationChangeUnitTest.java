package cms.gov.madie.measure.config.mongock;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cms.gov.madie.measure.repositories.MeasureActionLogRepository;
import cms.gov.madie.measure.repositories.MeasureSetActionLogRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionLog;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.MeasureSetActionLog;
import gov.cms.madie.models.measure.MeasureSet;

@ExtendWith(MockitoExtension.class)
public class MeasureSetActionLogMigrationChangeUnitTest {
  @Mock private MeasureSetRepository measureSetRepository;
  @Mock MeasureActionLogRepository measureActionLogRepository;
  @Mock MeasureSetActionLogRepository measureSetActionLogRepository;
  @InjectMocks private MeasureSetActionLogMigrationChangeUnit changeUnit;

  private ActionLog actionLog;
  Instant instant = Instant.parse("2025-04-06T21:06:00Z");
  private MeasureSet measureSet;
  MeasureSetActionLog measureSetActionLog = null;

  @BeforeEach
  void setUp() {
    actionLog = new ActionLog();
    actionLog.setId("action1");
    actionLog.setTargetId("measureSet1");
    Action action1 =
        Action.builder()
            .actionType(ActionType.CREATED)
            .additionalActionMessage("message1")
            .performedAt(instant)
            .performedBy("user1")
            .build();
    Action action2 =
        Action.builder()
            .actionType(ActionType.ASSOCIATED)
            .additionalActionMessage("message2")
            .performedAt(instant)
            .performedBy("user1")
            .build();
    actionLog.setActions(List.of(action1, action2));

    measureSet = MeasureSet.builder().measureSetId("measureSet1").build();

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
    measureSetActionLog =
        MeasureSetActionLog.builder()
            .id(actionLog.getId())
            .targetId("measureSet1")
            .actions(accessControlActions)
            .build();
  }

  @Test
  void testMigrateMeasureSetActionLogEmptyMeasureActionLog() {
    when(measureActionLogRepository.findAll()).thenReturn(List.of());

    changeUnit.migrateMeasureSetActionLog(
        measureSetRepository, measureActionLogRepository, measureSetActionLogRepository);

    verify(measureActionLogRepository, new Times(1)).findAll();
    verifyNoInteractions(measureSetRepository);
    verifyNoInteractions(measureSetActionLogRepository);
  }

  @Test
  void testMigrateMeasureSetActionLogNotInMeasureSet() {
    when(measureActionLogRepository.findAll()).thenReturn(List.of(actionLog));
    when(measureSetRepository.findById(anyString())).thenReturn(Optional.empty());

    changeUnit.migrateMeasureSetActionLog(
        measureSetRepository, measureActionLogRepository, measureSetActionLogRepository);

    verify(measureActionLogRepository, new Times(1)).findAll();
    verify(measureSetRepository, new Times(1)).findById("measureSet1");
    verifyNoInteractions(measureSetActionLogRepository);
  }

  @Test
  void testMigrateMeasureSetActionLog() {
    when(measureActionLogRepository.findAll()).thenReturn(List.of(actionLog));
    when(measureSetRepository.findById(anyString())).thenReturn(Optional.of(measureSet));

    changeUnit.migrateMeasureSetActionLog(
        measureSetRepository, measureActionLogRepository, measureSetActionLogRepository);

    verify(measureActionLogRepository, new Times(1)).findAll();
    verify(measureSetRepository, new Times(1)).findById("measureSet1");
    verify(measureSetActionLogRepository, new Times(1)).saveAll(List.of(measureSetActionLog));
    verify(measureActionLogRepository, new Times(1)).deleteAllById(List.of("action1"));
  }

  @Test
  public void testRollbackExecution() throws Exception {

    ReflectionTestUtils.setField(changeUnit, "actionLogsToBeMigrated", List.of(actionLog));
    ReflectionTestUtils.setField(changeUnit, "measureSetActionLogIds", List.of("action1"));

    changeUnit.rollbackExecution(measureActionLogRepository, measureSetActionLogRepository);

    verify(measureActionLogRepository, new Times(1)).saveAll(List.of(actionLog));
    verify(measureSetActionLogRepository, new Times(1)).deleteAllById(List.of("action1"));
  }

  @Test
  public void testRollbackExecutionNoActionLogs() throws Exception {

    ReflectionTestUtils.setField(changeUnit, "actionLogsToBeMigrated", Collections.emptyList());
    ReflectionTestUtils.setField(changeUnit, "measureSetActionLogIds", Collections.emptyList());

    changeUnit.rollbackExecution(measureActionLogRepository, measureSetActionLogRepository);

    verifyNoInteractions(measureActionLogRepository);
    verifyNoInteractions(measureSetActionLogRepository);
  }
}
