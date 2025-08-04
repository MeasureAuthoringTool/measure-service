package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.services.MeasureLockService;
import gov.cms.madie.models.common.MeasureLock;

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

  @PutMapping("/measures/{id}/measure-lock")
  public ResponseEntity<MeasureLock> updateMeasureLock(
      @PathVariable String measureId,
      @RequestHeader("Authorization") String accessToken,
      @RequestHeader(name = "harpId") String harpId) {
    return ResponseEntity.ok(measureLockService.lockMeasure(measureId, harpId, accessToken));
  }

  @DeleteMapping("/measures/{measureId}/measure-lock")
  public ResponseEntity<Void> unlockMeasure(
      @PathVariable String measureId,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {

    measureLockService.unlockMeasure(measureId, principal.getName(), accessToken);
    // Always 200 even if no lock?
    return ResponseEntity.ok().build();
  }
}
