package cms.gov.madie.measure.config.mongock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;

@ExtendWith(MockitoExtension.class)
public class TestCaseJsonDateTimeChangeUnitTest {

  @Mock private MeasureRepository measureRepository;
  @InjectMocks private TestCaseJsonDateTimeChangeUnit changeUnit;

  private Measure measure;
  private TestCase testCase;
  private String json =
      """
{
  "resourceType" : "Bundle",
  "id" : "6129133e02c62a011f2e6a2c",
  "type" : "collection",
  "entry" : [ {
    "fullUrl" : "https://madie.cms.gov/Patient/319bf9c8-10fc-4f27-8579-c930bf3abf93",
    "resource" : {
      "resourceType" : "Patient",
      "id" : "319bf9c8-10fc-4f27-8579-c930bf3abf93",
      "gender" : "male",
      "birthDate" : "1995-08-21"
    }
  }, {
    "fullUrl" : "https://madie.cms.gov/Encounter/5c954112b8484612c37f27dc",
    "resource" : {
      "resourceType" : "Encounter",
      "id" : "5c954112b8484612c37f27dc",
      "period" : {
        "start" : "2026-10-10T01:30:00.123+05:00",
        "end" : "2026-10-10T03:31:00.456+05:00"
      }
    }
  }, {
    "fullUrl" : "https://madie.cms.gov/Encounter/5c954112b8484612c37f27dd",
    "resource" : {
      "resourceType" : "Encounter",
      "id" : "5c954112b8484612c37f27dd",
      "period" : {
        "start" : "2026-10-10T20:21:22-05:00",
        "end" : "2026-10-10T23:24:25.456-05:00"
      }
    }
  }, {
    "fullUrl" : "https://madie.cms.gov/Encounter/5c954112b8484612c37f27de",
    "resource" : {
      "resourceType" : "Encounter",
      "id" : "5c954112b8484612c37f27de",
      "period" : {
        "start" : "2026-10-10T08:01:00.123+02:00",
        "end" : "2026-10-12T10:30:00.456+02:00"
      }
    }
  }, {
    "fullUrl" : "https://madie.cms.gov/Procedure/5ca62963b8484628b8de1f11",
    "resource" : {
      "resourceType" : "Procedure",
      "id" : "5ca62963b8484628b8de1f11",
      "performedPeriod" : {
        "start" : "2026-10-10T08:00:00.789+03:00"
      }
    }
  }, {
    "fullUrl" : "https://madie.cms.gov/Condition/ischemic-stroke-7f09",
    "resource" : {
      "resourceType" : "Condition",
      "id" : "ischemic-stroke-7f09",
      "recordedDate" : "2026-07-15T08:00:00.981+04:00"
    }
  } ]
}
  		""";

  @BeforeEach
  void setUp() {
    testCase = TestCase.builder().id("testCaseId").json(json).build();
    measure =
        Measure.builder()
            .id("testMeasureId")
            .model(ModelType.QI_CORE.toString())
            .testCases(List.of(testCase))
            .build();
  }

  @Test
  void testResetTimeZoneForEmptyMeasures() throws Exception {
    when(measureRepository.findAll()).thenReturn(List.of());

    changeUnit.resetTestCaseJsonDateTimeZone(measureRepository);

    verify(measureRepository, new Times(1)).findAll();
  }

