package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.services.ActionLogService;
import cms.gov.madie.measure.services.AppConfigService;
import cms.gov.madie.measure.services.DraftValueSetOverrideService;
import cms.gov.madie.measure.services.MeasureService;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.measure.DraftValueSetOverride;
import gov.cms.madie.models.measure.Measure;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manages the list of value sets for which draft content should be included when a measure's value
 * set expansions are retrieved for a specific manifest. The list belongs to a single measure
 * version.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DraftValueSetOverrideController extends AbstractMeasureController {

  private final DraftValueSetOverrideService draftValueSetOverrideService;
  private final MeasureService measureService;
  private final ActionLogService actionLogService;
  private final AppConfigService appConfigService;

  @Override
  protected AppConfigService getAppConfigService() {
    return appConfigService;
  }

  @GetMapping("/measures/{measureId}/draft-value-set-overrides")
  public ResponseEntity<List<DraftValueSetOverride>> getDraftValueSetOverrides(
      @PathVariable("measureId") String measureId, Principal principal) {
    final String username = principal.getName().toLowerCase();
    log.info(
        "User [{}] is retrieving the draft value set override list of Measure [{}]",
        username,
        measureId);
    return ResponseEntity.ok(draftValueSetOverrideService.getDraftValueSetOverrides(measureId));
  }

  @PostMapping("/measures/{measureId}/draft-value-set-overrides")
  public ResponseEntity<List<DraftValueSetOverride>> addDraftValueSetOverride(
      @PathVariable("measureId") String measureId,
      @RequestBody DraftValueSetOverride draftValueSetOverride,
      Principal principal) {
    final String username = principal.getName().toLowerCase();
    log.info(
        "User [{}] is adding a value set to the draft value set override list of Measure [{}]",
        username,
        measureId);
    checkMeasureLock(measureService.findMeasureById(measureId), username);
    List<DraftValueSetOverride> overrides =
        draftValueSetOverrideService.addDraftValueSetOverride(
            measureId, draftValueSetOverride, username);
    actionLogService.logAction(measureId, Measure.class, ActionType.UPDATED, username);
    return ResponseEntity.status(HttpStatus.CREATED).body(overrides);
  }

  @DeleteMapping("/measures/{measureId}/draft-value-set-overrides")
  public ResponseEntity<List<DraftValueSetOverride>> removeDraftValueSetOverride(
      @PathVariable("measureId") String measureId,
      @RequestParam(name = "oid", required = false) String oid,
      @RequestParam(name = "url", required = false) String url,
      Principal principal) {
    final String username = principal.getName().toLowerCase();
    log.info(
        "User [{}] is removing a value set from the draft value set override list of Measure [{}]",
        username,
        measureId);
    checkMeasureLock(measureService.findMeasureById(measureId), username);
    List<DraftValueSetOverride> overrides =
        draftValueSetOverrideService.removeDraftValueSetOverride(measureId, oid, url, username);
    actionLogService.logAction(measureId, Measure.class, ActionType.UPDATED, username);
    return ResponseEntity.ok(overrides);
  }
}
