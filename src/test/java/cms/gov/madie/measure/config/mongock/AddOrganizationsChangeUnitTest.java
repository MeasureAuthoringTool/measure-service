package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.OrganizationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.common.Organization;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.verification.Times;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AddOrganizationsChangeUnitTest {

  @Test
  @SuppressWarnings("unchecked")
  void addOrganizations() throws IOException {
    OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    new AddOrganizationsChangeUnit(new ObjectMapper()).addOrganizations(organizationRepository);

    ArgumentCaptor<List<Organization>> organizationList = ArgumentCaptor.forClass(List.class);
    verify(organizationRepository, new Times(1)).insert(organizationList.capture());
    assertEquals(264, organizationList.getValue().size());
  }

  @Test
  void rollbackExecution() {
    OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    new AddOrganizationsChangeUnit(new ObjectMapper()).rollbackExecution(organizationRepository);
    verify(organizationRepository, new Times(1)).deleteAll();
  }
}
