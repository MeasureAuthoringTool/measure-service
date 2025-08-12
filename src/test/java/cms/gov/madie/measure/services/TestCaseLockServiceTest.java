package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.dto.TestCaseLock;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.TestCaseLockRepository;
import cms.gov.madie.measure.resources.DuplicateKeyException;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;

@ExtendWith(MockitoExtension.class)
public class TestCaseLockServiceTest {

  @InjectMocks private TestCaseLockService service;
  @Mock private MeasureRepository MeasureRepository;
  @Mock private TestCaseLockRepository testCaseLockRepository;

  private Measure measure;
  private TestCase testCase;
  private TestCaseLock lock;
  private Instant instant = Instant.parse("2025-08-08T10:43:00Z");

  @BeforeEach
  public void setUp() {
    measure = Measure.builder().id("measureId").build();
    testCase = TestCase.builder().id("testCaseId").build();
    lock =
        TestCaseLock.builder()
            .measureId("measureId")
            .testCaseId("testCaseId")
            .lockedAt(instant)
            .lockedBy("test.user")
            .build();
  }

  @Test
  public void testLockTestCaseMeasureNotFound() {
    when(MeasureRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> service.lockTestCase("measureId", "testCaseId", "test.user"));
  }

  @Test
  public void testLockTestCaseMeasureNoTestCases() {
    when(MeasureRepository.findById(anyString())).thenReturn(Optional.of(measure));

    assertThrows(
        ResourceNotFoundException.class,
        () -> service.lockTestCase("measureId", "testCaseId", "test.user"));
  }

  @Test
  public void testLockTestCaseMeasureTestCaseNotFound() {
    measure.setTestCases(List.of(testCase));
    when(MeasureRepository.findById(anyString())).thenReturn(Optional.of(measure));

    assertThrows(
        ResourceNotFoundException.class,
        () -> service.lockTestCase("measureId", "testCaseId2", "test.user"));
  }

  @Test
  public void testLockTestCaseSuccess() {
    measure.setTestCases(List.of(testCase));
    when(MeasureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(testCaseLockRepository.insert(any(TestCaseLock.class))).thenReturn(lock);

    LockInfo lockInfo = service.lockTestCase("measureId", "testCaseId", "test.user");

    assertEquals(lockInfo.getLockedId(), "testCaseId");
    assertEquals(lockInfo.getLockedBy(), "test.user");
    assertFalse(lockInfo.isLocked());
  }

  @Test
  public void testLockTestCaseThrowsDuplicateKeyExceptionForSameUser() {
    measure.setTestCases(List.of(testCase));
    when(MeasureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(testCaseLockRepository.insert(any(TestCaseLock.class)))
        .thenThrow(DuplicateKeyException.class);
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.of(lock));

    LockInfo lockInfo = service.lockTestCase("measureId", "testCaseId", "test.user");

    assertNull(lockInfo.getLockedId());
    assertNull(lockInfo.getLockedBy());
    assertTrue(lockInfo.isLocked());
  }

  @Test
  public void testLockTestCaseThrowsDuplicateKeyExceptionForDifferentUser() {
    measure.setTestCases(List.of(testCase));
    when(MeasureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(testCaseLockRepository.insert(any(TestCaseLock.class)))
        .thenThrow(DuplicateKeyException.class);
    lock.setLockedBy("test.user2");
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.of(lock));

    LockInfo lockInfo = service.lockTestCase("measureId", "testCaseId", "test.user");

    assertNull(lockInfo.getLockedId());
    assertNull(lockInfo.getLockedBy());
    assertTrue(lockInfo.isLocked());
  }

  @Test
  public void testLockTestCaseThrowsDuplicateKeyExceptionNoExistingLock() {
    measure.setTestCases(List.of(testCase));
    when(MeasureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(testCaseLockRepository.insert(any(TestCaseLock.class)))
        .thenThrow(DuplicateKeyException.class);
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.empty());

    LockInfo lockInfo = service.lockTestCase("measureId", "testCaseId", "test.user");

    assertNull(lockInfo.getLockedId());
    assertNull(lockInfo.getLockedBy());
    assertTrue(lockInfo.isLocked());
  }

  @Test
  public void testUnlockTestCaseNotFound() {
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.empty());

    LockInfo lockInfo = service.unlockTestCase("testCaseId", "test.user");

    assertNull(lockInfo);
  }

  @Test
  public void testUnlockTestCaseLockFoundForSameUser() {
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.of(lock));

    LockInfo lockInfo = service.unlockTestCase("testCaseId", "test.user");

    assertNotNull(lockInfo);
    assertFalse(lockInfo.isLocked());
    assertNull(lockInfo.getLockedBy());
    assertNull(lockInfo.getLockedId());
  }

  @Test
  public void testUnlockTestCaseLockFoundForDifferentUser() {
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.of(lock));

    LockInfo lockInfo = service.unlockTestCase("testCaseId", "test.user2");

    assertNotNull(lockInfo);
    assertTrue(lockInfo.isLocked());
    assertEquals(lockInfo.getLockedId(), "testCaseId");
    assertEquals(lockInfo.getLockedBy(), "test.user");
  }
}
