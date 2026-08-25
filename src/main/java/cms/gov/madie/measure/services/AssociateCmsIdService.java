package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.InvalidRequestException;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.MeasureSet;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
public class AssociateCmsIdService extends BaseMeasureService {
  private final MeasureRepository measureRepository;
  private final MeasureSetRepository measureSetRepository;
  private final MeasureLockService measureLockService;
  private final ActionLogService actionLogService;

  @Autowired
  public AssociateCmsIdService(
      MeasureRepository measureRepository,
      MeasureSetRepository measureSetRepository,
      MeasureSetService measureSetService,
      AppConfigService appConfigService,
      MeasureLockService measureLockService,
      ActionLogService actionLogService) {
    // Pass parent dependencies to BaseMeasureService constructor
    super(measureRepository, measureSetService, appConfigService, measureLockService);
    // Assign child-specific fields
    this.measureRepository = measureRepository;
    this.measureSetRepository = measureSetRepository;
    this.measureLockService = measureLockService;
    this.actionLogService = actionLogService;
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
    verifyQiCoreIsNotComposite(qiCoreMeasure);
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

  private void verifyQiCoreIsNotComposite(Measure qiCoreMeasure) {
    if (qiCoreMeasure.getMeasureMetaData().isComposite()) {
      log.info(
          "CMS ID could not be associated. The QI-Core measure with Id [{}] is a composite measure.",
          qiCoreMeasure.getId());
      throw new InvalidResourceStateException(
          "CMS ID could not be associated. Composite measures cannot be linked.");
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
    measureLockService.checkMeasureLock(username, qiCoreMeasure, "associate");
  }
}
