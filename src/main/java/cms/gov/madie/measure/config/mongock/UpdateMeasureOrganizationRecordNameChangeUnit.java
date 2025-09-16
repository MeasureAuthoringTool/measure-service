package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.OrganizationRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "measure_orgs_update_record_name", order = "1", author = "madie_dev")
public class UpdateMeasureOrganizationRecordNameChangeUnit {

    // this method with updated filter conditions and change unit ID can be used for future updates
  @Execution
  public void updateMeasureOrganizationRecordName(OrganizationRepository organizationRepository) {
    log.info("Entering the organizations for updating the The Joint Commission name");

    organizationRepository.findAll().stream()
        .filter(org -> "The Joint Commission".equals(org.getName()))
        .forEach(
            org -> {
              org.setName("Joint Commission");
              organizationRepository.save(org);
            });

    log.info("Completed updateMeasureOrganizationRecordNameChangeUnit()");
  }

  @RollbackExecution
  public void rollbackExecution() {
    log.debug("Entering rollbackExecution()");
  }
}
