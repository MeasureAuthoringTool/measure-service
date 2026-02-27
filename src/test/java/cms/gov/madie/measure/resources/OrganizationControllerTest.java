package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.repositories.OrganizationRepository;
import gov.cms.madie.models.common.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

  @Mock private OrganizationRepository organizationRepository;

  @InjectMocks private OrganizationController organizationController;

  MockHttpServletRequest request;

  @BeforeEach
  public void setUp() {
    request = new MockHttpServletRequest();
  }

  @Test
  void getAllOrganizations() {
    List<Organization> organizationList = new ArrayList<>();
    organizationList.add(Organization.builder().name("org1").oid("1.2.3.4").build());
    organizationList.add(Organization.builder().name("org2").oid("1.2.3.5").build());
    organizationList.add(Organization.builder().name("org3").oid("1.2.3.6").build());

    when(organizationRepository.findAll()).thenReturn(organizationList);
    var result = organizationController.getAllOrganizations();
    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals(3, result.getBody().size());
  }

  @Test
  void noOrganizationsAvailableInDb() {
    when(organizationRepository.findAll()).thenReturn(new ArrayList<>());
    assertThrows(RuntimeException.class, () -> organizationController.getAllOrganizations());
  }
}
