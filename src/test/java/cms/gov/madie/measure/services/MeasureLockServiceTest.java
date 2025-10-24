package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.exceptions.LockNotObtainedException;
import cms.gov.madie.measure.locks.MeasureLock;
import cms.gov.madie.measure.repositories.MeasureLockRepository;
import gov.cms.madie.models.measure.Measure;

import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MeasureLockServiceTest {

  private MeasureLockRepository repository;
  private MeasureLockService service;
  private TestCaseLockService testCaseLockService;

  private final String measureId = "measure-1";
  private final String userName = "test-user";
  private Measure measure = Measure.builder().id("measureId").build();

  @BeforeEach
  void setup() {
    repository = mock(MeasureLockRepository.class);
    testCaseLockService = mock(TestCaseLockService.class);
    service = new MeasureLockService(repository, testCaseLockService);
  }

  @Test
  void testLockMeasureInsertsSuccessfully() {
    // insert succeeds
    LockInfo response = service.lockMeasure(measureId, userName);

    verify(repository, times(1)).insert(any(MeasureLock.class));
    assertThat(response.isLocked()).isTrue();
    assertThat(response.getLockedBy()).isEqualTo(userName);
  }

  @Test
  void testLockMeasureWhenDuplicateKeyAndSameUserAlreadyLocked() {
    // insert throws DuplicateKey
    doThrow(new DuplicateKeyException("duplicate someKey"))
        .when(repository)
        .insert(any(MeasureLock.class));

    MeasureLock existing = new MeasureLock();
    existing.setMeasureId(measureId);
    existing.setLockedBy(userName);
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(existing));

    LockInfo response = service.lockMeasure(measureId, userName);

    verify(repository).insert((MeasureLock) any());
    verify(repository).findByMeasureId(measureId);
    // it's the same user
    assertThat(response.isLocked()).isFalse();
    assertThat(response.getLockedBy()).isEqualTo(userName);
  }

  @Test
  void testLockMeasureWhenDuplicateKeyAndLockedByDifferentUser() {
    doThrow(new DuplicateKeyException("duplicate someKey"))
        .when(repository)
        .insert(any(MeasureLock.class));

    MeasureLock existing = new MeasureLock();
    existing.setMeasureId(measureId);
    existing.setLockedBy("other-user");
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(existing));

    LockInfo response = service.lockMeasure(measureId, userName);

    assertThat(response.isLocked()).isTrue();
    assertThat(response.getLockedBy()).isEqualTo("other-user");
  }

  @Test
  void testLockMeasureWhenDuplicateKeyAndNoExistingLockFound() {
    doThrow(new DuplicateKeyException("duplicate someKey"))
        .when(repository)
        .insert(any(MeasureLock.class));

    when(repository.findByMeasureId(measureId)).thenReturn(Optional.empty());

    LockInfo response = service.lockMeasure(measureId, userName);

    assertThat(response.isLocked()).isTrue();
    assertThat(response.getLockedBy()).isNull();
  }

  @Test
  void testUnlockMeasureWhenUserOwnsLock() {
    MeasureLock lock = new MeasureLock();
    lock.setMeasureId(measureId);
    lock.setLockedBy(userName);
    lock.setLockedAt(Instant.now());
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(lock));

    LockInfo response = service.unlockMeasure(measureId, userName);

    verify(repository).deleteByMeasureId(measureId);
    assertThat(response.isLocked()).isFalse();
    assertThat(response.getLockedBy()).isNull();
  }

  @Test
  void testUnlockMeasureWhenDifferentUserOwnsLock() {
    MeasureLock lock = new MeasureLock();
    lock.setMeasureId(measureId);
    lock.setLockedBy("other-user");
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(lock));

    LockInfo response = service.unlockMeasure(measureId, userName);

    verify(repository, never()).deleteByMeasureId(any());
    assertThat(response.isLocked()).isTrue();
    assertThat(response.getLockedBy()).isEqualTo("other-user");
  }

  @Test
  void testUnlockMeasureWhenNoLockExists() {
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.empty());

    LockInfo response = service.unlockMeasure(measureId, userName);

    assertThat(response.isLocked()).isTrue();
    assertThat(response.getLockedBy()).isNull();
  }

  @Test
  public void testUnlockByUser() {
    MeasureLock measureLock =
        MeasureLock.builder()
            .id("measureLockId")
            .measureId("measureId")
            .lockedBy("test.user")
            .build();
    when(repository.findAllByLockedBy(anyString())).thenReturn(List.of(measureLock));

    List<String> results = service.unlockByUser("test.user");

    String msg1 = "Delete measure locks for harpId: test.user";
    String msg2 = "Deleted measure lock: measureId";
    List<String> expected = new ArrayList<>();
    expected.add(msg1);
    expected.add(msg2);
    assertEquals(expected, results);
  }

  @Test
  public void testUnlockByUserLocksNotFound() {
    when(repository.findAllByLockedBy(anyString())).thenReturn(Collections.emptyList());

    List<String> results = service.unlockByUser("test.user");

    String msg1 = "Delete measure locks for harpId: test.user";
    String msg2 = "No measure locks found for harpId: test.user";
    List<String> expected = new ArrayList<>();
    expected.add(msg1);
    expected.add(msg2);
    assertEquals(expected, results);
  }

  @Test
  public void testCheckMeasureAndTestCaseLockNoLock() {
    MeasureLock lock = MeasureLock.builder().id("measureId").lockedBy(userName).build();
    when(repository.insert(any(MeasureLock.class))).thenReturn(lock);
    when(testCaseLockService.isAnyTestCaseLockedByOthers(anyString(), anyString()))
        .thenReturn(false);
    Measure measure = Measure.builder().id("measureId").build();
    boolean isLocked = service.checkMeasureAndTestCaseLock(userName, measure, "version");
    assertFalse(isLocked);
  }

  @Test
  public void testCheckMeasureAndTestCaseLockHasMeasureLock() {
    // measure locked by another user, throws DuplicateKeyException
    doThrow(new DuplicateKeyException("duplicate")).when(repository).insert(any(MeasureLock.class));
    when(repository.findByMeasureId("measureId"))
        .thenReturn(
            Optional.of(MeasureLock.builder().id("measureId").lockedBy("another.user").build()));

    Exception exception =
        assertThrows(
            LockNotObtainedException.class,
            () -> service.checkMeasureAndTestCaseLock(userName, measure, "version"));

    assertThat(
        exception.getMessage(),
        is(equalTo("Unable to version measure. Locked while being edited by another.user")));
  }

  @Test
  public void testCheckMeasureAndTestCaseLockHasTestCaseLock() {
    when(repository.insert(any(MeasureLock.class)))
        .thenReturn(MeasureLock.builder().id("measureId").lockedBy(userName).build());
    when(testCaseLockService.isAnyTestCaseLockedByOthers(anyString(), anyString()))
        .thenReturn(true);

    Exception exception =
        assertThrows(
            LockNotObtainedException.class,
            () -> service.checkMeasureAndTestCaseLock(userName, measure, "version"));

    assertThat(
        exception.getMessage(),
        is(
            equalTo(
                "Unable to version measure. One or more test cases are locked by another user.")));
  }

  @Test
  public void testCheckMeasureAndTestCaseLockMeasureNotLocked() {
    // Simulate lockMeasure returning isLocked = false
    // In MeasureLockService.lockMeasure, isLocked is set to false only if
    // DuplicateKeyException is thrown and the lock is owned by the same user
    doThrow(new DuplicateKeyException("duplicate")).when(repository).insert(any(MeasureLock.class));
    MeasureLock existing = MeasureLock.builder().measureId("measureId").lockedBy(userName).build();
    when(repository.findByMeasureId("measureId")).thenReturn(Optional.of(existing));

    when(testCaseLockService.isAnyTestCaseLockedByOthers(anyString(), anyString()))
        .thenReturn(false);

    Measure measure = Measure.builder().id("measureId").build();
    boolean isLocked = service.checkMeasureAndTestCaseLock(userName, measure, "version");
    assertFalse(isLocked);
  }

  @Test
  public void testCheckMeasureLockNoLock() {
    MeasureLock lock = MeasureLock.builder().id("measureId").lockedBy(userName).build();
    when(repository.insert(any(MeasureLock.class))).thenReturn(lock);
    Measure measure = Measure.builder().id("measureId").build();
    boolean isLocked = service.checkMeasureLock(userName, measure, "version");
    assertFalse(isLocked);
  }

  @Test
  public void testCheckMeasureLockLockedFalse() {
    // In MeasureLockService.lockMeasure, isLocked is set to false only if
    // DuplicateKeyException is thrown and the lock is owned by the same user
    doThrow(new DuplicateKeyException("duplicate")).when(repository).insert(any(MeasureLock.class));
    MeasureLock existing = MeasureLock.builder().measureId("measureId").lockedBy(userName).build();
    when(repository.findByMeasureId("measureId")).thenReturn(Optional.of(existing));

    Measure measure = Measure.builder().id("measureId").build();
    boolean isLocked = service.checkMeasureLock(userName, measure, "version");
    assertFalse(isLocked);
  }

  @Test
  public void testCheckMeasureLocked() {
    // measure locked by another user, throws DuplicateKeyException
    doThrow(new DuplicateKeyException("duplicate")).when(repository).insert(any(MeasureLock.class));
    when(repository.findByMeasureId("measureId"))
        .thenReturn(
            Optional.of(MeasureLock.builder().id("measureId").lockedBy("another.user").build()));

    Exception exception =
        assertThrows(
            LockNotObtainedException.class,
            () -> service.checkMeasureLock(userName, measure, "associate"));

    assertThat(
        exception.getMessage(),
        is(equalTo("Unable to associate measure. Locked while being edited by another.user")));
  }
}
