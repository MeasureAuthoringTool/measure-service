package cms.gov.madie.measure.config.mongock;

import java.util.ArrayList;
import java.util.List;

import cms.gov.madie.measure.locks.TestCaseLock;
import cms.gov.madie.measure.repositories.*;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionLog;
import gov.cms.madie.models.common.MeasureSetActionLog;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import gov.cms.madie.models.measure.TestCase;
import cms.gov.madie.measure.locks.MeasureLock;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;

@Slf4j
@Data
@ChangeUnit(id = "change_username_to_lower_case", order = "1", author = "madie_dev")
public class ChangeUserNameToLowerCaseChangeUnit {
  private List<Measure> originalMeasures = new ArrayList<>();
  private List<Measure> updatedMeasures = new ArrayList<>();
  private List<MeasureSet> originalMeasureSets = new ArrayList<>();
  private List<MeasureSet> updatedMeasureSets = new ArrayList<>();
  private List<ActionLog> originalMeasureActionLogs = new ArrayList<>();
  private List<ActionLog> updatedMeasureActionLogs = new ArrayList<>();
  private List<MeasureSetActionLog> originalMeasureSetActionLogs = new ArrayList<>();
  private List<MeasureSetActionLog> updatedMeasureSetActionLogs = new ArrayList<>();
  private List<ActionLog> originalTestCaseActionLogs = new ArrayList<>();
  private List<ActionLog> updatedTestCaseActionLogs = new ArrayList<>();
  private List<MeasureLock> originalMeasureLocks = new ArrayList<>();
  private List<MeasureLock> updatedMeasureLocks = new ArrayList<>();
  private List<TestCaseLock> originalTestCaseLocks = new ArrayList<>();
  private List<TestCaseLock> updatedTestCaseLocks = new ArrayList<>();

  @Execution
  public void changeAllUserNamesToLowerCase(
      MeasureRepository measureRepository,
      MeasureSetRepository measureSetRepository,
      MeasureActionLogRepository measureActionLogRepository,
      MeasureSetActionLogRepository measureSetActionLogRepository,
      ActionLogRepositoryImpl actionLogRepository,
      MeasureLockRepository measureLockRepository,
      TestCaseLockRepository testCaseLockRepository) {
    updateMeasures(measureRepository);
    updateMeasureSets(measureSetRepository);
    updateMeasureActionLogs(measureActionLogRepository);
    updateMeasureSetActionLogs(measureSetActionLogRepository);
    updateTestCaseActionLogs(actionLogRepository);
    updateMeasureLocks(measureLockRepository);
    updateTestCaseLocks(testCaseLockRepository);
  }

  void updateMeasures(MeasureRepository measureRepository) {
    originalMeasures = measureRepository.findAll();
    if (CollectionUtils.isNotEmpty(originalMeasures)) {
      log.info("originalMeasures: " + originalMeasures.size());
      for (Measure measure : originalMeasures) {
        boolean isUpdated = false;
        if (measure.getCreatedBy() != null
            && measure.getCreatedBy().chars().anyMatch(Character::isUpperCase)) {
          measure.setCreatedBy(measure.getCreatedBy().toLowerCase());
          isUpdated = true;
        }
        if (measure.getLastModifiedBy() != null
            && measure.getLastModifiedBy().chars().anyMatch(Character::isUpperCase)) {
          measure.setLastModifiedBy(measure.getLastModifiedBy().toLowerCase());
          isUpdated = true;
        }
        if (isUpdated) {
          updatedMeasures.add(measure);
        }
      }
      if (CollectionUtils.isNotEmpty(updatedMeasures)) {
        log.info("updatedMeasures: " + updatedMeasures.size());
        measureRepository.saveAll(updatedMeasures);
      }
    }
  }

