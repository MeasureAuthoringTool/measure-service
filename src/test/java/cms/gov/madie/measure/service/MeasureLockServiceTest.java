package cms.gov.madie.measure.service;

import cms.gov.madie.measure.dto.LockResponse;
import cms.gov.madie.measure.dto.MeasureLock;
import cms.gov.madie.measure.repositories.MeasureLockRepository;
import cms.gov.madie.measure.resources.DuplicateKeyException;
import cms.gov.madie.measure.services.MeasureLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MeasureLockServiceTest {

  private MeasureLockRepository repository;
  private MeasureLockService service;

  private final String measureId = "measure-1";
  private final String userName = "test-user";

  @BeforeEach
  void setup() {
    repository = mock(MeasureLockRepository.class);
    service = new MeasureLockService(repository);
  }

  @Test
  void testLockMeasure_InsertsSuccessfully() {
    // insert succeeds
    LockResponse response = service.lockMeasure(measureId, userName);

    verify(repository, times(1)).insert(any(MeasureLock.class));
    assertThat(response.isLocked()).isFalse();
    assertThat(response.getLockedBy()).isEqualTo(userName);
  }

  @Test
  void testLockMeasure_WhenDuplicateKey_AndSameUserAlreadyLocked() {
    // insert throws DuplicateKey
    doThrow(new DuplicateKeyException("duplicate", "someKey"))
        .when(repository)
        .insert(any(MeasureLock.class));

    MeasureLock existing = new MeasureLock();
    existing.setMeasureId(measureId);
    existing.setLockedBy(userName);
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(existing));

    LockResponse response = service.lockMeasure(measureId, userName);

    verify(repository).insert((MeasureLock) any());
    verify(repository).findByMeasureId(measureId);
    // it's the same user
    assertThat(response.isLocked()).isFalse();
    assertThat(response.getLockedBy()).isEqualTo(userName);
  }

  @Test
  void testLockMeasure_WhenDuplicateKey_AndLockedByDifferentUser() {
    doThrow(new DuplicateKeyException("duplicate", "someKey"))
        .when(repository)
        .insert(any(MeasureLock.class));

    MeasureLock existing = new MeasureLock();
    existing.setMeasureId(measureId);
    existing.setLockedBy("other-user");
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(existing));

    LockResponse response = service.lockMeasure(measureId, userName);

    assertThat(response.isLocked()).isTrue();
    assertThat(response.getLockedBy()).isEqualTo("other-user");
  }

  @Test
  void testLockMeasure_WhenDuplicateKey_AndNoExistingLockFound() {
    doThrow(new DuplicateKeyException("duplicate", "someKey"))
        .when(repository)
        .insert(any(MeasureLock.class));

    when(repository.findByMeasureId(measureId)).thenReturn(Optional.empty());

    LockResponse response = service.lockMeasure(measureId, userName);

    assertThat(response.isLocked()).isTrue();
    assertThat(response.getLockedBy()).isNull();
  }

  @Test
  void testUnlockMeasure_WhenUserOwnsLock() {
    MeasureLock lock = new MeasureLock();
    lock.setMeasureId(measureId);
    lock.setLockedBy(userName);
    lock.setLockedAt(Instant.now());
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(lock));

    LockResponse response = service.unlockMeasure(measureId, userName);

    verify(repository).deleteByMeasureId(measureId);
    assertThat(response.isLocked()).isFalse();
    assertThat(response.getLockedBy()).isNull();
  }

  @Test
  void testUnlockMeasure_WhenDifferentUserOwnsLock() {
    MeasureLock lock = new MeasureLock();
    lock.setMeasureId(measureId);
    lock.setLockedBy("other-user");
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(lock));

    LockResponse response = service.unlockMeasure(measureId, userName);

    verify(repository, never()).deleteByMeasureId(any());
    assertThat(response.isLocked()).isTrue();
    assertThat(response.getLockedBy()).isEqualTo("other-user");
  }

  @Test
  void testUnlockMeasure_WhenNoLockExists() {
    when(repository.findByMeasureId(measureId)).thenReturn(Optional.empty());

    LockResponse response = service.unlockMeasure(measureId, userName);

    assertThat(response.isLocked()).isTrue();
    assertThat(response.getLockedBy()).isNull();
  }
}
