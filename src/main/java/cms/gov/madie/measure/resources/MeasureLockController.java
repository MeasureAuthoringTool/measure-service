package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.services.MeasureLockService;
import cms.gov.madie.measure.services.TestCaseLockService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MeasureLockController {
  private final MeasureLockService measureLockService;
  private final TestCaseLockService testCaseLockService;

  @PutMapping("/measures/{measureId}/measure-lock")
  public ResponseEntity<LockInfo> updateMeasureLock(
      @PathVariable String measureId, Principal principal) {
    return ResponseEntity.ok(
        measureLockService.lockMeasure(measureId, principal.getName().toLowerCase()));
  }

  @DeleteMapping("/measures/{measureId}/measure-lock")
  public ResponseEntity<LockInfo> unlockMeasure(
      @PathVariable String measureId, Principal principal) {
    LockInfo response =
        measureLockService.unlockMeasure(measureId, principal.getName().toLowerCase());
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/measures/unlock")
  public ResponseEntity<List<String>> unlockAll(HttpServletRequest request, Principal principal) {
    final String username = principal.getName().toLowerCase();
    log.info("Unlock measures, test cases for user: " + username);
    List<String> messages = new ArrayList<>();
    messages.addAll(measureLockService.unlockByUser(username));
    messages.addAll(testCaseLockService.unlockByUser(username));
    return ResponseEntity.ok(messages);
  }
}
