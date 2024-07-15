package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cms.gov.madie.measure.exceptions.CqmConversionException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import gov.cms.madie.models.cqm.datacriteria.AdverseEvent;
import gov.cms.madie.models.cqm.datacriteria.AllergyIntolerance;
import gov.cms.madie.models.cqm.datacriteria.AssessmentOrder;
import gov.cms.madie.models.cqm.datacriteria.AssessmentPerformed;
import gov.cms.madie.models.cqm.datacriteria.AssessmentRecommended;
import gov.cms.madie.models.cqm.datacriteria.CareGoal;
import gov.cms.madie.models.cqm.datacriteria.CommunicationPerformed;
import gov.cms.madie.models.cqm.datacriteria.DeviceOrder;
import gov.cms.madie.models.cqm.datacriteria.DeviceRecommended;
import gov.cms.madie.models.cqm.datacriteria.Diagnosis;
import gov.cms.madie.models.cqm.datacriteria.DiagnosticStudyOrder;
import gov.cms.madie.models.cqm.datacriteria.DiagnosticStudyPerformed;
import gov.cms.madie.models.cqm.datacriteria.DiagnosticStudyRecommended;
import gov.cms.madie.models.cqm.datacriteria.EncounterOrder;
import gov.cms.madie.models.cqm.datacriteria.EncounterPerformed;
import gov.cms.madie.models.cqm.datacriteria.EncounterRecommended;
import gov.cms.madie.models.cqm.datacriteria.FamilyHistory;
import gov.cms.madie.models.cqm.datacriteria.ImmunizationAdministered;
import gov.cms.madie.models.cqm.datacriteria.ImmunizationOrder;
import gov.cms.madie.models.cqm.datacriteria.InterventionOrder;
import gov.cms.madie.models.cqm.datacriteria.InterventionPerformed;
import gov.cms.madie.models.cqm.datacriteria.InterventionRecommended;
import gov.cms.madie.models.cqm.datacriteria.LaboratoryTestOrder;
import gov.cms.madie.models.cqm.datacriteria.LaboratoryTestPerformed;
import gov.cms.madie.models.cqm.datacriteria.LaboratoryTestRecommended;
import gov.cms.madie.models.cqm.datacriteria.MedicationActive;
import gov.cms.madie.models.cqm.datacriteria.MedicationAdministered;
import gov.cms.madie.models.cqm.datacriteria.MedicationDischarge;
import gov.cms.madie.models.cqm.datacriteria.MedicationDispensed;
import gov.cms.madie.models.cqm.datacriteria.MedicationOrder;
import gov.cms.madie.models.cqm.datacriteria.Participation;
import gov.cms.madie.models.cqm.datacriteria.PatientCareExperience;
import gov.cms.madie.models.cqm.datacriteria.PatientCharacteristic;
import gov.cms.madie.models.cqm.datacriteria.PatientCharacteristicBirthdate;
import gov.cms.madie.models.cqm.datacriteria.PatientCharacteristicClinicalTrialParticipant;
import gov.cms.madie.models.cqm.datacriteria.PatientCharacteristicExpired;
import gov.cms.madie.models.cqm.datacriteria.PatientCharacteristicPayer;
import gov.cms.madie.models.cqm.datacriteria.PhysicalExamOrder;
import gov.cms.madie.models.cqm.datacriteria.PhysicalExamPerformed;
import gov.cms.madie.models.cqm.datacriteria.PhysicalExamRecommended;
import gov.cms.madie.models.cqm.datacriteria.ProcedureOrder;
import gov.cms.madie.models.cqm.datacriteria.ProcedurePerformed;
import gov.cms.madie.models.cqm.datacriteria.ProcedureRecommended;
import gov.cms.madie.models.cqm.datacriteria.ProviderCareExperience;
import gov.cms.madie.models.cqm.datacriteria.SubstanceAdministered;
import gov.cms.madie.models.cqm.datacriteria.SubstanceOrder;
import gov.cms.madie.models.cqm.datacriteria.SubstanceRecommended;
import gov.cms.madie.models.cqm.datacriteria.Symptom;
import gov.cms.madie.models.cqm.datacriteria.basetypes.DataElement;
import gov.cms.madie.models.cqm.datacriteria.basetypes.Interval;
import gov.cms.madie.models.measure.TestCase;

