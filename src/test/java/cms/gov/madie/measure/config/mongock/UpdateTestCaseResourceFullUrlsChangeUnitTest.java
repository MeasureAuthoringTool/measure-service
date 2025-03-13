package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.TestCaseService;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateTestCaseResourceFullUrlsChangeUnitTest {

  final String json =
      "{\"id\":\"632334c2414ba67d4e1d1c32\",\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[{\"fullUrl\":\"https://madie.cms.gov/Patient/0e3be52f-723e-4df4-a584-337daa19e259\",\"resource\":{\"id\":\"0e3be52f-723e-4df4-a584-337daa19e259\",\"meta\":{\"profile\":[\"http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-patient\"]},\"resourceType\":\"Patient\",\"extension\":[{\"extension\":[{\"url\":\"ombCategory\",\"valueCoding\":{\"system\":\"urn:oid:2.16.840.1.113883.6.238\",\"code\":\"2076-8\",\"display\":\"Native Hawaiian or Other Pacific Islander\",\"userSelected\":true}},{\"url\":\"text\",\"valueString\":\"Native Hawaiian or Other Pacific Islander\"}],\"url\":\"http://hl7.org/fhir/us/core/StructureDefinition/us-core-race\"},{\"extension\":[{\"url\":\"ombCategory\",\"valueCoding\":{\"system\":\"urn:oid:2.16.840.1.113883.6.238\",\"code\":\"2135-2\",\"display\":\"Hispanic or Latino\",\"userSelected\":true}},{\"url\":\"text\",\"valueString\":\"Hispanic or Latino\"}],\"url\":\"http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity\"}],\"identifier\":[{\"type\":{\"coding\":[{\"system\":\"http://terminology.hl7.org/CodeSystem/v2-0203\",\"code\":\"MR\"}]},\"system\":\"https://bonnie-fhir.healthit.gov/\",\"value\":\"632334c2414ba67d4e1d1c32\"}],\"name\":[{\"family\":\"DENEXPass\",\"given\":[\"HospiceCareReferral\"]}],\"gender\":\"female\",\"birthDate\":\"2005-12-31\"}},{\"fullUrl\":\"Encounter/encounter-inpatient-1c2a\",\"resource\":{\"id\":\"encounter-inpatient-1c2a\",\"resourceType\":\"Encounter\",\"meta\":{\"profile\":[\"http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-encounter\"]},\"status\":\"finished\",\"class\":{\"system\":\"http://terminology.hl7.org/CodeSystem/v3-ActCode\",\"code\":\"IMP\",\"display\":\"inpatient\"},\"type\":[{\"coding\":[{\"system\":\"http://snomed.info/sct\",\"version\":\"2022-03\",\"code\":\"183452005\",\"display\":\"Emergency hospital admission (procedure)\",\"userSelected\":true}]}],\"period\":{\"start\":\"2024-01-01T00:01:00.000+00:00\",\"end\":\"2024-01-02T08:30:00.000+00:00\"},\"hospitalization\":{\"dischargeDisposition\":{\"coding\":[{\"system\":\"http://snomed.info/sct\",\"code\":\"183919006\",\"display\":\"Urgent admission to hospice (procedure)\",\"userSelected\":true}]}},\"subject\":{\"reference\":\"Patient/0e3be52f-723e-4df4-a584-337daa19e259\"}}},{\"fullUrl\":\"MedicationRequest1/schedule-ii-iii-opioid-medications-1c2b\",\"resource\":{\"id\":\"schedule-ii-iii-opioid-medications-1c2b\",\"resourceType\":\"MedicationRequest\",\"meta\":{\"profile\":[\"http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-medicationrequest\"]},\"status\":\"active\",\"intent\":\"order\",\"doNotPerform\":false,\"category\":[{\"coding\":[{\"system\":\"http://terminology.hl7.org/CodeSystem/medicationrequest-category\",\"code\":\"discharge\",\"display\":\"Discharge\",\"userSelected\":true}]}],\"medicationCodeableConcept\":{\"coding\":[{\"system\":\"http://www.nlm.nih.gov/research/umls/rxnorm\",\"code\":\"1014599\",\"display\":\"acetaminophen 300 MG / oxycodone hydrochloride 10 MG Oral Tablet\",\"userSelected\":true}]},\"authoredOn\":\"2024-01-02T08:30:00.000+00:00\",\"requester\":{\"reference\":\"Practitioner/f007\",\"display\":\"Patrick Pump\"},\"subject\":{\"reference\":\"Patient/0e3be52f-723e-4df4-a584-337daa19e259\"}}},{\"fullUrl\":\"Coverage/1\",\"resource\":{\"resourceType\":\"Coverage\",\"beneficiary\":{\"reference\":\"Patient/0e3be52f-723e-4df4-a584-337daa19e259\"},\"id\":\"1\",\"meta\":{\"profile\":[\"http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-coverage\"]},\"payor\":[{\"reference\":\"Organization/123456\"}],\"status\":\"active\"}},{\"fullUrl\":\"Organization/123456\",\"resource\":{\"resourceType\":\"Organization\",\"active\":true,\"address\":[{\"use\":\"billing\",\"type\":\"postal\",\"line\":[\"P.O. Box 660044\"],\"city\":\"Dallas\",\"state\":\"TX\",\"postalCode\":\"75266-0044\",\"country\":\"USA\"}],\"id\":\"123456\",\"identifier\":[{\"use\":\"temp\",\"system\":\"urn:oid:2.16.840.1.113883.4.4\",\"value\":\"21-3259825\"}],\"meta\":{\"profile\":[\"http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-organization\"]},\"name\":\"Blue Cross Blue Shield of Texas\",\"telecom\":[{\"system\":\"phone\",\"value\":\"(+1) 972-766-6900\"}],\"type\":[{\"coding\":[{\"system\":\"http://terminology.hl7.org/CodeSystem/organization-type\",\"code\":\"pay\",\"display\":\"Payer\"}]}]}}]}";
  final String json2 =
      "{\"id\":\"6323489059967e30c06d0774\",\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[{\"fullUrl\":\"https://madie.cms.gov/Patient/5c316557-a562-48e4-94fd-76c02ddc388f\",\"resource\":{\"id\":\"5c316557-a562-48e4-94fd-76c02ddc388f\",\"meta\":{\"profile\":[\"http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-patient\"]},\"resourceType\":\"Patient\",\"extension\":[{\"extension\":[{\"url\":\"ombCategory\",\"valueCoding\":{\"system\":\"urn:oid:2.16.840.1.113883.6.238\",\"code\":\"1002-5\",\"display\":\"American Indian or Alaska Native\",\"userSelected\":true}},{\"url\":\"text\",\"valueString\":\"American Indian or Alaska Native\"}],\"url\":\"http://hl7.org/fhir/us/core/StructureDefinition/us-core-race\"},{\"extension\":[{\"url\":\"ombCategory\",\"valueCoding\":{\"system\":\"urn:oid:2.16.840.1.113883.6.238\",\"code\":\"2135-2\",\"display\":\"Hispanic or Latino\",\"userSelected\":true}},{\"url\":\"text\",\"valueString\":\"Hispanic or Latino\"}],\"url\":\"http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity\"}],\"identifier\":[{\"type\":{\"coding\":[{\"system\":\"http://terminology.hl7.org/CodeSystem/v2-0203\",\"code\":\"MR\"}]},\"system\":\"https://bonnie-fhir.healthit.gov/\",\"value\":\"6323489059967e30c06d0774\"}],\"name\":[{\"family\":\"NUMERPass\",\"given\":[\"2OpioidsAtDischarge\"]}],\"gender\":\"male\",\"birthDate\":\"2005-09-15\"}},{\"fullUrl\":\"Encounter/encounter-inpatient-53ef\",\"resource\":{\"id\":\"encounter-inpatient-53ef\",\"resourceType\":\"Encounter\",\"meta\":{\"profile\":[\"http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-encounter\"]},\"status\":\"finished\",\"class\":{\"system\":\"http://terminology.hl7.org/CodeSystem/v3-ActCode\",\"code\":\"IMP\",\"display\":\"inpatient\"},\"type\":[{\"coding\":[{\"system\":\"http://snomed.info/sct\",\"version\":\"2022-03\",\"code\":\"183452005\",\"display\":\"Emergency hospital admission (procedure)\",\"userSelected\":true}]}],\"period\":{\"start\":\"2024-01-01T00:00:00.000+00:00\",\"end\":\"2024-01-03T00:20:00.000+00:00\"},\"subject\":{\"reference\":\"Patient/5c316557-a562-48e4-94fd-76c02ddc388f\"}}},{\"fullUrl\":\"MedicationRequest/schedule-ii-iii-opioid-medications-53f1\",\"resource\":{\"id\":\"schedule-ii-iii-opioid-medications-53f1\",\"resourceType\":\"MedicationRequest\",\"meta\":{\"profile\":[\"http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-medicationrequest\"]},\"status\":\"active\",\"intent\":\"order\",\"doNotPerform\":false,\"category\":[{\"coding\":[{\"system\":\"http://terminology.hl7.org/CodeSystem/medicationrequest-category\",\"code\":\"discharge\",\"display\":\"Discharge\",\"userSelected\":true}]}],\"medicationCodeableConcept\":{\"coding\":[{\"system\":\"http://www.nlm.nih.gov/research/umls/rxnorm\",\"version\":\"2022-08\",\"code\":\"1014632\",\"display\":\"acetaminophen 300 MG / oxycodone hydrochloride 7.5 MG Oral Tablet\",\"userSelected\":true}]},\"authoredOn\":\"2024-01-03T00:15:00.000+00:00\",\"requester\":{\"reference\":\"Practitioner/f007\",\"display\":\"Patrick Pump\"},\"subject\":{\"reference\":\"Patient/5c316557-a562-48e4-94fd-76c02ddc388f\"}}},{\"fullUrl\":\"MedicationRequest/schedule-ii-iii-opioid-medications-0775\",\"resource\":{\"id\":\"schedule-ii-iii-opioid-medications-0775\",\"resourceType\":\"MedicationRequest\",\"meta\":{\"profile\":[\"http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-medicationrequest\"]},\"status\":\"active\",\"intent\":\"order\",\"doNotPerform\":false,\"category\":[{\"coding\":[{\"system\":\"http://terminology.hl7.org/CodeSystem/medicationrequest-category\",\"code\":\"discharge\",\"display\":\"Discharge\",\"userSelected\":true}]}],\"medicationCodeableConcept\":{\"coding\":[{\"system\":\"http://www.nlm.nih.gov/research/umls/rxnorm\",\"version\":\"2022-08\",\"code\":\"863845\",\"display\":\"Abuse-Deterrent morphine sulfate 100 MG / naltrexone hydrochloride 4 MG Extended Release Oral Capsule\",\"userSelected\":true}]},\"authoredOn\":\"2024-01-03T00:10:00.000+00:00\",\"requester\":{\"reference\":\"Practitioner/f007\",\"display\":\"Patrick Pump\"},\"subject\":{\"reference\":\"Patient/5c316557-a562-48e4-94fd-76c02ddc388f\"}}},{\"fullUrl\":\"Coverage/1\",\"resource\":{\"resourceType\":\"Coverage\",\"beneficiary\":{\"reference\":\"Patient/5c316557-a562-48e4-94fd-76c02ddc388f\"},\"id\":\"1\",\"meta\":{\"profile\":[\"http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-coverage\"]},\"payor\":[{\"reference\":\"Organization/123456\"}],\"status\":\"active\"}},{\"fullUrl\":\"Organization/123456\",\"resource\":{\"resourceType\":\"Organization\",\"active\":true,\"address\":[{\"use\":\"billing\",\"type\":\"postal\",\"line\":[\"P.O. Box 660044\"],\"city\":\"Dallas\",\"state\":\"TX\",\"postalCode\":\"75266-0044\",\"country\":\"USA\"}],\"id\":\"123456\",\"identifier\":[{\"use\":\"temp\",\"system\":\"urn:oid:2.16.840.1.113883.4.4\",\"value\":\"21-3259825\"}],\"meta\":{\"profile\":[\"http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-organization\"]},\"name\":\"Blue Cross Blue Shield of Texas\",\"telecom\":[{\"system\":\"phone\",\"value\":\"(+1) 972-766-6900\"}],\"type\":[{\"coding\":[{\"system\":\"http://terminology.hl7.org/CodeSystem/organization-type\",\"code\":\"pay\",\"display\":\"Payer\"}]}]}}]}";

  final String malformedJson =
      "{ \"resourceType\": \"Bundle\", \"type\": \"collection\", \"entry\": [{ \"fullUrl\": \"633c9d020968f8012250fc60 }]}"; // intentional - missing quotes around fullUrl ID

  @Mock MeasureRepository measureRepository;

  @Mock TestCaseService testCaseService;

  @InjectMocks UpdateTestCaseResourceFullUrlsChangeUnit changeUnit;

  Measure measure1;
  Measure measure2;
  Measure measure3;

  TestCase tc1;
  TestCase tc2;
  TestCase tc3;

  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor1;

  @BeforeEach
  public void setup() {
    tc1 = TestCase.builder().id("TC1").name("TC1").patientId(UUID.randomUUID()).json(json).build();
    tc2 = TestCase.builder().id("TC2").name("TC2").patientId(UUID.randomUUID()).json(json2).build();
    tc3 =
        TestCase.builder()
            .id("TC3")
            .name("MalformedTC")
            .patientId(UUID.randomUUID())
            .json(malformedJson)
            .build();
    measure1 =
        Measure.builder()
            .id("Measure1")
            .measureName("Measure1")
            .model(ModelType.QI_CORE.getValue())
            .testCases(List.of(tc1, tc2))
            .build();

    measure2 =
        Measure.builder()
            .id("QdmMeasure")
            .measureName("QdmMeasure")
            .model(ModelType.QDM_5_6.getValue())
            .build();
    measure3 =
        Measure.builder()
            .id("Measure3")
            .measureName("Measure3")
            .model(ModelType.QI_CORE.getValue())
            .testCases(List.of(tc3))
            .build();
  }

  @Test
  public void testChangeUnitExecutionEmptyRepository() {
    // given
    when(measureRepository.findAll()).thenReturn(List.of());

    // when
    changeUnit.updateTestCaseResourceFullUrls(measureRepository, testCaseService);

    // then
    verifyNoMoreInteractions(measureRepository);
    verifyNoMoreInteractions(testCaseService);
  }

  @Test
  public void testChangeUnitExecutionOnlyQdmRepository() {
    // given
    when(measureRepository.findAll()).thenReturn(List.of(measure2));

    // when
    changeUnit.updateTestCaseResourceFullUrls(measureRepository, testCaseService);

    // then
    verifyNoMoreInteractions(measureRepository);
    verifyNoMoreInteractions(testCaseService);
  }

  @Test
  public void testChangeUnitExecutionMultipleTestCases() {
    // given
    when(measureRepository.findAll()).thenReturn(List.of(measure1, measure2));

    // when
    changeUnit.updateTestCaseResourceFullUrls(measureRepository, testCaseService);

    // then
    verify(measureRepository, times(1)).save(measureArgumentCaptor1.capture());
    Measure measure = measureArgumentCaptor1.getValue();
    assertThat(measure.getTestCases(), is(notNullValue()));
    assertThat(measure.getTestCases().size(), is(equalTo(2)));
  }

  @Test
  public void testChangeUnitExecutionMalformedTestCaseJson() {
    // given
    when(measureRepository.findAll()).thenReturn(List.of(measure3));

    // when
    changeUnit.updateTestCaseResourceFullUrls(measureRepository, testCaseService);

    // then
    verify(measureRepository, times(1)).save(measureArgumentCaptor1.capture());
    Measure measure = measureArgumentCaptor1.getValue();
    assertThat(measure.getTestCases(), is(notNullValue()));
    assertThat(measure.getTestCases().size(), is(equalTo(1)));
    TestCase testCase = measure.getTestCases().get(0);
    assertThat(testCase.getJson(), is(notNullValue()));
    assertThat(testCase.getJson(), is(equalTo(malformedJson)));
  }

  @Test
  public void testRollBackEmpty() {
    changeUnit.rollbackExecution(measureRepository);
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testRollBackTwo() {
    ReflectionTestUtils.setField(changeUnit, "tempMeasures", List.of(measure1, measure2));
    changeUnit.rollbackExecution(measureRepository);
    verify(measureRepository, times(2)).save(any(Measure.class));
  }
}
