package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.PopulationBasisRepository;
import tools.jackson.databind.ObjectMapper;
import gov.cms.madie.models.common.PopulationBasis;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.verification.Times;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AddPopulationBasisChangeUnitTest {

  @Test
  @SuppressWarnings("unchecked")
  void addPopulationBasisValues() throws IOException {
    PopulationBasisRepository populationBasisRepository = mock(PopulationBasisRepository.class);
    new AddPopulationBasisChangeUnit(new ObjectMapper())
        .addPopulationBasisValues(populationBasisRepository);

    ArgumentCaptor<List<PopulationBasis>> populationBasisList = ArgumentCaptor.forClass(List.class);
    verify(populationBasisRepository, new Times(1)).insert(populationBasisList.capture());
    assertEquals(108, populationBasisList.getValue().size());
  }

  @Test
  void rollbackExecution() {
    PopulationBasisRepository populationBasisRepository = mock(PopulationBasisRepository.class);
    new AddPopulationBasisChangeUnit(new ObjectMapper())
        .rollbackExecution(populationBasisRepository);
    verify(populationBasisRepository, new Times(1)).deleteAll();
  }
}
