package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.locks.MeasureLock;
import cms.gov.madie.measure.repositories.MeasureLockRepository;
import cms.gov.madie.measure.resources.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeasureLockService {

  private final MeasureLockRepository measureLockRepository;

  public synchronized LockInfo lockMeasure(String measureId, String userName) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(Duration.ofMinutes(15)); // 15 minute lock

    MeasureLock lock = new MeasureLock();
    lock.setMeasureId(measureId);
    lock.setLockedBy(userName);
    lock.setLockedAt(now);
    lock.setExpiresAt(expiresAt);

    try {
      measureLockRepository.insert(lock);
      return new LockInfo(true, userName, measureId); // not locked by someone else
    } catch (DuplicateKeyException ex) {
      Optional<MeasureLock> existingLock = measureLockRepository.findByMeasureId(measureId);
      if (existingLock.isPresent()) {
        String lockedBy = existingLock.get().getLockedBy();
        boolean locked = !lockedBy.equals(userName);
        return new LockInfo(locked, lockedBy, measureId);
      }
      return new LockInfo(true, null, measureId); // fallback
    }
  }

  public synchronized LockInfo unlockMeasure(String measureId, String userName) {
    Optional<MeasureLock> existingLock = measureLockRepository.findByMeasureId(measureId);
    // it's our lock. We delete it.
    if (existingLock.isPresent() && existingLock.get().getLockedBy().equals(userName)) {
      measureLockRepository.deleteByMeasureId(measureId);
      return new LockInfo(false, null, measureId); // Successfully unlocked
    }
    // it's not our lock. we dont do anything
    return new LockInfo(true, existingLock.map(MeasureLock::getLockedBy).orElse(null), measureId);
  }

  public synchronized List<String> unlockByUser(String userName) {
    List<String> deleteMesssages = new ArrayList<>();
    deleteMesssages.add("Delete measure locks for harpId: " + userName);
    List<MeasureLock> existingLocks = measureLockRepository.findAllByLockedBy(userName);
    log.info(
        "locks found by harpId: "
            + userName
            + (CollectionUtils.isNotEmpty(existingLocks) ? existingLocks.size() : " none"));
    if (CollectionUtils.isNotEmpty(existingLocks)) {
      existingLocks.stream()
          .forEach(
              existingLock -> {
                measureLockRepository.deleteByMeasureId(existingLock.getMeasureId());
                deleteMesssages.add("Deleted measure lock: " + existingLock.getMeasureId());
              });
    } else {
      deleteMesssages.add("No measure locks found for harpId: " + userName);
    }
    return deleteMesssages;
  }
}
