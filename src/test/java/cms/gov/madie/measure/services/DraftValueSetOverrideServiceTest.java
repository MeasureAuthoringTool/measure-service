package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.exceptions.InvalidIdException;
import cms.gov.madie.measure.exceptions.InvalidRequestException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import cms.gov.madie.measure.repositories.TestCasePatchRepository;
import gov.cms.madie.models.measure.DraftValueSetOverride;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCaseConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DraftValueSetOverrideServiceTest {

  private static final String MEASURE_ID = "measure-1";
  private static final String USERNAME = "test.user";
  private static final String HYPERTENSION_OID = "2.16.840.1.113883.3.666.5.1002";
  private static final String OFFICE_VISIT_OID = "2.16.840.1.113883.3.666.5.1003";
  private static final String VSAC_URL_PREFIX = "http://cts.nlm.nih.gov/fhir/ValueSet/";

  @InjectMocks private DraftValueSetOverrideService draftValueSetOverrideService;

  @Mock private MeasureService measureService;

  @Mock private TestCasePatchRepository testCasePatchRepository;

  @Captor private ArgumentCaptor<List<DraftValueSetOverride>> overridesCaptor;

  private Measure measure;

  @BeforeEach
  void setUp() {
    measure =
        Measure.builder()
            .id(MEASURE_ID)
            .measureName("Test Measure")
            .testCaseConfiguration(
                TestCaseConfiguration.builder()
                    .manifestExpansion(ManifestExpansion.builder().id("ecqm-update-2024").build())
                    .build())
            .build();
  }

  private DraftValueSetOverride override(String oid, String name) {
    return DraftValueSetOverride.builder().oid(oid).url(VSAC_URL_PREFIX + oid).name(name).build();
  }

  private void withExistingOverrides(DraftValueSetOverride... overrides) {
    measure
        .getTestCaseConfiguration()
        .setDraftValueSetOverrides(new ArrayList<>(List.of(overrides)));
  }

  private void expectSave() {
    when(testCasePatchRepository.findAndModifyDraftValueSetOverrides(anyList(), eq(MEASURE_ID)))
        .thenAnswer(
            invocation -> {
              List<DraftValueSetOverride> saved = invocation.getArgument(0);
              return Measure.builder()
                  .id(MEASURE_ID)
                  .testCaseConfiguration(
                      TestCaseConfiguration.builder()
                          .draftValueSetOverrides(new ArrayList<>(saved))
                          .build())
                  .build();
            });
  }

  @Test
  void getDraftValueSetOverridesReturnsPersistedList() {
    withExistingOverrides(override(HYPERTENSION_OID, "Hypertension"));
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);

    List<DraftValueSetOverride> overrides =
        draftValueSetOverrideService.getDraftValueSetOverrides(MEASURE_ID);

    assertEquals(1, overrides.size());
    assertEquals(HYPERTENSION_OID, overrides.get(0).getOid());
    assertEquals("Hypertension", overrides.get(0).getName());
  }

  @Test
  void getDraftValueSetOverridesReturnsEmptyListWhenNoneExist() {
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);
    assertTrue(draftValueSetOverrideService.getDraftValueSetOverrides(MEASURE_ID).isEmpty());
  }

  @Test
  void getDraftValueSetOverridesReturnsEmptyListWhenNoTestCaseConfiguration() {
    measure.setTestCaseConfiguration(null);
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);
    assertTrue(draftValueSetOverrideService.getDraftValueSetOverrides(MEASURE_ID).isEmpty());
  }

  @Test
  void getDraftValueSetOverridesThrowsWhenMeasureNotFound() {
    when(measureService.findActiveMeasureById(MEASURE_ID))
        .thenThrow(new ResourceNotFoundException("Measure", MEASURE_ID));
    assertThrows(
        ResourceNotFoundException.class,
        () -> draftValueSetOverrideService.getDraftValueSetOverrides(MEASURE_ID));
  }

  @Test
  void getDraftValueSetOverridesThrowsForBlankMeasureId() {
    assertThrows(
        InvalidIdException.class, () -> draftValueSetOverrideService.getDraftValueSetOverrides(""));
  }

  @Test
  void addDraftValueSetOverridePersistsValueSet() {
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);
    expectSave();

    List<DraftValueSetOverride> overrides =
        draftValueSetOverrideService.addDraftValueSetOverride(
            MEASURE_ID, override(HYPERTENSION_OID, "Hypertension"), USERNAME);

    verify(testCasePatchRepository)
        .findAndModifyDraftValueSetOverrides(overridesCaptor.capture(), eq(MEASURE_ID));
    assertEquals(1, overridesCaptor.getValue().size());
    assertEquals(HYPERTENSION_OID, overridesCaptor.getValue().get(0).getOid());
    assertEquals(VSAC_URL_PREFIX + HYPERTENSION_OID, overridesCaptor.getValue().get(0).getUrl());
    assertEquals("Hypertension", overridesCaptor.getValue().get(0).getName());
    assertEquals(1, overrides.size());
    assertEquals(HYPERTENSION_OID, overrides.get(0).getOid());
  }

  @Test
  void addDraftValueSetOverrideKeepsExistingOverrides() {
    withExistingOverrides(override(HYPERTENSION_OID, "Hypertension"));
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);
    expectSave();

    List<DraftValueSetOverride> overrides =
        draftValueSetOverrideService.addDraftValueSetOverride(
            MEASURE_ID, override(OFFICE_VISIT_OID, "Office Visit"), USERNAME);

    assertEquals(2, overrides.size());
    assertEquals(HYPERTENSION_OID, overrides.get(0).getOid());
    assertEquals(OFFICE_VISIT_OID, overrides.get(1).getOid());
  }

  @Test
  void addDraftValueSetOverrideTrimsAndStripsUrnOidPrefix() {
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);
    expectSave();

    List<DraftValueSetOverride> overrides =
        draftValueSetOverrideService.addDraftValueSetOverride(
            MEASURE_ID,
            DraftValueSetOverride.builder()
                .oid("  urn:oid:" + HYPERTENSION_OID + "  ")
                .name("  Hypertension  ")
                .build(),
            USERNAME);

    assertEquals(HYPERTENSION_OID, overrides.get(0).getOid());
    assertEquals("Hypertension", overrides.get(0).getName());
  }

  @Test
  void addDraftValueSetOverrideDoesNotCreateDuplicate() {
    withExistingOverrides(override(HYPERTENSION_OID, "Hypertension"));
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);

    List<DraftValueSetOverride> overrides =
        draftValueSetOverrideService.addDraftValueSetOverride(
            MEASURE_ID, override(HYPERTENSION_OID, "Hypertension"), USERNAME);

    assertEquals(1, overrides.size());
    verify(testCasePatchRepository, never())
        .findAndModifyDraftValueSetOverrides(anyList(), anyString());
  }

  @Test
  void addDraftValueSetOverrideDoesNotCreateDuplicateWhenIdentifiedByUrl() {
    withExistingOverrides(DraftValueSetOverride.builder().oid(HYPERTENSION_OID).build());
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);

    List<DraftValueSetOverride> overrides =
        draftValueSetOverrideService.addDraftValueSetOverride(
            MEASURE_ID,
            DraftValueSetOverride.builder().url(VSAC_URL_PREFIX + HYPERTENSION_OID).build(),
            USERNAME);

    assertEquals(1, overrides.size());
    verify(testCasePatchRepository, never())
        .findAndModifyDraftValueSetOverrides(anyList(), anyString());
  }

  @Test
  void addDraftValueSetOverrideThrowsWhenValueSetIsNotIdentified() {
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);

    assertThrows(
        InvalidRequestException.class,
        () ->
            draftValueSetOverrideService.addDraftValueSetOverride(
                MEASURE_ID,
                DraftValueSetOverride.builder().name("Hypertension").build(),
                USERNAME));
    verify(testCasePatchRepository, never())
        .findAndModifyDraftValueSetOverrides(anyList(), anyString());
  }

  @Test
  void addDraftValueSetOverrideThrowsWhenOverrideIsNull() {
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);

    assertThrows(
        InvalidRequestException.class,
        () -> draftValueSetOverrideService.addDraftValueSetOverride(MEASURE_ID, null, USERNAME));
  }

  @Test
  void addDraftValueSetOverrideThrowsWhenUserIsNotAuthorized() {
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);
    doThrow(new UnauthorizedException("Measure", MEASURE_ID, USERNAME))
        .when(measureService)
        .verifyAuthorization(USERNAME, measure);

    assertThrows(
        UnauthorizedException.class,
        () ->
            draftValueSetOverrideService.addDraftValueSetOverride(
                MEASURE_ID, override(HYPERTENSION_OID, "Hypertension"), USERNAME));
    verify(testCasePatchRepository, never())
        .findAndModifyDraftValueSetOverrides(anyList(), anyString());
  }

  @Test
  void removeDraftValueSetOverrideRemovesValueSet() {
    withExistingOverrides(
        override(HYPERTENSION_OID, "Hypertension"), override(OFFICE_VISIT_OID, "Office Visit"));
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);
    expectSave();

    List<DraftValueSetOverride> overrides =
        draftValueSetOverrideService.removeDraftValueSetOverride(
            MEASURE_ID, HYPERTENSION_OID, null, USERNAME);

    verify(testCasePatchRepository)
        .findAndModifyDraftValueSetOverrides(overridesCaptor.capture(), eq(MEASURE_ID));
    assertEquals(1, overridesCaptor.getValue().size());
    assertEquals(OFFICE_VISIT_OID, overridesCaptor.getValue().get(0).getOid());
    assertEquals(1, overrides.size());
    assertEquals(OFFICE_VISIT_OID, overrides.get(0).getOid());
  }

  @Test
  void removeDraftValueSetOverrideRemovesValueSetIdentifiedByUrl() {
    withExistingOverrides(DraftValueSetOverride.builder().oid(HYPERTENSION_OID).build());
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);
    expectSave();

    List<DraftValueSetOverride> overrides =
        draftValueSetOverrideService.removeDraftValueSetOverride(
            MEASURE_ID, null, VSAC_URL_PREFIX + HYPERTENSION_OID, USERNAME);

    assertTrue(overrides.isEmpty());
  }

  @Test
  void removeDraftValueSetOverrideLeavesListUnchangedWhenValueSetIsNotOnIt() {
    withExistingOverrides(override(HYPERTENSION_OID, "Hypertension"));
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);

    List<DraftValueSetOverride> overrides =
        draftValueSetOverrideService.removeDraftValueSetOverride(
            MEASURE_ID, OFFICE_VISIT_OID, null, USERNAME);

    assertEquals(1, overrides.size());
    verify(testCasePatchRepository, never())
        .findAndModifyDraftValueSetOverrides(anyList(), anyString());
  }

  @Test
  void removeDraftValueSetOverrideThrowsWhenValueSetIsNotIdentified() {
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);

    assertThrows(
        InvalidRequestException.class,
        () ->
            draftValueSetOverrideService.removeDraftValueSetOverride(
                MEASURE_ID, "  ", null, USERNAME));
  }

  @Test
  void removeDraftValueSetOverrideThrowsWhenUserIsNotAuthorized() {
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);
    doThrow(new UnauthorizedException("Measure", MEASURE_ID, USERNAME))
        .when(measureService)
        .verifyAuthorization(USERNAME, measure);

    assertThrows(
        UnauthorizedException.class,
        () ->
            draftValueSetOverrideService.removeDraftValueSetOverride(
                MEASURE_ID, HYPERTENSION_OID, null, USERNAME));
  }

  @Test
  void savedListIsReturnedWhenMeasureIsNotReturnedByThePatch() {
    when(measureService.findActiveMeasureById(MEASURE_ID)).thenReturn(measure);
    when(testCasePatchRepository.findAndModifyDraftValueSetOverrides(any(), eq(MEASURE_ID)))
        .thenReturn(null);

    List<DraftValueSetOverride> overrides =
        draftValueSetOverrideService.addDraftValueSetOverride(
            MEASURE_ID, override(HYPERTENSION_OID, "Hypertension"), USERNAME);

    assertEquals(1, overrides.size());
  }
}
