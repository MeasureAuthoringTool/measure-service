package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.cqm.datacriteria.RelatedPerson;
import gov.cms.madie.models.cqm.datacriteria.Symptom;
import gov.cms.madie.models.cqm.datacriteria.basetypes.DataElement;
import gov.cms.madie.models.cqm.datacriteria.basetypes.Interval;
import gov.cms.madie.models.measure.QdmMeasure;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.dto.MadieFeatureFlag;
import cms.gov.madie.measure.exceptions.CqmConversionException;
import cms.gov.madie.measure.exceptions.LockNotObtainedException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import gov.cms.madie.models.measure.TestCase;

@ExtendWith(MockitoExtension.class)
public class TestCaseShiftDatesServiceQdmTest {
  @Mock private TestCaseService testCaseService;
  @Mock private MeasureService measureService;
  @InjectMocks private QdmTestCaseShiftDatesService qdmTestCaseShiftDatesService;
  @Mock private TestCaseLockService testCaseLockService;
  @Mock private AppConfigService appConfigService;

  private TestCase testCase;
  private static final String JSON =
      "{\"qdmVersion\":\"5.6\",\"dataElements\":[{\"dataElementCodes\":[{\"code\":\"14463-4\",\"system\":\"2.16.840.1.113883.6.1\",\"version\":null,\"display\":\"Chlamydia trachomatis [Presence] in Cervix by Organism specific culture\"}],\"_id\":\"666b3dda1d026b000017e20b\",\"performer\":[],\"relatedTo\":[],\"qdmTitle\":\"Laboratory Test, Performed\",\"hqmfOid\":\"2.16.840.1.113883.10.20.28.4.42\",\"qdmCategory\":\"laboratory_test\",\"qdmStatus\":\"performed\",\"qdmVersion\":\"5.6\",\"_type\":\"QDM::LaboratoryTestPerformed\",\"description\":\"Laboratory Test, Performed: Chlamydia Screening\",\"codeListId\":\"2.16.840.1.113883.3.464.1003.110.12.1052\",\"id\":\"666b3dda1d026b000017e20a\",\"components\":[{\"qdmVersion\":\"5.6\",\"_type\":\"QDM::Component\",\"_id\":\"666b3e2e1d026b000017e28d\",\"code\":{\"code\":\"105604006\",\"system\":\"2.16.840.1.113883.6.96\",\"version\":null,\"display\":\"Deficiency of naturally occurring coagulation factor inhibitor (disorder)\"}}],\"relevantPeriod\":{\"low\":\"2024-02-29T00:00:00.000+00:00\",\"high\":\"2024-06-28T00:00:00.000+00:00\",\"lowClosed\":true,\"highClosed\":true},\"relevantDatetime\":\"2024-06-29T00:00:00.000+00:00\",\"authorDatetime\":\"2024-02-29T00:00:00.000+00:00\",\"resultDatetime\":\"2024-02-29T00:00:00.000+00:00\"}],\"_id\":\"66698bcec3b50c0000acc383\"}";

  private static final String dateTimeString = "2024-02-29T00:00:00.000Z";

  @BeforeEach
  public void setUp() {
    testCase = new TestCase();
    testCase.setId("TESTID");
    testCase.setJson(JSON);
  }

  @Test
  void shiftTestCaseDates() {
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id("ID")
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .createdBy("test.user")
            .build();
    qdmMeasure.setTestCases(List.of(testCase));

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    doReturn(testCase)
        .when(testCaseService)
        .updateTestCase(any(), anyString(), anyString(), anyString());

    List<String> shiftedTestCaseIds =
        qdmTestCaseShiftDatesService.shiftTestCaseDates(
            qdmMeasure, List.of(qdmMeasure.getTestCases().get(0).getId()), 1, "TOKEN", principal);
    assertTrue(CollectionUtils.isNotEmpty(shiftedTestCaseIds));
  }

  @Test
  public void shiftDatesForTestCase() {
    TestCase modified = qdmTestCaseShiftDatesService.shiftDatesForTestCase(testCase, 1);

    assertNotNull(modified);
    assertTrue(modified.getJson().contains("2025"));
  }

  @Test
  public void shiftDatesForTestCaseInvalidJson() {
    String jsonInvalid = "";
    testCase.setJson(jsonInvalid);

    assertThrows(
        CqmConversionException.class,
        () -> qdmTestCaseShiftDatesService.shiftDatesForTestCase(testCase, 1));
  }

  @Test
  public void shiftDatesForTestCaseNoDataElement() {
    String jsonInvalid =
        "{\"_id\":\"66698bcec3b50c0000acc383\",\"qdmVersion\":\"5.6\",\"dataElements\":[]}";
    testCase.setJson(jsonInvalid);

    TestCase modified = qdmTestCaseShiftDatesService.shiftDatesForTestCase(testCase, 1);

    assertNotNull(modified);
    assertFalse(modified.getJson().contains("2025"));
    assertEquals(jsonInvalid, modified.getJson());
  }

  @Test
  public void shiftDatesSymptom() {
    Symptom symptom = new Symptom();
    Interval prevalencePeriod = new Interval();
    prevalencePeriod.setLow(getZonedDateTime(dateTimeString));
    prevalencePeriod.setHigh(getZonedDateTime(dateTimeString));
    symptom.setPrevalencePeriod(prevalencePeriod);

    qdmTestCaseShiftDatesService.shiftDates(symptom, 1);

    assertEquals(symptom.getPrevalencePeriod().getLow().getYear(), 2025);
    assertEquals(symptom.getPrevalencePeriod().getHigh().getYear(), 2025);
  }

  @Test
  public void shiftDatesRelatedPerson() {
    assertDoesNotThrow(() -> qdmTestCaseShiftDatesService.shiftDates(new RelatedPerson(), 1));
  }

