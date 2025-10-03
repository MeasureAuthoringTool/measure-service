package cms.gov.madie.measure.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.services.TestCaseLockService;

@ExtendWith(MockitoExtension.class)
public class TestCaseLockControllerTest {

  @InjectMocks private TestCaseLockController controller;
  @Mock private TestCaseLockService testCaseLockService;

  private LockInfo lockInfo =
      LockInfo.builder().isLocked(false).lockedBy("test.user").lockedId("testCaseId").build();

  @Test
  public void testAddTestCaseLock() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    when(testCaseLockService.lockTestCase(anyString(), anyString(), anyString()))
        .thenReturn(lockInfo);

    ResponseEntity<LockInfo> response =
        controller.addTestCaseLock("measureId", "testCaseId", principal);
    assertNotNull(response);
    assertEquals(response.getBody().getLockedBy(), "test.user");
    assertFalse(response.getBody().isLocked());
    assertEquals(response.getBody().getLockedId(), "testCaseId");
  }

  @Test
  public void testUnlockTestCase() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    when(testCaseLockService.unlockTestCase(anyString(), anyString())).thenReturn(lockInfo);

    ResponseEntity<LockInfo> response = controller.unlockTestCase("testCaseId", principal);
    assertNotNull(response);
    assertEquals(response.getBody().getLockedBy(), "test.user");
    assertFalse(response.getBody().isLocked());
    assertEquals(response.getBody().getLockedId(), "testCaseId");
  }

  @Test
  public void isMeasureLockedByOtherUserReturnsTrueWhenLockedByOtherUser() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    when(testCaseLockService.checkTestCaseLocksInMeasure(anyString(), anyString()))
        .thenReturn(true);

    ResponseEntity<Boolean> response =
        controller.isTestCaseLockedByOtherUser("measureId", principal);
    assertNotNull(response);
    assertEquals(Boolean.TRUE, response.getBody());
  }

  @Test
  public void isMeasureLockedByOtherUserReturnsFalseWhenNotLockedByOtherUser() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    when(testCaseLockService.checkTestCaseLocksInMeasure(anyString(), anyString()))
        .thenReturn(false);

    ResponseEntity<Boolean> response =
        controller.isTestCaseLockedByOtherUser("measureId", principal);
    assertNotNull(response);
    assertEquals(Boolean.FALSE, response.getBody());
  }
}
