package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.LockResponse;
import cms.gov.madie.measure.dto.MeasureLock;
import cms.gov.madie.measure.repositories.MeasureLockRepository;
import cms.gov.madie.measure.resources.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeasureLockService {

  private final MeasureLockRepository measureLockRepository;

  public synchronized LockResponse lockMeasure(String measureId, String userName) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(Duration.ofMinutes(15)); // 15 minute lock

    MeasureLock lock = new MeasureLock();
    lock.setMeasureId(measureId);
    lock.setLockedBy(userName);
    lock.setLockedAt(now);
    lock.setExpiresAt(expiresAt);

    try {
      measureLockRepository.insert(lock);
      return new LockResponse(true, userName); // not locked by someone else
    } catch (DuplicateKeyException ex) {
      Optional<MeasureLock> existingLock = measureLockRepository.findByMeasureId(measureId);
      if (existingLock.isPresent()) {
        String lockedBy = existingLock.get().getLockedBy();
        boolean locked = !lockedBy.equals(userName);
        return new LockResponse(locked, lockedBy);
      }
      return new LockResponse(true, null); // fallback
    }
  }

  public synchronized LockResponse unlockMeasure(String measureId, String userName) {
    Optional<MeasureLock> existingLock = measureLockRepository.findByMeasureId(measureId);
    // it's our lock. We delete it.
    if (existingLock.isPresent() && existingLock.get().getLockedBy().equals(userName)) {
      measureLockRepository.deleteByMeasureId(measureId);
      return new LockResponse(false, null); // Successfully unlocked
    }
    // it's not our lock. we dont do anything
    return new LockResponse(true, existingLock.map(MeasureLock::getLockedBy).orElse(null));
  }
}
