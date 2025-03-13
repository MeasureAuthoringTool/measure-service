package cms.gov.madie.measure.config.mongock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cms.gov.madie.measure.repositories.EndorsementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.common.EndorserOrganization;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.verification.Times;

class AddEndorsementsChangeUnitTest {

  @Test
  @SuppressWarnings("unchecked")
  void addEndorsements() throws IOException {
    EndorsementRepository endorsementRepository = mock(EndorsementRepository.class);
    new AddEndorsementsChangeUnit(new ObjectMapper()).addEndorsements(endorsementRepository);

    ArgumentCaptor<List<EndorserOrganization>> endorsementList =
        ArgumentCaptor.forClass(List.class);
    verify(endorsementRepository, new Times(1)).insert(endorsementList.capture());
    assertEquals(2, endorsementList.getValue().size());
  }

  @Test
  void rollbackExecution() {
    EndorsementRepository endorsementRepository = mock(EndorsementRepository.class);
    new AddEndorsementsChangeUnit(new ObjectMapper()).rollbackExecution(endorsementRepository);
    verify(endorsementRepository, new Times(1)).deleteAll();
  }
}
