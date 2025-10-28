package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

  @Mock private MeasureService measureService;
  @Mock private TestCaseService testCaseService;
  @Mock private ActionLogService actionLogService;
  @InjectMocks private AdminService adminService;

  @Test
  void updateHCPCUpdatesTestCaseJsonWhenModelIsQDM() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .model(ModelType.QDM_5_6.getValue())
            .testCases(
                List.of(
                    TestCase.builder()
                        .id("testCaseId")
                        .json("http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets")
                        .build()))
            .build();
    when(measureService.findMeasureById("measureId")).thenReturn(measure);

    Measure updatedMeasure = adminService.updateHcpcCodes("measureId", "testUser", "accessToken");

    verify(testCaseService, times(1))
        .updateTestCase(any(TestCase.class), eq("measureId"), eq("testUser"), eq("accessToken"));
    verify(actionLogService, times(1))
        .logAction(
            eq("measureId"),
            eq(Measure.class),
            eq(ActionType.UPDATED),
            eq("testUser"),
            eq("Admin Action: Overwrote HCPC Values."));
    assertThat(updatedMeasure, is(notNullValue()));
  }

  @Test
  void updateHCPCThrowsResourceNotFoundExceptionWhenMeasureDoesNotExist() {
    when(measureService.findMeasureById("invalidId")).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () -> adminService.updateHcpcCodes("invalidId", "testUser", "accessToken"));
  }

  @Test
  void updateHCPCReturnsMeasureWithoutChangesWhenModelIsNotQDM() {
    Measure measure = Measure.builder().id("measureId").model("FHIR").build();
    when(measureService.findMeasureById("measureId")).thenReturn(measure);

    Measure result = adminService.updateHcpcCodes("measureId", "testUser", "accessToken");

    verify(testCaseService, times(0)).updateTestCase(any(TestCase.class), anyString(), anyString(), anyString());
    verify(actionLogService, times(0)).logAction(anyString(), any(Class.class), any(ActionType.class), anyString(), anyString());
    assertThat(result, is(notNullValue()));
    assertThat(result.getModel(), is("FHIR"));
  }

  @Test
  void updateHCPCDoesNotUpdateTestCaseJsonWhenNoChangesAreRequired() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .model(ModelType.QDM_5_6.getValue())
            .testCases(
                List.of(
                    TestCase.builder().id("testCaseId").json("2.16.840.1.113883.6.285").build()))
            .build();
    when(measureService.findMeasureById("measureId")).thenReturn(measure);

    Measure updatedMeasure = adminService.updateHcpcCodes("measureId", "testUser", "accessToken");

    verify(testCaseService, times(0))
        .updateTestCase(any(TestCase.class), anyString(), anyString(), anyString());
    verify(actionLogService, times(0))
        .logAction(anyString(), any(Class.class), any(ActionType.class), anyString(), anyString());
    assertThat(updatedMeasure, is(notNullValue()));
  }
}
