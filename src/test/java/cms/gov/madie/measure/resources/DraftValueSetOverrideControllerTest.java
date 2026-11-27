package cms.gov.madie.measure.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.exceptions.LockNotObtainedException;
import cms.gov.madie.measure.services.ActionLogService;
import cms.gov.madie.measure.services.AppConfigService;
import cms.gov.madie.measure.services.DraftValueSetOverrideService;
import cms.gov.madie.measure.services.MeasureService;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.measure.DraftValueSetOverride;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureLock;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DraftValueSetOverrideControllerTest {

  private static final String MEASURE_ID = "measure-1";
  private static final String USERNAME = "test.user";
  private static final String HYPERTENSION_OID = "2.16.840.1.113883.3.666.5.1002";

  @InjectMocks private DraftValueSetOverrideController controller;

  @Mock private DraftValueSetOverrideService draftValueSetOverrideService;

  @Mock private MeasureService measureService;

  @Mock private ActionLogService actionLogService;

  @Mock private AppConfigService appConfigService;

  private Principal principal;
  private Measure measure;
  private DraftValueSetOverride override;

  @BeforeEach
  void setUp() {
    principal = mock(Principal.class);
    measure = Measure.builder().id(MEASURE_ID).measureName("Test Measure").build();
    override = DraftValueSetOverride.builder().oid(HYPERTENSION_OID).name("Hypertension").build();
  }

  @Test
  void getDraftValueSetOverridesReturnsList() {
    when(principal.getName()).thenReturn(USERNAME);
    when(draftValueSetOverrideService.getDraftValueSetOverrides(MEASURE_ID))
        .thenReturn(List.of(override));

    ResponseEntity<List<DraftValueSetOverride>> response =
        controller.getDraftValueSetOverrides(MEASURE_ID, principal);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().size());
    assertEquals(HYPERTENSION_OID, response.getBody().get(0).getOid());
  }

  @Test
  void addDraftValueSetOverrideReturnsCreatedAndLogsAction() {
    when(principal.getName()).thenReturn("Test.User");
    when(measureService.findMeasureById(MEASURE_ID)).thenReturn(measure);
    when(draftValueSetOverrideService.addDraftValueSetOverride(MEASURE_ID, override, USERNAME))
        .thenReturn(List.of(override));

    ResponseEntity<List<DraftValueSetOverride>> response =
        controller.addDraftValueSetOverride(MEASURE_ID, override, principal);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(1, response.getBody().size());
    verify(actionLogService)
        .logAction(eq(MEASURE_ID), eq(Measure.class), eq(ActionType.UPDATED), eq(USERNAME));
  }

  @Test
  void addDraftValueSetOverrideRejectsMeasureLockedByAnotherUser() {
    when(principal.getName()).thenReturn(USERNAME);
    measure.setMeasureLock(MeasureLock.builder().lockedBy("another.user").build());
    when(measureService.findMeasureById(MEASURE_ID)).thenReturn(measure);

    assertThrows(
        LockNotObtainedException.class,
        () -> controller.addDraftValueSetOverride(MEASURE_ID, override, principal));
    verify(draftValueSetOverrideService, never())
        .addDraftValueSetOverride(anyString(), any(DraftValueSetOverride.class), anyString());
    verify(actionLogService, never()).logAction(anyString(), any(), any(), anyString());
  }

  @Test
  void removeDraftValueSetOverrideReturnsRemainingOverridesAndLogsAction() {
    when(principal.getName()).thenReturn(USERNAME);
    when(measureService.findMeasureById(MEASURE_ID)).thenReturn(measure);
    when(draftValueSetOverrideService.removeDraftValueSetOverride(
            MEASURE_ID, HYPERTENSION_OID, null, USERNAME))
        .thenReturn(List.of());

    ResponseEntity<List<DraftValueSetOverride>> response =
        controller.removeDraftValueSetOverride(MEASURE_ID, HYPERTENSION_OID, null, principal);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(0, response.getBody().size());
    verify(actionLogService)
        .logAction(eq(MEASURE_ID), eq(Measure.class), eq(ActionType.UPDATED), eq(USERNAME));
  }

  @Test
  void removeDraftValueSetOverrideRejectsMeasureLockedByAnotherUser() {
    when(principal.getName()).thenReturn(USERNAME);
    measure.setMeasureLock(MeasureLock.builder().lockedBy("another.user").build());
    when(measureService.findMeasureById(MEASURE_ID)).thenReturn(measure);

    assertThrows(
        LockNotObtainedException.class,
        () ->
            controller.removeDraftValueSetOverride(MEASURE_ID, HYPERTENSION_OID, null, principal));
    verify(draftValueSetOverrideService, never())
        .removeDraftValueSetOverride(anyString(), any(), any(), anyString());
  }
}
