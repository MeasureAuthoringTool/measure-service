package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.MeasureLockService;
import cms.gov.madie.measure.services.TestCaseLockService;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MeasureLockController {
  private final MeasureLockService measureLockService;
  private final TestCaseLockService testCaseLockService;
  private final MeasureRepository measureRepository;

  @PutMapping("/measures/{measureId}/measure-lock")
  public ResponseEntity<LockInfo> updateMeasureLock(
      @PathVariable String measureId, Principal principal) {
    return ResponseEntity.ok(measureLockService.lockMeasure(measureId, principal.getName()));
  }

  @DeleteMapping("/measures/{measureId}/measure-lock")
  public ResponseEntity<LockInfo> unlockMeasure(
      @PathVariable String measureId, Principal principal) {
    LockInfo response = measureLockService.unlockMeasure(measureId, principal.getName());
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/measures/unlock")
  public ResponseEntity<List<String>> unlockAll(HttpServletRequest request, Principal principal) {
    final String username = principal.getName();
    log.info("Unlock measures, test cases for user: " + username);
    List<String> messages = new ArrayList<>();
    messages.addAll(measureLockService.unlockByUser(username));
    messages.addAll(testCaseLockService.unlockByUser(username));
    return ResponseEntity.ok(messages);
  }

  /*
   * This method is to check if the measure is locked by another user
   * Used by front end delete action tooltip.
   * 1. If the measure is locked by other user, it returns the harp id of the other user
   * 2. If any of the test cases is locked by other user,
   *    it returns "One or more test cases are locked by another user."
   * 3. Else, it returns "OK to proceed"
   */
  @GetMapping("/measures/{measureId}/lock-by-other-user")
  public ResponseEntity<String> isMeasureLockedByOtherUser(
      @PathVariable String measureId, Principal principal) {
    String lockMessage = "OK to proceed";
    LockInfo lock = measureLockService.getMeasureLock(measureId);
    if (lock.isLocked() && !principal.getName().equals(lock.getLockedBy())) {
      lockMessage = lock.getLockedBy();
    } else {
      Optional<Measure> measureOpt = measureRepository.findByIdAndActive(measureId, true);
      if (measureOpt.isEmpty()) {
        throw new ResourceNotFoundException("Measure", measureId);
      }
      if (CollectionUtils.isNotEmpty(measureOpt.get().getTestCases())) {
        List<String> testCaseIds =
            measureOpt.get().getTestCases().stream()
                .map(TestCase::getId)
                .collect(Collectors.toList());
        boolean testCaseLocksByOtherUser =
            testCaseLockService.testCaseLocksByOtherUser(
                measureId, testCaseIds, principal.getName());
        lockMessage =
            testCaseLocksByOtherUser
                ? "One or more test cases are locked by another user."
                : lockMessage;
      }
    }
    return ResponseEntity.ok(lockMessage);
  }
}
