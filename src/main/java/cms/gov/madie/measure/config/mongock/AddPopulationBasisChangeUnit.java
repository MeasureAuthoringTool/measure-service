package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.PopulationBasisRepository;
import tools.jackson.databind.ObjectMapper;
import gov.cms.madie.models.common.PopulationBasis;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@ChangeUnit(id = "add_population_basis_values_initializer", order = "1", author = "madie_dev")
public class AddPopulationBasisChangeUnit {
  private final ObjectMapper objectMapper;

  public AddPopulationBasisChangeUnit(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Execution
  public void addPopulationBasisValues(PopulationBasisRepository populationBasisRepository)
      throws IOException {
    InputStream inputStream = getClass().getResourceAsStream("/data/population-basis.json");
    List<PopulationBasis> populationBasisList =
        Arrays.asList(objectMapper.readValue(inputStream, PopulationBasis[].class));
    populationBasisRepository.insert(populationBasisList);
  }

  @RollbackExecution
  public void rollbackExecution(PopulationBasisRepository populationBasisRepository) {
    populationBasisRepository.deleteAll();
  }
}
