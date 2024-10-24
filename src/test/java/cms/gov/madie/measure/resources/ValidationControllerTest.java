package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.services.FhirServicesClient;
import cms.gov.madie.measure.services.VirusScanClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.HapiOperationOutcome;
import gov.cms.madie.models.scanner.ScanValidationDto;
import gov.cms.madie.models.scanner.VirusScanResponseDto;
import gov.cms.madie.models.scanner.VirusScanResultDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationControllerTest {

  @Mock private FhirServicesClient fhirServicesClient;

  @Mock private VirusScanClient virusScanClient;

  @Mock private ObjectMapper mapper;

  @InjectMocks private ValidationController validationController;

  @Captor ArgumentCaptor<String> testCaseJsonCaptor;
  @Captor ArgumentCaptor<ModelType> testCaseModelCaptor;
  @Captor ArgumentCaptor<String> accessTokenCaptor;

  @Test
  void testValidateBundleProxiesRequest() throws JsonProcessingException {
    final String accessToken = "Bearer TOKEN";
    final String testCaseJson = "{ \"resourceType\": \"GOOD JSON\" }";
    HttpHeaders headers = new HttpHeaders();
    final String goodOutcomeJson = "{ \"code\": 200, \"successful\": true }";
    HttpEntity<String> request = new HttpEntity<>(testCaseJson, headers);

    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenReturn(
            ResponseEntity.ok(HapiOperationOutcome.builder().code(200).successful(true).build()));

    when(mapper.writeValueAsString(any())).thenReturn(goodOutcomeJson);

    ResponseEntity<String> output =
        validationController.validateBundle(request, ModelType.QI_CORE.getValue(), accessToken);

    assertThat(output, is(notNullValue()));
    assertThat(output.getBody(), is(notNullValue()));
    assertThat(output.getBody(), is(equalTo(goodOutcomeJson)));
    verify(fhirServicesClient, times(1))
        .validateBundle(
            testCaseJsonCaptor.capture(),
            testCaseModelCaptor.capture(),
            accessTokenCaptor.capture());
    assertThat(testCaseJsonCaptor.getValue(), is(equalTo(testCaseJson)));
    assertThat(accessTokenCaptor.getValue(), is(equalTo(accessToken)));
  }

  @Test
  void testValidateBundleBadRequest() throws JsonProcessingException {
    final String accessToken = "Bearer TOKEN";
    final String testCaseJson = "{ \"resourceType\": \"GOOD JSON\" }";
    HttpHeaders headers = new HttpHeaders();
    HttpEntity<String> request = new HttpEntity<>(testCaseJson, headers);

    when(fhirServicesClient.validateBundle(anyString(), any(ModelType.class), anyString()))
        .thenReturn(
            ResponseEntity.ok(HapiOperationOutcome.builder().code(200).successful(true).build()));

    when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("BadJson") {});

    ResponseEntity<String> output =
        validationController.validateBundle(request, ModelType.QI_CORE.getValue(), accessToken);

    assertThat(output, is(notNullValue()));
    assertThat(output.getStatusCode(), is(HttpStatus.BAD_REQUEST));
    assertThat(output.getBody(), is(notNullValue()));
    assertThat(
        output.getBody(),
        is(
            equalTo(
                "Unable to validate test case JSON due to errors,"
                    + " but outcome not able to be interpreted!")));
    verify(fhirServicesClient, times(1))
        .validateBundle(
            testCaseJsonCaptor.capture(),
            testCaseModelCaptor.capture(),
            accessTokenCaptor.capture());
  }

  @Test
  void testScanFileHandlesNoFileResponse() {
    MultipartFile multipartFile = Mockito.mock(MultipartFile.class);
    when(multipartFile.getOriginalFilename()).thenReturn("TestFile.txt");
    Resource resource = Mockito.mock(Resource.class);
    when(multipartFile.getResource()).thenReturn(resource);
    Principal principal = Mockito.mock(Principal.class);
    when(principal.getName()).thenReturn("TestUser");
    VirusScanResponseDto scanResponse =
        VirusScanResponseDto.builder().filesScanned(0).cleanFileCount(0).build();
    when(virusScanClient.scanFile(any(Resource.class))).thenReturn(scanResponse);

    ResponseEntity<ScanValidationDto> output =
        validationController.scanFile(multipartFile, principal);
    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.BAD_REQUEST)));
    assertThat(output, is(notNullValue()));
    assertThat(output.getBody(), is(notNullValue()));
    assertThat(output.getBody().isValid(), is(false));
    assertThat(output.getBody().getError(), is(notNullValue()));
    assertThat(
        output.getBody().getError().getDefaultMessage(),
        is(equalTo("Validation service returned zero validated files.")));
  }

  @Test
  void testScanFileHandlesCleanFileResponse() {
    MultipartFile multipartFile = Mockito.mock(MultipartFile.class);
    when(multipartFile.getOriginalFilename()).thenReturn("TestFile.txt");
    Resource resource = Mockito.mock(Resource.class);
    when(multipartFile.getResource()).thenReturn(resource);
    Principal principal = Mockito.mock(Principal.class);
    when(principal.getName()).thenReturn("TestUser");
    VirusScanResponseDto scanResponse =
        VirusScanResponseDto.builder().filesScanned(1).cleanFileCount(1).build();
    when(virusScanClient.scanFile(any(Resource.class))).thenReturn(scanResponse);

    ScanValidationDto expected =
        ScanValidationDto.builder().fileName("TestFile.txt").valid(true).build();
    ResponseEntity<ScanValidationDto> output =
        validationController.scanFile(multipartFile, principal);
    assertThat(output, is(notNullValue()));
    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.OK)));
    assertThat(output.getBody(), is(equalTo(expected)));
  }

  @Test
  void testScanFileHandlesInfectedFileResponse() {
    MultipartFile multipartFile = Mockito.mock(MultipartFile.class);
    when(multipartFile.getOriginalFilename()).thenReturn("TestFile.txt");
    Resource resource = Mockito.mock(Resource.class);
    when(multipartFile.getResource()).thenReturn(resource);
    Principal principal = Mockito.mock(Principal.class);
    when(principal.getName()).thenReturn("TestUser");
    VirusScanResponseDto scanResponse =
        VirusScanResponseDto.builder()
            .filesScanned(1)
            .cleanFileCount(0)
            .infectedFileCount(1)
            .scanResults(
                List.of(
                    VirusScanResultDto.builder()
                        .fileName("TestFile.txt")
                        .infected(true)
                        .viruses(List.of("SomeBadVirus", "LessBadVirusButStillBad"))
                        .build()))
            .build();
    when(virusScanClient.scanFile(any(Resource.class))).thenReturn(scanResponse);

    ResponseEntity<ScanValidationDto> output =
        validationController.scanFile(multipartFile, principal);
    assertThat(output, is(notNullValue()));
    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.OK)));
    assertThat(output.getBody(), is(notNullValue()));
    assertThat(output.getBody().isValid(), is(false));
    assertThat(output.getBody().getFileName(), is(equalTo("TestFile.txt")));
    assertThat(output.getBody().getError(), is(notNullValue()));
    assertThat(
        output.getBody().getError().getDefaultMessage(),
        is(
            equalTo(
                "There was an error importing this file. "
                    + "Please contact the help desk for error code V100.")));
  }
}
