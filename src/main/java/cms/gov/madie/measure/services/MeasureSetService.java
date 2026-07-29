package cms.gov.madie.measure.services;

import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.config.security.RoleConstants;
import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.dto.excel.MeasureAccessReportDTO;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.GeneratorRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureReviewRepository;
import cms.gov.madie.measure.repositories.MeasureSetActionLogRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.MeasureSetActionLog;
import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureReview;
import gov.cms.madie.models.measure.MeasureSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeasureSetService {

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("MM/dd/yyyy").withZone(ZoneId.systemDefault());
  private final MeasureRepository measureRepository;
  private final MeasureSetRepository measureSetRepository;
  private final GeneratorRepository generatorRepository;
  private final ActionLogService actionLogService;
  private final UserServiceClient userServiceClient;
  private final MeasureSetActionLogRepository measureSetActionLogRepository;
  private final MeasureReviewRepository measureReviewRepository;

  public void createMeasureSet(
      final String harpId, final String measureId, final String savedMeasureSetId, String cmsId) {

    boolean isMeasureSetPresent = measureSetRepository.existsByMeasureSetId(savedMeasureSetId);
    if (!isMeasureSetPresent) {
      MeasureSet measureSet =
          MeasureSet.builder()
              .owner(harpId)
              .measureSetId(savedMeasureSetId)
              .cmsId((cmsId != null && !cmsId.equals("0")) ? Integer.parseInt(cmsId) : null)
              .build();
      MeasureSet savedMeasureSet = measureSetRepository.save(measureSet);
      log.info(
          "Measure set [{}] is successfully created for the measure [{}]",
          savedMeasureSet.getId(),
          measureId);
      actionLogService.logMeasureSetAction(
          savedMeasureSet.getMeasureSetId(), MeasureSet.class, ActionType.CREATED, harpId);
    }
  }

  /**
   * This method updates the ACLs based on given AclOperation for a measureSetId.
   *
   * @param measureSetId -> set id of a measure set
   * @param aclOperation -> AclOperation to be updated
   * @param userName -> userName performing action
   * @return an instance of MeasureSet
   */
  public MeasureSet updateMeasureSetAcls(
      String measureSetId, AclOperation aclOperation, String userName, boolean isAdmin) {
    Optional<MeasureSet> optionalMeasureSet = measureSetRepository.findByMeasureSetId(measureSetId);
    if (optionalMeasureSet.isPresent()) {
      Map<String, ActionType> actionLogDetails = new HashMap<>();
      MeasureSet measureSet = optionalMeasureSet.get().toBuilder().build();
      if (AclOperation.AclAction.GRANT == aclOperation.getAction()) {
        if (CollectionUtils.isEmpty(measureSet.getAcls())) {
          // if no acl present, add it
          measureSet.setAcls(aclOperation.getAcls());

          aclOperation
              .getAcls()
              .forEach(
                  aclSpecification -> {
                    String userId = aclSpecification.getUserId();

                    aclSpecification
                        .getRoles()
                        .forEach(
                            roleEnum -> {
                              if (roleEnum == RoleEnum.SHARED_WITH) {
                                actionLogDetails.put(userId, ActionType.SHARED);
                              }
                            });
                  });
        } else {
          // update acl
          aclOperation
              .getAcls()
              .forEach(
                  acl -> {
                    String userId = acl.getUserId();

                    // check if acl is already present for the user
                    AclSpecification aclSpecification =
                        findAclSpecificationByUserId(measureSet, userId);
                    // if acl is not present, add it
                    if (aclSpecification == null) {
                      measureSet.getAcls().add(acl);

                      acl.getRoles()
                          .forEach(
                              roleEnum -> {
                                if (roleEnum == RoleEnum.SHARED_WITH) {
                                  actionLogDetails.put(userId, ActionType.SHARED);
                                }
                              });
                    } else {
                      acl.getRoles()
                          .forEach(
                              roleEnum -> {
                                if (!aclSpecification.getRoles().contains(roleEnum)) {
                                  aclSpecification.getRoles().add(roleEnum);

                                  if (roleEnum == RoleEnum.SHARED_WITH) {
                                    actionLogDetails.put(userId, ActionType.SHARED);
                                  }
                                }
                              });
                    }
                  });
        }
      } else if (AclOperation.AclAction.REVOKE == aclOperation.getAction()) {
        aclOperation
            .getAcls()
            .forEach(
                acl -> {
                  String userId = acl.getUserId();

                  // check if acl already present for the user
                  AclSpecification aclSpecification =
                      findAclSpecificationByUserId(measureSet, acl.getUserId());
                  if (aclSpecification != null) {
                    // remove roles from ACL
                    acl.getRoles()
                        .forEach(
                            roleEnum -> {
                              if (aclSpecification.getRoles().contains(roleEnum)) {
                                aclSpecification.getRoles().remove(roleEnum);

                                if (roleEnum == RoleEnum.SHARED_WITH) {
                                  actionLogDetails.put(userId, ActionType.UNSHARED);
                                }
                              }
                            });

                    // after removing the roles if there is no role left, remove acl
                    if (aclSpecification.getRoles().isEmpty()) {
                      measureSet.getAcls().remove(aclSpecification);
                    }
                  }
                });
      }

      MeasureSet updatedMeasureSet = measureSetRepository.save(measureSet);
      log.info("ACL updated for Measure set [{}]", updatedMeasureSet.getId());

      Map<String, UserDetailsDto> userDetailsMap =
          userServiceClient.getBulkUserDetails(new ArrayList<>(actionLogDetails.keySet()));

      actionLogDetails.forEach(
          (userId, actionType) -> {
            actionLogService.logShareAccessControlAction(
                measureSetId,
                MeasureSet.class,
                actionType,
                userName,
                userId,
                String.format(
                    actionType == ActionType.UNSHARED
                        ? "Unshared with - %s%s"
                        : "Shared with - %s%s",
                    formatDisplayName(userDetailsMap, userId),
                    isAdmin ? " by MADiE Admin" : ""));
          });

      return updatedMeasureSet;
    } else {
      String error =
          String.format(
              "User %s called updateMeasureSetAcls with AclOperation %s but failed because no "
                  + "measure set exists with measure set ID %s",
              userName, aclOperation.toString(), measureSetId);
      log.error(error);
      throw new ResourceNotFoundException(error);
    }
  }

  public MeasureSet createAndUpdateCmsId(String measureSetId, String username) {
    Optional<MeasureSet> measureSet = measureSetRepository.findByMeasureSetId(measureSetId);
    if (!measureSet.isPresent()) {
      throw new ResourceNotFoundException(
          "No measure set exists for measure with measure set id " + measureSetId);
    }
    if (measureSet.get().getCmsId() != null) {
      throw new InvalidRequestException(
          "CMS ID already exists. Once a CMS Identifier has been generated it may not "
              + "be modified or removed for any draft or version of a measure.");
    }
    int generatedSequenceNumber = generatorRepository.findAndModify("cms_id");
    measureSet.get().setCmsId(generatedSequenceNumber);
    MeasureSet updatedMeasureSet = measureSetRepository.save(measureSet.get());
    log.info("cms id for the Measure set [{}] is successfully created", updatedMeasureSet.getId());
    actionLogService.logMeasureSetAction(
        updatedMeasureSet.getMeasureSetId(),
        MeasureSet.class,
        ActionType.CREATE_CMSID,
        username,
        String.format("Created CMS ID %s", updatedMeasureSet.getCmsId()));
    return updatedMeasureSet;
  }

  public String deleteCmsId(String measureId, Integer cmsId, String harpId, String userName) {
    Optional<Measure> optionalMeasure = measureRepository.findById(measureId);

    if (optionalMeasure.isPresent()) {
      Measure measure = optionalMeasure.get();

      String measureSetId = measure.getMeasureSetId();
      Optional<MeasureSet> optionalMeasureSet =
          measureSetRepository.findByMeasureSetId(measureSetId);

      if (optionalMeasureSet.isEmpty()) {
        throw new ResourceNotFoundException(
            "No measure set exists for measure with measure set id of " + measureSetId);
      }

      MeasureSet measureSet = optionalMeasureSet.get();

      if (!measureSet.getOwner().equalsIgnoreCase(harpId)) {
        throw new HarpIdMismatchException(harpId, measureSet.getOwner(), measure.getId());
      }

      if (measureSet.getCmsId() == null) {
        throw new ResourceNotFoundException(
            String.format(
                "No CMS id of %s exists to be deleted "
                    + "within measure set with measure set id of %s",
                cmsId, measureSetId));
      }

      if (!measureSet.getCmsId().equals(cmsId)) {
        throw new InvalidIdException(
            String.format(
                "CMS id of %s passed in does not match CMS id of %s within "
                    + "measure set with measure set id of %s",
                cmsId, measureSet.getCmsId(), measureSetId));
      }

      List<Measure> measures = measureRepository.findAllByMeasureSetIdAndActive(measureSetId, true);

      if (measures.size() > 1) {
        throw new InvalidRequestException(
            String.format(
                "Measure set with measure set id of %s contains more than 1 measure. "
                    + "Cannot delete CMS id when measure set has more than 1 version of measure.",
                measureSetId));
      }

      measureSet.setCmsId(null);
      measureSetRepository.save(measureSet);

      log.info(
          "With the measure id of [{}], successfully queried "
              + "for its measure set with measure set id of [{}] and deleted CMS id "
              + "of [{}] from the measure set",
          measureId,
          measureSetId,
          cmsId);

      actionLogService.logMeasureSetAction(
          measureSetId,
          MeasureSet.class,
          ActionType.DELETE_CMSID,
          userName,
          String.format("Deleted CMS ID %s", cmsId));

      return String.format(
          "CMS id of %s was deleted successfully from " + "measure set with measure set id of %s",
          cmsId, measureSetId);
    } else {
      throw new ResourceNotFoundException("No measure exists with measure id of " + measureId);
    }
  }

  public MeasureSet findByMeasureSetId(final String measureSetId) {
    return measureSetRepository.findByMeasureSetId(measureSetId).orElse(null);
  }

  private AclSpecification findAclSpecificationByUserId(MeasureSet measureSet, String userId) {
    if (CollectionUtils.isEmpty(measureSet.getAcls())) {
      return null;
    }
    AclSpecification aclSpecification =
        measureSet.getAcls().stream()
            .filter(existingAcl -> existingAcl.getUserId().equalsIgnoreCase(userId))
            .findFirst()
            .orElse(null);
    return aclSpecification;
  }

  public List<MeasureListDTO> getMeasuresByMeasureSetId(
      String measureSetId,
      boolean sortByLatestVersion,
      MeasureSearchCriteria measureSearchCriteria) {
    List<MeasureListDTO> measuresByMeasureSetId =
        measureSetRepository.findMeasuresByMeasureSetId(
            measureSetId, sortByLatestVersion, measureSearchCriteria);

    enrichWithReviewStatus(measureSetId, measuresByMeasureSetId);

    return measuresByMeasureSetId;
  }

  private void enrichWithReviewStatus(String measureSetId, List<MeasureListDTO> measures) {
    if (CollectionUtils.isEmpty(measures)) {
      return;
    }
    Set<String> readyForReviewMeasureIds =
        measureReviewRepository.findAllByMeasureSetId(measureSetId).stream()
            .filter(review -> ReviewStatus.READY_FOR_REVIEW.equals(review.getStatus()))
            .map(MeasureReview::getMeasureId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    measures.forEach(
        measure ->
            measure.setReviewStatus(
                readyForReviewMeasureIds.contains(measure.getId()) ? "Ready" : ""));
  }

  public List<Measure> getRecentMeasuresByMeasureSetId(List<String> measureSetIds) {
    List<Measure> mostRecentMeasures = new ArrayList<>();
    for (String measureSetId : measureSetIds) {
      List<MeasureListDTO> measures = getMeasuresByMeasureSetId(measureSetId, false, null);
      if (measures != null && !measures.isEmpty()) {
        MeasureListDTO measure = measures.get(measures.size() - 1);
        measureRepository.findById(measure.getId()).ifPresent(mostRecentMeasures::add);
      }
    }
    return mostRecentMeasures;
  }

  /**
   * Change ownership in MeasureSet
   *
   * @param measureSetId - the MeasureSet that needs change of ownership
   * @param userId - new owner
   * @param retainShareAccess - add SHARED_WITH for the original owner if true, otherwise keep the
   *     current Acls
   * @param conductedBy - the user that performs the ownership change
   * @return the updated MeasureSet
   */
  public MeasureSet changeOwnership(
      String measureSetId,
      String userId,
      boolean retainShareAccess,
      String conductedBy,
      String accessToken) {
    Optional<MeasureSet> optionalMeasureSet = measureSetRepository.findByMeasureSetId(measureSetId);

    if (optionalMeasureSet.isEmpty()) {
      log.error(
          "Measure with set id [{}] cannot change ownership to user [{}]. Measure set may not exist.",
          measureSetId,
          userId);
      throw new ResourceNotFoundException("MeasureSet", measureSetId);
    }

    MeasureSet measureSet = optionalMeasureSet.get();
    String originalOwner = optionalMeasureSet.get().getOwner();
    boolean isAdmin =
        userServiceClient.hasRole(conductedBy, RoleConstants.MADiE_ADMIN, accessToken);
    // Only the original owner can transfer ownership for non-admin users that conduct the
    // changeOwnership action
    if (!originalOwner.equalsIgnoreCase(conductedBy) && !isAdmin) {
      log.error(
          "User [{}] attempted to transfer ownership of measure set [{}] but is not the original owner [{}].",
          conductedBy,
          measureSetId,
          originalOwner);
      throw new UnauthorizedException(
          String.format(
              "User %s does not have permissions to transfer ownership of the measure Set with ID: %s.",
              conductedBy, measureSetId));
    }

    measureSet.setOwner(userId);

    boolean previouslyShared = false;

    // Remove SHARED_WITH role from new owner if it exists
    if (!CollectionUtils.isEmpty(measureSet.getAcls())) {
      // Find the ACL for the user
      AclSpecification userAcl =
          measureSet.getAcls().stream()
              .filter(acl -> acl.getUserId().equals(userId) && acl.getRoles() != null)
              .findFirst()
              .orElse(null);

      if (userAcl != null) {
        // Remove SHARED_WITH role
        previouslyShared = userAcl.getRoles().remove(RoleEnum.SHARED_WITH);

        // Remove ACL entirely if no roles remain
        if (userAcl.getRoles().isEmpty()) {
          measureSet.getAcls().remove(userAcl);
        }
      }
    }

    // Retain SHARED access for original owner if requested
    if (retainShareAccess) {
      List<AclSpecification> acls =
          !CollectionUtils.isEmpty(measureSet.getAcls()) ? measureSet.getAcls() : new ArrayList<>();
      boolean hasUserAlreadyBeenSharedWith =
          acls.stream().anyMatch(acl -> originalOwner.equalsIgnoreCase(acl.getUserId()));
      if (!hasUserAlreadyBeenSharedWith) {
        acls.add(
            AclSpecification.builder()
                .userId(originalOwner.toLowerCase())
                .roles(Set.of(RoleEnum.SHARED_WITH))
                .build());
      }
      measureSet.setAcls(acls);
    }

    MeasureSet updatedMeasureSet = measureSetRepository.save(measureSet);

    log.info(
        "Measure set [{}] ownership transferred from original owner [{}] to new owner [{}] by user [{}].",
        updatedMeasureSet.getId(),
        originalOwner,
        userId,
        conductedBy);

    Map<String, UserDetailsDto> userDetailsMap =
        userServiceClient.getBulkUserDetails(List.of(originalOwner, userId));

    actionLogService.logMeasureSetAction(
        measureSetId,
        MeasureSet.class,
        ActionType.OWNERSHIP_TRANSFER,
        conductedBy,
        String.format(
            "Transferred from %s to %s%s",
            formatDisplayName(userDetailsMap, originalOwner),
            formatDisplayName(userDetailsMap, userId),
            isAdmin ? " by MADiE Admin" : ""));

    if (retainShareAccess) {
      actionLogService.logShareAccessControlAction(
          updatedMeasureSet.getMeasureSetId(),
          MeasureSet.class,
          ActionType.SHARED,
          conductedBy,
          originalOwner,
          String.format(
              "Shared with - %s%s",
              formatDisplayName(userDetailsMap, originalOwner), isAdmin ? " by MADiE Admin" : ""));

      log.info(
          "Retained SHARED role for user [{}] on measure set [{}] after ownership transfer",
          originalOwner,
          updatedMeasureSet.getMeasureSetId());
    }

    if (previouslyShared) {
      actionLogService.logShareAccessControlAction(
          updatedMeasureSet.getMeasureSetId(),
          MeasureSet.class,
          ActionType.UNSHARED,
          conductedBy,
          userId,
          String.format("%s now has owner permissions instead of share permissions", userId));

      log.info(
          "Removed SHARED role for user [{}] on measure set [{}] after ownership transfer",
          userId,
          updatedMeasureSet.getMeasureSetId());
    }

    return updatedMeasureSet;
  }

  /**
   * Get list of users that a measure set is shared with along with the date shared. If measure set
   * has not been shared with any users, returns an empty list. The date shared is determined by the
   * most recent SHARED action for the user in the MeasureSetActionLog. If no SHARED action is found
   * for the user, the date shared will be displayed as "-".
   *
   * @param measureSet -> measure set to get shared users for
   * @return List of MeasureAccessReportDTO.SharedWithUser
   */
  public List<MeasureAccessReportDTO.SharedWithUser> getSharedUsersForMeasureSet(
      MeasureSet measureSet) {
    if (measureSet == null || CollectionUtils.isEmpty(measureSet.getAcls())) {
      return Collections.emptyList();
    }
    MeasureSetActionLog actionLog =
        measureSetActionLogRepository.findByTargetId(measureSet.getMeasureSetId()).orElse(null);
    return measureSet.getAcls().stream()
        .filter(acl -> !acl.getUserId().equals(measureSet.getOwner()))
        .map(
            aclSpecification -> {
              String sharedWithUser = aclSpecification.getUserId();
              return MeasureAccessReportDTO.SharedWithUser.builder()
                  .userId(sharedWithUser)
                  .dateShared(getDateShared(actionLog, sharedWithUser))
                  .build();
            })
        .toList();
  }

  String formatDisplayName(Map<String, UserDetailsDto> userDetailsMap, String harpId) {
    UserDetailsDto userDetailsDto = userDetailsMap.get(harpId);

    if (userDetailsDto == null) {
      return harpId;
    }

    String name =
        Stream.of(userDetailsDto.getFirstName(), userDetailsDto.getLastName())
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.joining(" "));

    return name.isEmpty() ? harpId : name + " (" + harpId + ")";
  }

  private String getDateShared(MeasureSetActionLog actionLog, String userId) {
    if (actionLog == null || CollectionUtils.isEmpty(actionLog.getActions())) {
      return "-";
    }
    // get date shared by finding the most recent SHARED action for the user in the action log
    return actionLog.getActions().stream()
        .filter(
            action ->
                userId.equalsIgnoreCase(action.getSharedWith())
                    && action.getActionType() == ActionType.SHARED)
        .map(AccessControlAction::getPerformedAt)
        .max(Instant::compareTo)
        .map(DATE_FORMATTER::format)
        .orElse("-");
  }

  public void populatePerformedByDisplayNames(List<Action> actions) {
    if (org.springframework.util.CollectionUtils.isEmpty(actions)) {
      return;
    }
    List<String> harpIds =
        actions.stream()
            .map(Action::getPerformedBy)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toList();
    if (harpIds.isEmpty()) {
      return;
    }
    Map<String, UserDetailsDto> userDetailsMap = userServiceClient.getBulkUserDetails(harpIds);
    actions.stream()
        .filter(action -> StringUtils.isNotBlank(action.getPerformedBy()))
        .forEach(
            action ->
                action.setPerformedBy(formatDisplayName(userDetailsMap, action.getPerformedBy())));
  }
}
