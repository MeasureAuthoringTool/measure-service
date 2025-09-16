package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.OrganizationRepository;
import gov.cms.madie.models.common.Organization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UpdateMeasureOrganizationRecordNameChangeUnitTest {

  @Mock private OrganizationRepository organizationRepository;

  public List<Organization> buildOrganizations() {
    return List.of(
        Organization.builder().id("OrgId1").name("The Joint Commission").build(),
        Organization.builder().id("OrgId2").name("Another Organization").build(),
        Organization.builder().id("OrgId3").name("The Example Commission").build());
  }

  @Test
  void updateMeasureOrganizationRecordNameUpdatesMatchingOrganizations() throws Exception {
    when(organizationRepository.findAll()).thenReturn(buildOrganizations());

    new UpdateMeasureOrganizationRecordNameChangeUnit()
        .updateMeasureOrganizationRecordName(organizationRepository);

    verify(organizationRepository, times(1)).save(any(Organization.class));
    verify(organizationRepository, times(1))
        .save(
            argThat(
                org -> "Joint Commission".equals(org.getName()) && "OrgId1".equals(org.getId())));
  }

  @Test
  void updateMeasureOrganizationRecordNameDoesNothingIfNoMatchingOrganizations() throws Exception {
    when(organizationRepository.findAll())
        .thenReturn(
            List.of(
                Organization.builder().id("OrgId1").name("Another Organization").build(),
                Organization.builder().id("OrgId2").name("Different Organization").build()));

    new UpdateMeasureOrganizationRecordNameChangeUnit()
        .updateMeasureOrganizationRecordName(organizationRepository);

    verify(organizationRepository, never()).save(any());
  }

  @Test
  void updateMeasureOrganizationRecordNameHandlesEmptyRepository() throws Exception {
    when(organizationRepository.findAll()).thenReturn(List.of());

    new UpdateMeasureOrganizationRecordNameChangeUnit()
        .updateMeasureOrganizationRecordName(organizationRepository);

    verify(organizationRepository, never()).save(any());
  }
}
