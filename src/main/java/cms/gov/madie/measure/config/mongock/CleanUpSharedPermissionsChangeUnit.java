package cms.gov.madie.measure.config.mongock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;

import cms.gov.madie.measure.repositories.MeasureSetActionLogRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.services.ActionLogService;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.MeasureSetActionLog;
import gov.cms.madie.models.measure.MeasureSet;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "cleanup_shared_permissions_for_owners", order = "1", author = "madie_dev")
public class CleanUpSharedPermissionsChangeUnit {
  private List<MeasureSet> originalMeasureSets = new ArrayList<>();
  private List<MeasureSet> updatedMeasureSets = new ArrayList<>();
  private final ActionLogService actionLogService;

  public CleanUpSharedPermissionsChangeUnit(ActionLogService actionLogService) {
    this.actionLogService = actionLogService;
  }

  @Execution
  public List<MeasureSet> cleanUpSharedPermissionsForOwners(
      MeasureSetRepository measureSetRepository,
      MeasureSetActionLogRepository measureSetActionLogRepository) {
    List<MeasureSet> measureSets = measureSetRepository.findAll();

    for (MeasureSet measureSet : measureSets) {
      List<AclSpecification> updatedAcls = new ArrayList<>();
      Map<String, ActionType> actionLogDetails = new HashMap<>();

      if (CollectionUtils.isNotEmpty(measureSet.getAcls())) {
        for (AclSpecification acl : measureSet.getAcls()) {
          if (measureSet.getOwner().equalsIgnoreCase(acl.getUserId())
              && acl.getRoles().contains(RoleEnum.SHARED_WITH)) {
            acl.getRoles().remove(RoleEnum.SHARED_WITH);
            log.info(
                "remove SHARED_WITH for measureSetId: [{}], owner: [{}]",
                measureSet.getMeasureSetId(),
                measureSet.getOwner());
            actionLogDetails.put(measureSet.getOwner(), ActionType.UNSHARED);
          }
          if (!acl.getRoles().isEmpty()) {
            updatedAcls.add(acl);
          }
        }
        if (measureSet.getAcls().size() != updatedAcls.size()) {
          // save to originalMeasureSets before update, for possible roll back
          originalMeasureSets.add(measureSet);
          measureSet.setAcls(updatedAcls);
          updatedMeasureSets.add(measureSet);
          measureSetRepository.save(measureSet);
          actionLogDetails.forEach(
              (userId, actionType) -> {
                boolean success =
                    actionLogService.logShareAccessControlAction(
                        measureSet.getMeasureSetId(),
                        MeasureSet.class,
                        actionType,
                        "admin",
                        userId,
                        "Cleaning up share access on owned measure");
                if (!success) {
                  measureSetActionLogRepository.save(
                      MeasureSetActionLog.builder()
                          .targetId(measureSet.getMeasureSetId())
                          .actions(
                              List.of(
                                  AccessControlAction.builder()
                                      .actionType(ActionType.UNSHARED)
                                      .sharedWith(userId)
                                      .additionalActionMessage(
                                          "Cleaning up share access on owned measure")
                                      .build()))
                          .build());
                }
              });
          log.info(
              "Logging actions for MeasureSetId: [{}], owner: [{}], size: [{}]",
              measureSet.getMeasureSetId(),
              measureSet.getOwner(),
              actionLogDetails.size());
        }
      }
    }
    log.info("updatedMeasureSets -> [{}]", updatedMeasureSets.toString());
    return updatedMeasureSets;
  }

  @RollbackExecution
  public void rollbackExecution(MeasureSetRepository measureSetRepository) {
    log.debug("Entering rollbackExecution()");

    measureSetRepository.deleteAll(updatedMeasureSets);
    measureSetRepository.saveAll(originalMeasureSets);
  }
}
