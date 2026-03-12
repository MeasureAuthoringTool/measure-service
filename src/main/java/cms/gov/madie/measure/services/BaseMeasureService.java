package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.InvalidMeasureStateException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.locks.MeasureLock;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.MeasureServiceUtil;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
@NoArgsConstructor(force = true)
public abstract class BaseMeasureService {
  private final MeasureRepository measureRepository;
  private final MeasureSetService measureSetService;
  private final AppConfigService appConfigService;
  private final MeasureLockService measureLockService;

  AclOperation buildShareAclOperation(List<String> userIds) {
    return AclOperation.builder()
        .acls(buildShareAclSpecifications(userIds))
        .action(AclOperation.AclAction.GRANT)
        .build();
  }

  public Measure findMeasureById(final String id) {
    return measureRepository
        .findById(id)
        .map(
            m -> {
              Measure.MeasureBuilder builder =
                  m.toBuilder()
                      .measureSet(measureSetService.findByMeasureSetId(m.getMeasureSetId()));

              // Map measure lock
              MeasureLock lock = measureLockService.findByMeasureId(m.getId());
              if (lock != null) {
                builder.measureLock(
                    gov.cms.madie.models.measure.MeasureLock.builder()
                        .id(lock.getId())
                        .lockedBy(lock.getLockedBy())
                        .build());
              }

              return builder.build();
            })
        .orElse(null);
  }

  /**
   * Verifies that the user is authorized to perform share/unshare operations on the given measures.
   * - Restrict sharing to owners only (ownerOnly = true). - Allow unsharing by owners or users who
   * the measure is already shared with (ownerOnly = false).
   */
  void verifyShareAuthorization(
      Map<String, List<String>> measureUserIdMap, String username, boolean ownerOnly) {
    log.info(
        "User [{}] has called verifyShareAuthorization to determine whether operation with [{}]"
            + " is allowed to be performed",
        username,
        measureUserIdMap);

    measureUserIdMap
        .keySet()
        .forEach(
            measureId -> {
              Measure measure = findMeasureById(measureId);

              if (measure == null) {
                log.error(
                    "User [{}] called verifyShareAuthorization with measureUserIdMap [{}] but "
                        + "failed because the measure with measure ID [{}] does not exist.",
                    username,
                    measureUserIdMap,
                    measureId);
                throw new ResourceNotFoundException("Measure does not exist: " + measureId);
              }
              verifyAuthorization(
                  username, measure, ownerOnly ? List.of() : List.of(RoleEnum.SHARED_WITH));
            });

    log.info(
        "User [{}] successfully called verifyShareAuthorization and determined that operation "
            + "with [{}] is allowed to be performed",
        username,
        measureUserIdMap);
  }

  List<AclSpecification> buildShareAclSpecifications(List<String> userIds) {
    return userIds.stream()
        .map(
            userId ->
                AclSpecification.builder()
                    .userId(userId.toLowerCase())
                    .roles(Set.of(RoleEnum.SHARED_WITH))
                    .build())
        .toList();
  }

  /**
   * Verifies the specified user has privileges on the given measure based on measure owner and the
   * passed roles. Providing null or empty roles will perform an authorization check for owner only.
   *
   * @param username username to be authorized
   * @param measure measure to be authorized
   * @param roles additional roles besides 'Owner'
   */
  public void verifyAuthorization(String username, Measure measure, List<RoleEnum> roles) {
    MeasureSet measureSet =
        measure.getMeasureSet() == null
            ? measureSetService.findByMeasureSetId(measure.getMeasureSetId())
            : measure.getMeasureSet();
    if (measureSet == null) {
      log.error(
          "User [{}] called verifyAuthorization but failed because no measure set exists for "
              + "measure with measure ID [{}]",
          username,
          measure.getId());
      throw new InvalidMeasureStateException(
          "No measure set exists for measure with ID " + measure.getId());
    }
    MeasureServiceUtil.verifyMeasureSetAuthorization(
        username, "Measure", measure.getId(), roles, measureSet);
  }
}
