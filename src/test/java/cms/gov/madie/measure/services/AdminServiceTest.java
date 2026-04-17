package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

  @Mock private MeasureService measureService;
  @Mock private MeasureRepository measureRepository;
  @InjectMocks private AdminService adminService;

  private final String incorrectCodeSystemValue =
      "http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets";
  private final String codeSystemValue = "2.16.840.1.113883.6.285";

  @Test
  void updateCodeSystemInTestCaseJsonWhenModelIsQDM() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .model(ModelType.QDM_5_6.getValue())
            .testCases(
                List.of(
                    TestCase.builder()
                        .id("testCaseId")
                        .caseNumber(80)
                        .json(incorrectCodeSystemValue)
                        .build()))
            .build();
    when(measureService.findMeasureById("measureId")).thenReturn(measure);
    when(measureRepository.save(any(Measure.class))).thenReturn(measure);

    List<Integer> caseNumbers =
        adminService.updateCodeSystem(
            "measureId", "testUser", incorrectCodeSystemValue, codeSystemValue);

    assertThat(caseNumbers, is(notNullValue()));
    assertThat(caseNumbers.size(), is(equalTo(1)));
    assertThat(caseNumbers.get(0), is(equalTo(80)));
  }

  @Test
  void updateCodeSystemThrowsInvalidRequestExceptionWhenCodeSystemNotProvided() {
    when(measureService.findMeasureById("invalidId")).thenReturn(null);

    assertThrows(
        InvalidRequestException.class,
        () -> adminService.updateCodeSystem("invalidId", "testUser", "", codeSystemValue));
  }

  @Test
  void updateCodeSystemThrowsResourceNotFoundExceptionWhenMeasureDoesNotExist() {
    when(measureService.findMeasureById("invalidId")).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            adminService.updateCodeSystem(
                "invalidId", "testUser", incorrectCodeSystemValue, codeSystemValue));
  }

  @Test
  void updateCodeSystemThrowsErrorWhenModelIsNotQDM() {
    Measure measure = Measure.builder().id("measureId").model("FHIR").build();
    when(measureService.findMeasureById("measureId")).thenReturn(measure);

    assertThrows(
        InvalidRequestException.class,
        () ->
            adminService.updateCodeSystem(
                "measureId", "testUser", incorrectCodeSystemValue, codeSystemValue));
  }

  @Test
  void updateCodeSystemDoesNotUpdateTestCaseJsonWhenNoChangesAreRequired() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .model(ModelType.QDM_5_6.getValue())
            .testCases(
                List.of(
                    TestCase.builder()
                        .id("testCaseId")
                        .caseNumber(1)
                        .json("2.16.840.1.113883.6.285")
                        .build()))
            .build();
    when(measureService.findMeasureById("measureId")).thenReturn(measure);

    List<Integer> caseNumbers =
        adminService.updateCodeSystem(
            "measureId", "testUser", incorrectCodeSystemValue, codeSystemValue);

    assertThat(caseNumbers, is(notNullValue()));
    assertThat(caseNumbers.size(), is(equalTo(0)));
  }

  @Test
  void backfillTestCaseSetIdsThrowsInvalidResourceStateExceptionWhenNoTestCases() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .model(ModelType.QI_CORE.getValue())
            .testCases(List.of())
            .build();

    assertThrows(
        InvalidResourceStateException.class,
        () -> adminService.backfillTestCaseSetIds(measure, "testUser"));
  }

  @Test
  void
      backfillTestCaseSetIdsThrowsTestCaseSetIdsAlreadyAssignedExceptionWhenCurrentMeasureHasIds() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .model(ModelType.QI_CORE.getValue())
            .testCases(
                List.of(TestCase.builder().id("tc1").testCaseSetId(UUID.randomUUID()).build()))
            .build();

    assertThrows(
        TestCaseSetIdsAlreadyAssignedException.class,
        () -> adminService.backfillTestCaseSetIds(measure, "testUser"));
  }

  @Test
  void backfillTestCaseSetIdsThrowsUnsupportedTypeExceptionWhenAnotherMeasureInSetHasIds() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .model(ModelType.QI_CORE.getValue())
            .testCases(List.of(TestCase.builder().id("tc1").build()))
            .build();

    when(measureRepository.testCaseSetIdExistsInSet("measureSetId")).thenReturn(true);

    assertThrows(
        UnsupportedTypeException.class,
        () -> adminService.backfillTestCaseSetIds(measure, "testUser"));
  }

  @Test
  void backfillTestCaseSetIdsAssignsUUIDToEachTestCaseAndSaves() {
    TestCase tc1 = TestCase.builder().id("tc1").build();
    TestCase tc2 = TestCase.builder().id("tc2").build();

    Measure measure =
        Measure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .model(ModelType.QI_CORE.getValue())
            .testCases(List.of(tc1, tc2))
            .build();

    when(measureRepository.testCaseSetIdExistsInSet("measureSetId")).thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(measure);

    Measure result = adminService.backfillTestCaseSetIds(measure, "testUser");

    assertThat(result, is(notNullValue()));
    assertThat(tc1.getTestCaseSetId(), is(notNullValue()));
    assertThat(tc2.getTestCaseSetId(), is(notNullValue()));
    verify(measureRepository, times(1)).save(measure);
  }
}