  void updateMeasureSets(MeasureSetRepository measureSetRepository) {
    originalMeasureSets = measureSetRepository.findAll();
    if (CollectionUtils.isNotEmpty(originalMeasureSets)) {
      log.info("originalMeasureSets: " + originalMeasureSets.size());
      for (MeasureSet measureSet : originalMeasureSets) {
        boolean isUpdated = false;
        if (measureSet.getOwner() != null
            && measureSet.getOwner().chars().anyMatch(Character::isUpperCase)) {
          measureSet.setOwner(measureSet.getOwner().toLowerCase());
          isUpdated = true;
        }
        if (CollectionUtils.isNotEmpty(measureSet.getAcls())) {
          List<AclSpecification> updatedAcls = new ArrayList<>();
          boolean aclUpdated = false;
          for (AclSpecification acl : measureSet.getAcls()) {
            String userId = acl.getUserId();
            if (userId.chars().anyMatch(Character::isUpperCase)) {
              acl.setUserId(userId.toLowerCase());
              updatedAcls.add(acl);
              aclUpdated = true;
              isUpdated = true;
            } else {
              updatedAcls.add(acl);
            }
          }
          if (aclUpdated) {
            measureSet.setAcls(updatedAcls);
          }
        }
        if (isUpdated) {
          updatedMeasureSets.add(measureSet);
        }
      }
      if (CollectionUtils.isNotEmpty(updatedMeasureSets)) {
        log.info("updatedMeasureSets: " + updatedMeasureSets.size());
        measureSetRepository.saveAll(updatedMeasureSets);
      }
    }
  }

  void updateMeasureActionLogs(MeasureActionLogRepository measureActionLogRepository) {
    originalMeasureActionLogs = measureActionLogRepository.findAll();
    if (CollectionUtils.isNotEmpty(originalMeasureActionLogs)) {
      log.info("originalMeasureActionLogs: " + originalMeasureActionLogs.size());
      for (ActionLog actionLog : originalMeasureActionLogs) {
        updateActions(actionLog, "measureActionLog");
      }
      if (CollectionUtils.isNotEmpty(updatedMeasureActionLogs)) {
        log.info("updatedMeasureActionLogs: " + updatedMeasureActionLogs.size());
        measureActionLogRepository.saveAll(updatedMeasureActionLogs);
      }
    }
  }

