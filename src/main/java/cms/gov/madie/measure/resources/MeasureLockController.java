package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.services.MeasureLockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MeasureLockController {
  private final MeasureLockService measureLockService;

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
}
