package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.services.FhirServicesClient;
import cms.gov.madie.measure.services.VirusScanClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.HapiOperationOutcome;
import gov.cms.madie.models.scanner.ScanValidationDto;
import gov.cms.madie.models.scanner.VirusScanResponseDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/validations")
@AllArgsConstructor
public class ValidationController {

  private FhirServicesClient fhirServicesClient;
  private VirusScanClient virusScanClient;
  private ObjectMapper mapper;

  @PostMapping(
      path = "/bundles",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> validateBundle(
      HttpEntity<String> request,
      @RequestParam String model,
      @RequestHeader("Authorization") String accessToken) {
    try {
      ModelType modelType = ModelType.valueOfName(model);
      ResponseEntity<HapiOperationOutcome> output =
          fhirServicesClient.validateBundle(request.getBody(), modelType, accessToken);
      return ResponseEntity.ok(mapper.writeValueAsString(output.getBody()));

    } catch (JsonProcessingException ex) {
      return ResponseEntity.badRequest()
          .body(
              "Unable to validate test case JSON due to errors,"
                  + " but outcome not able to be interpreted!");
    }
  }

  @PostMapping("/files")
  public ResponseEntity<ScanValidationDto> scanFile(
      @RequestParam("file") MultipartFile multipartFile, Principal principal) {
    final String fileName = StringUtils.cleanPath(multipartFile.getOriginalFilename());
    final String username = principal.getName();
    VirusScanResponseDto scanResponse = virusScanClient.scanFile(multipartFile.getResource());
    if (scanResponse.getFilesScanned() == 0) {
      log.warn(
          "User [{}] tried to validate file [{}] but virus scan service "
              + "response contained zero scanned files!",
          username,
          fileName);
      return ResponseEntity.badRequest()
          .body(
              ScanValidationDto.builder()
                  .fileName(null)
                  .valid(false)
                  .error(
                      new ObjectError(
                          fileName,
                          new String[] {"400"},
                          null,
                          "Validation service returned zero validated files."))
                  .build());
    } else if (scanResponse.getFilesScanned() == scanResponse.getCleanFileCount()) {
      // all files are clean!
      log.info("User [{}] invoked virus scan proxy endpoint and scanned file was clean", username);
      return ResponseEntity.ok(ScanValidationDto.builder().fileName(fileName).valid(true).build());
    } else {
      // errors occurred or virus detected
      log.info(
          "User [{}] invoked virus scan proxy endpoint and scanned file was infected! "
              + "Returning error code V100.",
          username);
      return ResponseEntity.ok(
          ScanValidationDto.builder()
              .fileName(fileName)
              .valid(false)
              .error(
                  new ObjectError(
                      fileName,
                      new String[] {"V100"},
                      null,
                      "There was an error importing this file. "
                          + "Please contact the help desk for error code V100."))
              .build());
    }
  }
}
