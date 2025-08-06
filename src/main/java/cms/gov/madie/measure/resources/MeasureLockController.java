package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.LockResponse;
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
  public ResponseEntity<LockResponse> updateMeasureLock(
      @PathVariable String measureId, Principal principal) {
    return ResponseEntity.ok(measureLockService.lockMeasure(measureId, principal.getName()));
  }

  @DeleteMapping("/measures/{measureId}/measure-lock")
  public ResponseEntity<LockResponse> unlockMeasure(
      @PathVariable String measureId, Principal principal) {
    LockResponse response = measureLockService.unlockMeasure(measureId, principal.getName());
    return ResponseEntity.ok(response);
  }
}
