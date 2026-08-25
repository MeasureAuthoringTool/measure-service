package cms.gov.madie.measure.resources;

import static cms.gov.madie.measure.constants.BundleTypeConstants.CALCULATION;

import cms.gov.madie.measure.exceptions.BundleOperationException;
import cms.gov.madie.measure.exceptions.CqlElmTranslationErrorException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.BundleService;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureScoring;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BundleControllerTest {

  @Mock private MeasureRepository measureRepository;

  @Mock private BundleService bundleService;

  @InjectMocks private BundleController bundleController;

  @Test
  void testBundleMeasureThrowsNotFoundException() {
    Principal principal = mock(Principal.class);
    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            bundleController.getMeasureBundle(
                "MeasureID", principal, "Bearer TOKEN", CALCULATION, "Info"));
  }

  @Test
  void testBundleMeasureForSharedUsers() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user2");
    final String json = "{\"message\": \"GOOD JSON\"}";
    var acl = new AclSpecification();
    acl.setUserId("test.user2");
    acl.setRoles(Set.of(RoleEnum.SHARED_WITH));
    final Measure measure = Measure.builder().createdBy("test.user").build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(bundleService.bundleMeasure(any(Measure.class), anyString(), anyString(), anyString()))
        .thenReturn(json);
    ResponseEntity<String> output =
        bundleController.getMeasureBundle(
            "MeasureID", principal, "Bearer TOKEN", CALCULATION, "Info");
    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.OK)));
    assertThat(output.getBody(), is(equalTo(json)));
  }

  @Test
  void testBundleMeasureThrowsOperationException() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    final String elmJson = "{\"text\": \"ELM JSON\"}";
    final Measure measure =
        Measure.builder()
            .createdBy("test.user")
            .groups(
                List.of(
                    Group.builder()
                        .groupDescription("Group1")
                        .scoring(MeasureScoring.RATIO.toString())
                        .build()))
            .elmJson(elmJson)
            .build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(bundleService.bundleMeasure(any(Measure.class), anyString(), anyString(), anyString()))
        .thenThrow(
            new BundleOperationException("Measure", "MeasureID", new RuntimeException("cause")));
    assertThrows(
        BundleOperationException.class,
        () ->
            bundleController.getMeasureBundle(
                "MeasureID", principal, "Bearer TOKEN", CALCULATION, "Info"));
  }

  @Test
  void testBundleMeasureThrowsElmTranslationException() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    final String elmJson = "{\"text\": \"ELM JSON\"}";
    final Measure measure =
        Measure.builder()
            .createdBy("test.user")
            .groups(
                List.of(
                    Group.builder()
                        .groupDescription("Group1")
                        .scoring(MeasureScoring.RATIO.toString())
                        .build()))
            .elmJson(elmJson)
            .build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(bundleService.bundleMeasure(any(Measure.class), anyString(), anyString(), anyString()))
        .thenThrow(new CqlElmTranslationErrorException(measure.getMeasureName()));
    assertThrows(
        CqlElmTranslationErrorException.class,
        () ->
            bundleController.getMeasureBundle(
                "MeasureID", principal, "Bearer TOKEN", CALCULATION, "Info"));
  }

  @Test
  void testBundleMeasureReturnsBundleString() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    final String elmJson = "{\"text\": \"ELM JSON\"}";
    final String json = "{\"message\": \"GOOD JSON\"}";
    final Measure measure =
        Measure.builder()
            .createdBy("test.user")
            .groups(
                List.of(
                    Group.builder()
                        .groupDescription("Group1")
                        .scoring(MeasureScoring.RATIO.toString())
                        .build()))
            .elmJson(elmJson)
            .build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(bundleService.bundleMeasure(any(Measure.class), anyString(), anyString(), anyString()))
        .thenReturn(json);
    ResponseEntity<String> output =
        bundleController.getMeasureBundle(
            "MeasureID", principal, "Bearer TOKEN", CALCULATION, "Info");
    assertThat(output, is(notNullValue()));
    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.OK)));
    assertThat(output.getBody(), is(equalTo(json)));
  }
}