@ExtendWith(MockitoExtension.class)
public class TestCaseShiftDatesServiceTest {
  @Mock private TestCaseService testCaseService;
  @InjectMocks private TestCaseShiftDatesService testCaseShiftDatesService;

  private TestCase testCase;
  private static final String json =
      "{\"qdmVersion\":\"5.6\",\"dataElements\":[{\"dataElementCodes\":[{\"code\":\"14463-4\",\"system\":\"2.16.840.1.113883.6.1\",\"version\":null,\"display\":\"Chlamydia trachomatis [Presence] in Cervix by Organism specific culture\"}],\"_id\":\"666b3dda1d026b000017e20b\",\"performer\":[],\"relatedTo\":[],\"qdmTitle\":\"Laboratory Test, Performed\",\"hqmfOid\":\"2.16.840.1.113883.10.20.28.4.42\",\"qdmCategory\":\"laboratory_test\",\"qdmStatus\":\"performed\",\"qdmVersion\":\"5.6\",\"_type\":\"QDM::LaboratoryTestPerformed\",\"description\":\"Laboratory Test, Performed: Chlamydia Screening\",\"codeListId\":\"2.16.840.1.113883.3.464.1003.110.12.1052\",\"id\":\"666b3dda1d026b000017e20a\",\"components\":[{\"qdmVersion\":\"5.6\",\"_type\":\"QDM::Component\",\"_id\":\"666b3e2e1d026b000017e28d\",\"code\":{\"code\":\"105604006\",\"system\":\"2.16.840.1.113883.6.96\",\"version\":null,\"display\":\"Deficiency of naturally occurring coagulation factor inhibitor (disorder)\"}}],\"relevantPeriod\":{\"low\":\"2024-02-29T00:00:00.000+00:00\",\"high\":\"2024-06-28T00:00:00.000+00:00\",\"lowClosed\":true,\"highClosed\":true},\"relevantDatetime\":\"2024-06-29T00:00:00.000+00:00\",\"authorDatetime\":\"2024-02-29T00:00:00.000+00:00\",\"resultDatetime\":\"2024-02-29T00:00:00.000+00:00\"}],\"_id\":\"66698bcec3b50c0000acc383\"}";
  private static final String dateTimeString = "2024-02-29T00:00:00.000Z";

  @BeforeEach
  public void setUp() {
    testCase = new TestCase();
    testCase.setId("TESTID");
    testCase.setJson(json);
  }

  @Test
  public void shiftTestCaseDates() {
    when(testCaseService.findTestCasesByMeasureId(anyString())).thenReturn(List.of(testCase));

    TestCase modified =
        testCaseShiftDatesService.shiftTestCaseDates(
            "TestMeasureId", "TESTID", 1, "test.user", "TOKEN");

    assertNotNull(modified);
    assertTrue(modified.getJson().contains("2025"));
  }

