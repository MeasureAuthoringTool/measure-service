package cms.gov.madie.measure.resources;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import cms.gov.madie.measure.services.ActionLogService;
import cms.gov.madie.measure.services.ExportService;
import cms.gov.madie.measure.services.MeasureService;
import gov.cms.madie.models.common.ActionType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.FhirServicesClient;
import cms.gov.madie.measure.utils.ControllerUtil;
import cms.gov.madie.measure.utils.ExportFileNamesUtil;
import gov.cms.madie.models.measure.Measure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ExportController {

  private final MeasureRepository measureRepository;
  private final FhirServicesClient fhirServicesClient;
  private final ExportService exportService;
  private final MeasureService measureService;
  private final ActionLogService actionLogService;

  @GetMapping(path = "/measures/{id}/exports", produces = "application/zip")
  public ResponseEntity<byte[]> getZip(
      Principal principal,
      @PathVariable("id") String id,
      @RequestParam(value = "elmErrorSeverity", required = false, defaultValue = "Info")
          String elmErrorSeverity,
      @RequestHeader("Authorization") String accessToken) {

    final String username = principal.getName();
    log.info("User [{}] is attempting to export measure [{}]", username, id);

    Measure measure = measureService.findMeasureById(id);

    if (measure == null) {
      throw new ResourceNotFoundException("Measure", id);
    }
    var packageDto = exportService.getMeasureExport(measure, accessToken, elmErrorSeverity);

    actionLogService.logAction(id, Measure.class, ActionType.EXPORTED_MEASURE, principal.getName());

    return ResponseEntity.status(
            packageDto.isFromStorage() ? HttpStatus.OK.value() : HttpStatus.CREATED.value())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment;filename=\"" + ExportFileNamesUtil.getExportFileName(measure) + ".zip\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(packageDto.getExportPackage());
  }

  @PutMapping(path = ControllerUtil.TEST_CASES + "/exports", produces = "application/zip")
  public ResponseEntity<byte[]> getTestCaseExport(
      Principal principal,
      @RequestHeader("Authorization") String accessToken,
      @PathVariable String measureId,
      @RequestParam Optional<String> bundleType,
      @RequestBody List<String> testCaseId) {

    final String username = principal.getName();
    log.info("User [{}] is attempting to export test cases for [{}]", username, measureId);

    Optional<Measure> measureOptional = measureRepository.findById(measureId);

    if (measureOptional.isEmpty()) {
      throw new ResourceNotFoundException("Measure", measureId);
    }

    Measure measure = measureOptional.get();
    // change Measure Bundle Type to "type" for export

    ResponseEntity<byte[]> exportResponse =
        fhirServicesClient.getTestCaseExports(
            measure, accessToken, testCaseId, bundleType.orElse(("COLLECTION").toUpperCase()));

    int statusCode = exportResponse.getStatusCode().value();

    if (statusCode == 200) {
      log.info(
          "Successfully exported all [{}] test cases for measure [{}] by user [{}]",
          testCaseId.size(),
          measureId,
          username);
      actionLogService.logAction(measureId, Measure.class, ActionType.EXPORTED_TESTCASES, username);
    } else if (statusCode == 206) {
      log.warn(
          "Partial export for measure [{}] by user [{}]. Some test cases could not be exported.",
          measureId,
          username);
      actionLogService.logAction(measureId, Measure.class, ActionType.EXPORTED_TESTCASES, username);
    } else {
      log.error(
          "Failed to export test cases for measure [{}] by user [{}]. Status: [{}]",
          measureId,
          username,
          statusCode);
    }

    return exportResponse;
  }

  @PutMapping(path = "/measures/{id}/test-cases/qrda", produces = "application/zip")
  public ResponseEntity<byte[]> getQRDA(
      Principal principal,
      @PathVariable("id") String id,
      @RequestBody QrdaRequestDTO requestDTO,
      @RequestHeader("Authorization") String accessToken) {

    final String username = principal.getName();
    log.info("User [{}] is attempting to export QRDA for measure [{}]", username, id);

    Optional<Measure> measureOptional = measureRepository.findById(id);

    if (measureOptional.isEmpty()) {
      throw new ResourceNotFoundException("Measure", id);
    }

    return ResponseEntity.status(HttpStatus.OK)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment;filename=\""
                + ExportFileNamesUtil.getExportFileName(requestDTO.getMeasure())
                + ".zip\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(exportService.getQRDA(requestDTO, accessToken));
  }
}
