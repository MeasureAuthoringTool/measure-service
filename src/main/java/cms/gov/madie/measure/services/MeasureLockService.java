package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.exceptions.LockNotObtainedException;
import cms.gov.madie.measure.locks.MeasureLock;
import cms.gov.madie.measure.repositories.MeasureLockRepository;
import gov.cms.madie.models.measure.Measure;

import org.springframework.dao.DuplicateKeyException;
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
  private final TestCaseLockService testCaseLockService;

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

  public LockInfo unlockMeasure(String measureId, String userName) {
    Optional<MeasureLock> existingLock = measureLockRepository.findByMeasureId(measureId);
    // it's our lock. We delete it.
    if (existingLock.isPresent() && existingLock.get().getLockedBy().equals(userName)) {
      measureLockRepository.deleteByMeasureId(measureId);
      return new LockInfo(false, null, measureId); // Successfully unlocked
    }
    // it's not our lock. we dont do anything
    return new LockInfo(true, existingLock.map(MeasureLock::getLockedBy).orElse(null), measureId);
  }

  public List<String> unlockByUser(String userName) {
    List<String> deleteMesssages = new ArrayList<>();
    deleteMesssages.add("Delete measure locks for harpId: " + userName);
    List<MeasureLock> existingLocks = measureLockRepository.findAllByLockedBy(userName);
    log.info(
        (CollectionUtils.isNotEmpty(existingLocks) ? existingLocks.size() : "No")
            + " measure locks found for harpId: "
            + userName);
    if (CollectionUtils.isNotEmpty(existingLocks)) {
      existingLocks.forEach(
          existingLock -> {
            measureLockRepository.deleteByMeasureId(existingLock.getMeasureId());
            deleteMesssages.add("Deleted measure lock: " + existingLock.getMeasureId());
          });
    } else {
      deleteMesssages.add("No measure locks found for harpId: " + userName);
    }
    return deleteMesssages;
  }

  public boolean checkMeasureAndTestCaseLock(String username, Measure measure, String action) {
    LockInfo lock = lockMeasure(measure.getId(), username);
    boolean measureLockedByOthers = lock.isLocked() && !username.equals(lock.getLockedBy());
    log.info(
        "user: [{}] is trying to lock Measure: [{}]. Measure is [{}] [{}]",
        username,
        measure.getId(),
        measureLockedByOthers ? "locked by " : "not locked.",
        measureLockedByOthers ? lock.getLockedBy() : "");
    if (measureLockedByOthers) {
      String error =
          "Unable to " + action + " measure. Locked while being edited by " + lock.getLockedBy();
      log.info("user: " + username + ": " + error);
      throw new LockNotObtainedException(error);
    }

    boolean isAnyTestCaseLocked =
        testCaseLockService.isAnyTestCaseLockedByOthers(measure.getId(), username);
    log.info("Measure: [{}] has test case lock? [{}]", measure.getId(), isAnyTestCaseLocked);
    if (isAnyTestCaseLocked) {
      String error =
          "Unable to " + action + " measure. One or more test cases are locked by another user.";
      log.info("user: " + username + ": " + error);
      unlockMeasure(measure.getId(), username);
      log.info("user: [{}] unlocked Measure: [{}]", username, measure.getId());

      throw new LockNotObtainedException(error);
    }
    return false;
  }
}
