package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.OrganizationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.common.Organization;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@ChangeUnit(
    id = "add_updated_organizations_initializer_05222024",
    order = "1",
    author = "madie_dev")
public class AddOrganizationsChangeUnit {

  private final ObjectMapper objectMapper;

  public AddOrganizationsChangeUnit(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Execution
  public void addOrganizations(OrganizationRepository organizationRepository) throws IOException {
    InputStream inputStream = getClass().getResourceAsStream("/data/organizations.json");
    List<Organization> organizationList =
        Arrays.asList(objectMapper.readValue(inputStream, Organization[].class));
    // deleting the previous existing records
    organizationRepository.deleteAll();
    organizationRepository.insert(organizationList);
  }

  @RollbackExecution
  public void rollbackExecution(OrganizationRepository organizationRepository) {
    organizationRepository.deleteAll();
  }
}
