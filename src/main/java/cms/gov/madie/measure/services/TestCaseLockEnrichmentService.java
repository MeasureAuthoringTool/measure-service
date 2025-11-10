package cms.gov.madie.measure.services;

import cms.gov.madie.measure.locks.TestCaseLock;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.TestCaseLockInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

/**
 * Service responsible for enriching test cases with lock information. This service adds lock data
 * to test cases when they are retrieved, showing which test cases are locked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseLockEnrichmentService {

  private final TestCaseLockService testCaseLockService;

  /**
   * Enriches a list of test cases with lock information.
   *
   * @param testCases the test cases to enrich
   * @param username the current username (unused but kept for API consistency)
   */
  public void enrichTestCasesWithLockInfo(List<TestCase> testCases, String username) {
    if (isEmpty(testCases)) {
      return;
    }

    // Fetch all locks for these test cases
    List<String> testCaseIds = testCases.stream().map(TestCase::getId).toList();
    List<TestCaseLock> locks = testCaseLockService.getLocksByTestCaseIds(testCaseIds);

    // Create a map for quick lookup
    Map<String, TestCaseLock> lockMap =
        locks.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    TestCaseLock::getTestCaseId, java.util.function.Function.identity()));

    // Enrich test cases with lock info
    testCases.forEach(
        testCase -> {
          TestCaseLock lock = lockMap.get(testCase.getId());
          if (lock != null) {
            testCase.setTestCaseLock(convertToLockInfo(lock));
          }
        });
  }

  /**
   * Enriches a single test case with lock information.
   *
   * @param testCase the test case to enrich
   * @param username the current username (unused but kept for API consistency)
   */
  public void enrichTestCaseWithLockInfo(TestCase testCase, String username) {
    if (testCase == null) {
      return;
    }

    Optional<TestCaseLock> lock = testCaseLockService.getLockByTestCaseId(testCase.getId());

    lock.ifPresent(testCaseLock -> testCase.setTestCaseLock(convertToLockInfo(testCaseLock)));
  }

  /**
   * Converts a TestCaseLock entity to a TestCaseLockInfo DTO.
   *
   * @param lock the lock entity
   * @return the lock info DTO
   */
  private TestCaseLockInfo convertToLockInfo(TestCaseLock lock) {
    return TestCaseLockInfo.builder()
        .measureId(lock.getMeasureId())
        .testCaseId(lock.getTestCaseId())
        .lockedBy(lock.getLockedBy())
        .lockedAt(lock.getLockedAt())
        .expiresAt(lock.getExpiresAt())
        .build();
  }
}
