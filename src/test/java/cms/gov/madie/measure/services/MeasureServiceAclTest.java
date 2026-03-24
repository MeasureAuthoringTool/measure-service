package cms.gov.madie.measure.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MeasureServiceAclTest {

  @Mock private MeasureRepository measureRepository;
  @Mock private MeasureSetService measureSetService;
  @Mock private ActionLogService actionLogService;
  @Mock private AppConfigService appConfigService;
  @Mock private MeasureLockService measureLockService;

  @InjectMocks private MeasureService measureService;

  @Test
  public void testUpdateAccessControlList() {
    Measure measure = Measure.builder().id("123").measureSetId("1-2-3").build();
    AclSpecification aclSpecification = new AclSpecification();
    aclSpecification.setUserId("test");
    aclSpecification.setRoles(Set.of(RoleEnum.SHARED_WITH));
    MeasureSet measureSet =
        MeasureSet.builder()
            .measureSetId(measure.getMeasureSetId())
            .acls(List.of(aclSpecification))
            .build();
    AclOperation aclOperation =
        AclOperation.builder()
            .acls(List.of(aclSpecification))
            .action(AclOperation.AclAction.GRANT)
            .build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);
    when(measureLockService.findByMeasureId(anyString())).thenReturn(null);
    when(measureSetService.updateMeasureSetAcls(any(), any(), eq("userName"), eq(false)))
        .thenReturn(measureSet);

    List<AclSpecification> aclSpecifications =
        measureService.updateAccessControlList(measure.getId(), aclOperation, "userName", false);
    assertThat(aclSpecifications.size(), is(equalTo(1)));
    assertThat(aclSpecifications.get(0).getUserId(), is(aclSpecification.getUserId()));
    assertThat(aclSpecifications.get(0).getRoles(), is(aclSpecification.getRoles()));
  }

  @Test
  public void testUpdateAccessControlListNoMeasure() {
    AclOperation aclOperation = AclOperation.builder().build();
    when(measureRepository.findById(eq("123"))).thenReturn(Optional.empty());
    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> measureService.updateAccessControlList("123", aclOperation, "userName", false));

    assertThat(ex.getMessage(), is(equalTo("Measure does not exist: 123")));
  }
}
