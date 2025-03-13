package cms.gov.madie.measure.resources;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.security.Principal;

import cms.gov.madie.measure.exceptions.InvalidIdException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import cms.gov.madie.measure.exceptions.BadVersionRequestException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import cms.gov.madie.measure.services.VersionService;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;

@ExtendWith(MockitoExtension.class)
public class MeasureVersionControllerTest {

  @Mock VersionService versionService;

  @InjectMocks MeasureVersionController measureVersionController;

  @Mock Principal principal;

  private Measure measure;

  private static final String TEST_ACCESS_TOKEN = "test-user-access-token";

  @BeforeEach
  public void setUp() {
    measure = new Measure();
    measure.setId("testMeasureId");
  }

  @Test
  public void testCreateVersionReturnsResourceNotFoundException() {
    when(principal.getName()).thenReturn("testUser");
    when(versionService.createVersion(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new ResourceNotFoundException("Measure", measure.getId()));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            measureVersionController.createVersion(
                "testMeasureId", "MAJOR", principal, "accesstoken"));
  }

  @Test
  public void testCreateVersionReturnsBadVersionRequestException() {
    when(principal.getName()).thenReturn("testUser");

    doThrow(
            new BadVersionRequestException(
                "Measure", measure.getId(), principal.getName(), "Invalid version request."))
        .when(versionService)
        .createVersion(anyString(), anyString(), anyString(), anyString());
    assertThrows(
        BadVersionRequestException.class,
        () ->
            measureVersionController.createVersion(
                "testMeasureId", "NOTVALIDVERSIONTYPE", principal, "accesstoken"));
  }

  @Test
  public void testCreateVersionReturnsUnauthorizedException() {
    when(principal.getName()).thenReturn("testUser");

    doThrow(new UnauthorizedException("Measure", measure.getId(), principal.getName()))
        .when(versionService)
        .createVersion(anyString(), anyString(), anyString(), anyString());
    assertThrows(
        UnauthorizedException.class,
        () ->
            measureVersionController.createVersion(
                "testMeasureId", "MAJOR", principal, "accesstoken"));
  }

  @Test
  public void testCreateVersionSuccess() {
    when(principal.getName()).thenReturn("testUser");
    Measure updatedMeasure = Measure.builder().id("testMeasureId").createdBy("testUser").build();
    Version updatedVersion = Version.builder().major(3).minor(0).revisionNumber(0).build();
    updatedMeasure.setVersion(updatedVersion);
    MeasureMetaData updatedMetaData = new MeasureMetaData();
    updatedMetaData.setDraft(false);
    updatedMeasure.setMeasureMetaData(updatedMetaData);
    when(versionService.createVersion(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(updatedMeasure);

    ResponseEntity<Measure> entity =
        measureVersionController.createVersion("testMeasureId", "MAJOR", principal, "accesstoken");
    assertThat(entity, is(notNullValue()));
    assertThat(entity.getStatusCode(), is(HttpStatus.OK));
    assertThat(entity.getBody(), is(equalTo(updatedMeasure)));
  }

  @Test
  public void testCheckVersionTestCaseErrors() {
    when(principal.getName()).thenReturn("testUser");
    Measure updatedMeasure = Measure.builder().id("testMeasureId").createdBy("testUser").build();
    Version updatedVersion = Version.builder().major(3).minor(0).revisionNumber(0).build();
    updatedMeasure.setVersion(updatedVersion);
    MeasureMetaData updatedMetaData = new MeasureMetaData();
    updatedMetaData.setDraft(false);
    updatedMeasure.setMeasureMetaData(updatedMetaData);

    when(versionService.checkValidVersioning(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(VersionService.VersionValidationResult.TEST_CASE_ERROR);

    ResponseEntity<Void> entity =
        measureVersionController.checkValidVersion(
            "testMeasureId", "MAJOR", principal, "accesstoken");
    assertThat(entity, is(notNullValue()));
    assertThat(entity.getStatusCode(), is(HttpStatus.ACCEPTED));
  }

  @Test
  public void testCheckValidVersioningSuccess() {
    when(principal.getName()).thenReturn("testUser");
    Measure updatedMeasure = Measure.builder().id("testMeasureId").createdBy("testUser").build();
    Version updatedVersion = Version.builder().major(3).minor(0).revisionNumber(0).build();
    updatedMeasure.setVersion(updatedVersion);
    MeasureMetaData updatedMetaData = new MeasureMetaData();
    updatedMetaData.setDraft(false);
    updatedMeasure.setMeasureMetaData(updatedMetaData);

    when(versionService.checkValidVersioning(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(VersionService.VersionValidationResult.VALID);

    ResponseEntity<Void> response =
        measureVersionController.checkValidVersion(
            "testMeasureId", "MAJOR", principal, "accesstoken");
    assertThat(response, is(notNullValue()));
    assertThat(response.getStatusCode(), is(HttpStatus.OK));
  }

  @Test
  public void testCreateDraftSuccessfully() {
    when(principal.getName()).thenReturn("testUser");
    measure.setMeasureName("Test");
    measure.setModel("QI-Core v4.1.1");
    when(versionService.createDraft(
            anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(measure);

    ResponseEntity<Measure> entity =
        measureVersionController.createDraft(TEST_ACCESS_TOKEN, "12", measure, principal);
    assertThat(entity.getStatusCode(), is(HttpStatus.CREATED));
    assertThat(entity.getBody(), is(equalTo(measure)));
  }

  @Test
  public void testCreateDraftEmptyMeasure() {
    Exception ex =
        assertThrows(
            InvalidIdException.class,
            () ->
                measureVersionController.createDraft(TEST_ACCESS_TOKEN, "12", measure, principal));

    assertThat(ex.getMessage(), is(equalTo("Measure name is required.")));
  }
}
