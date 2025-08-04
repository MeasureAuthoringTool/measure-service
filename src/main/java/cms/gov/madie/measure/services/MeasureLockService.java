package cms.gov.madie.measure.services;

import cms.gov.madie.measure.repositories.MeasureLockRepository;
import gov.cms.madie.models.common.MeasureLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeasureLockService {

  private final MeasureLockRepository measureLockRepository;

  public MeasureLock lockMeasure(String measureId, String harpId, String accessToken) {
    Optional<MeasureLock> existingLock = measureLockRepository.findByMeasureId(measureId);

    // no lock at all, make the original lock document
    if (existingLock.isEmpty()) {
      return measureLockRepository.save(
          MeasureLock.builder()
              .measureId(measureId)
              .harpId(harpId)
              .createdAt(Instant.now())
              .build());
    }

    MeasureLock lock = existingLock.get();

    // there's a cleared lock in here. We take ownership
    if (lock.getHarpId() == null || lock.getHarpId().isEmpty()) {
      lock.setHarpId(harpId);
      lock.setCreatedAt(Instant.now());
      return measureLockRepository.save(lock);
    }

    // somebody else owns lock.
    if (!lock.getHarpId().equals(harpId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Measure is already locked by user: " + lock.getHarpId());
    }

    // I own lock. I update timestamp
    lock.setCreatedAt(Instant.now());
    return measureLockRepository.save(lock);
  }

  public MeasureLock unlockMeasure(String measureId, String harpId, String accessToken) {
    Optional<MeasureLock> existingLock = measureLockRepository.findByMeasureId(measureId);

    // No lock, dont care
    if (existingLock.isEmpty()) {
      return null;
    }

    MeasureLock lock = existingLock.get();

    // Someone else owns the throw?
    if (!harpId.equals(lock.getHarpId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You do not own the lock on this measure.");
    }

    // I own lock, I unlock
    lock.setHarpId(null);
    lock.setCreatedAt(null);

    return measureLockRepository.save(lock);
  }
}
