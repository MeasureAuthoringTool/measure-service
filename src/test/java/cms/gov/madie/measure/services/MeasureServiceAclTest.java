package cms.gov.madie.measure.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.exceptions.InvalidIdException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.dto.UserDetailsDto;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MeasureServiceAclTest {

  @Mock private MeasureRepository measureRepository;
  @Mock private MeasureSetService measureSetService;
  @Mock private ActionLogService actionLogService;
  @Mock private AppConfigService appConfigService;
  @Mock private MeasureLockService measureLockService;
  @Mock private UserServiceClient userServiceClient;

  @InjectMocks private MeasureService measureService;

  private static final String ACCESS_TOKEN = "test-token";

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
    when(userServiceClient.getUserDetails(anyString(), anyString()))
        .thenReturn(UserDetailsDto.builder().active(true).build());
    when(measureSetService.updateMeasureSetAcls(any(), any(), eq("userName"), eq(false)))
        .thenReturn(measureSet);

    List<AclSpecification> aclSpecifications =
        measureService.updateAccessControlList(
            measure.getId(), aclOperation, "userName", false, ACCESS_TOKEN);
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
            () ->
                measureService.updateAccessControlList(
                    "123", aclOperation, "userName", false, ACCESS_TOKEN));

    assertThat(ex.getMessage(), is(equalTo("Measure does not exist: 123")));
  }

  @Test
  public void testUpdateAccessControlListWithRevokeSkipsValidation() {
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
            .action(AclOperation.AclAction.REVOKE)
            .build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);
    when(measureLockService.findByMeasureId(anyString())).thenReturn(null);
    when(measureSetService.updateMeasureSetAcls(any(), any(), eq("userName"), eq(false)))
        .thenReturn(measureSet);

    measureService.updateAccessControlList(
        measure.getId(), aclOperation, "userName", false, ACCESS_TOKEN);

    verify(userServiceClient, never()).getUserDetails(anyString(), anyString());
  }

  @Test
  public void testUpdateAccessControlListWithGrantThrowsWhenUserIsInvalid() {
    Measure measure = Measure.builder().id("123").measureSetId("1-2-3").build();
    AclSpecification aclSpecification = new AclSpecification();
    aclSpecification.setUserId("invalidUser");
    aclSpecification.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder()
            .acls(List.of(aclSpecification))
            .action(AclOperation.AclAction.GRANT)
            .build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureSetService.findByMeasureSetId(anyString()))
        .thenReturn(MeasureSet.builder().build());
    when(measureLockService.findByMeasureId(anyString())).thenReturn(null);
    when(userServiceClient.getUserDetails(anyString(), anyString())).thenReturn(null);

    Exception ex =
        assertThrows(
            InvalidIdException.class,
            () ->
                measureService.updateAccessControlList(
                    measure.getId(), aclOperation, "userName", false, ACCESS_TOKEN));

    assertEquals(
        "The provided HARP ID (invalidUser) is not associated with an active MADiE user.",
        ex.getMessage());
    verify(userServiceClient, times(1)).getUserDetails(eq("invalidUser"), eq(ACCESS_TOKEN));
  }

  @Test
  public void testValidateHarpIdsWhenUserRolesDtoNull() {
    when(userServiceClient.getUserDetails(anyString(), anyString())).thenReturn(null);

    Exception exception =
        assertThrows(
            InvalidIdException.class, () -> measureService.validateHarpId("user1", ACCESS_TOKEN));

    assertEquals(
        "The provided HARP ID (user1) is not associated with an active MADiE user.",
        exception.getMessage());
    verify(userServiceClient, times(1)).getUserDetails(anyString(), anyString());
  }

  @Test
  public void testValidateHarpIdsWhenUserDetailsDtoIsNotActive() {
    UserDetailsDto userDetailsDto = UserDetailsDto.builder().harpId("user1").active(false).build();
    when(userServiceClient.getUserDetails(anyString(), anyString())).thenReturn(userDetailsDto);

    Exception exception =
        assertThrows(
            InvalidIdException.class, () -> measureService.validateHarpId("user1", ACCESS_TOKEN));

    assertEquals(
        "The provided HARP ID (user1) is not associated with an active MADiE user.",
        exception.getMessage());
    verify(userServiceClient, times(1)).getUserDetails(anyString(), anyString());
  }
}