  void updateActions(ActionLog actionLog, String measureActionLog) {
    boolean isUpdated = false;
    List<Action> actions = actionLog.getActions();
    List<Action> updatedActions = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(actions)) {
      for (Action action : actions) {
        if (action.getPerformedBy() != null
            && action.getPerformedBy().chars().anyMatch(Character::isUpperCase)) {
          action.setPerformedBy(action.getPerformedBy().toLowerCase());
          updatedActions.add(action);
          isUpdated = true;
        } else {
          updatedActions.add(action);
        }
      }
      if (isUpdated) {
        actionLog.setActions(updatedActions);
      }
    }
    if (isUpdated) {
      if ("measureActionLog".equalsIgnoreCase(measureActionLog)) {
        updatedMeasureActionLogs.add(actionLog);
      } else {
        updatedTestCaseActionLogs.add(actionLog);
      }
    }
  }

  void updateMeasureSetActionLogs(MeasureSetActionLogRepository measureSetActionLogRepository) {
    originalMeasureSetActionLogs = measureSetActionLogRepository.findAll();
    if (CollectionUtils.isNotEmpty(originalMeasureSetActionLogs)) {
      log.info("originalMeasureSetActionLogs: " + originalMeasureSetActionLogs.size());
      for (MeasureSetActionLog actionLog : originalMeasureSetActionLogs) {
        boolean isUpdated = false;
        List<AccessControlAction> actions = actionLog.getActions();
        List<AccessControlAction> updatedActions = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(actions)) {
          for (AccessControlAction action : actions) {
            if (action.getPerformedBy() != null
                && action.getPerformedBy().chars().anyMatch(Character::isUpperCase)) {
              action.setPerformedBy(action.getPerformedBy().toLowerCase());
              updatedActions.add(action);
              isUpdated = true;
            } else {
              updatedActions.add(action);
            }
          }
          if (isUpdated) {
            actionLog.setActions(updatedActions);
          }
        }
        if (isUpdated) {
          updatedMeasureSetActionLogs.add(actionLog);
        }
      }
      if (CollectionUtils.isNotEmpty(updatedMeasureSetActionLogs)) {
        log.info("updatedMeasureSetActionLogs: " + updatedMeasureSetActionLogs.size());
        measureSetActionLogRepository.saveAll(updatedMeasureSetActionLogs);
      }
    }
  }

  void updateTestCaseActionLogs(ActionLogRepository actionLogRepository) {
    originalTestCaseActionLogs = actionLogRepository.findAllActionLogs(TestCase.class);
    if (CollectionUtils.isNotEmpty(originalTestCaseActionLogs)) {
      log.info("originalTestCaseActionLogs: " + originalTestCaseActionLogs.size());
      for (ActionLog actionLog : originalTestCaseActionLogs) {
        updateActions(actionLog, "testCaseActionLog");
      }
      if (CollectionUtils.isNotEmpty(updatedTestCaseActionLogs)) {
        log.info("updatedTestCaseActionLogs: " + updatedTestCaseActionLogs.size());
        actionLogRepository.updateAllActionLogs(updatedTestCaseActionLogs, TestCase.class);
      }
    }
  }

  void updateMeasureLocks(MeasureLockRepository measureLockRepository) {
    originalMeasureLocks = measureLockRepository.findAll();
    if (CollectionUtils.isNotEmpty(originalMeasureLocks)) {
      log.info("originalMeasureLocks: " + originalMeasureLocks.size());
      for (MeasureLock measureLock : originalMeasureLocks) {
        boolean isUpdated = false;
        if (measureLock.getLockedBy() != null
            && measureLock.getLockedBy().chars().anyMatch(Character::isUpperCase)) {
          measureLock.setLockedBy(measureLock.getLockedBy().toLowerCase());
          isUpdated = true;
        }
        if (isUpdated) {
          updatedMeasureLocks.add(measureLock);
        }
      }
      if (CollectionUtils.isNotEmpty(updatedMeasureLocks)) {
        log.info("updatedMeasureLocks: " + updatedMeasureLocks.size());
        measureLockRepository.saveAll(updatedMeasureLocks);
      }
    }
  }

  void updateTestCaseLocks(TestCaseLockRepository testCaseLockRepository) {
    originalTestCaseLocks = testCaseLockRepository.findAll();
    if (CollectionUtils.isNotEmpty(originalTestCaseLocks)) {
      log.info("originalTestCaseLocks: " + originalTestCaseLocks.size());
      for (TestCaseLock testCaseLock : originalTestCaseLocks) {
        boolean isUpdated = false;
        if (testCaseLock.getLockedBy() != null
            && testCaseLock.getLockedBy().chars().anyMatch(Character::isUpperCase)) {
          testCaseLock.setLockedBy(testCaseLock.getLockedBy().toLowerCase());
          isUpdated = true;
        }
        if (isUpdated) {
          updatedTestCaseLocks.add(testCaseLock);
        }
      }
      if (CollectionUtils.isNotEmpty(updatedTestCaseLocks)) {
        log.info("updatedTestCaseLocks: " + updatedTestCaseLocks.size());
        testCaseLockRepository.saveAll(updatedTestCaseLocks);
      }
    }
  }

  @RollbackExecution
  public void rollbackChanges(
      MeasureRepository measureRepository,
      MeasureSetRepository measureSetRepository,
      MeasureActionLogRepository measureActionLogRepository,
      MeasureSetActionLogRepository measureSetActionLogRepository,
      ActionLogRepositoryImpl actionLogRepository,
      MeasureLockRepository measureLockRepository,
      TestCaseLockRepository testCaseLockRepository) {
    if (CollectionUtils.isNotEmpty(originalMeasures)) {
      measureRepository.saveAll(originalMeasures);
    }
    if (CollectionUtils.isNotEmpty(originalMeasureSets)) {
      measureSetRepository.saveAll(originalMeasureSets);
    }
    if (CollectionUtils.isNotEmpty(originalMeasureActionLogs)) {
      measureActionLogRepository.saveAll(originalMeasureActionLogs);
    }
    if (CollectionUtils.isNotEmpty(originalMeasureSetActionLogs)) {
      measureSetActionLogRepository.saveAll(originalMeasureSetActionLogs);
    }
    if (CollectionUtils.isNotEmpty(originalTestCaseActionLogs)) {
      actionLogRepository.saveAllActionLogs(originalTestCaseActionLogs, TestCase.class);
    }

    if (CollectionUtils.isNotEmpty(originalMeasureLocks)) {
      measureLockRepository.saveAll(originalMeasureLocks);
    }
    if (CollectionUtils.isNotEmpty(originalTestCaseLocks)) {
      testCaseLockRepository.saveAll(originalTestCaseLocks);
    }
  }
}