  @Test
  void testDoesNotResetTimeZoneForQdmMeasures() throws Exception {
    measure.setModel(ModelType.QDM_5_6.toString());

    when(measureRepository.findAll()).thenReturn(List.of(measure));

    changeUnit.resetTestCaseJsonDateTimeZone(measureRepository);

    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(0)).save(measure);
  }

  @Test
  void testDoesNotResetTimeZoneForQiCoreMeasureWithNoTestCases() throws Exception {
    measure.setTestCases(null);
    when(measureRepository.findAll()).thenReturn(List.of(measure));

    changeUnit.resetTestCaseJsonDateTimeZone(measureRepository);

    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(0)).save(measure);
  }

  @Test
  void testDoesNotResetTimeZoneForQiCoreMeasureWithNoTestCaseJson() throws Exception {
    testCase.setJson(null);
    measure.setTestCases(List.of(testCase));
    when(measureRepository.findAll()).thenReturn(List.of(measure));

    changeUnit.resetTestCaseJsonDateTimeZone(measureRepository);

    verify(measureRepository, new Times(1)).findAll();
  }

  @Test
  void testDoesNotResetTimeZoneForQiCoreMeasureWithInvalidTestCaseJson() throws Exception {
    testCase.setJson(json.replace("}", ""));
    measure.setTestCases(List.of(testCase));
    when(measureRepository.findAll()).thenReturn(List.of(measure));

    changeUnit.resetTestCaseJsonDateTimeZone(measureRepository);

    verify(measureRepository, new Times(1)).findAll();

    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-10T01:30:00.123+05:00"));
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-10T03:31:00.456+05:00"));
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-10T20:21:22-05:00"));
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-10T23:24:25.456-05:00"));
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-10T08:01:00.123+02:00"));
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-12T10:30:00.456+02:00"));

    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-10T08:00:00.789+03:00"));
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-07-15T08:00:00.981+04:00"));
  }

  @Test
  void testResetTimeZoneSuccessfully() throws Exception {

    when(measureRepository.findAll()).thenReturn(List.of(measure));

    changeUnit.resetTestCaseJsonDateTimeZone(measureRepository);

    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(1)).save(measure);

    // 2026-10-10T01:30:00.123+05:00 -> 2026-10-09T20:30:00.123+00:00
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-09T20:30:00.123+00:00"));
    // 2026-10-10T03:31:00.456+05:00 -> 2026-10-09T22:31:00.456+00:00
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-09T22:31:00.456+00:00"));
    // 2026-10-10T20:21:22-05:00 -> 2026-10-11T01:21:22.000+00:00
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-11T01:21:22.000+00:00"));
    // 2026-10-10T23:24:25.456-05:00 -> 2026-10-11T04:24:25.456+00:00
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-11T04:24:25.456+00:00"));
    // 2026-10-10T08:01:00.123+02:00 -> 2026-10-10T06:01:00.123+00:00
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-10T06:01:00.123+00:00"));
    // 2026-10-12T10:30:00.456+02:00 -> 2026-10-12T08:30:00.456+00:00
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-12T08:30:00.456+00:00"));

    // 2026-10-10T08:00:00.789+03:00 -> 2026-10-10T05:00:00.789+00:00
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-10-10T05:00:00.789+00:00"));
    // 2026-07-15T08:00:00.981+04:00 -> 2026-07-15T04:00:00.981+00:00
    assertTrue(measure.getTestCases().get(0).getJson().contains("2026-07-15T04:00:00.981+00:00"));
  }

  @Test
  public void testRollbackExecutionHasMeasures() throws Exception {
    ReflectionTestUtils.setField(changeUnit, "tempMeasures", List.of(measure));

    changeUnit.rollbackExecution(measureRepository);

    verify(measureRepository, new Times(1)).saveAll(List.of(measure));

    TestCase testCase = measure.getTestCases().get(0);
    assertNotNull(testCase);

    assertTrue(testCase.getJson().contains("2026-10-10T01:30:00.123+05:00"));
    assertTrue(testCase.getJson().contains("2026-10-10T03:31:00.456+05:00"));
    assertTrue(testCase.getJson().contains("2026-10-10T20:21:22-05:00"));
    assertTrue(testCase.getJson().contains("2026-10-10T23:24:25.456-05:00"));
    assertTrue(testCase.getJson().contains("2026-10-10T08:01:00.123+02:00"));
    assertTrue(testCase.getJson().contains("2026-10-12T10:30:00.456+02:00"));

    assertTrue(testCase.getJson().contains("2026-10-10T08:00:00.789+03:00"));
    assertTrue(testCase.getJson().contains("2026-07-15T08:00:00.981+04:00"));
  }

  @Test
  public void testRollbackExecutionNoMeasures() throws Exception {

    ReflectionTestUtils.setField(changeUnit, "tempMeasures", List.of());

    changeUnit.rollbackExecution(measureRepository);

    verifyNoInteractions(measureRepository);
  }
}
