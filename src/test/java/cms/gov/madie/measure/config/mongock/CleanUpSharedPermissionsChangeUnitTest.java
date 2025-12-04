package cms.gov.madie.measure.config.mongock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.repositories.MeasureSetActionLogRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.services.ActionLogService;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.MeasureSetActionLog;
import gov.cms.madie.models.measure.MeasureSet;

@ExtendWith(MockitoExtension.class)
public class CleanUpSharedPermissionsChangeUnitTest {
  @Mock private MeasureSetRepository measureSetRepository;
  @Mock private MeasureSetActionLogRepository measureSetActionLogRepository;
  @Mock private ActionLogService actionLogService;
  @InjectMocks private CleanUpSharedPermissionsChangeUnit changeUnit;

  private MeasureSet measureSet1;
  private MeasureSet measureSet2;
  private MeasureSet measureSet3;

  private RoleEnum role = RoleEnum.SHARED_WITH;
  private final String USER1 = "testCreatedBy1";
  private final String USER2 = "testCreatedBy2";
  private AclSpecification aclSpecification1 =
      AclSpecification.builder().userId(USER1).roles(new HashSet<>(Set.of(role))).build();
  private AclSpecification aclSpecification2 =
      AclSpecification.builder().userId(USER2).roles(new HashSet<>(Set.of(role))).build();
  private AclSpecification aclSpecification3 =
      AclSpecification.builder().userId(USER1).roles(new HashSet<>(Set.of(role))).build();
  private AclSpecification aclSpecification4 =
      AclSpecification.builder().userId(USER1).roles(new HashSet<>(Set.of(role))).build();
  private AclSpecification aclSpecification5 =
      AclSpecification.builder().userId("differentUser").roles(new HashSet<>(Set.of(role))).build();

  @BeforeEach
  public void setUp() {

    measureSet1 =
        MeasureSet.builder()
            .measureSetId("testMeasureSetId1")
            .owner(USER1)
            .acls(new ArrayList<>(List.of(aclSpecification1, aclSpecification2)))
            .build();
    measureSet2 =
        MeasureSet.builder()
            .measureSetId("testMeasureSetId2")
            .owner(USER1)
            .acls(new ArrayList<>(List.of(aclSpecification3, aclSpecification4)))
            .build();
    measureSet3 =
        MeasureSet.builder()
            .measureSetId("testMeasureSetId3")
            .owner(USER1)
            .acls(new ArrayList<>(List.of(aclSpecification5)))
            .build();
  }

  @Test
  public void testCleanUpSharedPermissionsForOwners() {
    when(measureSetRepository.findAll()).thenReturn(List.of(measureSet1, measureSet2, measureSet3));
    when(actionLogService.logShareAccessControlAction(
            anyString(), any(), any(ActionType.class), anyString(), anyString(), anyString()))
        .thenReturn(true);

    List<MeasureSet> updatedMeasureSets =
        changeUnit.cleanUpSharedPermissionsForOwners(
            measureSetRepository, measureSetActionLogRepository);

    verify(measureSetRepository, new Times(1)).findAll();
    assertEquals(2, updatedMeasureSets.size());
    verify(actionLogService, times(2))
        .logShareAccessControlAction(
            anyString(), any(), any(ActionType.class), anyString(), anyString(), anyString());
    verify(measureSetActionLogRepository, times(0)).save(any(MeasureSetActionLog.class));
    verify(measureSetRepository, times(2)).save(any(MeasureSet.class));
  }

  @Test
  public void testCleanUpSharedPermissionsForOwnersNoAcls() {
    measureSet3.setAcls(Collections.emptyList());
    when(measureSetRepository.findAll()).thenReturn(List.of(measureSet3));

    List<MeasureSet> updatedMeasureSets =
        changeUnit.cleanUpSharedPermissionsForOwners(
            measureSetRepository, measureSetActionLogRepository);

    verify(measureSetRepository, new Times(1)).findAll();
    assertEquals(0, updatedMeasureSets.size());
    verify(actionLogService, times(0))
        .logShareAccessControlAction(
            anyString(), any(), any(ActionType.class), anyString(), anyString(), anyString());
    verify(measureSetRepository, times(0)).save(any(MeasureSet.class));
  }

  @Test
  public void testRollbackExecution() {
    when(measureSetRepository.findAll()).thenReturn(List.of(measureSet1, measureSet2, measureSet3));
    changeUnit.cleanUpSharedPermissionsForOwners(
        measureSetRepository, measureSetActionLogRepository);

    changeUnit.rollbackExecution(measureSetRepository);

    verify(measureSetRepository, times(1)).deleteAll(any(List.class));
    verify(measureSetRepository, times(1)).saveAll(any(List.class));
  }
}
