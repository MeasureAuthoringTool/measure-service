package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.locks.MeasureLock;
import cms.gov.madie.measure.locks.TestCaseLock;
import cms.gov.madie.measure.repositories.*;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionLog;
import gov.cms.madie.models.common.MeasureSetActionLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ChangeUserNameToLowerCaseTest {
  @Mock MeasureRepository measureRepository;
  @Mock MeasureSetRepository measureSetRepository;
  @Mock MeasureActionLogRepository measureActionLogRepository;
  @Mock MeasureSetActionLogRepository measureSetActionLogRepository;
  @Mock ActionLogRepositoryImpl actionLogRepository;
  @Mock MeasureLockRepository measureLockRepository;
  @Mock TestCaseLockRepository testCaseLockRepository;
  @InjectMocks ChangeUserNameToLowerCaseChangeUnit changeUnit;

  private Measure measure;
  private MeasureSet measureSet;
  private AclSpecification aclSpecification;
  private final String LOWER_CASE_USER_NAME = "user1";
  private final String UPPER_CASE_USER_NAME = "User1";

  private Action action;
  private ActionLog measureAcctionLog;
  private AccessControlAction accessControlAction;
  private MeasureSetActionLog measureSetActionLog;
  private Action testCaseAction;
  private ActionLog testCaseActionLog;

  private MeasureLock measureLock;
  private TestCaseLock testCaseLock;

  @BeforeEach
  void setUp() {
    measure =
        Measure.builder()
            .createdBy(LOWER_CASE_USER_NAME)
            .lastModifiedBy(UPPER_CASE_USER_NAME)
            .build();
    aclSpecification = AclSpecification.builder().userId(LOWER_CASE_USER_NAME).build();
    measureSet =
        MeasureSet.builder().owner(LOWER_CASE_USER_NAME).acls(List.of(aclSpecification)).build();

    action = Action.builder().performedBy(LOWER_CASE_USER_NAME).build();
    measureAcctionLog = ActionLog.builder().actions(List.of(action)).build();

    accessControlAction = AccessControlAction.builder().performedBy(LOWER_CASE_USER_NAME).build();
    measureSetActionLog =
        MeasureSetActionLog.builder().actions(List.of(accessControlAction)).build();

    testCaseAction = Action.builder().performedBy(LOWER_CASE_USER_NAME).build();
    testCaseActionLog = ActionLog.builder().actions(List.of(testCaseAction)).build();

    measureLock = MeasureLock.builder().lockedBy(LOWER_CASE_USER_NAME).build();
    testCaseLock = TestCaseLock.builder().lockedBy(LOWER_CASE_USER_NAME).build();
  }

  @Test
  void testChangeAllUserNamesToLowerCase() {
    when(measureRepository.findAll()).thenReturn(List.of(measure));
    aclSpecification.setUserId(UPPER_CASE_USER_NAME);
    measureSet.setAcls(List.of(aclSpecification));
    when(measureSetRepository.findAll()).thenReturn(List.of(measureSet));

    action.setPerformedBy(UPPER_CASE_USER_NAME);
    measureAcctionLog.setActions(List.of(action));
    when(measureActionLogRepository.findAll()).thenReturn(List.of(measureAcctionLog));

    accessControlAction.setPerformedBy(UPPER_CASE_USER_NAME);
    measureSetActionLog.setActions(List.of(accessControlAction));
    when(measureSetActionLogRepository.findAll()).thenReturn(List.of(measureSetActionLog));

    testCaseAction.setPerformedBy(UPPER_CASE_USER_NAME);
    testCaseActionLog.setActions(List.of(testCaseAction));
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(testCaseActionLog));

    measureLock.setLockedBy(UPPER_CASE_USER_NAME);
    when(measureLockRepository.findAll()).thenReturn(List.of(measureLock));

    testCaseLock.setLockedBy(UPPER_CASE_USER_NAME);
    when(testCaseLockRepository.findAll()).thenReturn(List.of(testCaseLock));

    changeUnit.changeAllUserNamesToLowerCase(
        measureRepository,
        measureSetRepository,
        measureActionLogRepository,
        measureSetActionLogRepository,
        actionLogRepository,
        measureLockRepository,
        testCaseLockRepository);

    assertEquals(1, changeUnit.getUpdatedMeasures().size());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getUpdatedMeasures().get(0).getLastModifiedBy());
    assertEquals(1, changeUnit.getUpdatedMeasureSets().size());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getUpdatedMeasureSets().get(0).getOwner());
    assertEquals(
        LOWER_CASE_USER_NAME,
        changeUnit.getUpdatedMeasureSets().get(0).getAcls().get(0).getUserId());

    assertEquals(1, changeUnit.getUpdatedMeasureActionLogs().size());
    assertEquals(
        LOWER_CASE_USER_NAME,
        changeUnit.getUpdatedMeasureActionLogs().get(0).getActions().get(0).getPerformedBy());

    assertEquals(1, changeUnit.getUpdatedMeasureSetActionLogs().size());
    assertEquals(
        LOWER_CASE_USER_NAME,
        changeUnit.getUpdatedMeasureSetActionLogs().get(0).getActions().get(0).getPerformedBy());

    assertEquals(1, changeUnit.getUpdatedTestCaseActionLogs().size());
    assertEquals(
        LOWER_CASE_USER_NAME,
        changeUnit.getUpdatedTestCaseActionLogs().get(0).getActions().get(0).getPerformedBy());

    assertEquals(1, changeUnit.getUpdatedMeasureLocks().size());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getUpdatedMeasureLocks().get(0).getLockedBy());
    assertEquals(1, changeUnit.getUpdatedTestCaseLocks().size());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getUpdatedTestCaseLocks().get(0).getLockedBy());
  }

  @Test
  void testUpdateMeasures() {
    Measure measure2 =
        measure.toBuilder()
            .createdBy(UPPER_CASE_USER_NAME)
            .lastModifiedBy(LOWER_CASE_USER_NAME)
            .build();
    Measure measure3 =
        measure.toBuilder()
            .createdBy(LOWER_CASE_USER_NAME)
            .lastModifiedBy(LOWER_CASE_USER_NAME)
            .build();
    Measure measure4 = measure.toBuilder().createdBy(null).lastModifiedBy(null).build();

    when(measureRepository.findAll()).thenReturn(List.of(measure, measure2, measure3, measure4));
    changeUnit.updateMeasures(measureRepository);

    assertEquals(2, changeUnit.getUpdatedMeasures().size());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getUpdatedMeasures().get(0).getCreatedBy());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getUpdatedMeasures().get(0).getLastModifiedBy());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getUpdatedMeasures().get(1).getCreatedBy());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getUpdatedMeasures().get(1).getLastModifiedBy());
  }

  @Test
  void testUpdateMeasuresNoMeasuresFromDB() {
    when(measureRepository.findAll()).thenReturn(List.of());
    changeUnit.updateMeasures(measureRepository);

    assertEquals(0, changeUnit.getUpdatedMeasures().size());
  }

  @Test
  void testUpdateMeasuresNoUpdatesNeeded() {
    measure.setLastModifiedBy(LOWER_CASE_USER_NAME);
    when(measureRepository.findAll()).thenReturn(List.of(measure));
    changeUnit.updateMeasures(measureRepository);

    assertEquals(0, changeUnit.getUpdatedMeasures().size());
  }

  @Test
  void testUpdateMeasureSets() {
    MeasureSet measureSet2 =
        measureSet.toBuilder().owner(UPPER_CASE_USER_NAME).acls(List.of(aclSpecification)).build();
    AclSpecification aclSpecification2 =
        AclSpecification.builder().userId(UPPER_CASE_USER_NAME).build();
    MeasureSet measureSet3 =
        measureSet.toBuilder().owner(LOWER_CASE_USER_NAME).acls(List.of(aclSpecification2)).build();
    MeasureSet measureSet4 = measureSet.toBuilder().owner(null).acls(List.of()).build();
    when(measureSetRepository.findAll())
        .thenReturn(List.of(measureSet, measureSet2, measureSet3, measureSet4));

    changeUnit.updateMeasureSets(measureSetRepository);

    assertEquals(2, changeUnit.getUpdatedMeasureSets().size());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getUpdatedMeasureSets().get(0).getOwner());
    assertEquals(
        LOWER_CASE_USER_NAME,
        changeUnit.getUpdatedMeasureSets().get(0).getAcls().get(0).getUserId());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getUpdatedMeasureSets().get(1).getOwner());
    assertEquals(
        LOWER_CASE_USER_NAME,
        changeUnit.getUpdatedMeasureSets().get(1).getAcls().get(0).getUserId());
  }

  @Test
  void testUpdateMeasureSetsNoMeasureSetsFromDB() {
    when(measureSetRepository.findAll()).thenReturn(List.of());

    changeUnit.updateMeasureSets(measureSetRepository);

    assertEquals(0, changeUnit.getUpdatedMeasureSets().size());
  }

  @Test
  void testUpdateMeasureSetsNoUpdatesNeeded() {
    when(measureSetRepository.findAll()).thenReturn(List.of(measureSet));

    changeUnit.updateMeasureSets(measureSetRepository);

    assertEquals(0, changeUnit.getUpdatedMeasureSets().size());
  }

  @Test
  void testUpdateMeasureActionLogs() {
    ActionLog measureActionLog2 = new ActionLog();
    measureActionLog2.setActions(
        (List.of(
            action,
            Action.builder().performedBy(UPPER_CASE_USER_NAME).build(),
            Action.builder().build())));
    ActionLog measureAcctionLog3 = new ActionLog();
    when(measureActionLogRepository.findAll())
        .thenReturn(List.of(measureAcctionLog, measureActionLog2, measureAcctionLog3));

    changeUnit.updateMeasureActionLogs(measureActionLogRepository);

    assertEquals(1, changeUnit.getUpdatedMeasureActionLogs().size());
    assertEquals(3, changeUnit.getUpdatedMeasureActionLogs().get(0).getActions().size());
    assertEquals(
        LOWER_CASE_USER_NAME,
        changeUnit.getUpdatedMeasureActionLogs().get(0).getActions().get(0).getPerformedBy());
    assertEquals(
        LOWER_CASE_USER_NAME,
        changeUnit.getUpdatedMeasureActionLogs().get(0).getActions().get(1).getPerformedBy());
    assertNull(
        changeUnit.getUpdatedMeasureActionLogs().get(0).getActions().get(2).getPerformedBy());
  }

  @Test
  void testUpdateMeasureActionLogsNoMeasureActionLogsFromDB() {
    when(measureActionLogRepository.findAll()).thenReturn(List.of());

    changeUnit.updateMeasureActionLogs(measureActionLogRepository);

    assertEquals(0, changeUnit.getUpdatedMeasureActionLogs().size());
  }

  @Test
  void testUpdateMeasureActionLogsNoUpdatesNeeded() {
    when(measureActionLogRepository.findAll()).thenReturn(List.of(measureAcctionLog));

    changeUnit.updateMeasureActionLogs(measureActionLogRepository);

    assertEquals(0, changeUnit.getUpdatedMeasureActionLogs().size());
  }

  @Test
  void testUpdateMeasureSetActionLogs() {
    MeasureSetActionLog measureSetActionLog2 = new MeasureSetActionLog();
    measureSetActionLog2.setActions(
        (List.of(
            accessControlAction,
            AccessControlAction.builder().performedBy(UPPER_CASE_USER_NAME).build(),
            AccessControlAction.builder().build())));
    MeasureSetActionLog measureSetActionLog3 = new MeasureSetActionLog();
    when(measureSetActionLogRepository.findAll())
        .thenReturn(List.of(measureSetActionLog, measureSetActionLog2, measureSetActionLog3));

    changeUnit.updateMeasureSetActionLogs(measureSetActionLogRepository);

    assertEquals(1, changeUnit.getUpdatedMeasureSetActionLogs().size());
    assertEquals(3, changeUnit.getUpdatedMeasureSetActionLogs().get(0).getActions().size());
    assertEquals(
        LOWER_CASE_USER_NAME,
        changeUnit.getUpdatedMeasureSetActionLogs().get(0).getActions().get(0).getPerformedBy());
    assertEquals(
        LOWER_CASE_USER_NAME,
        changeUnit.getUpdatedMeasureSetActionLogs().get(0).getActions().get(1).getPerformedBy());
    assertNull(
        changeUnit.getUpdatedMeasureSetActionLogs().get(0).getActions().get(2).getPerformedBy());
  }

  @Test
  void testUpdateMeasureSetActionLogsNoMeasureSetActionLogsFromDB() {
    when(measureSetActionLogRepository.findAll()).thenReturn(List.of());

    changeUnit.updateMeasureSetActionLogs(measureSetActionLogRepository);

    assertEquals(0, changeUnit.getUpdatedMeasureSetActionLogs().size());
  }

  @Test
  void testUpdateMeasureSetActionLogsNoUpdatesNeeded() {
    when(measureSetActionLogRepository.findAll()).thenReturn(List.of(measureSetActionLog));

    changeUnit.updateMeasureSetActionLogs(measureSetActionLogRepository);

    assertEquals(0, changeUnit.getUpdatedMeasureSetActionLogs().size());
  }

  @Test
  void testUpdateTestCaseActionLogs() {
    Action testCaseAction2 = new Action();
    testCaseAction2.setPerformedBy(UPPER_CASE_USER_NAME);
    Action testCaseAction3 = new Action();
    testCaseActionLog.setActions(List.of(testCaseAction, testCaseAction2, testCaseAction3));
    ActionLog testCaseActionLog2 = ActionLog.builder().build();
    when(actionLogRepository.findAllActionLogs(any()))
        .thenReturn(List.of(testCaseActionLog, testCaseActionLog2));

    changeUnit.updateTestCaseActionLogs(actionLogRepository);
    assertEquals(1, changeUnit.getUpdatedTestCaseActionLogs().size());
    assertEquals(3, changeUnit.getUpdatedTestCaseActionLogs().get(0).getActions().size());
    assertEquals(
        LOWER_CASE_USER_NAME,
        changeUnit.getUpdatedTestCaseActionLogs().get(0).getActions().get(0).getPerformedBy());
    assertEquals(
        LOWER_CASE_USER_NAME,
        changeUnit.getUpdatedTestCaseActionLogs().get(0).getActions().get(1).getPerformedBy());
    assertNull(
        changeUnit.getUpdatedTestCaseActionLogs().get(0).getActions().get(2).getPerformedBy());
    assertEquals(2, changeUnit.getOriginalTestCaseActionLogs().size());
  }

  @Test
  void testUpdateTestCaseActionLogsNoTestCaseActionLogsFromDB() {
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of());

    changeUnit.updateTestCaseActionLogs(actionLogRepository);

    assertEquals(0, changeUnit.getUpdatedTestCaseActionLogs().size());
  }

  @Test
  void testUpdateTestCaseActionLogsNoUpdatesNeeded() {
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(testCaseActionLog));

    changeUnit.updateTestCaseActionLogs(actionLogRepository);

    assertEquals(0, changeUnit.getUpdatedTestCaseActionLogs().size());
  }

  @Test
  void testUpdateMeasureLocks() {
    MeasureLock measureLock2 = MeasureLock.builder().lockedBy(UPPER_CASE_USER_NAME).build();
    MeasureLock measureLock3 = MeasureLock.builder().lockedBy(null).build();

    when(measureLockRepository.findAll())
        .thenReturn(List.of(measureLock, measureLock2, measureLock3));

    changeUnit.updateMeasureLocks(measureLockRepository);

    assertEquals(1, changeUnit.getUpdatedMeasureLocks().size());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getUpdatedMeasureLocks().get(0).getLockedBy());
    assertEquals(3, changeUnit.getOriginalMeasureLocks().size());
  }

  @Test
  void testUpdateMeasureLocksNoMeasureLocksFromDB() {
    when(measureLockRepository.findAll()).thenReturn(List.of());

    changeUnit.updateMeasureLocks(measureLockRepository);

    assertEquals(0, changeUnit.getUpdatedMeasureLocks().size());
  }

  @Test
  void testUpdateMeasureLocksNoUpdatesNeeded() {
    when(measureLockRepository.findAll()).thenReturn(List.of(measureLock));

    changeUnit.updateMeasureLocks(measureLockRepository);

    assertEquals(0, changeUnit.getUpdatedMeasureLocks().size());
    assertEquals(1, changeUnit.getOriginalMeasureLocks().size());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getOriginalMeasureLocks().get(0).getLockedBy());
  }

  @Test
  void testUpdateTestCaseLocks() {
    TestCaseLock testCaseLock2 = TestCaseLock.builder().lockedBy(UPPER_CASE_USER_NAME).build();
    TestCaseLock testCaseLock3 = TestCaseLock.builder().lockedBy(null).build();

    when(testCaseLockRepository.findAll())
        .thenReturn(List.of(testCaseLock, testCaseLock2, testCaseLock3));

    changeUnit.updateTestCaseLocks(testCaseLockRepository);

    assertEquals(1, changeUnit.getUpdatedTestCaseLocks().size());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getUpdatedTestCaseLocks().get(0).getLockedBy());
    assertEquals(3, changeUnit.getOriginalTestCaseLocks().size());
  }

  @Test
  void testUpdateTestCaseLocksNoTestCaseLocksFromDB() {
    when(testCaseLockRepository.findAll()).thenReturn(List.of());

    changeUnit.updateTestCaseLocks(testCaseLockRepository);

    assertEquals(0, changeUnit.getUpdatedTestCaseLocks().size());
  }

  @Test
  void testUpdateTestCaseLocksNoUpdatesNeeded() {
    when(testCaseLockRepository.findAll()).thenReturn(List.of(testCaseLock));

    changeUnit.updateTestCaseLocks(testCaseLockRepository);

    assertEquals(0, changeUnit.getUpdatedTestCaseLocks().size());
    assertEquals(1, changeUnit.getOriginalTestCaseLocks().size());
    assertEquals(LOWER_CASE_USER_NAME, changeUnit.getOriginalTestCaseLocks().get(0).getLockedBy());
  }

  @Test
  void testRollback() {
    ReflectionTestUtils.setField(changeUnit, "originalMeasures", List.of(measure));
    ReflectionTestUtils.setField(changeUnit, "originalMeasureSets", List.of(measureSet));
    ReflectionTestUtils.setField(
        changeUnit, "originalMeasureActionLogs", List.of(measureAcctionLog));
    ReflectionTestUtils.setField(
        changeUnit, "originalMeasureSetActionLogs", List.of(measureSetActionLog));
    ReflectionTestUtils.setField(
        changeUnit, "originalTestCaseActionLogs", List.of(testCaseActionLog));
    ReflectionTestUtils.setField(changeUnit, "originalMeasureLocks", List.of(measureLock));
    ReflectionTestUtils.setField(changeUnit, "originalTestCaseLocks", List.of(testCaseLock));

    changeUnit.rollbackChanges(
        measureRepository,
        measureSetRepository,
        measureActionLogRepository,
        measureSetActionLogRepository,
        actionLogRepository,
        measureLockRepository,
        testCaseLockRepository);
  }

  @Test
  void testRollbackNoOriginalData() {
    // Since the rollback method is empty, we just need to ensure it can be called without error
    changeUnit.rollbackChanges(
        measureRepository,
        measureSetRepository,
        measureActionLogRepository,
        measureSetActionLogRepository,
        actionLogRepository,
        measureLockRepository,
        testCaseLockRepository);
  }
}
