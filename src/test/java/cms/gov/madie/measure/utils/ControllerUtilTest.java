package cms.gov.madie.measure.utils;

import cms.gov.madie.measure.exceptions.UnauthorizedException;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.Stratification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ControllerUtilTest {

  private Measure measure;

  @BeforeEach
  public void setUp() {
    measure =
        Measure.builder()
            .active(true)
            .id("xyz-p13r-13ert")
            .cql("test cql")
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version("0.001")
            .createdAt(Instant.now())
            .createdBy("test user")
            .lastModifiedAt(Instant.now())
            .lastModifiedBy("test user")
            .build();
  }

  @Test
  public void testVerifyAuthorizationThrowsExceptionForDifferentUsers() {
    assertThrows(
        UnauthorizedException.class, () -> ControllerUtil.verifyAuthorization("user1", measure));
  }

  @Test
  public void testVerifyAuthorizationPassesForSharedUser() throws Exception {
    AclSpecification acl = new AclSpecification();
    acl.setUserId("userTest");
    acl.setRoles(List.of(RoleEnum.SHARED_WITH));
    measure.setAcls(List.of(acl));
    assertDoesNotThrow(() -> ControllerUtil.verifyAuthorization("userTest", measure));
  }
}