  @Test
  public void shiftTestCaseDatesNoResourceFound() {
    when(testCaseService.findTestCasesByMeasureId(anyString())).thenReturn(Collections.emptyList());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            testCaseShiftDatesService.shiftTestCaseDates(
                "TestMeasureId", "TESTID", 1, "test.user", "TOKEN"));
  }

  @Test
  public void shiftTestCaseDatesThrowsExceptionWhenTestCaseNotFound() {
    when(testCaseService.findTestCasesByMeasureId(anyString())).thenReturn(List.of(testCase));

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            testCaseShiftDatesService.shiftTestCaseDates(
                "TestMeasureId", "TestIdNotFound", 1, "test.user", "TOKEN"));
  }

  @Test
  public void shiftTestCaseDatesInvalidJson() {
    String jsonInvalid = "";
    testCase.setJson(jsonInvalid);
    when(testCaseService.findTestCasesByMeasureId(anyString())).thenReturn(List.of(testCase));

    assertThrows(
        CqmConversionException.class,
        () ->
            testCaseShiftDatesService.shiftTestCaseDates(
                "TestMeasureId", "TESTID", 1, "test.user", "TOKEN"));
  }

  @Test
  public void shiftTestCaseDatesNoDataElement() {
    String jsonInvalid =
        "{\n"
            + "  \"_id\" : \"66698bcec3b50c0000acc383\",\n"
            + "  \"qdmVersion\" : \"5.6\",\n"
            + "  \"dataElements\" : [ ]\n"
            + "}";
    testCase.setJson(jsonInvalid);
    when(testCaseService.findTestCasesByMeasureId(anyString())).thenReturn(List.of(testCase));

    TestCase modified =
        testCaseShiftDatesService.shiftTestCaseDates(
            "TestMeasureId", "TESTID", 1, "test.user", "TOKEN");

    assertNotNull(modified);
    assertFalse(modified.getJson().contains("2025"));
  }

  @Test
  public void shiftDatesAdverseEvent() {
    AdverseEvent adverseEvent = new AdverseEvent();
    adverseEvent.setAuthorDatetime(getZonedDateTime(dateTimeString));
    // adverseEvent.setRelevantDatetime(getZonedDateTime("2024-07-03T08:15:30.000+00.00"));
    adverseEvent.setRelevantDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(adverseEvent, 1);

    assertEquals(adverseEvent.getAuthorDatetime().getYear(), 2025);
    assertEquals(adverseEvent.getRelevantDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesAllergyIntolerance() {
    AllergyIntolerance allergyIntolerance = new AllergyIntolerance();
    Interval prevalencePeriod = new Interval();
    prevalencePeriod.setLow(getZonedDateTime(dateTimeString));
    prevalencePeriod.setHigh(getZonedDateTime(dateTimeString));
    allergyIntolerance.setAuthorDatetime(getZonedDateTime(dateTimeString));
    allergyIntolerance.setPrevalencePeriod(prevalencePeriod);

    testCaseShiftDatesService.shiftDates(allergyIntolerance, 1);

    assertEquals(allergyIntolerance.getAuthorDatetime().getYear(), 2025);
    assertEquals(allergyIntolerance.getPrevalencePeriod().getLow().getYear(), 2025);
    assertEquals(allergyIntolerance.getPrevalencePeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesAssessmentOrder() {
    AssessmentOrder assessmentOrder = new AssessmentOrder();
    assessmentOrder.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(assessmentOrder, 1);

    assertEquals(assessmentOrder.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesAssessmentPerformed() {
    AssessmentPerformed assessmentPerformed = new AssessmentPerformed();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    assessmentPerformed.setAuthorDatetime(getZonedDateTime(dateTimeString));
    assessmentPerformed.setRelevantDatetime(getZonedDateTime(dateTimeString));
    assessmentPerformed.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(assessmentPerformed, 1);

    assertEquals(assessmentPerformed.getAuthorDatetime().getYear(), 2025);
    assertEquals(assessmentPerformed.getRelevantDatetime().getYear(), 2025);
    assertEquals(assessmentPerformed.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(assessmentPerformed.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesAssessmentRecommended() {
    AssessmentRecommended assessmentRecommended = new AssessmentRecommended();
    assessmentRecommended.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(assessmentRecommended, 1);

    assertEquals(assessmentRecommended.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesCareGoal() {
    CareGoal careGoal = new CareGoal();
    careGoal.setStatusDate(getLocalDate("2024-07-03"));
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    careGoal.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(careGoal, 1);

    assertEquals(careGoal.getStatusDate().getYear(), 2025);
    assertEquals(careGoal.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(careGoal.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  private LocalDate getLocalDate(String strDate) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    return LocalDate.parse(strDate, formatter);
  }

  @Test
  public void shiftDatesCommunicationPerformed() {
    CommunicationPerformed communicationPerformed = new CommunicationPerformed();
    communicationPerformed.setAuthorDatetime(getZonedDateTime(dateTimeString));
    communicationPerformed.setSentDatetime(getZonedDateTime(dateTimeString));
    communicationPerformed.setReceivedDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(communicationPerformed, 1);

    assertEquals(communicationPerformed.getAuthorDatetime().getYear(), 2025);
    assertEquals(communicationPerformed.getSentDatetime().getYear(), 2025);
    assertEquals(communicationPerformed.getReceivedDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesDeviceOrder() {
    DeviceOrder deviceOrder = new DeviceOrder();
    deviceOrder.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(deviceOrder, 1);

    assertEquals(deviceOrder.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesDeviceRecommended() {
    DeviceRecommended deviceRecommended = new DeviceRecommended();
    deviceRecommended.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(deviceRecommended, 1);

    assertEquals(deviceRecommended.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesDiagnosis() {
    Diagnosis diagnosis = new Diagnosis();
    diagnosis.setAuthorDatetime(getZonedDateTime(dateTimeString));
    Interval prevalencePeriod = new Interval();
    prevalencePeriod.setLow(getZonedDateTime(dateTimeString));
    prevalencePeriod.setHigh(getZonedDateTime(dateTimeString));
    diagnosis.setPrevalencePeriod(prevalencePeriod);

    testCaseShiftDatesService.shiftDates(diagnosis, 1);

    assertEquals(diagnosis.getAuthorDatetime().getYear(), 2025);
    assertEquals(diagnosis.getPrevalencePeriod().getLow().getYear(), 2025);
    assertEquals(diagnosis.getPrevalencePeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesDiagnosticStudyOrder() {
    DiagnosticStudyOrder diagnosticStudyOrder = new DiagnosticStudyOrder();
    diagnosticStudyOrder.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(diagnosticStudyOrder, 1);

    assertEquals(diagnosticStudyOrder.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesDiagnosticStudyPerformed() {
    DiagnosticStudyPerformed diagnosticStudyPerformed = new DiagnosticStudyPerformed();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    diagnosticStudyPerformed.setAuthorDatetime(getZonedDateTime(dateTimeString));

    diagnosticStudyPerformed.setRelevantDatetime(getZonedDateTime(dateTimeString));
    diagnosticStudyPerformed.setRelevantPeriod(relevantPeriod);
    diagnosticStudyPerformed.setResultDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(diagnosticStudyPerformed, 1);

    assertEquals(diagnosticStudyPerformed.getAuthorDatetime().getYear(), 2025);
    assertEquals(diagnosticStudyPerformed.getRelevantDatetime().getYear(), 2025);
    assertEquals(diagnosticStudyPerformed.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(diagnosticStudyPerformed.getRelevantPeriod().getHigh().getYear(), 2025);
    assertEquals(diagnosticStudyPerformed.getResultDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesDiagnosticStudyRecommended() {
    DiagnosticStudyRecommended diagnosticStudyRecommended = new DiagnosticStudyRecommended();

    diagnosticStudyRecommended.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(diagnosticStudyRecommended, 1);

    assertEquals(diagnosticStudyRecommended.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesEncounterOrder() {
    EncounterOrder encounterOrder = new EncounterOrder();
    encounterOrder.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(encounterOrder, 1);

    assertEquals(encounterOrder.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesEncounterPerformed() {
    EncounterPerformed encounterPerformed = new EncounterPerformed();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    encounterPerformed.setAuthorDatetime(getZonedDateTime(dateTimeString));
    encounterPerformed.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(encounterPerformed, 1);

    assertEquals(encounterPerformed.getAuthorDatetime().getYear(), 2025);
    assertEquals(encounterPerformed.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(encounterPerformed.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesEncounterRecommended() {
    EncounterRecommended encounterRecommended = new EncounterRecommended();
    encounterRecommended.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(encounterRecommended, 1);

    assertEquals(encounterRecommended.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesFamilyHistory() {
    FamilyHistory familyHistory = new FamilyHistory();
    familyHistory.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(familyHistory, 1);

    assertEquals(familyHistory.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesImmunizationAdministered() {
    ImmunizationAdministered immunizationAdministered = new ImmunizationAdministered();
    immunizationAdministered.setAuthorDatetime(getZonedDateTime(dateTimeString));

    immunizationAdministered.setRelevantDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(immunizationAdministered, 1);

    assertEquals(immunizationAdministered.getAuthorDatetime().getYear(), 2025);
    assertEquals(immunizationAdministered.getRelevantDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesImmunizationOrder() {
    ImmunizationOrder immunizationOrder = new ImmunizationOrder();
    immunizationOrder.setAuthorDatetime(getZonedDateTime(dateTimeString));
    immunizationOrder.setActiveDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(immunizationOrder, 1);

    assertEquals(immunizationOrder.getAuthorDatetime().getYear(), 2025);
    assertEquals(immunizationOrder.getActiveDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesInterventionOrder() {
    InterventionOrder interventionOrder = new InterventionOrder();
    interventionOrder.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(interventionOrder, 1);

    assertEquals(interventionOrder.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesInterventionPerformed() {
    InterventionPerformed interventionPerformed = new InterventionPerformed();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    interventionPerformed.setAuthorDatetime(getZonedDateTime(dateTimeString));
    interventionPerformed.setRelevantDatetime(getZonedDateTime(dateTimeString));
    interventionPerformed.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(interventionPerformed, 1);

    assertEquals(interventionPerformed.getAuthorDatetime().getYear(), 2025);
    assertEquals(interventionPerformed.getRelevantDatetime().getYear(), 2025);
    assertEquals(interventionPerformed.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(interventionPerformed.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesInterventionRecommended() {
    InterventionRecommended interventionRecommended = new InterventionRecommended();
    interventionRecommended.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(interventionRecommended, 1);

    assertEquals(interventionRecommended.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesLaboratoryTestOrder() {
    LaboratoryTestOrder laboratoryTestOrder = new LaboratoryTestOrder();
    laboratoryTestOrder.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(laboratoryTestOrder, 1);

    assertEquals(laboratoryTestOrder.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesLaboratoryTestPerformed() {
    LaboratoryTestPerformed laboratoryTestPerformed = new LaboratoryTestPerformed();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    laboratoryTestPerformed.setAuthorDatetime(getZonedDateTime(dateTimeString));
    laboratoryTestPerformed.setRelevantDatetime(getZonedDateTime(dateTimeString));
    laboratoryTestPerformed.setRelevantPeriod(relevantPeriod);
    laboratoryTestPerformed.setResultDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(laboratoryTestPerformed, 1);

    assertEquals(laboratoryTestPerformed.getAuthorDatetime().getYear(), 2025);
    assertEquals(laboratoryTestPerformed.getRelevantDatetime().getYear(), 2025);
    assertEquals(laboratoryTestPerformed.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(laboratoryTestPerformed.getRelevantPeriod().getHigh().getYear(), 2025);
    assertEquals(laboratoryTestPerformed.getResultDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesLaboratoryTestRecommended() {
    LaboratoryTestRecommended laboratoryTestRecommended = new LaboratoryTestRecommended();
    laboratoryTestRecommended.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(laboratoryTestRecommended, 1);

    assertEquals(laboratoryTestRecommended.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesMedicationActive() {
    MedicationActive medicationActive = new MedicationActive();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    medicationActive.setRelevantDatetime(getZonedDateTime(dateTimeString));
    medicationActive.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(medicationActive, 1);

    assertEquals(medicationActive.getRelevantDatetime().getYear(), 2025);
    assertEquals(medicationActive.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(medicationActive.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesMedicationAdministered() {
    MedicationAdministered medicationAdministered = new MedicationAdministered();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    medicationAdministered.setAuthorDatetime(getZonedDateTime(dateTimeString));
    medicationAdministered.setRelevantDatetime(getZonedDateTime(dateTimeString));
    medicationAdministered.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(medicationAdministered, 1);

    assertEquals(medicationAdministered.getAuthorDatetime().getYear(), 2025);
    assertEquals(medicationAdministered.getRelevantDatetime().getYear(), 2025);
    assertEquals(medicationAdministered.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(medicationAdministered.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesMedicationDischarge() {
    MedicationDischarge medicationDischarge = new MedicationDischarge();
    medicationDischarge.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(medicationDischarge, 1);

    assertEquals(medicationDischarge.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesMedicationDispensed() {
    MedicationDispensed medicationDispensed = new MedicationDispensed();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    medicationDispensed.setAuthorDatetime(getZonedDateTime(dateTimeString));
    medicationDispensed.setRelevantDatetime(getZonedDateTime(dateTimeString));
    medicationDispensed.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(medicationDispensed, 1);

    assertEquals(medicationDispensed.getAuthorDatetime().getYear(), 2025);
    assertEquals(medicationDispensed.getRelevantDatetime().getYear(), 2025);
    assertEquals(medicationDispensed.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(medicationDispensed.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesMedicationOrder() {
    MedicationOrder medicationOrder = new MedicationOrder();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    medicationOrder.setAuthorDatetime(getZonedDateTime(dateTimeString));
    medicationOrder.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(medicationOrder, 1);

    assertEquals(medicationOrder.getAuthorDatetime().getYear(), 2025);
    assertEquals(medicationOrder.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(medicationOrder.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesParticipation() {
    Participation participation = new Participation();
    Interval participationPeriod = new Interval();
    participationPeriod.setLow(getZonedDateTime(dateTimeString));
    participationPeriod.setHigh(getZonedDateTime(dateTimeString));
    participation.setParticipationPeriod(participationPeriod);

    testCaseShiftDatesService.shiftDates(participation, 1);

    assertEquals(participation.getParticipationPeriod().getLow().getYear(), 2025);
    assertEquals(participation.getParticipationPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesPatientCareExperience() {
    PatientCareExperience patientCareExperience = new PatientCareExperience();
    patientCareExperience.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(patientCareExperience, 1);

    assertEquals(patientCareExperience.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesPatientCharacteristic() {
    PatientCharacteristic patientCharacteristic = new PatientCharacteristic();
    patientCharacteristic.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(patientCharacteristic, 1);

    assertEquals(patientCharacteristic.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesPatientCharacteristicBirthdate() {
    PatientCharacteristicBirthdate patientCharacteristicBirthdate =
        new PatientCharacteristicBirthdate();

    patientCharacteristicBirthdate.setBirthDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(patientCharacteristicBirthdate, 1);

    assertEquals(patientCharacteristicBirthdate.getBirthDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesPatientCharacteristicClinicalTrialParticipant() {
    PatientCharacteristicClinicalTrialParticipant patientCharacteristicClinicalTrialParticipant =
        new PatientCharacteristicClinicalTrialParticipant();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    patientCharacteristicClinicalTrialParticipant.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(patientCharacteristicClinicalTrialParticipant, 1);

    assertEquals(
        patientCharacteristicClinicalTrialParticipant.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(
        patientCharacteristicClinicalTrialParticipant.getRelevantPeriod().getHigh().getYear(),
        2025);
  }

  @Test
  public void shiftDatesPatientCharacteristicExpired() {
    PatientCharacteristicExpired patientCharacteristicExpired = new PatientCharacteristicExpired();

    patientCharacteristicExpired.setExpiredDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(patientCharacteristicExpired, 1);

    assertEquals(patientCharacteristicExpired.getExpiredDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesPatientCharacteristicPayer() {
    PatientCharacteristicPayer patientCharacteristicPayer = new PatientCharacteristicPayer();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    patientCharacteristicPayer.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(patientCharacteristicPayer, 1);

    assertEquals(patientCharacteristicPayer.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(patientCharacteristicPayer.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesPhysicalExamOrder() {
    PhysicalExamOrder physicalExamOrder = new PhysicalExamOrder();
    physicalExamOrder.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(physicalExamOrder, 1);

    assertEquals(physicalExamOrder.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesPhysicalExamPerformed() {
    PhysicalExamPerformed physicalExamPerformed = new PhysicalExamPerformed();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    physicalExamPerformed.setAuthorDatetime(getZonedDateTime(dateTimeString));
    physicalExamPerformed.setRelevantDatetime(getZonedDateTime(dateTimeString));
    physicalExamPerformed.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(physicalExamPerformed, 1);

    assertEquals(physicalExamPerformed.getAuthorDatetime().getYear(), 2025);
    assertEquals(physicalExamPerformed.getRelevantDatetime().getYear(), 2025);
    assertEquals(physicalExamPerformed.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(physicalExamPerformed.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesPhysicalExamRecommended() {
    PhysicalExamRecommended physicalExamRecommended = new PhysicalExamRecommended();
    physicalExamRecommended.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(physicalExamRecommended, 1);

    assertEquals(physicalExamRecommended.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesProcedureOrder() {
    ProcedureOrder procedureOrder = new ProcedureOrder();
    procedureOrder.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(procedureOrder, 1);

    assertEquals(procedureOrder.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesProcedurePerformed() {
    ProcedurePerformed procedurePerformed = new ProcedurePerformed();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    procedurePerformed.setAuthorDatetime(getZonedDateTime(dateTimeString));
    procedurePerformed.setRelevantDatetime(getZonedDateTime(dateTimeString));
    procedurePerformed.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(procedurePerformed, 1);

    assertEquals(procedurePerformed.getAuthorDatetime().getYear(), 2025);
    assertEquals(procedurePerformed.getRelevantDatetime().getYear(), 2025);
    assertEquals(procedurePerformed.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(procedurePerformed.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesProcedureRecommended() {
    ProcedureRecommended procedureRecommended = new ProcedureRecommended();
    procedureRecommended.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(procedureRecommended, 1);

    assertEquals(procedureRecommended.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesProviderCareExperience() {
    ProviderCareExperience providerCareExperience = new ProviderCareExperience();
    providerCareExperience.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(providerCareExperience, 1);

    assertEquals(providerCareExperience.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesSubstanceAdministered() {
    SubstanceAdministered substanceAdministered = new SubstanceAdministered();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    substanceAdministered.setAuthorDatetime(getZonedDateTime(dateTimeString));
    substanceAdministered.setRelevantDatetime(getZonedDateTime(dateTimeString));
    substanceAdministered.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(substanceAdministered, 1);

    assertEquals(substanceAdministered.getAuthorDatetime().getYear(), 2025);
    assertEquals(substanceAdministered.getRelevantDatetime().getYear(), 2025);
    assertEquals(substanceAdministered.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(substanceAdministered.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesSubstanceOrder() {
    SubstanceOrder substanceOrder = new SubstanceOrder();
    Interval relevantPeriod = new Interval();
    relevantPeriod.setLow(getZonedDateTime(dateTimeString));
    relevantPeriod.setHigh(getZonedDateTime(dateTimeString));
    substanceOrder.setAuthorDatetime(getZonedDateTime(dateTimeString));
    substanceOrder.setRelevantPeriod(relevantPeriod);

    testCaseShiftDatesService.shiftDates(substanceOrder, 1);

    assertEquals(substanceOrder.getAuthorDatetime().getYear(), 2025);
    assertEquals(substanceOrder.getRelevantPeriod().getLow().getYear(), 2025);
    assertEquals(substanceOrder.getRelevantPeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesSubstanceRecommended() {
    SubstanceRecommended substanceRecommended = new SubstanceRecommended();
    substanceRecommended.setAuthorDatetime(getZonedDateTime(dateTimeString));

    testCaseShiftDatesService.shiftDates(substanceRecommended, 1);

    assertEquals(substanceRecommended.getAuthorDatetime().getYear(), 2025);
  }

  @Test
  public void shiftDatesSymptom() {
    Symptom symptom = new Symptom();
    Interval prevalencePeriod = new Interval();
    prevalencePeriod.setLow(getZonedDateTime(dateTimeString));
    prevalencePeriod.setHigh(getZonedDateTime(dateTimeString));
    symptom.setPrevalencePeriod(prevalencePeriod);

    testCaseShiftDatesService.shiftDates(symptom, 1);

    assertEquals(symptom.getPrevalencePeriod().getLow().getYear(), 2025);
    assertEquals(symptom.getPrevalencePeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesUnsupportedDataType() {
    assertThrows(
        CqmConversionException.class,
        () -> testCaseShiftDatesService.shiftDates(new DataElement(), 1));
  }

  private ZonedDateTime getZonedDateTime(String dateTimeStr) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    return ZonedDateTime.parse(dateTimeStr, formatter);
  }

  @Test
  public void shiftAllTestCaseDates() {
    when(testCaseService.findTestCasesByMeasureId(anyString())).thenReturn(List.of(testCase));
    when(testCaseService.updateTestCase(any(TestCase.class), anyString(), anyString(), anyString()))
        .thenReturn(testCase);

    List<TestCase> modified =
        testCaseShiftDatesService.shiftAllTestCaseDates("TestMeasureId", 1, "test.user", "TOKEN");

    assertNotNull(modified);
    assertEquals(modified.size(), 1);
    assertTrue(modified.get(0).getJson().contains("2025"));
  }

  @Test
  public void shiftAllTestCaseDatesNoResourceFound() {
    when(testCaseService.findTestCasesByMeasureId(anyString())).thenReturn(Collections.emptyList());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            testCaseShiftDatesService.shiftAllTestCaseDates(
                "TestMeasureId", 1, "test.user", "TOKEN"));
  }

  @Test
  public void shiftDatesForTestCaseNoJson() {
    assertThrows(
        CqmConversionException.class,
        () ->
            testCaseShiftDatesService.shiftDatesForTestCase(
                TestCase.builder().id("testCaseId").build(), 1));
  }
}
