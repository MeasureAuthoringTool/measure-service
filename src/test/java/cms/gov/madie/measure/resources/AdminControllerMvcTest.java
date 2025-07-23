package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.SecurityConfig;
import cms.gov.madie.measure.dto.JobStatus;
import cms.gov.madie.measure.dto.MeasureTestCaseValidationReport;
import cms.gov.madie.measure.dto.TestCaseValidationReport;
import cms.gov.madie.measure.repositories.CqmMeasureRepository;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.*;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminController.class})
@ActiveProfiles("test")
@Import(SecurityConfig.class)
public class AdminControllerMvcTest {
  private static final String ADMIN_TEST_API_KEY_HEADER = "api-key";
  private static final String ADMIN_TEST_API_KEY_HEADER_VALUE = "0a51991c";
  private static final String TEST_USER_ID = "test-okta-user-id-123";

  @MockitoBean private MeasureService measureService;
  @MockitoBean private MeasureSetService measureSetService;
  @MockitoBean private TestCaseService testCaseService;
  @MockitoBean private TestCaseValidationService testCaseValidationService;
  @MockitoBean private ActionLogService actionLogService;
  @MockitoBean private VersionService versionService;

  @MockitoBean private MeasureRepository measureRepository;
  @MockitoBean private ExportRepository exportRepository;
  @MockitoBean private CqmMeasureRepository cqmMeasureRepository;

  @Autowired private MockMvc mockMvc;

