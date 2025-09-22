package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.locks.MeasureLock;
import cms.gov.madie.measure.repositories.MeasureLockRepository;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MeasureLockServiceTest {

  private MeasureLockRepository repository;
  private MeasureLockService service;

  private final String measureId = "measure-1";
  private final String userName = "test-user";
  private MeasureLock measureLock;

  @BeforeEach
  void setup() {
    repository = mock(MeasureLockRepository.class);
    service = new MeasureLockService(repository);

    measureLock = MeasureLock.builder().measureId(measureId).lockedBy(userName).build();
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

    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(measureLock));

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

    //    MeasureLock existing = new MeasureLock();
    //    existing.setMeasureId(measureId);
    //    existing.setLockedBy("other-user");
    //    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(existing));
    measureLock.setLockedBy("other-user");
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(measureLock));

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
    //    MeasureLock lock = new MeasureLock();
    //    lock.setMeasureId(measureId);
    //    lock.setLockedBy(userName);
    //    lock.setLockedAt(Instant.now());
    //    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(lock));
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(measureLock));

    LockInfo response = service.unlockMeasure(measureId, userName);

    verify(repository).deleteByMeasureId(measureId);
    assertThat(response.isLocked()).isFalse();
    assertThat(response.getLockedBy()).isNull();
  }

  @Test
  void testUnlockMeasureWhenDifferentUserOwnsLock() {
    //    MeasureLock lock = new MeasureLock();
    //    lock.setMeasureId(measureId);
    //    lock.setLockedBy("other-user");
    //    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(lock));
    measureLock.setLockedBy("other-user");
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(measureLock));

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
    //    MeasureLock measureLock =
    //        MeasureLock.builder()
    //            .id("measureLockId")
    //            .measureId("measureId")
    //            .lockedBy("test.user")
    //            .build();
    when(repository.findAllByLockedBy(anyString())).thenReturn(List.of(measureLock));

    List<String> results = service.unlockByUser("test.user");

    String msg1 = "Delete measure locks for harpId: test.user";
    String msg2 = "Deleted measure lock: measure-1";
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
  void testGetMeasureLockInfoLockExists() {
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(measureLock));

    LockInfo response = service.getMeasureLock(measureId);

    assertThat(response.isLocked()).isTrue();
    assertThat(response.getLockedBy()).isNotNull();
    assertEquals(userName, response.getLockedBy());
  }

  @Test
  void testGetMeasureLockInfoLockDoesNotExist() {
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.empty());

    LockInfo response = service.getMeasureLock(measureId);

    assertThat(response.isLocked()).isFalse();
    assertThat(response.getLockedBy()).isNull();
    assertThat(response.getLockedId()).isNull();
  }
}
