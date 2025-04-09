package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.BundleService;
import gov.cms.madie.models.measure.Measure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
public class BundleController {

  private final MeasureRepository measureRepository;

  private final BundleService bundleService;

  @GetMapping(path = "/measures/{measureId}/bundle", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getMeasureBundle(
      @PathVariable String measureId,
      Principal principal,
      @RequestHeader("Authorization") String accessToken,
      @RequestParam(required = false, defaultValue = "calculation", name = "bundleType")
          String bundleType,
      @RequestParam(value = "elmErrorSeverity", required = false, defaultValue = "Info")
          String elmErrorSeverity) {
    Optional<Measure> measureOptional = measureRepository.findById(measureId);
    log.info(
        "User [{}] is attempting to create a new measure bundle for [{}]",
        principal.getName(),
        measureId);
    if (measureOptional.isEmpty()) {
      throw new ResourceNotFoundException("Measure", measureId);
    }
    Measure measure = measureOptional.get();

    return ResponseEntity.ok(
        bundleService.bundleMeasure(measure, accessToken, bundleType, elmErrorSeverity));
  }
}
