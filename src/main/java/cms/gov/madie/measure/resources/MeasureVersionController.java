package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.exceptions.InvalidIdException;
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
public class MeasureVersionController extends AbstractMeasureController {

  private final VersionService versionService;
  private final MeasureService measureService;
  private final AppConfigService appConfigService;

  @Override
  protected AppConfigService getAppConfigService() {
    return appConfigService;
  }

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
}
