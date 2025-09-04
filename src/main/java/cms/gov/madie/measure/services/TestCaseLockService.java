package cms.gov.madie.measure.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.TestCaseLockRepository;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.locks.TestCaseLock;
import cms.gov.madie.measure.dto.LockInfo;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseLockService {
  private final MeasureRepository measureRepository;
  private final TestCaseLockRepository testCaseLockRepository;

  public synchronized LockInfo lockTestCase(String measureId, String testCaseId, String userName) {
    validateMeasureAndTestCase(measureId, testCaseId);

    LockInfo lockInfo = null;
    TestCaseLock lock = makeNewLock(measureId, testCaseId, userName);

    try {
      testCaseLockRepository.insert(lock);
      lockInfo = LockInfo.builder().lockedId(testCaseId).isLocked(true).lockedBy(userName).build();
    } catch (DuplicateKeyException ex) {
      Optional<TestCaseLock> existingLock = testCaseLockRepository.findByTestCaseId(testCaseId);
      if (existingLock.isPresent()) {
        lockInfo =
            LockInfo.builder()
                .lockedId(testCaseId)
                .isLocked(true)
                .lockedBy(existingLock.get().getLockedBy())
                .build();
      }
    }
    return lockInfo;
  }

  public LockInfo unlockTestCase(String testCaseId, String userName) {
    Optional<TestCaseLock> existingLock = testCaseLockRepository.findByTestCaseId(testCaseId);
    if (existingLock.isPresent()) {
      if (existingLock.get().getLockedBy().equals(userName)) {
        testCaseLockRepository.deleteByTestCaseId(testCaseId);
        return LockInfo.builder().isLocked(false).build();
      } else {
        return LockInfo.builder()
            .lockedId((existingLock.get().getTestCaseId()))
            .isLocked(true)
            .lockedBy(existingLock.get().getLockedBy())
            .build();
      }
    }
    return null;
  }

  TestCase validateMeasureAndTestCase(String measureId, String testCaseId) {
    Optional<Measure> measureOptional = measureRepository.findById(measureId);
    if (measureOptional.isEmpty()) {
      throw new ResourceNotFoundException("Measure", measureId);
    }
    List<TestCase> testCases = measureOptional.get().getTestCases();
    if (CollectionUtils.isEmpty(testCases)) {
      throw new ResourceNotFoundException("TestCase", testCaseId);
    }
    TestCase testCase =
        testCases.stream()
            .filter(tc -> tc.getId().equals(testCaseId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));
    return testCase;
  }

  public List<String> unlockByUser(String userName) {
    List<String> deleteMessages = new ArrayList<>();
    deleteMessages.add("Delete test case locks for harpId: " + userName);
    List<TestCaseLock> existingLocks = testCaseLockRepository.findAllByLockedBy(userName);
    log.info(
        (CollectionUtils.isNotEmpty(existingLocks) ? existingLocks.size() : "No")
            + " test case locks found for harpId: "
            + userName);
    if (CollectionUtils.isNotEmpty(existingLocks)) {
      existingLocks.forEach(
          existingLock -> {
            testCaseLockRepository.deleteByTestCaseId(existingLock.getTestCaseId());
            deleteMessages.add("Deleted test case lock for Id: " + existingLock.getTestCaseId());
          });
    } else {
      deleteMessages.add("No test case locks found for harpId: " + userName);
    }
    return deleteMessages;
  }

  public boolean lockTestCases(String measureId, List<String> testCaseIds, String userId) {
    boolean failure = false;
    List<TestCaseLock> locks = new ArrayList<>();
    testCaseIds.forEach(
        testCaseId -> {
          validateMeasureAndTestCase(measureId, testCaseId);
          TestCaseLock lock = makeNewLock(measureId, testCaseId, userId);
          locks.add(lock);
        });
    List<TestCaseLock> insertedLocks = new ArrayList<>();
    for (TestCaseLock lock : locks) {
      try {
        testCaseLockRepository.insert(lock);
        insertedLocks.add(lock);
      } catch (DuplicateKeyException ex) {
        Optional<TestCaseLock> existingLock =
            testCaseLockRepository.findByTestCaseId(lock.getTestCaseId());
        if (existingLock.isPresent()) {
          if (!existingLock.get().getLockedBy().equalsIgnoreCase(userId)) {
            log.error(
                "lockTestCases: DuplicateKeyException for testCaseId: [{}], lockedBy: [{}], new userId: [{}]",
                existingLock.get().getTestCaseId(),
                existingLock.get().getLockedBy(),
                userId);
            failure = true;
            break;
          }
          log.info(
              "lockTestCases: DuplicateKeyException for testCaseId: [{}], lockedBy is the same as userId: ",
              existingLock.get().getTestCaseId(),
              userId);
        }
      }
    }
    if (failure) {
      insertedLocks.forEach(
          lock -> {
            log.info("lockTestCases: revert lock: delete testCaseId: [{}]", lock.getTestCaseId());
            testCaseLockRepository.deleteByTestCaseId(lock.getTestCaseId());
          });
    }
    log.info("lockTestCases: " + !failure);
    return !failure;
  }

  private TestCaseLock makeNewLock(String measureId, String testCaseId, String userName) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(Duration.ofMinutes(15)); // 15 minute lock

    return TestCaseLock.builder()
        .measureId(measureId)
        .testCaseId(testCaseId)
        .lockedBy(userName)
        .lockedAt(Instant.now())
        .expiresAt(expiresAt)
        .build();
  }

  public boolean unlockTestCases(List<String> testCaseIds, String userId) {
    boolean success = true;
    if (CollectionUtils.isNotEmpty(testCaseIds)) {
      for (String testCaseId : testCaseIds) {
        Optional<TestCaseLock> existingLock = testCaseLockRepository.findByTestCaseId(testCaseId);
        if (existingLock.isPresent()) {
          if (existingLock.get().getLockedBy().equals(userId)) {
            testCaseLockRepository.deleteByTestCaseId(testCaseId);
          } else {
            log.info(
                "testCaseId: [{}] lockedBy: [{}] is different than userId: [{}]",
                testCaseId,
                existingLock.get().getLockedBy(),
                userId);
            success = false;
          }
        } else {
          log.info("testCaseId: [{}] not found in TestCaseLock", testCaseId);
          success = false;
        }
      }
    } else {
      log.info("testCaseIds passed in are null or empty");
      success = false;
    }
    log.info("lockTestCases: " + success);
    return success;
  }
}
