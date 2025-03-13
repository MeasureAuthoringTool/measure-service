package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.OrganizationRepository;
import gov.cms.madie.models.common.Organization;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@ChangeUnit(id = "measure_orgs_update", order = "2", author = "madie_dev")
public class UpdateMeasureOrganizationsChangeUnit {

  @Execution
  public void updateMeasureOrganizations(
      MeasureRepository measureRepository, OrganizationRepository organizationRepository)
      throws Exception {
    log.info("Entering updateMeasureOrganizations()");

    List<Organization> allOrganizations = organizationRepository.findAll();
    if (allOrganizations == null || allOrganizations.isEmpty()) {
      log.error(
          "No organizations found! Exiting updateMeasureOrganizations to "
              + "prevent from destroying all measures!");
      return;
    }
    List<Measure> measureList = measureRepository.findAll();
    Map<String, Organization> orgMap =
        allOrganizations.stream()
            .collect(Collectors.toMap(Organization::getName, Function.identity()));
    measureList.stream()
        .forEach(
            measure -> {
              MeasureMetaData measureMetaData = measure.getMeasureMetaData();
              boolean updateRequired = false;
              if (measureMetaData.getSteward() != null) {
                updateRequired = true;
                Organization stewardOrg = orgMap.get(measureMetaData.getSteward().getName());
                if (stewardOrg != null) {
                  measureMetaData.setSteward(stewardOrg);
                } else {
                  measureMetaData.setSteward(null);
                }
              }
              List<Organization> developers = measureMetaData.getDevelopers();
              if (developers != null && !measureMetaData.getDevelopers().isEmpty()) {
                updateRequired = true;
                List<Organization> updatedOrgs =
                    developers.stream()
                        .filter(developer -> orgMap.containsKey(developer.getName()))
                        .map(developer -> orgMap.get(developer.getName()))
                        .collect(Collectors.toList());
                measure.getMeasureMetaData().setDevelopers(updatedOrgs);
              }

              if (updateRequired) {
                log.info(
                    "[MADiE System] updating steward and/or developers on measure with ID [{}]. "
                        + "Any invalid orgs will be removed, "
                        + "additional org data for valid orgs will be added.",
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