  @Test
  public void testValidateAllMeasureTestCasesNoMeasuresFoundDefaultDraftOnly() throws Exception {
    when(measureService.getAllActiveMeasureIds(eq(true))).thenReturn(List.of());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/test-cases/validations")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reports", empty()))
        .andExpect(jsonPath("$.impactedMeasures", empty()));
    verifyNoInteractions(testCaseService);
  }

  @Test
  public void testValidateAllMeasureTestCasesNoMeasuresFoundProvidedDraftOnly() throws Exception {
    when(measureService.getAllActiveMeasureIds(eq(true))).thenReturn(List.of());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/test-cases/validations?draftOnly=true")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reports", empty()))
        .andExpect(jsonPath("$.impactedMeasures", empty()));
    verifyNoInteractions(testCaseService);
  }

  @Test
  public void testValidateAllMeasureTestCasesNoMeasuresFoundProvidedNotDraftOnly()
      throws Exception {
    when(measureService.getAllActiveMeasureIds(eq(false))).thenReturn(List.of());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/test-cases/validations?draftOnly=false")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reports", empty()))
        .andExpect(jsonPath("$.impactedMeasures", empty()));
    verifyNoInteractions(testCaseService);
  }

  @Test
  public void testValidateAllMeasureTestCasesNoImpactedMeasureDefaultDraftOnly() throws Exception {
    when(measureService.getAllActiveMeasureIds(eq(true))).thenReturn(List.of("M1", "M2"));
    MeasureTestCaseValidationReport report1 =
        MeasureTestCaseValidationReport.builder()
            .measureId("M1")
            .jobStatus(JobStatus.COMPLETED)
            .measureSetId("MSet1")
            .testCaseValidationReport(
                TestCaseValidationReport.builder()
                    .testCaseId("TC1")
                    .previousValidResource(true)
                    .currentValidResource(true)
                    .build())
            .testCaseValidationReport(
                TestCaseValidationReport.builder()
                    .testCaseId("TC2")
                    .previousValidResource(false)
                    .currentValidResource(false)
                    .build())
            .build();
    when(testCaseService.updateTestCaseValidResourcesWithReport(eq("M1"), anyString()))
        .thenReturn(report1);

    MeasureTestCaseValidationReport report2 =
        MeasureTestCaseValidationReport.builder()
            .measureId("M2")
            .jobStatus(JobStatus.COMPLETED)
            .measureSetId("MSet2")
            .testCaseValidationReport(
                TestCaseValidationReport.builder()
                    .testCaseId("TC3")
                    .previousValidResource(true)
                    .currentValidResource(true)
                    .build())
            .testCaseValidationReport(
                TestCaseValidationReport.builder()
                    .testCaseId("TC4")
                    .previousValidResource(true)
                    .currentValidResource(true)
                    .build())
            .build();
    when(testCaseService.updateTestCaseValidResourcesWithReport(eq("M2"), anyString()))
        .thenReturn(report2);

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/test-cases/validations")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reports[0].measureId").value("M1"))
        .andExpect(jsonPath("$.reports[0].measureSetId").value("MSet1"))
        .andExpect(jsonPath("$.reports[0].jobStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.reports[0].testCaseValidationReports[0]").exists())
        .andExpect(jsonPath("$.reports[0].testCaseValidationReports[0].testCaseId").value("TC1"))
        .andExpect(
            jsonPath("$.reports[0].testCaseValidationReports[0].previousValidResource").value(true))
        .andExpect(
            jsonPath("$.reports[0].testCaseValidationReports[0].currentValidResource").value(true))
        .andExpect(jsonPath("$.reports[0].testCaseValidationReports[1]").exists())
        .andExpect(jsonPath("$.reports[0].testCaseValidationReports[1].testCaseId").value("TC2"))
        .andExpect(
            jsonPath("$.reports[0].testCaseValidationReports[1].previousValidResource")
                .value(false))
        .andExpect(
            jsonPath("$.reports[0].testCaseValidationReports[1].currentValidResource").value(false))
        .andExpect(jsonPath("$.reports[1].testCaseValidationReports[0]").exists())
        .andExpect(jsonPath("$.reports[1].testCaseValidationReports[0].testCaseId").value("TC3"))
        .andExpect(
            jsonPath("$.reports[1].testCaseValidationReports[0].previousValidResource").value(true))
        .andExpect(
            jsonPath("$.reports[1].testCaseValidationReports[0].currentValidResource").value(true))
        .andExpect(jsonPath("$.reports[1].testCaseValidationReports[1]").exists())
        .andExpect(jsonPath("$.reports[1].testCaseValidationReports[1].testCaseId").value("TC4"))
        .andExpect(
            jsonPath("$.reports[1].testCaseValidationReports[1].previousValidResource").value(true))
        .andExpect(
            jsonPath("$.reports[1].testCaseValidationReports[1].currentValidResource").value(true))
        .andExpect(jsonPath("$.impactedMeasures", empty()));

    verify(testCaseService, times(1)).updateTestCaseValidResourcesWithReport(eq("M1"), anyString());
    verify(testCaseService, times(1)).updateTestCaseValidResourcesWithReport(eq("M2"), anyString());
    verifyNoInteractions(measureSetService);
  }

  @Test
  public void testValidateAllMeasureTestCasesOneImpactedMeasureDefaultDraftOnly() throws Exception {
    when(measureService.getAllActiveMeasureIds(eq(true))).thenReturn(List.of("M1", "M2"));
    MeasureTestCaseValidationReport report1 =
        MeasureTestCaseValidationReport.builder()
            .measureId("M1")
            .jobStatus(JobStatus.COMPLETED)
            .measureSetId("MSet1")
            .testCaseValidationReport(
                TestCaseValidationReport.builder()
                    .testCaseId("TC1")
                    .previousValidResource(true)
                    .currentValidResource(true)
                    .build())
            .testCaseValidationReport(
                TestCaseValidationReport.builder()
                    .testCaseId("TC2")
                    .previousValidResource(false)
                    .currentValidResource(false)
                    .build())
            .build();
    when(testCaseService.updateTestCaseValidResourcesWithReport(eq("M1"), anyString()))
        .thenReturn(report1);

    MeasureTestCaseValidationReport report2 =
        MeasureTestCaseValidationReport.builder()
            .measureId("M2")
            .jobStatus(JobStatus.COMPLETED)
            .measureSetId("MSet2")
            .testCaseValidationReport(
                TestCaseValidationReport.builder()
                    .testCaseId("TC3")
                    .previousValidResource(true)
                    .currentValidResource(false)
                    .build())
            .testCaseValidationReport(
                TestCaseValidationReport.builder()
                    .testCaseId("TC4")
                    .previousValidResource(true)
                    .currentValidResource(true)
                    .build())
            .testCaseValidationReport(
                TestCaseValidationReport.builder()
                    .testCaseId("TC5")
                    .previousValidResource(true)
                    .currentValidResource(false)
                    .build())
            .build();
    when(testCaseService.updateTestCaseValidResourcesWithReport(eq("M2"), anyString()))
        .thenReturn(report2);
    when(measureSetService.findByMeasureSetId(eq("MSet2")))
        .thenReturn(MeasureSet.builder().owner("Owner12").build());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/test-cases/validations")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reports[0].measureId").value("M1"))
        .andExpect(jsonPath("$.reports[0].measureSetId").value("MSet1"))
        .andExpect(jsonPath("$.reports[0].jobStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.reports[0].testCaseValidationReports[0]").exists())
        .andExpect(jsonPath("$.reports[0].testCaseValidationReports[0].testCaseId").value("TC1"))
        .andExpect(
            jsonPath("$.reports[0].testCaseValidationReports[0].previousValidResource").value(true))
        .andExpect(
            jsonPath("$.reports[0].testCaseValidationReports[0].currentValidResource").value(true))
        .andExpect(jsonPath("$.reports[0].testCaseValidationReports[1]").exists())
        .andExpect(jsonPath("$.reports[0].testCaseValidationReports[1].testCaseId").value("TC2"))
        .andExpect(
            jsonPath("$.reports[0].testCaseValidationReports[1].previousValidResource")
                .value(false))
        .andExpect(
            jsonPath("$.reports[0].testCaseValidationReports[1].currentValidResource").value(false))
        .andExpect(jsonPath("$.reports[1].testCaseValidationReports[0]").exists())
        .andExpect(jsonPath("$.reports[1].testCaseValidationReports[0].testCaseId").value("TC3"))
        .andExpect(
            jsonPath("$.reports[1].testCaseValidationReports[0].previousValidResource").value(true))
        .andExpect(
            jsonPath("$.reports[1].testCaseValidationReports[0].currentValidResource").value(false))
        .andExpect(jsonPath("$.reports[1].testCaseValidationReports[1]").exists())
        .andExpect(jsonPath("$.reports[1].testCaseValidationReports[1].testCaseId").value("TC4"))
        .andExpect(
            jsonPath("$.reports[1].testCaseValidationReports[1].previousValidResource").value(true))
        .andExpect(
            jsonPath("$.reports[1].testCaseValidationReports[1].currentValidResource").value(true))
        .andExpect(jsonPath("$.reports[1].testCaseValidationReports[2]").exists())
        .andExpect(jsonPath("$.reports[1].testCaseValidationReports[2].testCaseId").value("TC5"))
        .andExpect(
            jsonPath("$.reports[1].testCaseValidationReports[2].previousValidResource").value(true))
        .andExpect(
            jsonPath("$.reports[1].testCaseValidationReports[2].currentValidResource").value(false))
        .andExpect(jsonPath("$.impactedMeasures[0]").exists())
        .andExpect(jsonPath("$.impactedMeasures[0].measureId").value("M2"))
        .andExpect(jsonPath("$.impactedMeasures[0].measureSetId").value("MSet2"))
        .andExpect(jsonPath("$.impactedMeasures[0].measureOwner").value("Owner12"))
        .andExpect(jsonPath("$.impactedMeasures[0].impactedTestCasesCount").value(2))
        .andExpect(jsonPath("$.impactedMeasures[1]").doesNotExist());

    verify(testCaseService, times(1)).updateTestCaseValidResourcesWithReport(eq("M1"), anyString());
    verify(testCaseService, times(1)).updateTestCaseValidResourcesWithReport(eq("M2"), anyString());
  }

  @Test
  public void testAdminMeasurePermaDeleteResourceNotFoundException() throws Exception {
    when(measureService.findMeasureById(anyString())).thenReturn(null);

    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/admin/measures/{id}", "12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isNotFound());

    verify(measureService, times(1)).findMeasureById(anyString());
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testAdminMeasurePermaDeleteHarpIdMismatchException() throws Exception {
    Measure testMsr =
        Measure.builder()
            .id("12345")
            .measureSet(MeasureSet.builder().owner("owner1").build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(testMsr);

    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/admin/measures/{id}", "12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner2"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Response could not be completed because the HARP id of owner2 passed in does not match the owner of the measure with the measure id of 12345. The owner of the measure is owner1"));

    verify(measureService, times(1)).findMeasureById(anyString());
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testAdminMeasurePermaDelete() throws Exception {
    Measure testMsr =
        Measure.builder()
            .id("12345")
            .measureSet(MeasureSet.builder().owner("owner1").build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(testMsr);
    doNothing().when(measureRepository).delete(any(Measure.class));

    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/admin/measures/{id}", "12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", equalTo("12345")));
  }

  @Test
  public void testAdminMeasureGetSharedWithResourceNotFoundException() throws Exception {
    when(measureService.findMeasureById(anyString())).thenReturn(null);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/admin/measures/sharedWith?measureids=12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isNotFound());

    verify(measureService, times(1)).findMeasureById(anyString());
  }

  @Test
  public void testAdminMeasureGetSharedWithHarpIdMismatchException() throws Exception {
    Measure testMsr = Measure.builder().id("12345").build();
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("raoulduke");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    List<AclSpecification> acls = List.of(acl1);
    MeasureSet measureSet = MeasureSet.builder().acls(acls).owner("owner1").build();
    testMsr.setMeasureSet(measureSet);
    when(measureService.findMeasureById(anyString())).thenReturn(testMsr);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/admin/measures/sharedWith?measureids=12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner2"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Response could not be completed because the HARP id of owner2 passed in does not match the owner of the measure with the measure id of 12345. The owner of the measure is owner1"));
  }

  @Test
  public void testAdminMultipleMeasuresGetSharedWith() throws Exception {
    Measure msr1 = Measure.builder().id("12345").build();
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("raoulduke");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    Measure msr2 = Measure.builder().id("6789").build();

    List<AclSpecification> acls = List.of(acl1);
    MeasureSet measureSet = MeasureSet.builder().acls(acls).owner("owner1").build();
    msr1.setMeasureSet(measureSet);
    msr2.setMeasureSet(measureSet);
    when(measureService.findMeasureById(eq("12345"))).thenReturn(msr1);
    when(measureService.findMeasureById(eq("6789"))).thenReturn(msr2);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/admin/measures/sharedWith?measureids=12345,6789")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].measureId", equalTo("12345")))
        .andExpect(jsonPath("$[1].measureId", equalTo("6789")))
        .andExpect(jsonPath("$[0].sharedWith.[0].userId", equalTo("raoulduke")));
  }

  @Test
  public void testAdminMeasureGetSharedWith() throws Exception {
    Measure testMsr = Measure.builder().id("12345").build();
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("raoulduke");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    List<AclSpecification> acls = List.of(acl1);
    MeasureSet measureSet = MeasureSet.builder().acls(acls).owner("owner1").build();
    testMsr.setMeasureSet(measureSet);
    when(measureService.findMeasureById(anyString())).thenReturn(testMsr);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/admin/measures/sharedWith?measureids=12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].measureId", equalTo("12345")))
        .andExpect(jsonPath("$[0].sharedWith.[0].userId", equalTo("raoulduke")));
  }

  @Test
  public void testAdminMeasureGetSharedWithNoone() throws Exception {
    Measure testMsr = Measure.builder().id("12345").build();

    MeasureSet measureSet = MeasureSet.builder().acls(null).owner("owner1").build();
    testMsr.setMeasureSet(measureSet);
    when(measureService.findMeasureById(anyString())).thenReturn(testMsr);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/admin/measures/sharedWith?measureids=12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].measureId", equalTo("12345")))
        .andExpect(jsonPath("$[0].sharedWith", equalTo(null)));
  }

  @Test
  public void testAdminMeasureDeleteThrowsWhenMeasureNotFound() throws Exception {
    when(measureService.findMeasureById(anyString())).thenReturn(null);

    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/admin/measures/{id}", "12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isNotFound());
  }

  @Test
  public void testBlocksNonAuthorizedDeleteRequests() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/admin/measures/{id}", "12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isForbidden());
  }

  @Test
  public void testAdminMeasureChangeVersionThrowsWhenMeasureNotFound() throws Exception {
    when(measureService.findMeasureById(anyString())).thenReturn(null);

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/{id}/correct-version", "12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .queryParam("correctVersion", "2.0.000")
                .queryParam("draftVersion", "1.0.000")
                .queryParam("inCorrectVersion", "3.0.000")
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isNotFound());
    verify(measureService, times(1)).findMeasureById(anyString());
  }

  @Test
  public void testAdminMeasureChangeVersionHarpIdMismatchException() throws Exception {
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            Measure.builder()
                .id("123456")
                .measureSetId("ms-123")
                .measureSet(MeasureSet.builder().owner("owner1").build())
                .version(Version.builder().major(3).minor(0).revisionNumber(0).build())
                .build());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/{id}/correct-version", "12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .queryParam("correctVersion", "2.0.000")
                .queryParam("draftVersion", "1.0.000")
                .queryParam("inCorrectVersion", "3.0.000")
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner2"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Response could not be completed because the HARP id of owner2 passed in does not match the owner of the measure with the measure id of 123456. The owner of the measure is owner1"));

    verify(measureService, times(1)).findMeasureById(anyString());
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testAdminMeasureChangeVersionThrowsIfAssociatedMeasureSetAlreadyHasDraft()
      throws Exception {
    Measure testMsr = Measure.builder().id("12345").measureSetId("ms-123").build();
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            Measure.builder()
                .id("123456")
                .measureSetId("ms-123")
                .measureSet(MeasureSet.builder().owner("owner1").build())
                .version(Version.builder().major(3).minor(0).revisionNumber(0).build())
                .build());
    doReturn(List.of(testMsr))
        .when(measureRepository)
        .findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(List.of("ms-123"), true, true);

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/{id}/correct-version", "12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .queryParam("correctVersion", "2.0.000")
                .queryParam("draftVersion", "1.0.000")
                .queryParam("inCorrectVersion", "3.0.000")
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isBadRequest());
    verify(measureService, times(1)).findMeasureById(anyString());
  }

  @Test
  public void testAdminMeasureChangeVersionThrowsWhenDraftVersionIsGreaterThanCorrectVersion()
      throws Exception {
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            Measure.builder()
                .id("123456")
                .measureSetId("ms-123")
                .measureSet(MeasureSet.builder().owner("owner1").build())
                .version(Version.builder().major(3).minor(0).revisionNumber(0).build())
                .build());
    doReturn(null)
        .when(measureRepository)
        .findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(List.of("ms-123"), true, true);

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/{id}/correct-version", "12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .queryParam("correctVersion", "2.0.000")
                .queryParam("draftVersion", "3.0.000")
                .queryParam("inCorrectVersion", "3.0.000")
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isBadRequest());

    verify(measureService, times(1)).findMeasureById(anyString());
  }

  @Test
  public void testAdminMeasureChangeVersionThrowsWhenGivenVersionIsAlreadyAssociated()
      throws Exception {
    Version version = Version.builder().major(2).minor(0).revisionNumber(0).build();
    Measure testMsr = Measure.builder().id("12345").measureSetId("ms-123").version(version).build();
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            Measure.builder()
                .id("123456")
                .measureSetId("ms-123")
                .measureSet(MeasureSet.builder().owner("owner1").build())
                .version(Version.builder().major(3).minor(0).revisionNumber(0).build())
                .build());
    doReturn(null)
        .when(measureRepository)
        .findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(List.of("ms-123"), true, true);
    doReturn(List.of(testMsr))
        .when(measureRepository)
        .findAllByMeasureSetIdAndActive("ms-123", true);

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/{id}/correct-version", "12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .queryParam("correctVersion", "2.0.000")
                .queryParam("draftVersion", "1.0.000")
                .queryParam("inCorrectVersion", "3.0.000")
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isConflict());
    verify(measureRepository, times(1))
        .findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(List.of("ms-123"), true, true);
  }

  @Test
  public void testAdminMeasureChangeVersionSuccessfully() throws Exception {
    Version version = Version.builder().major(4).minor(2).revisionNumber(0).build();
    Version version1 = Version.builder().major(3).minor(0).revisionNumber(0).build();
    Measure testMsr =
        Measure.builder()
            .id("12345")
            .measureSetId("ms-123")
            .cql("library Test version '3.0.000'")
            .cqlLibraryName("Test")
            .model(ModelType.QDM_5_6.getValue())
            .version(version)
            .build();
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            Measure.builder()
                .id("123456")
                .measureSetId("ms-123")
                .measureSet(MeasureSet.builder().owner("owner1").build())
                .cql("library Test version '3.0.000'")
                .cqlLibraryName("Test")
                .version(version1)
                .measureMetaData(MeasureMetaData.builder().draft(false).build())
                .model(ModelType.QDM_5_6.getValue())
                .build());
    doReturn(null)
        .when(measureRepository)
        .findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(List.of("ms-123"), true, true);
    doReturn(List.of(testMsr))
        .when(measureRepository)
        .findAllByMeasureSetIdAndActive("ms-123", true);
    when(versionService.generateLibraryContentLine(
            "Test", Version.builder().major(3).minor(0).revisionNumber(0).build()))
        .thenReturn("library Test version '3.0.000'");
    when(versionService.generateLibraryContentLine(
            "Test", Version.builder().major(1).minor(0).revisionNumber(0).build()))
        .thenReturn("library Test version '1.0.000'");

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/{id}/correct-version", "12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .queryParam("correctVersion", "2.0.000")
                .queryParam("draftVersion", "1.0.000")
                .queryParam("inCorrectVersion", "3.0.000")
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isOk());

    verify(measureRepository, times(1))
        .findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(List.of("ms-123"), true, true);
    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive("ms-123", true);
  }

  @Test
  public void updateTestCaseValidationStatusProcessesValidatingTestCases() throws Exception {
    Measure measure =
        Measure.builder()
            .id("M1")
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .testCases(
                List.of(
                    TestCase.builder()
                        .id("TC1")
                        .validationStatus(TestCaseValidationStatus.VALIDATING.toString())
                        .build()))
            .build();

    when(measureRepository.findAllByModel(ModelType.QI_CORE_6_0_0.getValue()))
        .thenReturn(List.of(measure));

    doAnswer(
            invocation -> {
              String testCaseId = invocation.getArgument(0, String.class);
              String measureId = invocation.getArgument(1, String.class);
              if ("TC1".equals(testCaseId) && "M1".equals(measureId)) {
                measure
                    .getTestCases()
                    .get(0)
                    .setValidationStatus(TestCaseValidationStatus.PENDING.toString());
              }
              return measure;
            })
        .when(measureRepository)
        .setValidationStatusToPending(anyString(), anyString());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/test-cases/restart-validation")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk());

    verify(testCaseValidationService, times(1))
        .submitOnSaveValidationTask(
            eq("M1"),
            eq(measure.getTestCases().get(0)),
            eq("test-okta"),
            eq(ModelType.QI_CORE_6_0_0));
    assertEquals(
        TestCaseValidationStatus.PENDING.toString(),
        measure.getTestCases().get(0).getValidationStatus());
  }

  @Test
  public void updateTestCaseValidationStatusProcessesPendingTestCases() throws Exception {
    Measure measure =
        Measure.builder()
            .id("M1")
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .testCases(
                List.of(
                    TestCase.builder()
                        .id("TC1")
                        .validationStatus(TestCaseValidationStatus.PENDING.toString())
                        .build()))
            .build();

    when(measureRepository.findAllByModel(ModelType.QI_CORE_6_0_0.getValue()))
        .thenReturn(List.of(measure));

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/test-cases/restart-validation")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk());

    verify(testCaseValidationService, times(1))
        .submitOnSaveValidationTask(
            eq("M1"), any(TestCase.class), eq("test-okta"), eq(ModelType.QI_CORE_6_0_0));
  }

  @Test
  public void updateTestCaseValidationStatusSkipsNonValidatingOrPendingTestCases()
      throws Exception {
    Measure measure =
        Measure.builder()
            .id("M1")
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .testCases(
                List.of(
                    TestCase.builder()
                        .id("TC1")
                        .validationStatus(TestCaseValidationStatus.INVALID.toString())
                        .build()))
            .build();

    when(measureRepository.findAllByModel(ModelType.QI_CORE_6_0_0.getValue()))
        .thenReturn(List.of(measure));

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/test-cases/restart-validation")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk());

    verify(testCaseValidationService, never())
        .submitOnSaveValidationTask(
            anyString(), any(TestCase.class), anyString(), any(ModelType.class));
  }

  @Test
  public void updateTestCaseValidationStatusHandlesEmptyMeasureList() throws Exception {
    when(measureRepository.findAllByModel(ModelType.QI_CORE_6_0_0.getValue()))
        .thenReturn(Collections.emptyList());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/test-cases/restart-validation")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk());

    verifyNoInteractions(testCaseValidationService);
  }

  @Test
  public void updateTestCaseValidationStatusHandlesMeasuresWithoutTestCases() throws Exception {
    Measure measure =
        Measure.builder()
            .id("M1")
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .testCases(Collections.emptyList())
            .build();

    when(measureRepository.findAllByModel(ModelType.QI_CORE_6_0_0.getValue()))
        .thenReturn(List.of(measure));

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/test-cases/restart-validation")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk());

    verifyNoInteractions(testCaseValidationService);
  }

  @Test
  public void updateTestCaseValidationStatusProcessesMultipleTestCasesWithDifferentStatuses()
      throws Exception {
    Measure measure =
        Measure.builder()
            .id("M1")
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .testCases(
                List.of(
                    TestCase.builder()
                        .id("TC1")
                        .validationStatus(TestCaseValidationStatus.VALIDATING.toString())
                        .build(),
                    TestCase.builder()
                        .id("TC2")
                        .validationStatus(TestCaseValidationStatus.PENDING.toString())
                        .build(),
                    TestCase.builder()
                        .id("TC3")
                        .validationStatus(TestCaseValidationStatus.INVALID.toString())
                        .build()))
            .build();

    when(measureRepository.findAllByModel(ModelType.QI_CORE_6_0_0.getValue()))
        .thenReturn(List.of(measure));

    // Mock the repository behavior to update the validation status to PENDING
    doAnswer(
            invocation -> {
              String testCaseId = invocation.getArgument(0, String.class);
              String measureId = invocation.getArgument(1, String.class);
              if ("TC1".equals(testCaseId) && "M1".equals(measureId)) {
                measure
                    .getTestCases()
                    .get(0)
                    .setValidationStatus(TestCaseValidationStatus.PENDING.toString());
              }
              return null;
            })
        .when(measureRepository)
        .setValidationStatusToPending(anyString(), anyString());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/admin/measures/test-cases/restart-validation")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk());

    verify(testCaseValidationService, times(1))
        .submitOnSaveValidationTask(
            eq("M1"),
            eq(measure.getTestCases().get(1)),
            eq("test-okta"),
            eq(ModelType.QI_CORE_6_0_0));
    verify(testCaseValidationService, never())
        .submitOnSaveValidationTask(
            eq("M1"),
            eq(measure.getTestCases().get(2)),
            eq("test-okta"),
            eq(ModelType.QI_CORE_6_0_0));

    assertEquals(
        TestCaseValidationStatus.PENDING.toString(),
        measure.getTestCases().get(0).getValidationStatus());
    assertEquals(
        TestCaseValidationStatus.PENDING.toString(),
        measure.getTestCases().get(1).getValidationStatus());
    assertEquals(
        TestCaseValidationStatus.INVALID.toString(),
        measure.getTestCases().get(2).getValidationStatus());
  }
}
