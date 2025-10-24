package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.*;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.repositories.TestCasePatchRepository;
import cms.gov.madie.measure.resources.DuplicateKeyException;
import cms.gov.madie.measure.utils.MeasureServiceUtil;
import cms.gov.madie.measure.utils.MeasureUtil;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.*;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.measure.*;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class MeasureService {
  private final MeasureRepository measureRepository;
  private final MeasureSetRepository measureSetRepository;
  private final TestCasePatchRepository testCasePatchRepository;
  private final ElmTranslatorClient elmTranslatorClient;
  private final MeasureUtil measureUtil;
  private final ActionLogService actionLogService;
  private final MeasureSetService measureSetService;
  private final CqlTemplateConfigService cqlTemplateConfigService;

  private final TerminologyValidationService terminologyValidationService;
  private final AppConfigService appConfigService;
  private final MeasureLockService measureLockService;

  public void verifyAuthorizationByMeasureSetId(
      String username, String measureSetId, boolean ownerOnly) {
    MeasureSet measureSet = measureSetService.findByMeasureSetId(measureSetId);
    if (measureSet == null) {
      throw new InvalidMeasureStateException(
          "No measure set exists for measure set ID " + measureSetId);
    }

    MeasureServiceUtil.verifyMeasureSetAuthorization(
        username,
        "MeasureSet",
        measureSetId,
        ownerOnly ? List.of() : List.of(RoleEnum.SHARED_WITH),
        measureSet);
  }

  /**
   * Throws unAuthorizedException, if the measure is not owned by the user or if the measure is not
   * shared with the user
   */
  public void verifyAuthorization(String username, Measure measure) {
    verifyAuthorization(username, measure, List.of(RoleEnum.SHARED_WITH));
  }

  /**
   * Verifies the specified user has privileges on the given measure based on measure owner and the
   * passed roles. Providing null or empty roles will perform an authorization check for owner only.
   *
   * @param username
   * @param measure
   * @param roles
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

  // TODO: start replacing usage of measureRepository.findById with this method
  public Measure findMeasureById(final String id) {
    return measureRepository
        .findById(id)
        .map(
            m ->
                m.toBuilder()
                    .measureSet(measureSetService.findByMeasureSetId(m.getMeasureSetId()))
                    .build())
        .orElse(null);
  }

  public Measure findActiveMeasureById(@Nullable String measureId) {
    // also returns the exception when id is not found
    return measureRepository
        .findByIdAndActive(measureId, true)
        .orElseThrow(
            () -> {
              log.info("Could not find active Measure with id: {}", measureId);
              return new ResourceNotFoundException("Measure", measureId);
            });
  }

  public Measure createMeasure(
      Measure measure, final String username, String accessToken, boolean addDefaultCQL) {
    log.info("User [{}] is attempting to create a new measure", username);
    checkDuplicateCqlLibraryName(measure.getCqlLibraryName());
    MeasureServiceUtil.validateMeasurementPeriod(
        measure.getMeasurementPeriodStart(), measure.getMeasurementPeriodEnd());
    updateMeasurementPeriods(measure);
    Measure measureCopy = measure.toBuilder().build();
    Set<MeasureErrorType> errorTypes = new HashSet<>();
    try {
      measureCopy = updateElm(measureCopy, accessToken);
    } catch (CqlElmTranslationErrorException ex) {
      errorTypes.add(MeasureErrorType.ERRORS_ELM_JSON);
    }
    try {
      terminologyValidationService.validateTerminology(measureCopy.getElmJson(), accessToken);
    } catch (InvalidTerminologyException ex) {
      errorTypes.add(MeasureErrorType.INVALID_TERMINOLOGY);
    }
    if (!CollectionUtils.isEmpty(errorTypes)) {
      measureCopy.setCqlErrors(true);
      measureCopy.setErrors(errorTypes);
    }
    Instant now = Instant.now();
    measureCopy.setId(null); // Clear ID so that the unique GUID from MongoDB will be applied
    measureCopy.setCreatedBy(username);
    measureCopy.setCreatedAt(now);
    measureCopy.setLastModifiedBy(username);
    measureCopy.setLastModifiedAt(now);
    measureCopy.setVersion(new Version(0, 0, 0));
    measureCopy.setVersionId(UUID.randomUUID().toString());
    measureCopy.setMeasureSetId(UUID.randomUUID().toString());
    measureCopy.setTestCaseConfiguration(TestCaseConfiguration.builder().ravIncluded(true).build());
    if (measureCopy.getMeasureMetaData() != null) {
      measureCopy.getMeasureMetaData().setDraft(true);
    } else {
      MeasureMetaData metaData = new MeasureMetaData();
      metaData.setDraft(true);
      measureCopy.setMeasureMetaData(metaData);
    }
    if (addDefaultCQL) {
      if (ModelType.QI_CORE.getValue().equalsIgnoreCase(measure.getModel())) {
        measureCopy.setCql(
            cqlTemplateConfigService.getQiCore411CqlTemplate() != null
                ? cqlTemplateConfigService
                    .getQiCore411CqlTemplate()
                    .replace("CYBTest3", measureCopy.getCqlLibraryName())
                : "");
      } else if (ModelType.QDM_5_6.getValue().equalsIgnoreCase(measure.getModel())) {
        measureCopy.setCql(
            cqlTemplateConfigService.getQdm56CqlTemplate() != null
                ? cqlTemplateConfigService
                    .getQdm56CqlTemplate()
                    .replace("CYBTestQDMMeasure3", measureCopy.getCqlLibraryName())
                : "");
      } else if (ModelType.QI_CORE_6_0_0.getValue().equalsIgnoreCase(measure.getModel())) {
        measureCopy.setCql(
            cqlTemplateConfigService.getQiCore600CqlTemplate() != null
                ? cqlTemplateConfigService
                    .getQiCore600CqlTemplate()
                    .replace("libraryName", measureCopy.getCqlLibraryName())
                : "");
      }
    }
    Measure savedMeasure = measureRepository.save(measureCopy);
    log.info(
        "User [{}] successfully created new measure with ID [{}]", username, savedMeasure.getId());
    actionLogService.logAction(savedMeasure.getId(), Measure.class, ActionType.CREATED, username);

    measureSetService.createMeasureSet(
        username, savedMeasure.getId(), savedMeasure.getMeasureSetId(), null);
    return savedMeasure;
  }

  /**
   * Only Measure owner or shared with users can update TestcaseConfig These updates are not
   * restricted to draft measures
   *
   * @param username username
   * @param measureId measureId
   * @param testCaseConfig testCaseConfig
   * @return Updated measure
   */
  public Measure updateMeasureTestCaseConfiguration(
      String username, String measureId, TestCaseConfiguration testCaseConfig) {
    if (measureId == null || measureId.isEmpty()) {
      log.error("updateMeasureTestCaseConfiguration:: Measure ID is null or empty");
      throw new InvalidIdException("Measure", "Update (PUT)", "(PUT [base]/[resource]/[id])");
    }
    if (testCaseConfig == null) {
      log.error(
          "updateMeasureTestCaseConfiguration:: Test Case Configuration is null for Measure ID [{}]",
          measureId);
      throw new InvalidRequestException("TestCaseConfiguration cannot be null");
    }
    final Measure existingMeasure = findActiveMeasureById(measureId);

    verifyAuthorization(username, existingMeasure);

    Measure updatedMeasure =
        testCasePatchRepository.findAndModifyTestCaseConfig(testCaseConfig, measureId);
    log.info(
        "Measure ID {}, Test Case Configuration has been updated to [{}] by User : [{}] ",
        updatedMeasure.getId(),
        testCaseConfig,
        username);

    return updatedMeasure;
  }

  public Measure updateMeasure(
      final Measure existingMeasure,
      final String username,
      final Measure updatingMeasure,
      final String accessToken) {
    if (measureUtil.isCqlLibraryNameChanged(updatingMeasure, existingMeasure)) {
      checkDuplicateCqlLibraryName(updatingMeasure.getCqlLibraryName());
    }
    if (StringUtils.isBlank(existingMeasure.getVersionId())) {
      existingMeasure.setVersionId(UUID.randomUUID().toString());
    }
    if (StringUtils.isBlank(existingMeasure.getMeasureSetId())) {
      existingMeasure.setMeasureSetId(UUID.randomUUID().toString());
    }
    // update the included libraries on cql change
    if (!StringUtils.equals(updatingMeasure.getCql(), existingMeasure.getCql())) {
      updatingMeasure.setIncludedLibraries(
          MeasureUtil.getIncludedLibraries(updatingMeasure.getCql()));
    }
    // remove stratifications that do not have associations or cql definitions
    boolean isQiCoreModel =
        ModelType.QI_CORE.getValue().equalsIgnoreCase(updatingMeasure.getModel())
            || ModelType.QI_CORE_6_0_0.getValue().equalsIgnoreCase(updatingMeasure.getModel());

    if (!CollectionUtils.isEmpty(updatingMeasure.getGroups())) {
      for (Group group : updatingMeasure.getGroups()) {
        if (!CollectionUtils.isEmpty(group.getStratifications())) {
          List<Stratification> filteredStratifications =
              group.getStratifications().stream()
                  .filter(
                      stratification ->
                          isQiCoreModel
                              ? !CollectionUtils.isEmpty(stratification.getAssociations())
                              : StringUtils.isNotBlank(stratification.getCqlDefinition()))
                  .collect(Collectors.toList());
          group.setStratifications(filteredStratifications);
        }
      }
    }

    if (measureUtil.isMeasurementPeriodChanged(updatingMeasure, existingMeasure)) {
      MeasureServiceUtil.validateMeasurementPeriod(
          updatingMeasure.getMeasurementPeriodStart(), updatingMeasure.getMeasurementPeriodEnd());
      updateMeasurementPeriods(updatingMeasure);
    }

    updateReferences(updatingMeasure.getMeasureMetaData());

    if (!ModelType.QDM_5_6.getValue().equalsIgnoreCase(updatingMeasure.getModel())) {
      updateMeasureDefinitions(updatingMeasure.getMeasureMetaData());
    }

    Measure outputMeasure = updatingMeasure;
    if (measureUtil.isMeasureCqlChanged(existingMeasure, updatingMeasure)
        || measureUtil.isSupplementalDataChanged(existingMeasure, updatingMeasure)
        || measureUtil.isRiskAdjustmentChanged(existingMeasure, updatingMeasure)) {
      try {
        outputMeasure =
            measureUtil.validateAllMeasureDependencies(updateElm(updatingMeasure, accessToken));

        // remove this condition when we validate for terminology service errors in
        // backend
        if (!outputMeasure.isCqlErrors()) {
          outputMeasure.setCqlErrors(updatingMeasure.isCqlErrors());
        }
        // no errors were encountered so remove the ELM JSON error
        // TODO: remove this when backend validations for CQL/ELM are enhanced
        outputMeasure.setErrors(
            measureUtil.removeError(outputMeasure.getErrors(), MeasureErrorType.ERRORS_ELM_JSON));
      } catch (CqlElmTranslationErrorException ex) {
        outputMeasure =
            updatingMeasure.toBuilder()
                .cqlErrors(true)
                .error(MeasureErrorType.ERRORS_ELM_JSON)
                .build();
      }
    } else {
      // prevent users from manually clearing errors!
      outputMeasure.setErrors(existingMeasure.getErrors());
    }

    outputMeasure.getMeasureMetaData().setDraft(existingMeasure.getMeasureMetaData().isDraft());
    outputMeasure.setLastModifiedBy(username);
    outputMeasure.setLastModifiedAt(Instant.now());
    // prevent users from overwriting the createdAt/By
    outputMeasure.setCreatedAt(existingMeasure.getCreatedAt());
    outputMeasure.setCreatedBy(existingMeasure.getCreatedBy());
    // prevent users from overwriting versionId and measureSetId
    outputMeasure.setVersionId(existingMeasure.getVersionId());
    outputMeasure.setMeasureSetId(existingMeasure.getMeasureSetId());
    return measureRepository.findAndModify(outputMeasure);
  }

  public Measure deactivateMeasure(final String id, final String username) {
    if (StringUtils.isBlank(id) || StringUtils.isBlank(username)) {
      throw new InvalidIdException("Username and Measure Id is required.");
    }
    final Measure existingMeasure = findMeasureById(id);
    if (existingMeasure == null) {
      throw new ResourceNotFoundException("Measure does not exist.");
    }
    if (!username.equalsIgnoreCase(existingMeasure.getMeasureSet().getOwner())) {
      throw new UnauthorizedException("User is not authorized to delete this measure.");
    }

    if (!existingMeasure.getMeasureMetaData().isDraft()) {
      throw new InvalidDraftStatusException(id);
    }
    if (!existingMeasure.isActive()) {
      throw new InvalidResourceStateException("Measure is inactive.");
    }

    if (appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)) {
      measureLockService.checkMeasureAndTestCaseLock(username, existingMeasure, "delete");
    }
    existingMeasure.setActive(false);
    existingMeasure.setLastModifiedBy(username);
    existingMeasure.setLastModifiedAt(Instant.now());
    // prevent users from overwriting the createdAt/By
    existingMeasure.setCreatedAt(existingMeasure.getCreatedAt());
    existingMeasure.setCreatedBy(existingMeasure.getCreatedBy());
    // prevent users from overwriting versionId and measureSetId
    existingMeasure.setVersionId(existingMeasure.getVersionId());
    existingMeasure.setMeasureSetId(existingMeasure.getMeasureSetId());
    Measure saveMeasure = measureRepository.save(existingMeasure);
    actionLogService.logAction(id, Measure.class, ActionType.DELETED, username);
    measureLockService.unlockMeasure(id, username);
    return saveMeasure;
  }

  private void updateMeasurementPeriods(Measure measure) {
    Date startDate = measure.getMeasurementPeriodStart();
    Instant startInstant =
        startDate.toInstant().atOffset(ZoneOffset.UTC).with(LocalTime.MIN).toInstant();
    measure.setMeasurementPeriodStart(Date.from(startInstant));

    Date endDate = measure.getMeasurementPeriodEnd();
    Instant endInstant =
        endDate.toInstant().atOffset(ZoneOffset.UTC).with(LocalTime.MAX).toInstant();
    measure.setMeasurementPeriodEnd(Date.from(endInstant));
  }

  public void checkDuplicateCqlLibraryName(String cqlLibraryName) {
    if (StringUtils.isNotEmpty(cqlLibraryName)) {
      List<Measure> measureList = measureRepository.findAllByCqlLibraryName(cqlLibraryName);
      if (!measureList.isEmpty()) {
        throw new DuplicateKeyException(
            "cqlLibraryName", "CQL library with given name already exists.");
      }
    }
  }

  public void checkDeletionCredentials(String username, String createdBy) {
    if (!username.equalsIgnoreCase(createdBy)) {
      throw new InvalidDeletionCredentialsException(username);
    }
  }

  public Measure updateElm(Measure measure, String accessToken) {
    if (measure != null && StringUtils.isNotBlank(measure.getCql())) {
      final ElmJson elmJson =
          elmTranslatorClient.getElmJson(measure.getCql(), measure.getModel(), accessToken);
      if (elmTranslatorClient.hasErrors(elmJson)) {
        throw new CqlElmTranslationErrorException(measure.getMeasureName());
      }

      return measure.toBuilder().elmJson(elmJson.getJson()).elmXml(elmJson.getXml()).build();
    }
    return measure;
  }

  public List<AclSpecification> updateAccessControlList(
      String measureId, AclOperation aclOperation, String userName) {
    log.info(
        "User [{}] has called updateAccessControlList with measure ID [{}] and AclOperation [{}]",
        userName,
        measureId,
        aclOperation.toString());
    Optional<Measure> persistedMeasure = measureRepository.findById(measureId);
    if (persistedMeasure.isEmpty()) {
      log.error(
          "User [{}] called updateAccessControlList but failed because the measure with measure "
              + "ID [{}] does not exist.",
          userName,
          measureId);
      throw new ResourceNotFoundException("Measure does not exist: " + measureId);
    }

    Measure measure = persistedMeasure.get();
    MeasureSet measureSet =
        measureSetService.updateMeasureSetAcls(measure.getMeasureSetId(), aclOperation, userName);

    log.info(
        "User [{}] successfully called updateAccessControlList with measure ID [{}] and "
            + "AclOperation [{}]. The AclSpecification is now [{}]",
        userName,
        measureId,
        aclOperation,
        measureSet.getAcls());
    return measureSet.getAcls();
  }

  public Map<String, List<SharedUser>> getSharedMeasures(List<String> measureIds, String username) {
    Map<String, List<SharedUser>> sharedMeasures = new HashMap<>();

    for (String measureId : measureIds) {
      Measure measure = findMeasureById(measureId);

      if (measure == null) {
        log.error(
            "User [{}] called getSharedMeasures but failed because the measure with measure ID "
                + "[{}] does not exist.",
            username,
            measureId);
        throw new ResourceNotFoundException("Measure does not exist: " + measureId);
      }

      if (measure.getMeasureSet() == null) {
        log.error(
            "User [{}] called getSharedMeasures but failed because no measure set exists for "
                + "measure with measure ID [{}]",
            username,
            measureId);
        throw new InvalidMeasureStateException(
            "No measure set exists for measure with ID: " + measure.getId());
      }

      if (measure.getMeasureSet().getAcls() == null) {
        sharedMeasures.put(measureId, Collections.emptyList());
      } else {
        List<String> userIds =
            measure.getMeasureSet().getAcls().stream()
                .filter(
                    aclSpecification -> aclSpecification.getRoles().contains(RoleEnum.SHARED_WITH))
                .map(AclSpecification::getUserId)
                .toList();

        MeasureSetActionLog measureSetActionLog =
            actionLogService.findMeasureSetActionLogByTargetId(measure.getMeasureSetId());

        if (measureSetActionLog != null) {
          Collections.reverse(measureSetActionLog.getActions());
          List<AccessControlAction> shareActions =
              measureSetActionLog.getActions().stream()
                  .filter(action -> action.getActionType().equals(ActionType.SHARED))
                  .toList();

          List<SharedUser> sharedUsers =
              userIds.stream()
                  .map(
                      userId -> {
                        SharedUser sharedUser = SharedUser.builder().userId(userId).build();

                        Optional<AccessControlAction> latestShareActionByUserId =
                            shareActions.stream()
                                .filter(action -> action.getSharedWith().equals(userId))
                                .findFirst();
                        latestShareActionByUserId.ifPresent(
                            action -> sharedUser.setPerformedAt(action.getPerformedAt()));

                        return sharedUser;
                      })
                  .toList();

          sharedMeasures.put(measureId, sharedUsers);
        } else {
          sharedMeasures.put(
              measureId,
              userIds.stream().map(userId -> SharedUser.builder().userId(userId).build()).toList());
        }
      }
    }

    return sharedMeasures;
  }

  public Map<String, List<AclSpecification>> shareMeasures(
      Map<String, List<String>> measureUserIdMap, String username) {
    log.info(
        "User [{}] has called shareMeasures with measureUserIdMap [{}]",
        username,
        measureUserIdMap);

    Map<String, List<AclSpecification>> measureIdToAclSpecification = new HashMap<>();

    // Restrict sharing to owners of measure only
    verifyShareAuthorization(measureUserIdMap, username, true);

    measureUserIdMap.forEach(
        (measureId, userIds) -> {
          AclOperation aclOperation = buildShareAclOperation(userIds);
          measureIdToAclSpecification.put(
              measureId, updateAccessControlList(measureId, aclOperation, username));
        });

    log.info(
        "User [{}] successfully called shareMeasures with measureUserIdMap [{}]. The "
            + "AclSpecification is now [{}]",
        username,
        measureUserIdMap,
        measureIdToAclSpecification);

    return measureIdToAclSpecification;
  }

  public Map<String, List<AclSpecification>> unshareMeasures(
      Map<String, List<String>> measureUserIdMap, String username) {
    log.info(
        "User [{}] has called unshareMeasures with measureUserIdMap [{}]",
        username,
        measureUserIdMap);

    Map<String, List<AclSpecification>> measureIdToAclSpecification = new HashMap<>();

    // Allow unsharing by owners of measure or already shared user of measure
    verifyShareAuthorization(measureUserIdMap, username, false);

    measureUserIdMap.forEach(
        (measureId, userIds) -> {
          AclOperation aclOperation = buildUnshareAclOperation(userIds);
          measureIdToAclSpecification.put(
              measureId, updateAccessControlList(measureId, aclOperation, username));
        });

    log.info(
        "User [{}] successfully called unshareMeasures with measureUserIdMap [{}]. The "
            + "AclSpecification is now [{}]",
        username,
        measureUserIdMap,
        measureIdToAclSpecification);

    return measureIdToAclSpecification;
  }

  /**
   * Verifies that the user is authorized to perform share/unshare operations on the given measures.
   * - Restrict sharing to owners only (ownerOnly = true). - Allow unsharing by owners or users who
   * the measure is already shared with (ownerOnly = false).
   */
  private void verifyShareAuthorization(
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

  private AclOperation buildShareAclOperation(List<String> userIds) {
    return AclOperation.builder()
        .acls(buildShareAclSpecifications(userIds))
        .action(AclOperation.AclAction.GRANT)
        .build();
  }

  private AclOperation buildUnshareAclOperation(List<String> userIds) {
    return AclOperation.builder()
        .acls(buildShareAclSpecifications(userIds))
        .action(AclOperation.AclAction.REVOKE)
        .build();
  }

  private List<AclSpecification> buildShareAclSpecifications(List<String> userIds) {
    return userIds.stream()
        .map(
            userId ->
                AclSpecification.builder()
                    .userId(userId)
                    .roles(Set.of(RoleEnum.SHARED_WITH))
                    .build())
        .toList();
  }

  public boolean changeOwnership(String measureId, String userid, String username) {
    boolean result = false;
    Optional<Measure> persistedMeasure = measureRepository.findById(measureId);
    if (persistedMeasure.isPresent()) {
      Measure measure = persistedMeasure.get();
      measureSetService.changeOwnership(measure.getMeasureSetId(), userid, false, username);
      result = true;
    }
    return result;
  }

  public Map<String, Boolean> getMeasureDrafts(List<String> measureSetIds) {
    Map<String, Boolean> measureSetMap = new HashMap<>();
    List<Measure> measures =
        measureRepository.findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(
            measureSetIds, true, true);
    // for every found measureSetId, put the id & false (not draftable in the map)
    measureSetIds.forEach(
        id -> {
          if (measures.stream().anyMatch(measure -> measure.getMeasureSetId().equals(id))) {
            measureSetMap.put(id, Boolean.FALSE);
          } else { // measures doesn't contain ID
            measureSetMap.put(id, Boolean.TRUE);
          }
        });
    // For measureSetIds that were searched, but not returned put the id & true ( is
    // draftable )

    return measureSetMap;
  }

  public List<String> getAllActiveMeasureIds(boolean draftOnly) {
    return (draftOnly
            ? measureRepository.findAllMeasureIdsByActiveAndMeasureMetaDataDraft(true)
            : measureRepository.findAllMeasureIdsByActive())
        .stream().map(Measure::getId).collect(Collectors.toList());
  }

  public Page<MeasureListDTO> getMeasuresByCriteria(
      MeasureSearchCriteria searchCriteria,
      List<OwnershipType> ownershipTypes,
      Pageable pageReq,
      String username,
      // TODO Remove parameter when either measureSearch or EditTestsOnVersionedMeasure is removed.
      String invocationSource) {
    return measureRepository.searchMeasuresByCriteria(
        username, pageReq, searchCriteria, ownershipTypes, invocationSource);
  }

  protected void updateReferences(MeasureMetaData metaData) {
    if (metaData != null && !CollectionUtils.isEmpty(metaData.getReferences())) {
      List<Reference> references =
          metaData.getReferences().stream().map(this::updateReference).toList();
      metaData.setReferences(references);
    }
  }

  protected void updateMeasureDefinitions(MeasureMetaData metaData) {
    if (metaData != null && !CollectionUtils.isEmpty(metaData.getMeasureDefinitions())) {
      List<MeasureDefinition> definitions =
          metaData.getMeasureDefinitions().stream().map(this::updateMeasureDefinition).toList();
      metaData.setMeasureDefinitions(definitions);
    }
  }

  private MeasureDefinition updateMeasureDefinition(MeasureDefinition definition) {
    return MeasureDefinition.builder()
        .id(
            StringUtils.isBlank(definition.getId())
                ? UUID.randomUUID().toString()
                : definition.getId())
        .term(definition.getTerm())
        .definition(definition.getDefinition())
        .build();
  }

  private Reference updateReference(Reference reference) {
    return Reference.builder()
        .id(
            StringUtils.isBlank(reference.getId())
                ? UUID.randomUUID().toString()
                : reference.getId())
        .referenceText(reference.getReferenceText())
        .referenceType(reference.getReferenceType())
        .build();
  }

  public List<Measure> findAllByMeasureSetId(String measureSetId) {
    return measureRepository.findAllByMeasureSetIdAndActive(measureSetId, true);
  }

  public void deleteVersionedMeasures(List<Measure> measures) {

    List<Measure> versionedMeasures =
        measures.stream()
            .filter(
                measure ->
                    measure.getMeasureMetaData() != null && !measure.getMeasureMetaData().isDraft())
            .collect(Collectors.toList());
    if (!CollectionUtils.isEmpty(versionedMeasures)) {
      String deletedMeasureIds =
          versionedMeasures.stream().map(Measure::getId).collect(Collectors.joining(","));
      measureRepository.deleteAll(versionedMeasures);
      log.info("Versioned Measure IDs [{}] are deleted.", deletedMeasureIds);
    }
  }

  public void copyQdmMetaData(Measure qiCoreMeasure, Measure qdmMeasure) {
    MeasureMetaData qiCoreMeasureMetaData = qiCoreMeasure.getMeasureMetaData();
    MeasureMetaData qdmMeasureMetaData = qdmMeasure.getMeasureMetaData();

    log.info(
        "Copying the meta data from QDM measure [{}] to QI Core measure[{}]",
        qiCoreMeasure.getId(),
        qdmMeasure.getId());

    if (!CollectionUtils.isEmpty(qdmMeasureMetaData.getEndorsements())) {
      qiCoreMeasureMetaData.setEndorsements(qdmMeasureMetaData.getEndorsements());
    }
    if (qdmMeasureMetaData.getSteward() != null) {
      qiCoreMeasureMetaData.setSteward(qdmMeasureMetaData.getSteward());
    }
    if (!CollectionUtils.isEmpty(qdmMeasureMetaData.getDevelopers())) {
      qiCoreMeasureMetaData.setDevelopers(qdmMeasureMetaData.getDevelopers());
    }
    if (StringUtils.isNotBlank(qdmMeasureMetaData.getDescription())) {
      qiCoreMeasureMetaData.setDescription(qdmMeasureMetaData.getDescription());
    }
    if (StringUtils.isNotBlank(qdmMeasureMetaData.getRationale())) {
      qiCoreMeasureMetaData.setRationale(qdmMeasureMetaData.getRationale());
    }
    if (StringUtils.isNotBlank(qdmMeasureMetaData.getGuidance())) {
      qiCoreMeasureMetaData.setGuidance(qdmMeasureMetaData.getGuidance());
    }
    if (StringUtils.isNotBlank(qdmMeasureMetaData.getDefinition())) {
      qiCoreMeasureMetaData.setDefinition(qdmMeasureMetaData.getDefinition());
    }
    if (StringUtils.isNotBlank(qdmMeasureMetaData.getClinicalRecommendation())) {
      qiCoreMeasureMetaData.setClinicalRecommendation(
          qdmMeasureMetaData.getClinicalRecommendation());
    }
    if (!CollectionUtils.isEmpty(qdmMeasureMetaData.getReferences())) {
      qiCoreMeasureMetaData.setReferences(qdmMeasureMetaData.getReferences());
    }
    if (StringUtils.isNotBlank(qdmMeasureMetaData.getCopyright())) {
      qiCoreMeasureMetaData.setCopyright(qdmMeasureMetaData.getCopyright());
    }
    if (StringUtils.isNotBlank(qdmMeasureMetaData.getDisclaimer())) {
      qiCoreMeasureMetaData.setDisclaimer(qdmMeasureMetaData.getDisclaimer());
    }

    qiCoreMeasure.setMeasurementPeriodStart(qdmMeasure.getMeasurementPeriodStart());
    qiCoreMeasure.setMeasurementPeriodEnd(qdmMeasure.getMeasurementPeriodEnd());

    measureRepository.save(qiCoreMeasure);
  }

  public MeasureSet associateCmsId(
      String username, String qiCoreMeasureId, String qdmMeasureId, boolean copyMetaData) {
    if (StringUtils.isBlank(qiCoreMeasureId) || StringUtils.isBlank(qdmMeasureId)) {
      log.info(
          "CMS ID could not be associated. Measure Ids [{}],[{}] cannot be null",
          qiCoreMeasureId,
          qdmMeasureId);
      throw new InvalidIdException("CMS ID could not be associated. Please try again.");
    }

    Measure qiCoreMeasure = findMeasureById(qiCoreMeasureId);
    Measure qdmMeasure = findMeasureById(qdmMeasureId);

    if (qiCoreMeasure == null || qdmMeasure == null) {
      log.info(
          "CMS ID could not be associated. Measures with given Ids [{}],[{}] are not found",
          qiCoreMeasureId,
          qdmMeasureId);
      throw new ResourceNotFoundException("CMS ID could not be associated. Please try again.");
    }

    validateCmsIdAssociation(username, qiCoreMeasure, qdmMeasure);

    if (copyMetaData) {
      copyQdmMetaData(qiCoreMeasure, qdmMeasure);
      log.info(
          "User [{}] successfully copied the meta data from QDM Measure with Id [{}] to "
              + "QI Core Measure with Id [{}]",
          username,
          qdmMeasureId,
          qiCoreMeasureId);
    }

    MeasureSet measureSet = qiCoreMeasure.getMeasureSet();
    measureSet.setCmsId(qdmMeasure.getMeasureSet().getCmsId());
    measureSetRepository.save(measureSet);
    log.info(
        "User [{}] successfully associated the measures [{}], [{}] with CMS ID [{}]",
        username,
        qiCoreMeasureId,
        qdmMeasureId,
        measureSet.getCmsId());

    measureLockService.unlockMeasure(qiCoreMeasureId, username);

    String associationSuccessMessage =
        "QI Core measure with ID %s and QDM measure with ID %s are Associated with "
            + "CMS ID %s on %s.";
    String copyMetaDataStatusMessage =
        copyMetaData ? " Metadata was copied over" : " Metadata was NOT copied over";

    actionLogService.logMeasureSetAction(
        measureSet.getMeasureSetId(),
        MeasureSet.class,
        ActionType.ASSOCIATED,
        username,
        String.format(
            associationSuccessMessage + copyMetaDataStatusMessage,
            qiCoreMeasureId,
            qdmMeasureId,
            measureSet.getCmsId(),
            Instant.now()));

    return measureSet;
  }

  public List<Measure> getQiCoreMeasuresByCmsId(Integer qdmCmsId) {
    return measureRepository.findAllByModelAndCmsId(ModelType.QI_CORE.getValue(), qdmCmsId);
  }

  void validateCmsIdAssociation(String username, Measure qiCoreMeasure, Measure qdmMeasure) {
    if (qiCoreMeasure == null || qdmMeasure == null) {
      throw new ResourceNotFoundException("CMS ID could not be associated. Please try again.");
    }

    verifyOneQiCoreAndOneQdmMeasure(qiCoreMeasure, qdmMeasure);
    verifyOwner(username, qiCoreMeasure, qdmMeasure);
    verifyQdmHasCmsId(qdmMeasure);
    verifyQiCoreDoesNotHaveCmsId(qiCoreMeasure);
    verifyQiCoreIsDraft(qiCoreMeasure);
    verifyNoOtherQiCoreHasCmsId(qdmMeasure);
    verifyQiCoreMeasureNotLocked(qiCoreMeasure, username);
  }

  private void verifyOneQiCoreAndOneQdmMeasure(Measure qiCoreMeasure, Measure qdmMeasure) {
    if ((!qiCoreMeasure.getModel().equals(ModelType.QI_CORE.getValue())
            && !qiCoreMeasure.getModel().equals(ModelType.QI_CORE_6_0_0.getValue()))
        || !qdmMeasure.getModel().equals(ModelType.QDM_5_6.getValue())) {
      log.info("CMS ID could not be associated. Must pass in one QDM and one QI-Core measure");
      throw new InvalidRequestException(
          "CMS ID could not be associated. Must select one QDM and one QI-Core measure.");
    }
  }

  private void verifyOwner(String username, Measure qiCoreMeasure, Measure qdmMeasure) {
    // only owners(not shared users) can perform cms id association
    if (!(StringUtils.equals(qiCoreMeasure.getMeasureSet().getOwner(), username)
        && StringUtils.equals(qdmMeasure.getMeasureSet().getOwner(), username))) {
      log.info(
          "CMS ID could not be associated for measures with IDs [{}], [{}]. User is not authorized "
              + "to perform CMS id association",
          qiCoreMeasure.getId(),
          qdmMeasure.getId());
      throw new UnauthorizedException("CMS ID could not be associated. Please try again.");
    }
  }

  private void verifyQdmHasCmsId(Measure qdmMeasure) {
    if (qdmMeasure.getMeasureSet().getCmsId() == null) {
      log.info(
          "CMS ID could not be associated. QDM measure with Id [{}] doesn't have CMS ID "
              + "associated with it",
          qdmMeasure.getId());
      throw new InvalidRequestException("CMS ID could not be associated. Please try again.");
    }
  }

  private void verifyQiCoreDoesNotHaveCmsId(Measure qiCoreMeasure) {
    if (qiCoreMeasure.getMeasureSet().getCmsId() != null) {
      log.info(
          "CMS ID could not be associated. The QI-Core measure with Id [{}] already has a CMS ID.",
          qiCoreMeasure.getId());
      throw new InvalidResourceStateException(
          "CMS ID could not be associated. The QI-Core measure already has a CMS ID.");
    }
  }

  private void verifyQiCoreIsDraft(Measure qiCoreMeasure) {
    if (!qiCoreMeasure.getMeasureMetaData().isDraft()) {
      log.info(
          "CMS ID could not be associated. The QI-Core measure with Id [{}] is versioned.",
          qiCoreMeasure.getId());
      throw new InvalidResourceStateException(
          "CMS ID could not be associated. The QI-Core measure is versioned.");
    }
  }

  private void verifyNoOtherQiCoreHasCmsId(Measure qdmMeasure) {
    if (!CollectionUtils.isEmpty(getQiCoreMeasuresByCmsId(qdmMeasure.getMeasureSet().getCmsId()))) {
      log.info(
          "CMS ID could not be associated. A QI-Core measure already utilizes the CMS ID [{}].",
          qdmMeasure.getMeasureSet().getCmsId());
      throw new InvalidResourceStateException(
          "CMS ID could not be associated. A QI-Core measure already utilizes that CMS ID.");
    }
  }

  private void verifyQiCoreMeasureNotLocked(Measure qiCoreMeasure, String username) {
    if (appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)) {
      measureLockService.checkMeasureLock(username, qiCoreMeasure, "associate");
    }
  }

  /**
   * Find out all the measures that includes any version of given library name
   *
   * @param libraryName - library name for which usage needs to be determined
   * @return List of LibraryUsage
   */
  public List<LibraryUsage> findLibraryUsage(String libraryName) {
    if (StringUtils.isBlank(libraryName)) {
      throw new InvalidRequestException("Please provide library name.");
    }
    return measureRepository.findLibraryUsageByLibraryName(libraryName);
  }

  public int countMeasuresByOwnership(
      boolean isActive, String userId, List<OwnershipType> ownershipTypes) {
    return measureRepository.countMeasuresByOwnership(isActive, userId, ownershipTypes);
  }

  public boolean transferMeasures(
      List<String> measureIds, String harpId, boolean retainShareAccess, String conductedBy) {
    boolean result = true;
    for (String measureId : measureIds) {
      Optional<Measure> persistedMeasure = measureRepository.findById(measureId);
      if (persistedMeasure.isPresent()) {
        measureSetService.changeOwnership(
            persistedMeasure.get().getMeasureSetId(), harpId, retainShareAccess, conductedBy);
      } else {
        result = false;
      }
    }
    return result;
  }

  public List<Action> getMeasureHistory(String measureId, String userName) {
    if (StringUtils.isBlank(measureId)) {
      throw new InvalidRequestException("Measure ID cannot be null or empty.");
    }

    Optional<Measure> persistedMeasure = measureRepository.findById(measureId);
    if (persistedMeasure.isEmpty()) {
      throw new ResourceNotFoundException("Measure does not exist: " + measureId);
    }

    List<Action> measureHistory =
        actionLogService.findMeasureHistory(measureId, persistedMeasure.get().getMeasureSetId());
    log.info(
        "User [{}] successfully retrieved the history of the measure with ID [{}]",
        userName,
        measureId);

    return measureHistory;
  }
}
