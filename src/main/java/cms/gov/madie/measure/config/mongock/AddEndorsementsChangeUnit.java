package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.EndorsementRepository;
import tools.jackson.databind.ObjectMapper;
import gov.cms.madie.models.common.EndorserOrganization;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@ChangeUnit(id = "add_updated_endorsements_initializer_2", order = "1", author = "madie_dev")
public class AddEndorsementsChangeUnit {

  private final ObjectMapper objectMapper;

  public AddEndorsementsChangeUnit(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Execution
  public void addEndorsements(EndorsementRepository endorsementRepository) throws IOException {
    InputStream inputStream = getClass().getResourceAsStream("/data/endorsements.json");
    List<EndorserOrganization> endorsementList =
        Arrays.asList(objectMapper.readValue(inputStream, EndorserOrganization[].class));
    // deleting the previous existing records
    endorsementRepository.deleteAll();
    endorsementRepository.insert(endorsementList);
  }

  @RollbackExecution
  public void rollbackExecution(EndorsementRepository endorsementRepository) {
    endorsementRepository.deleteAll();
  }
}
