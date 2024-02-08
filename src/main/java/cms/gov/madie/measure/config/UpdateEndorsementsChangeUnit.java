package cms.gov.madie.measure.config;

import cms.gov.madie.measure.repositories.EndorsementRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.EndorserOrganization;
import gov.cms.madie.models.measure.Endorsement;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "endorsements_update_1", order = "2", author = "madie_dev")
public class UpdateEndorsementsChangeUnit {

  @Execution
  public void updateEndorsements(
      MeasureRepository measureRepository, EndorsementRepository endorsementRepository)
      throws Exception {
    log.info("Entering updateEndorsements()");

    List<EndorserOrganization> allEndorsers = endorsementRepository.findAll();
    if (allEndorsers == null || allEndorsers.isEmpty()) {
      log.error(
          "No endorsements found! Exiting updateEndorsements to "
              + "prevent from destroying all measures!");
      return;
    }
    List<Measure> measureList = measureRepository.findAll();
    Map<String, EndorserOrganization> orgMap =
        allEndorsers.stream()
            .collect(
                Collectors.toMap(
                    EndorserOrganization::getEndorserOrganization, Function.identity()));
    measureList.stream()
        .forEach(
            measure -> {
              MeasureMetaData measureMetaData = measure.getMeasureMetaData();
              boolean updateRequired = false;
              List<Endorsement> endorsements = measureMetaData.getEndorsements();
              if (endorsements != null && !measureMetaData.getEndorsements().isEmpty()) {
                updateRequired = true;
                List<Endorsement> updatedOrgs =
                    endorsements.stream()
                        .map(
                            endorsement -> {
                              if (!orgMap.containsKey(endorsement.getEndorser())) {
                                endorsement.setEndorser("CMS Consensus Based Entity");
                              }
                              return endorsement;
                            })
                        .collect(Collectors.toList());
                measure.getMeasureMetaData().setEndorsements(updatedOrgs);
              }

              if (updateRequired) {
                log.info(
                    "[MADiE System] updating endorsement on measure with ID [{}]. "
                        + "Any invalid endorsement will be removed, "
                        + "additional endorsement data for valid endorsements will be added.",
                    measure.getId());
                measureRepository.save(measure);
              }
            });
    log.info("Completed updateMeasureOrganizations()");
  }

  @RollbackExecution
  public void rollbackExecution(MeasureRepository measureRepository) throws Exception {
    log.debug("Entering rollbackExecution()");

    // cannot really roll this back unless we backup the data..
  }
}
