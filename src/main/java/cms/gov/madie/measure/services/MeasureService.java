package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.resources.DuplicateKeyException;
import cms.gov.madie.measure.utils.MeasureUtil;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.measure.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
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
  private final ElmTranslatorClient elmTranslatorClient;
  private final MeasureUtil measureUtil;
  private final ActionLogService actionLogService;
  private final MeasureSetService measureSetService;
  private final CqlTemplateConfigService cqlTemplateConfigService;

  private final TerminologyValidationService terminologyValidationService;

  public void verifyAuthorizationByMeasureSetId(
      String username, String measureSetId, boolean ownerOnly) {
    MeasureSet measureSet = measureSetService.findByMeasureSetId(measureSetId);
    if (measureSet == null) {
      throw new InvalidMeasureStateException(
          "No measure set exists for measure set ID " + measureSetId);
    }

    verifyMeasureSetAuthorization(
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
      throw new InvalidMeasureStateException(
          "No measure set exists for measure with ID " + measure.getId());
    }
    verifyMeasureSetAuthorization(username, "Measure", measure.getId(), roles, measureSet);
  }

  private void verifyMeasureSetAuthorization(
      String username,
      String target,
      String targetId,
      List<RoleEnum> roles,
      MeasureSet measureSet) {
    List<RoleEnum> allowedRoles = roles == null ? List.of() : roles;
    if (!measureSet.getOwner().equalsIgnoreCase(username)
        && (CollectionUtils.isEmpty(measureSet.getAcls())
            || measureSet.getAcls().stream()
                .noneMatch(
                    acl ->
                        acl.getUserId().equalsIgnoreCase(username)
                            && acl.getRoles().stream().anyMatch(allowedRoles::contains)))) {
      throw new UnauthorizedException(target, targetId, username);
    }
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

  public Measure createMeasure(
      Measure measure, final String username, String accessToken, boolean addDefaultCQL) {
    log.info("User [{}] is attempting to create a new measure", username);
    checkDuplicateCqlLibraryName(measure.getCqlLibraryName());
    validateMeasurementPeriod(
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
    // Clear ID so that the unique GUID from MongoDB will be applied
    measureCopy.setId(null);
    measureCopy.setCreatedBy(username);
    measureCopy.setCreatedAt(now);
    measureCopy.setLastModifiedBy(username);
    measureCopy.setLastModifiedAt(now);
    measureCopy.setVersion(new Version(0, 0, 0));
    measureCopy.setVersionId(UUID.randomUUID().toString());
    measureCopy.setMeasureSetId(UUID.randomUUID().toString());
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
    if (measureUtil.isTestCaseConfigurationChanged(updatingMeasure, existingMeasure)) {
      log.info(
          "Measure ID {}, Test Case Configuration has been updated to [{}] by User : [{}] ",
          existingMeasure.getId(),
          updatingMeasure.getTestCaseConfiguration(),
          username);
    }

    if (measureUtil.isMeasurementPeriodChanged(updatingMeasure, existingMeasure)) {
      validateMeasurementPeriod(
          updatingMeasure.getMeasurementPeriodStart(), updatingMeasure.getMeasurementPeriodEnd());
      updateMeasurementPeriods(updatingMeasure);
    }

    updateReferenceId(updatingMeasure.getMeasureMetaData());

    if (!ModelType.QDM_5_6.getValue().equalsIgnoreCase(updatingMeasure.getModel())) {
      updateMeasureDefinitionId(updatingMeasure.getMeasureMetaData());
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

    // clear testcase groups for qdm when scoring or patient basis is changed.
    // for QDM, scoring and patient basis are present outside the group
    // therefor we need to clear testcase groups while updating measure
    if (outputMeasure.getModel().equalsIgnoreCase(ModelType.QDM_5_6.getValue())
        && !CollectionUtils.isEmpty(existingMeasure.getTestCases())) {
      QdmMeasure qdmExistingMeasure = (QdmMeasure) existingMeasure;
      QdmMeasure qdmUpdatingMeasure = (QdmMeasure) updatingMeasure;

      if (!StringUtils.equals(qdmExistingMeasure.getScoring(), qdmUpdatingMeasure.getScoring())
          || (qdmExistingMeasure.isPatientBasis() != qdmUpdatingMeasure.isPatientBasis())) {
        List<TestCase> updatedTestCases =
            existingMeasure.getTestCases().stream()
                .map(
                    testcase -> {
                      testcase.setGroupPopulations(new ArrayList<>());
                      return testcase;
                    })
                .collect(Collectors.toList());
        outputMeasure.setTestCases(updatedTestCases);
      }
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
    return measureRepository.save(outputMeasure);
  }

  public Measure deactivateMeasure(final String id, final String username) {
    if (StringUtils.isBlank(id)) {
      String message = "Invalid measure id: " + id;
      log.error(message);
      throw new InvalidIdException(message);
    }
    final Measure existingMeasure = findMeasureById(id);
    if (existingMeasure != null && existingMeasure.getMeasureMetaData().isDraft()) {
      if (existingMeasure.isActive()) {
        verifyAuthorization(username, existingMeasure);
      } else {
        throw new InvalidDraftStatusException(id);
      }

    } else {
      throw new ResourceNotFoundException("Measure not found during delete action.");
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

  public void validateMeasurementPeriod(Date measurementPeriodStart, Date measurementPeriodEnd) {
    if (measurementPeriodStart == null || measurementPeriodEnd == null) {
      throw new InvalidMeasurementPeriodException(
          "Measurement period date is required and must be valid");
    }
    SimpleDateFormat checkYear = new SimpleDateFormat("yyyy");
    int checkMeasurementPeriodStart = Integer.parseInt(checkYear.format(measurementPeriodStart));
    int checkMeasurementPeriodEnd = Integer.parseInt(checkYear.format(measurementPeriodEnd));

    if (1900 > checkMeasurementPeriodStart
        || checkMeasurementPeriodStart > 2099
        || 1900 > checkMeasurementPeriodEnd
        || checkMeasurementPeriodEnd > 2099) {
      throw new InvalidMeasurementPeriodException(
          "Measurement periods should be between the years 1900 and 2099.");
    }

    if (measurementPeriodEnd.compareTo(measurementPeriodStart) < 1) {
      throw new InvalidMeasurementPeriodException(
          "Measurement period end date should be greater than measurement period start date.");
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

  public void checkVersionIdChanged(String changedVersionId, String originalVersionId) {
    if (StringUtils.isBlank(changedVersionId) && !StringUtils.isBlank(originalVersionId)) {
      throw new InvalidVersionIdException(changedVersionId);
    } else if (!StringUtils.isBlank(changedVersionId)
        && !StringUtils.isBlank(originalVersionId)
        && !changedVersionId.equalsIgnoreCase(originalVersionId)) {
      throw new InvalidVersionIdException(changedVersionId);
    }
  }

  public List<AclSpecification> updateAccessControlList(
      String measureId, AclOperation aclOperation) {
    Optional<Measure> persistedMeasure = measureRepository.findById(measureId);
    if (persistedMeasure.isEmpty()) {
      throw new ResourceNotFoundException("Measure does not exist: " + measureId);
    }

    Measure measure = persistedMeasure.get();
    MeasureSet measureSet =
        measureSetService.updateMeasureSetAcls(measure.getMeasureSetId(), aclOperation);
    actionLogService.logAction(
        measureId, Measure.class, ActionType.UPDATED, "admin", "ACL updated successfully");
    return measureSet.getAcls();
  }

  public Map<String, List<String>> getSharedWithUserIds(List<String> measureIds) {
    Map<String, List<String>> userIdsByMeasureId = new HashMap<>();

    for (String measureId : measureIds) {
      Measure measure = findMeasureById(measureId);

      if (measure == null) {
        throw new ResourceNotFoundException("Measure does not exist: " + measureId);
      }

      if (measure.getMeasureSet() == null) {
        throw new InvalidMeasureStateException(
            "No measure set exists for measure with ID: " + measure.getId());
      }

      if (measure.getMeasureSet().getAcls() == null) {
        userIdsByMeasureId.put(measureId, Collections.emptyList());
      } else {
        userIdsByMeasureId.put(
            measureId,
            measure.getMeasureSet().getAcls().stream()
                .filter(
                    aclSpecification -> aclSpecification.getRoles().contains(RoleEnum.SHARED_WITH))
                .map(AclSpecification::getUserId)
                .sorted()
                .toList());
      }
    }

    return userIdsByMeasureId;
  }

  public boolean changeOwnership(String measureId, String userid) {
    boolean result = false;
    Optional<Measure> persistedMeasure = measureRepository.findById(measureId);
    if (persistedMeasure.isPresent()) {
      Measure measure = persistedMeasure.get();
      measureSetService.updateOwnership(measure.getMeasureSetId(), userid);
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
      boolean filterByCurrentUser,
      Pageable pageReq,
      String username) {
    return measureRepository.searchMeasuresByCriteria(
        username, pageReq, searchCriteria, filterByCurrentUser);
  }

  protected void updateReferenceId(MeasureMetaData metaData) {
    if (metaData != null && !CollectionUtils.isEmpty(metaData.getReferences())) {
      List<Reference> references =
          (List<Reference>)
              metaData.getReferences().stream()
                  .map(reference -> updateReference(reference))
                  .toList();
      metaData.setReferences(references);
    }
  }

  protected void updateMeasureDefinitionId(MeasureMetaData metaData) {
    if (metaData != null && !CollectionUtils.isEmpty(metaData.getMeasureDefinitions())) {
      List<MeasureDefinition> definitions =
          (List<MeasureDefinition>)
              metaData.getMeasureDefinitions().stream()
                  .map(definition -> updateMeasureDefinition(definition))
                  .toList();
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

    String associationSuccessMessage =
        "QI Core measure with ID %s and QDM measure with ID %s are Associated with "
            + "CMS ID %s on %s.";
    String copyMetaDataStatusMessage =
        copyMetaData ? " Metadata was copied over" : " Metadata was NOT copied over";

    actionLogService.logAction(
        measureSet.getId(),
        Measure.class,
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

  public void validateCmsIdAssociation(String username, Measure qiCoreMeasure, Measure qdmMeasure) {

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

    if (StringUtils.equals(qiCoreMeasure.getModel(), qdmMeasure.getModel())) {
      log.info(
          "CMS ID could not be associated. Both measures with IDs [{}],[{}] are of same model type",
          qiCoreMeasure.getId(),
          qdmMeasure.getId());
      throw new InvalidRequestException("CMS ID could not be associated. Please try again.");
    }

    if (qdmMeasure.getMeasureSet().getCmsId() == null) {
      log.info(
          "CMS ID could not be associated. QDM measure with Id [{}] doesn't have CMS ID "
              + "associated with it",
          qdmMeasure.getId());
      throw new InvalidRequestException("CMS ID could not be associated. Please try again.");
    }

    if (qiCoreMeasure.getMeasureSet().getCmsId() != null) {
      log.info(
          "CMS ID could not be associated. The QI-Core measure with Id [{}] already has a CMS ID.",
          qiCoreMeasure.getId());
      throw new InvalidResourceStateException(
          "CMS ID could not be associated. The QI-Core measure already has a CMS ID.");
    }

    if (!qiCoreMeasure.getMeasureMetaData().isDraft()) {
      log.info(
          "CMS ID could not be associated. The QI-Core measure with Id [{}] is versioned.",
          qiCoreMeasure.getId());
      throw new InvalidResourceStateException(
          "CMS ID could not be associated. The QI-Core measure is versioned.");
    }

    if (!CollectionUtils.isEmpty(getQiCoreMeasuresByCmsId(qdmMeasure.getMeasureSet().getCmsId()))) {
      log.info(
          "CMS ID could not be associated. A QI-Core measure already utilizes the CMS ID [{}].",
          qdmMeasure.getMeasureSet().getCmsId());
      throw new InvalidResourceStateException(
          "CMS ID could not be associated. A QI-Core measure already utilizes that CMS ID.");
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
}
