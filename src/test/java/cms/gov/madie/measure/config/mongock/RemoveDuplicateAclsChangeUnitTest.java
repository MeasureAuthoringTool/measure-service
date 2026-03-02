package cms.gov.madie.measure.config.mongock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cms.gov.madie.measure.repositories.MeasureSetRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.measure.MeasureSet;

@ExtendWith(MockitoExtension.class)
public class RemoveDuplicateAclsChangeUnitTest {
  @Mock private MeasureSetRepository measureSetRepository;
  @InjectMocks private RemoveDuplicateAclsChangeUnit changeUnit;

  private AclSpecification acl1 =
      AclSpecification.builder().userId("testUser1").roles(Set.of(RoleEnum.SHARED_WITH)).build();
  private AclSpecification acl2 =
      AclSpecification.builder().userId("testUser2").roles(Set.of(RoleEnum.SHARED_WITH)).build();
  private AclSpecification acl3 =
      AclSpecification.builder().userId("testuser1").roles(Set.of(RoleEnum.SHARED_WITH)).build();
  private MeasureSet measureSet = null;

  @BeforeEach
  public void setUp() {
    measureSet = MeasureSet.builder().acls(List.of(acl1, acl2, acl3)).build();
  }

  @Test
  void testRemoveDuplicateAcls() {
    when(measureSetRepository.findAll()).thenReturn(List.of(measureSet));

    changeUnit.removeDuplicateAcls(measureSetRepository);

    // After execution, measureSet should have only one ACL for testUser1 (case-insensitive)
    List<AclSpecification> updatedAcls = measureSet.getAcls();

    // Expected 2 ACLs after removing duplicates
    assertEquals(2, updatedAcls.size());
    // Expected only one ACL for testUser1
    assertEquals(
        1,
        updatedAcls.stream().filter(acl -> acl.getUserId().equalsIgnoreCase("testUser1")).count());
    // Expected one ACL for testUser2
    assertEquals(
        1,
        updatedAcls.stream().filter(acl -> acl.getUserId().equalsIgnoreCase("testUser2")).count());
  }

  @Test
  void testRemoveDuplicateAclsWhenNoDuplicates() {
    AclSpecification acl4 =
        AclSpecification.builder().userId("testUser3").roles(Set.of(RoleEnum.SHARED_WITH)).build();
    measureSet.setAcls(List.of(acl1, acl2, acl4));

    when(measureSetRepository.findAll()).thenReturn(List.of(measureSet));

    changeUnit.removeDuplicateAcls(measureSetRepository);

    List<AclSpecification> updatedAcls = measureSet.getAcls();
    // Expected 3 ACLs since there are no duplicates
    assertEquals(3, updatedAcls.size());
  }

  @Test
  void testRemoveDuplicateWhenNoMeasureSets() {
    when(measureSetRepository.findAll()).thenReturn(List.of());

    changeUnit.removeDuplicateAcls(measureSetRepository);

    // No measure sets, so no ACLs should be modified
    // Just ensure that the method runs without exceptions
    assertDoesNotThrow(() -> measureSetRepository.findAll());
  }

  @Test
  void testRemoveDuplicateWhenNoAcls() {
    measureSet.setAcls(List.of());
    when(measureSetRepository.findAll()).thenReturn(List.of(measureSet));

    changeUnit.removeDuplicateAcls(measureSetRepository);

    List<AclSpecification> updatedAcls = measureSet.getAcls();
    assertEquals(0, updatedAcls.size());
  }

  @Test
  void testRemoveDuplicateWhenNoRoles() {
    AclSpecification acl4 = AclSpecification.builder().userId("testUser4").build();
    measureSet.setAcls(List.of(acl1, acl2, acl4));

    when(measureSetRepository.findAll()).thenReturn(List.of(measureSet));

    changeUnit.removeDuplicateAcls(measureSetRepository);

    List<AclSpecification> updatedAcls = measureSet.getAcls();
    assertEquals(2, updatedAcls.size());
  }

  @Test
  void testRemoveDuplicateWhenRolesDoNotContainSharedWith() {
    AclSpecification acl4 = AclSpecification.builder().userId("testUser4").roles(Set.of()).build();
    measureSet.setAcls(List.of(acl1, acl2, acl4));

    when(measureSetRepository.findAll()).thenReturn(List.of(measureSet));

    changeUnit.removeDuplicateAcls(measureSetRepository);

    List<AclSpecification> updatedAcls = measureSet.getAcls();
    assertEquals(2, updatedAcls.size());
  }

  @Test
  void testRollbackExecution() {
    ReflectionTestUtils.setField(changeUnit, "copyOfAllMeasureSets", List.of(measureSet));

    // Simulate rollback
    changeUnit.rollbackExecution(measureSetRepository);

    // After rollback, measureSet should have the original ACLs
    List<AclSpecification> rolledBackAcls = measureSet.getAcls();

    assertEquals(3, rolledBackAcls.size());
    assertEquals(
        2,
        rolledBackAcls.stream()
            .filter(acl -> acl.getUserId().equalsIgnoreCase("testUser1"))
            .count());
    assertEquals(
        1,
        rolledBackAcls.stream()
            .filter(acl -> acl.getUserId().equalsIgnoreCase("testUser2"))
            .count());
  }

  @Test
  void testRollbackExecutionWhenNoMeasureSets() {
    when(measureSetRepository.findAll()).thenReturn(List.of());

    changeUnit.rollbackExecution(measureSetRepository);

    // No measure sets, so no ACLs should be modified
    // Just ensure that the method runs without exceptions
    assertDoesNotThrow(() -> measureSetRepository.findAll());
  }
}
