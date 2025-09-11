package cms.gov.madie.measure.resources;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.services.TestCaseLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TestCaseLockController {
  private final TestCaseLockService testCaseLockService;

  @PostMapping("/measures/{measureId}/test-cases/{testCaseId}/lock")
  public ResponseEntity<LockInfo> addTestCaseLock(
      @PathVariable String measureId, @PathVariable String testCaseId, Principal principal) {
    return ResponseEntity.ok(
        testCaseLockService.lockTestCase(measureId, testCaseId, principal.getName()));
  }

  @DeleteMapping("/test-cases/{testCaseId}/lock")
  public ResponseEntity<LockInfo> unlockTestCase(
      @PathVariable String testCaseId, Principal principal) {
    return ResponseEntity.ok(testCaseLockService.unlockTestCase(testCaseId, principal.getName()));
  }
}
