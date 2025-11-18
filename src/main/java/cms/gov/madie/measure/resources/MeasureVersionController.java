package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.MadieFeatureFlag;
import cms.gov.madie.measure.exceptions.InvalidIdException;
import cms.gov.madie.measure.exceptions.LockNotObtainedException;
import cms.gov.madie.measure.services.AppConfigService;
import cms.gov.madie.measure.services.VersionService;
import cms.gov.madie.measure.services.MeasureService;
import gov.cms.madie.models.measure.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

import static cms.gov.madie.measure.services.VersionService.VersionValidationResult.TEST_CASE_ERROR;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/measures")
public class MeasureVersionController {

  private final VersionService versionService;
  private final MeasureService measureService;
  private final AppConfigService appConfigService;

  @PutMapping("/{id}/version")
  public ResponseEntity<Measure> createVersion(
      @PathVariable("id") String id,
      @RequestParam String versionType,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {
    final String username = principal.getName();
    final Measure existingMeasure = measureService.findMeasureById(id);
    checkMeasureLock(existingMeasure, username);
    return ResponseEntity.ok(versionService.createVersion(id, versionType, username, accessToken));
  }

  @GetMapping("/{id}/version")
  public ResponseEntity<Void> checkValidVersion(
      @PathVariable("id") String id,
      @RequestParam String versionType,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {
    var validationResult =
        versionService.checkValidVersioning(id, versionType, principal.getName(), accessToken);
    if (validationResult == TEST_CASE_ERROR) {
      return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @GetMapping("/{id}/next-version")
  public ResponseEntity<String> getNextVersionNumber(
      @PathVariable("id") String id, @RequestParam String versionType) {
    Measure measure = measureService.findMeasureById(id);
    return ResponseEntity.ok(versionService.getNextVersion(measure, versionType).toString());
  }

  @PostMapping("/{id}/draft")
  public ResponseEntity<Measure> createDraft(
      @RequestHeader("Authorization") String accessToken,
      @PathVariable("id") String id,
      @RequestBody final Measure measure,
      Principal principal) {
    if (StringUtils.isBlank(measure.getMeasureName())) {
      throw new InvalidIdException("Measure name is required.");
    }
    var output =
        versionService.createDraft(
            id, measure.getMeasureName(), measure.getModel(), principal.getName(), accessToken);
    return ResponseEntity.status(HttpStatus.CREATED).body(output);
  }

  /**
   * Checks if a measure is locked by another user and throws LockNotObtainedException if so. This
   * method only performs the check if the LOCKING feature flag is enabled.
   *
   * @param measure the measure to check
   * @param username the username of the current user
   * @throws LockNotObtainedException if the measure is locked by a different user
   */
  private void checkMeasureLock(Measure measure, String username) {
    if (appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)) {
      log.debug("Checking lock for measure [{}]", measure.getId());
      if (measure.getMeasureLock() != null) {
        log.debug(
            "Measure Lock found for measure [{}] locked by user [{}]",
            measure.getId(),
            measure.getMeasureLock().getLockedBy());
        if (!measure.getMeasureLock().getLockedBy().equalsIgnoreCase(username)) {
          throw new LockNotObtainedException(
              "Unable to update measure. Measure is locked by another user.");
        }
      }
    }
  }
}
