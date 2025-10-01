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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.locks.TestCaseLock;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.TestCaseLockRepository;
import org.springframework.dao.DuplicateKeyException;

import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;

@ExtendWith(MockitoExtension.class)
public class TestCaseLockServiceTest {

  @InjectMocks private TestCaseLockService service;
  @Mock private MeasureRepository measureRepository;
  @Mock private TestCaseLockRepository testCaseLockRepository;

  private Measure measure;
  private TestCase testCase;
  private TestCase testCase2;
  private TestCaseLock lock;
  private Instant instant = Instant.parse("2025-08-08T10:43:00Z");
  private TestCaseLock lock2;

  @BeforeEach
  public void setUp() {
    measure = Measure.builder().id("measureId").build();
    testCase = TestCase.builder().id("testCaseId").build();
    testCase2 = TestCase.builder().id("testCaseId2").build();
    lock =
        TestCaseLock.builder()
            .measureId("measureId")
            .testCaseId("testCaseId")
            .lockedAt(instant)
            .lockedBy("test.user")
            .build();

    lock2 =
        TestCaseLock.builder()
            .measureId("measureId")
            .testCaseId("testCaseId2")
            .lockedAt(instant)
            .lockedBy("another.user")
            .build();
  }

  @Test
  public void testLockTestCaseMeasureNotFound() {
    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> service.lockTestCase("measureId", "testCaseId", "test.user"));
  }

  @Test
  public void testLockTestCaseMeasureNoTestCases() {
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));

    assertThrows(
        ResourceNotFoundException.class,
        () -> service.lockTestCase("measureId", "testCaseId", "test.user"));
  }

  @Test
  public void testLockTestCaseMeasureTestCaseNotFound() {
    measure.setTestCases(List.of(testCase));
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));

    assertThrows(
        ResourceNotFoundException.class,
        () -> service.lockTestCase("measureId", "testCaseId2", "test.user"));
  }

  @Test
  public void testLockTestCaseSuccess() {
    measure.setTestCases(List.of(testCase));
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(testCaseLockRepository.insert(any(TestCaseLock.class))).thenReturn(lock);

    LockInfo lockInfo = service.lockTestCase("measureId", "testCaseId", "test.user");

    assertEquals(lockInfo.getLockedId(), "testCaseId");
    assertEquals(lockInfo.getLockedBy(), "test.user");
    assertTrue(lockInfo.isLocked());
  }

  @Test
  public void testLockTestCaseThrowsDuplicateKeyExceptionForSameUser() {
    measure.setTestCases(List.of(testCase));
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(testCaseLockRepository.insert(any(TestCaseLock.class)))
        .thenThrow(DuplicateKeyException.class);
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.of(lock));

    LockInfo lockInfo = service.lockTestCase("measureId", "testCaseId", "test.user");

    assertEquals(lockInfo.getLockedId(), "testCaseId");
    assertEquals(lockInfo.getLockedBy(), "test.user");
    assertTrue(lockInfo.isLocked());
  }

  @Test
  public void testLockTestCaseThrowsDuplicateKeyExceptionForDifferentUser() {
    measure.setTestCases(List.of(testCase));
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(testCaseLockRepository.insert(any(TestCaseLock.class)))
        .thenThrow(DuplicateKeyException.class);
    lock.setLockedBy("test.user2");
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.of(lock));

    LockInfo lockInfo = service.lockTestCase("measureId", "testCaseId", "test.user");

    assertEquals(lockInfo.getLockedId(), "testCaseId");
    assertEquals(lockInfo.getLockedBy(), "test.user2");
    assertTrue(lockInfo.isLocked());
  }

  @Test
  public void testLockTestCaseThrowsDuplicateKeyExceptionNoExistingLock() {
    measure.setTestCases(List.of(testCase));
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(testCaseLockRepository.insert(any(TestCaseLock.class)))
        .thenThrow(DuplicateKeyException.class);
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.empty());

    LockInfo lockInfo = service.lockTestCase("measureId", "testCaseId", "test.user");

    assertNull(lockInfo);
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

  @Test
  public void testUnlockByUser() {
    TestCaseLock testCaseLock =
        TestCaseLock.builder().testCaseId("testCaseId").lockedBy("test.user").build();
    when(testCaseLockRepository.findAllByLockedBy(anyString())).thenReturn(List.of(testCaseLock));

    List<String> results = service.unlockByUser("test.user");

    String msg1 = "Delete test case locks for harpId: test.user";
    String msg2 = "Deleted test case lock for Id: testCaseId";
    List<String> expected = List.of(msg1, msg2);
    assertEquals(expected, results);
  }

  @Test
  public void testUnlockByUserLocksNotFound() {
    when(testCaseLockRepository.findAllByLockedBy(anyString())).thenReturn(Collections.emptyList());

    List<String> results = service.unlockByUser("test.user");

    String msg1 = "Delete test case locks for harpId: test.user";
    String msg2 = "No test case locks found for harpId: test.user";
    List<String> expected = List.of(msg1, msg2);
    assertEquals(expected, results);
  }

  @Test
  public void testUnlockTestCasesIdsNull() {
    boolean result = service.unlockAllTestCases(List.of(), "test.user");
    assertFalse(result);
  }

  @Test
  public void testUnlockTestCasesLockNotFound() {
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.empty());
    boolean result = service.unlockAllTestCases(List.of("testCaseId", "testCaseId2"), "test.user");
    assertFalse(result);
  }

  @Test
  public void testUnlockTestCasesLockByDifferentUser() {
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.of(lock));
    boolean result = service.unlockAllTestCases(List.of("testCaseId"), "another.user");
    assertFalse(result);
  }

  @Test
  public void testUnlockTestCasesSuccess() {
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.of(lock));
    boolean result = service.unlockAllTestCases(List.of("testCaseId"), "test.user");
    assertTrue(result);
  }

  @Test
  public void testLockAllTestCasesTestCaseIdsNull() {
    List<LockInfo> locks = service.lockAllTestCases("testMeasureId", null, "test.user");
    assertTrue(CollectionUtils.isEmpty(locks));
  }

  @Test
  public void testLockAllTestCasesPartialSuccess() {
    measure.setTestCases(List.of(testCase, testCase2));
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(testCaseLockRepository.insert(any(TestCaseLock.class)))
        .thenReturn(lock) // first call: lock acquired
        .thenThrow(DuplicateKeyException.class); // second call: failure
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.of(lock2));

    List<LockInfo> failedLocks =
        service.lockAllTestCases("measureId", List.of("testCaseId", "testCaseId2"), "test.user");

    assertTrue(failedLocks.size() == 1);
    assertEquals(lock2.getLockedBy(), failedLocks.get(0).getLockedBy());
  }

  @Test
  public void testLockAllTestCasesAllSuccess() {
    measure.setTestCases(List.of(testCase, testCase2));
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(testCaseLockRepository.insert(any(TestCaseLock.class)))
        .thenReturn(lock) // first call: lock acquired
        .thenReturn(lock2); // second call: lock acquired

    List<LockInfo> failedLocks =
        service.lockAllTestCases("measureId", List.of("testCaseId", "testCaseId2"), "test.user");

    assertTrue(failedLocks.size() == 0);
  }

  @Test
  public void testLockAllTestCasesLockInfoNull() {
    measure.setTestCases(List.of(testCase));
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(testCaseLockRepository.insert(any(TestCaseLock.class)))
        .thenThrow(DuplicateKeyException.class);
    when(testCaseLockRepository.findByTestCaseId(anyString())).thenReturn(Optional.empty());

    List<LockInfo> locks =
        service.lockAllTestCases("testMeasureId", List.of("testCaseId"), "test.user");
    assertTrue(CollectionUtils.isEmpty(locks));
  }
}