  @Test
  public void shiftDatesUnsupportedDataType() {
    assertThrows(
        CqmConversionException.class,
        () -> qdmTestCaseShiftDatesService.shiftDates(new DataElement(), 1));
  }

  private ZonedDateTime getZonedDateTime(String dateTimeStr) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    return ZonedDateTime.parse(dateTimeStr, formatter);
  }

  @Test
  public void shiftDatesForTestCaseNoJson() {
    assertThrows(
        CqmConversionException.class,
        () ->
            qdmTestCaseShiftDatesService.shiftDatesForTestCase(
                TestCase.builder().id("testCaseId").build(), 1));
  }

  @Test
  void testShiftTestCaseDatesWithFailure() {
    TestCase testCase2 = TestCase.builder().id("TESTID2").title("TITLE2").json(JSON).build();
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id("ID")
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .createdBy("test.user")
            .testCases(List.of(testCase, testCase2))
            .build();

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    when(testCaseService.updateTestCase(any(), anyString(), anyString(), anyString()))
        .thenReturn(testCase)
        .thenThrow(CqmConversionException.class);

    List<String> shiftedTestCaseIds =
        qdmTestCaseShiftDatesService.shiftTestCaseDates(
            qdmMeasure,
            List.of(
                qdmMeasure.getTestCases().get(0).getId(), qdmMeasure.getTestCases().get(1).getId()),
            1,
            "TOKEN",
            principal);

    assertFalse(CollectionUtils.isEmpty(shiftedTestCaseIds));
    assertTrue(shiftedTestCaseIds.size() == 1);
    assertEquals(testCase.getId(), shiftedTestCaseIds.get(0));
  }

  @Test
  void testShiftAndUpdateThrowsExcpetion() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    when(testCaseService.updateTestCase(any(), anyString(), anyString(), anyString()))
        .thenThrow(ResourceNotFoundException.class);

    List<TestCase> shiftedAndUpdated =
        qdmTestCaseShiftDatesService.shiftAndUpdate(List.of(testCase), 1, "ID", principal, "TOKEN");
    assertTrue(CollectionUtils.isEmpty(shiftedAndUpdated));
  }

  @Test
  void testShiftTestCaseDatesWhenFeatureFlagOn() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(true);
    TestCase testCase2 = TestCase.builder().id("TESTID2").title("TITLE2").json(JSON).build();
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id("ID")
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .createdBy("test.user")
            .testCases(List.of(testCase, testCase2))
            .build();

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    when(testCaseLockService.lockAllTestCases(anyString(), any(List.class), anyString()))
        .thenReturn(Collections.emptyList());
    when(testCaseService.updateTestCase(any(), anyString(), anyString(), anyString()))
        .thenReturn(testCase)
        .thenThrow(CqmConversionException.class);
    when(testCaseLockService.unlockAllTestCases(any(List.class), anyString())).thenReturn(true);

    List<String> failedTestCases =
        qdmTestCaseShiftDatesService.shiftTestCaseDates(
            qdmMeasure,
            List.of(
                qdmMeasure.getTestCases().get(0).getId(), qdmMeasure.getTestCases().get(1).getId()),
            1,
            "TOKEN",
            principal);
    assertFalse(CollectionUtils.isEmpty(failedTestCases));
    assertTrue(failedTestCases.size() == 1);
    assertEquals("TESTID", failedTestCases.get(0));
  }

  @Test
  void testShiftTestCaseDatesWhenFeatureFlagAndSeries() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(true);
    TestCase testCase2 =
        TestCase.builder().id("TESTID2").series("SERIES2").title("TITLE2").json(JSON).build();
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id("ID")
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .createdBy("test.user")
            .testCases(List.of(testCase, testCase2))
            .build();

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    when(testCaseLockService.lockAllTestCases(anyString(), any(List.class), anyString()))
        .thenReturn(Collections.emptyList());
    when(testCaseService.updateTestCase(any(), anyString(), anyString(), anyString()))
        .thenReturn(testCase)
        .thenThrow(CqmConversionException.class);
    when(testCaseLockService.unlockAllTestCases(any(List.class), anyString())).thenReturn(true);

    List<String> failedTestCases =
        qdmTestCaseShiftDatesService.shiftTestCaseDates(
            qdmMeasure,
            List.of(
                qdmMeasure.getTestCases().get(0).getId(), qdmMeasure.getTestCases().get(1).getId()),
            1,
            "TOKEN",
            principal);
    assertFalse(CollectionUtils.isEmpty(failedTestCases));
    assertTrue(failedTestCases.size() == 1);
    assertEquals("TESTID", failedTestCases.get(0));
  }

  @Test
  void testShiftTestCaseDatesWhenLockFails() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(true);
    TestCase testCase2 = TestCase.builder().id("TESTID2").title("TITLE2").json(JSON).build();
    QdmMeasure qdmMeasure =
        QdmMeasure.builder()
            .id("ID")
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .createdBy("test.user")
            .testCases(List.of(testCase, testCase2))
            .build();

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    LockInfo lock = LockInfo.builder().lockedId("TESTID2").lockedBy("another.user").build();
    when(testCaseLockService.lockAllTestCases(anyString(), any(List.class), anyString()))
        .thenReturn(List.of(lock));
    when(testCaseLockService.unlockAllTestCases(any(List.class), anyString())).thenReturn(true);

    assertThrows(
        LockNotObtainedException.class,
        () ->
            qdmTestCaseShiftDatesService.shiftTestCaseDates(
                qdmMeasure,
                List.of(
                    qdmMeasure.getTestCases().get(0).getId(),
                    qdmMeasure.getTestCases().get(1).getId()),
                1,
                "TOKEN",
                principal));
  }
}
