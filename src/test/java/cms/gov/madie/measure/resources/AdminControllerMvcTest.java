package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.config.security.SecurityConfigTest;
import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.dto.JobStatus;
import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.dto.MeasureTestCaseValidationReport;
import cms.gov.madie.measure.dto.TestCaseValidationReport;
import cms.gov.madie.measure.exceptions.InvalidRequestException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.TestCaseSetIdsAlreadyAssignedException;
import cms.gov.madie.measure.exceptions.UnsupportedTypeException;
import cms.gov.madie.measure.repositories.CqmMeasureRepository;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.OrganizationRepository;
import cms.gov.madie.measure.services.*;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.*;
import gov.cms.madie.models.dto.UserRolesDto;
import gov.cms.madie.models.measure.*;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@WebMvcTest({AdminController.class})
@ActiveProfiles("test")
@Import({SecurityConfigTest.class})
public class AdminControllerMvcTest {
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
  @MockitoBean private OrganizationRepository organizationRepository;

  @MockitoBean private ExportService exportService;

  @MockitoBean private MeasureLockService measureLockService;
  @MockitoBean private TestCaseLockService testCaseLockService;
  @MockitoBean private AdminService adminService;
  @MockitoBean private AppConfigService appConfigService;
  @MockitoBean private UserServiceClient userServiceClient;
  @MockitoBean private CacheManager cacheManager;

  @Autowired private MockMvc mockMvc;

  private Group group;
  private TestCase testCase1;
  private TestCase testCase2;
  private MeasureSet measureSet;
  private Version version;
  private FhirMeasure versionedMeasure;

  @BeforeEach
  public void setUp() {
    Population population =
        Population.builder()
            .id("groupId")
            .name(PopulationType.INITIAL_POPULATION)
            .definition("ipp")
            .build();
    Stratification stratification =
        Stratification.builder()
            .id("test-strat")
            .cqlDefinition("Initial Population")
            .association(PopulationType.INITIAL_POPULATION)
            .associations(List.of(PopulationType.INITIAL_POPULATION))
            .build();
    group =
        Group.builder()
            .id("groupId")
            .measureGroupTypes(List.of(MeasureGroupTypes.OUTCOME))
            .scoring(MeasureScoring.COHORT.toString())
            .populationBasis("boolean")
            .populations(List.of(population))
            .stratifications(List.of(stratification))
            .build();
    TestCaseGroupPopulation testCaseGroupPopulation1 =
        TestCaseGroupPopulation.builder()
            .scoring(MeasureScoring.COHORT.toString())
            .populationBasis("boolean")
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder()
                        .name(PopulationType.INITIAL_POPULATION)
                        .expected("1")
                        .actual("1")
                        .build()))
            .stratificationValues(
                List.of(
                    TestCaseStratificationValue.builder()
                        .name("Strata-1")
                        .id("strat1Id")
                        .expected("1")
                        .build()))
            .build();
    TestCaseGroupPopulation testCaseGroupPopulation2 =
        TestCaseGroupPopulation.builder()
            .scoring(MeasureScoring.COHORT.toString())
            .populationBasis("boolean")
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder()
                        .name(PopulationType.INITIAL_POPULATION)
                        .expected("0")
                        .actual("0")
                        .build()))
            .stratificationValues(
                List.of(
                    TestCaseStratificationValue.builder()
                        .name("Strata-1")
                        .id("strat1Id")
                        .expected("0")
                        .build()))
            .build();
    testCase1 =
        TestCase.builder()
            .id("testCaseId")
            .patientId(UUID.fromString("3d2abb9d-c10a-4ab3-ae1a-1684ab61c07e"))
            .title("title")
            .description("description")
            .series("series")
            .groupPopulations(List.of(testCaseGroupPopulation1))
            .build();
    testCase2 =
        TestCase.builder()
            .id("testCaseId")
            .patientId(UUID.fromString("3d2abb9d-c10a-4ab3-ae1a-1684ab61c07e"))
            .title("title")
            .description("description")
            .series("series")
            .groupPopulations(List.of(testCaseGroupPopulation2))
            .build();
    measureSet = MeasureSet.builder().id("measureSetId").owner("owner").build();
    version = Version.builder().major(1).minor(0).revisionNumber(0).build();
    versionedMeasure =
        FhirMeasure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .measureSet(measureSet)
            .model(String.valueOf(ModelType.QI_CORE))
            .cqlLibraryName("CqlLibraryName")
            .version(version)
            .groups(List.of(group))
            .testCases(List.of(testCase2))
            .build();
  }

  @Test
  public void testValidateAllMeasureTestCasesNoMeasuresFoundDefaultDraftOnly() throws Exception {
    when(measureService.getAllActiveMeasureIds(eq(true))).thenReturn(List.of());

    mockMvc
        .perform(
            put("/admin/measures/test-cases/validations")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
            put("/admin/measures/test-cases/validations?draftOnly=true")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
            put("/admin/measures/test-cases/validations?draftOnly=false")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
            put("/admin/measures/test-cases/validations")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
            put("/admin/measures/test-cases/validations")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isNotFound());
  }

  @Test
  public void testBlocksNonAuthorizedDeleteRequests() throws Exception {
    UserRolesDto userRolesDto = new UserRolesDto();
    userRolesDto.setRoles(List.of("MADIE-USER"));
    when(userServiceClient.getUserRoles(eq(TEST_USER_ID), anyString())).thenReturn(userRolesDto);

    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/admin/measures/{id}", "12345")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-USER")))
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isForbidden());
  }

  @Test
  public void testAdminMeasureChangeVersionThrowsWhenMeasureNotFound() throws Exception {
    when(measureService.findMeasureById(anyString())).thenReturn(null);

    mockMvc
        .perform(
            put("/admin/measures/{id}/correct-version", "12345")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .queryParam("correctVersion", "2.0.000")
                .queryParam("draftVersion", "1.0.000")
                .queryParam("inCorrectVersion", "3.0.000")
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
            put("/admin/measures/{id}/correct-version", "12345")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .queryParam("correctVersion", "2.0.000")
                .queryParam("draftVersion", "1.0.000")
                .queryParam("inCorrectVersion", "3.0.000")
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
            put("/admin/measures/{id}/correct-version", "12345")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .queryParam("correctVersion", "2.0.000")
                .queryParam("draftVersion", "1.0.000")
                .queryParam("inCorrectVersion", "3.0.000")
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
            put("/admin/measures/{id}/correct-version", "12345")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .queryParam("correctVersion", "2.0.000")
                .queryParam("draftVersion", "3.0.000")
                .queryParam("inCorrectVersion", "3.0.000")
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
            put("/admin/measures/{id}/correct-version", "12345")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .queryParam("correctVersion", "2.0.000")
                .queryParam("draftVersion", "1.0.000")
                .queryParam("inCorrectVersion", "3.0.000")
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
            put("/admin/measures/{id}/correct-version", "12345")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .queryParam("correctVersion", "2.0.000")
                .queryParam("draftVersion", "1.0.000")
                .queryParam("inCorrectVersion", "3.0.000")
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isOk());

    verify(measureRepository, times(1))
        .findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(List.of("ms-123"), true, true);
    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive("ms-123", true);
  }

  @Test
  public void testCorrectMeasureVersionSavesMeasureAndLogsExpectedMessage() throws Exception {
    String measureId = "123";
    String inCorrectVersion = "1.0.000";
    String correctVersion = "0.1.000";
    String draftVersion = "0.0.000";
    String harpId = "harpId";
    String principalName = "testUser";

    MeasureSet measureSet =
        MeasureSet.builder().id("measureSetId").measureSetId("measureSetId").owner(harpId).build();

    Measure measure =
        Measure.builder()
            .id(measureId)
            .measureSetId(measureSet.getMeasureSetId())
            .version(Version.parse(inCorrectVersion))
            .cqlLibraryName("TestLibrary")
            .cql("library TestLibrary version '1.0.000'")
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .measureSet(measureSet)
            .build();

    when(measureService.findMeasureById(measureId)).thenReturn(measure);
    when(measureRepository.findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(
            anyList(), anyBoolean(), anyBoolean()))
        .thenReturn(Collections.emptyList());
    when(versionService.generateLibraryContentLine(anyString(), any(Version.class)))
        .thenAnswer(
            invocation -> {
              String libName = invocation.getArgument(0);
              Version version = invocation.getArgument(1);
              return "library " + libName + " version '" + version + "'";
            });

    // Capture the Measure object passed to save
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    when(measureRepository.save(measureCaptor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    mockMvc
        .perform(
            put("/admin/measures/{id}/correct-version", measureId)
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .header("Authorization", "test-okta")
                .header("harpId", harpId)
                .param("inCorrectVersion", inCorrectVersion)
                .param("correctVersion", correctVersion)
                .param("draftVersion", draftVersion)
                .principal(() -> principalName))
        .andExpect(status().isOk());

    verify(measureService).findMeasureById(measureId);
    verify(measureRepository)
        .findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(
            eq(List.of(measure.getMeasureSetId())), eq(true), eq(true));
    verify(versionService)
        .generateLibraryContentLine(eq(measure.getCqlLibraryName()), eq(measure.getVersion()));
    verify(measureRepository).save(any(Measure.class));

    // Assert that the measure was updated correctly
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(Version.parse(draftVersion), savedMeasure.getVersion());
    assertTrue(savedMeasure.getMeasureMetaData().isDraft());
    assertTrue(savedMeasure.getCql().contains("version '0.0.000'"));

    // Verify the action was logged
    verify(actionLogService)
        .logAction(
            eq(measureId),
            eq(Measure.class),
            eq(ActionType.VERSION_REVERT),
            eq(TEST_USER_ID),
            eq(String.format("Reverted from version %s to %s", inCorrectVersion, correctVersion)));
  }

  @Test
  public void updateTestCaseValidationStatusProcessesValidatingTestCases() throws Exception {
    Measure measure =
        Measure.builder()
            .id("M1")
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
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
            put("/admin/measures/test-cases/restart-validation")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk());

    verify(testCaseValidationService, times(1))
        .submitOnImportValidationTask(
            eq("M1"),
            eq(measure.getTestCases().get(0)),
            eq("test-okta"),
            eq(ModelType.QI_CORE_6_0_0),
            eq(false));
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
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
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
            put("/admin/measures/test-cases/restart-validation")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk());

    verify(testCaseValidationService, times(1))
        .submitOnImportValidationTask(
            eq("M1"), any(TestCase.class), eq("test-okta"), eq(ModelType.QI_CORE_6_0_0), eq(false));
  }

  @Test
  public void updateTestCaseValidationStatusSkipsNonValidatingOrPendingTestCases()
      throws Exception {
    Measure measure =
        Measure.builder()
            .id("M1")
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
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
            put("/admin/measures/test-cases/restart-validation")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk());

    verify(testCaseValidationService, never())
        .submitOnImportValidationTask(
            anyString(), any(TestCase.class), anyString(), any(ModelType.class), anyBoolean());
  }

  @Test
  public void updateTestCaseValidationStatusHandlesEmptyMeasureList() throws Exception {
    when(measureRepository.findAllByModel(ModelType.QI_CORE_6_0_0.getValue()))
        .thenReturn(Collections.emptyList());

    mockMvc
        .perform(
            put("/admin/measures/test-cases/restart-validation")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
            put("/admin/measures/test-cases/restart-validation")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
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
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
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
            put("/admin/measures/test-cases/restart-validation")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk());

    verify(testCaseValidationService, times(1))
        .submitOnImportValidationTask(
            eq("M1"),
            eq(measure.getTestCases().get(1)),
            eq("test-okta"),
            eq(ModelType.QI_CORE_6_0_0),
            eq(false));
    verify(testCaseValidationService, never())
        .submitOnImportValidationTask(
            eq("M1"),
            eq(measure.getTestCases().get(2)),
            eq("test-okta"),
            eq(ModelType.QI_CORE_6_0_0),
            eq(false));

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

  @Test
  public void unlockAll() throws Exception {
    String msg1 = "Delete measure locks for harpId: " + TEST_USER_ID;
    String msg2 = "Deleted measure lock: measureId";
    String msg3 = "Delete test case locks for harpId: " + TEST_USER_ID;
    String msg4 = "Deleted test case lock: testCaseId";
    List<String> deleteMeasureLocksMsg = List.of(msg1, msg2);
    List<String> deleteTestCaseLocksMsg = List.of(msg3, msg4);

    when(measureLockService.unlockByUser(anyString())).thenReturn(deleteMeasureLocksMsg);
    when(testCaseLockService.unlockByUser(anyString())).thenReturn(deleteTestCaseLocksMsg);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.delete("/admin/measures/test-cases/locks")
                    .with(csrf())
                    .with(
                        jwt()
                            .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                            .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                    .header("Authorization", "test-okta")
                    .header("harpId", TEST_USER_ID))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(result.getResponse().getContentAsString().contains(msg1));
    assertTrue(result.getResponse().getContentAsString().contains(msg2));
    assertTrue(result.getResponse().getContentAsString().contains(msg3));
    assertTrue(result.getResponse().getContentAsString().contains(msg4));
  }

  @Test
  public void overwriteExpectedValuesQiCore() throws Exception {
    FhirMeasure draftMeasure =
        FhirMeasure.builder()
            .id("measureId")
            .model(String.valueOf(ModelType.QI_CORE))
            .cqlLibraryName("CqlLibraryName")
            .measureSetId("measureSetId")
            .measureSet(measureSet)
            .version(version)
            .groups(List.of(group))
            .testCases(List.of(testCase1))
            .build();

    when(measureService.findMeasureById(anyString())).thenReturn(draftMeasure);
    MvcResult result =
        mockMvc
            .perform(
                put("/admin/measures/{id}", draftMeasure.getId())
                    .with(csrf())
                    .with(
                        jwt()
                            .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                            .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                    .header("Authorization", "test-okta")
                    .content(toJsonString(versionedMeasure))
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(result.getResponse().getContentAsString().contains("false"));
  }

  @Test
  public void overwriteExpectedValuesQdm() throws Exception {
    TestCaseGroupPopulation tcgp1 =
        TestCaseGroupPopulation.builder()
            .scoring(MeasureScoring.COHORT.toString())
            .populationBasis("boolean")
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder()
                        .name(PopulationType.INITIAL_POPULATION)
                        .expected(1)
                        .actual(1)
                        .build()))
            .stratificationValues(Collections.emptyList())
            .build();
    TestCase tc1 = testCase1.toBuilder().build();
    tc1.setGroupPopulations(List.of(tcgp1));
    QdmMeasure draftMeasure =
        QdmMeasure.builder()
            .id("measureId")
            .model(String.valueOf(ModelType.QDM_5_6))
            .cqlLibraryName("CqlLibraryName")
            .measureSetId("measureSetId")
            .measureSet(measureSet)
            .version(version)
            .patientBasis(true)
            .groups(List.of(group))
            .testCases(List.of(tc1))
            .build();
    TestCaseGroupPopulation tcgp2 =
        TestCaseGroupPopulation.builder()
            .scoring(MeasureScoring.COHORT.toString())
            .populationBasis("boolean")
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder()
                        .name(PopulationType.INITIAL_POPULATION)
                        .expected(1)
                        .actual(1)
                        .build()))
            .stratificationValues(Collections.emptyList())
            .build();
    TestCase tc2 = testCase2.toBuilder().build();
    tc2.setGroupPopulations(List.of(tcgp2));
    QdmMeasure versionedMeasure =
        QdmMeasure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .measureSet(measureSet)
            .model(String.valueOf(ModelType.QDM_5_6))
            .cqlLibraryName("CqlLibraryName")
            .version(version)
            .patientBasis(true)
            .groups(List.of(group))
            .testCases(List.of(tc2))
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(draftMeasure);
    MvcResult result =
        mockMvc
            .perform(
                put("/admin/measures/{id}", draftMeasure.getId())
                    .with(csrf())
                    .with(
                        jwt()
                            .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                            .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                    .header("Authorization", "test-okta")
                    .content(toJsonString(versionedMeasure))
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(result.getResponse().getContentAsString().contains("false"));
  }

  private String toJsonString(Object obj) throws JacksonException {
    ObjectMapper mapper =
        JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    return mapper.writeValueAsString(obj);
  }

  @Test
  public void overwriteExpectedValuesThrowsResourceNotFoundException() throws Exception {
    when(measureService.findMeasureById(anyString())).thenReturn(null);

    mockMvc
        .perform(
            put("/admin/measures/{id}", "measureId")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .header("Authorization", "test-okta")
                .content(toJsonString(versionedMeasure))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isNotFound())
        .andReturn();

    verify(measureService, times(1)).findMeasureById(anyString());
  }

  @Test
  public void overwriteExpectedValuesThrowsInvalidRequestException() throws Exception {
    MeasureSet mSet = MeasureSet.builder().measureSetId("differentMeasureSetId").build();
    FhirMeasure draftMeasure =
        FhirMeasure.builder()
            .id("anotherMeasureId")
            .model(String.valueOf(ModelType.QI_CORE))
            .cqlLibraryName("CqlLibraryName")
            .measureSetId("differentMeasureSetId")
            .measureSet(mSet)
            .version(version)
            .groups(List.of(group))
            .testCases(List.of(testCase1))
            .build();

    when(measureService.findMeasureById(anyString())).thenReturn(draftMeasure);

    mockMvc
        .perform(
            put("/admin/measures/{id}", draftMeasure.getId())
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .header("Authorization", "test-okta")
                .content(toJsonString(versionedMeasure))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andReturn();

    verify(measureService, times(1)).findMeasureById(anyString());
  }

  @Test
  public void overwriteExpectedValuesThrowsInvalidRequestExceptionVersionDifferent()
      throws Exception {
    Version version = Version.builder().major(1).minor(1).revisionNumber(1).build();
    FhirMeasure draftMeasure =
        FhirMeasure.builder()
            .id("anotherMeasureId")
            .model(String.valueOf(ModelType.QI_CORE))
            .cqlLibraryName("CqlLibraryName")
            .measureSetId("measureSetId")
            .measureSet(measureSet)
            .version(version)
            .groups(List.of(group))
            .testCases(List.of(testCase1))
            .build();

    when(measureService.findMeasureById(anyString())).thenReturn(draftMeasure);

    mockMvc
        .perform(
            put("/admin/measures/{id}", draftMeasure.getId())
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .header("Authorization", "test-okta")
                .content(toJsonString(versionedMeasure))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andReturn();

    verify(measureService, times(1)).findMeasureById(anyString());
  }

  @Test
  public void coverwriteExpectedValuesTestCaseIdDifferent() throws Exception {
    TestCase tc = testCase1.toBuilder().id("anotherTestCaseId").build();
    FhirMeasure draftMeasure =
        FhirMeasure.builder()
            .id("measureId")
            .model(String.valueOf(ModelType.QI_CORE))
            .cqlLibraryName("CqlLibraryName")
            .measureSetId("measureSetId")
            .measureSet(measureSet)
            .version(version)
            .groups(List.of(group))
            .testCases(List.of(tc))
            .build();

    when(measureService.findMeasureById(anyString())).thenReturn(draftMeasure);
    MvcResult result =
        mockMvc
            .perform(
                put("/admin/measures/{id}", draftMeasure.getId())
                    .with(csrf())
                    .with(
                        jwt()
                            .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                            .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                    .header("Authorization", "test-okta")
                    .content(toJsonString(versionedMeasure))
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(result.getResponse().getContentAsString().contains("false"));
  }

  @Test
  public void updateCodeSystemInTestCaseJsonSuccessfully() throws Exception {
    when(adminService.updateCodeSystem(eq("measureId"), eq(TEST_USER_ID), anyString(), anyString()))
        .thenReturn(List.of(80));

    MvcResult result =
        mockMvc
            .perform(
                put("/admin/measures/{id}/testcases/code-system-correction", "measureId")
                    .with(csrf())
                    .with(
                        jwt()
                            .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                            .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                    .param("incorrectCodeSystem", "test")
                    .param("correctCodeSystem", "test")
                    .header("Authorization", "test-okta"))
            .andExpect(status().isOk())
            .andReturn();

    verify(adminService, times(1))
        .updateCodeSystem(eq("measureId"), eq(TEST_USER_ID), anyString(), anyString());
    Assertions.assertThat(result.getResponse().getContentAsString()).contains("80");
  }

  @Test
  public void updateCodeSystemThrowsResourceNotFoundExceptionWhenMeasureNotFound()
      throws Exception {
    when(adminService.updateCodeSystem(eq("invalidId"), eq(TEST_USER_ID), anyString(), anyString()))
        .thenThrow(new ResourceNotFoundException("Measure with id invalidId not found"));
    mockMvc
        .perform(
            put("/admin/measures/{id}/testcases/code-system-correction", "invalidId")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .param("incorrectCodeSystem", "test")
                .param("correctCodeSystem", "test")
                .header("Authorization", "test-okta"))
        .andExpect(status().isNotFound());

    verify(adminService, times(1))
        .updateCodeSystem(eq("invalidId"), eq(TEST_USER_ID), anyString(), anyString());
  }

  @Test
  void testDeleteCmsId() throws Exception {
    String measureId = "measureId";
    String measureSetId = "measureSetId";
    int cmsId = 6;
    Principal principal = mock(Principal.class);
    String principalName = "testuser";
    when(principal.getName()).thenReturn(principalName);
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            Measure.builder()
                .id(measureId)
                .measureSetId("measureSetId")
                .measureSet(MeasureSet.builder().measureSetId("measureSetId").cmsId(cmsId).build())
                .build());
    ;
    when(measureSetService.deleteCmsId(anyString(), anyInt(), anyString(), anyString()))
        .thenReturn(
            String.format(
                "CMS id of %s was deleted successfully from "
                    + "measure set with measure set id of %s",
                cmsId, measureSetId));

    MvcResult result =
        mockMvc
            .perform(
                delete("/admin/measures/{id}/delete-cms-id", measureId)
                    .with(csrf())
                    .with(
                        jwt()
                            .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                            .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                    .header("Authorization", "test-okta")
                    .header("harpId", TEST_USER_ID)
                    .param("cmsId", String.valueOf(cmsId))
                    .principal(() -> "testuser"))
            .andExpect(status().isOk())
            .andReturn();

    String expectedBody =
        String.format(
            "CMS id of %s was deleted successfully from measure set with measure set id of %s",
            cmsId, measureSetId);

    assertThat(result.getResponse(), is(notNullValue()));
    assertEquals(expectedBody, result.getResponse().getContentAsString());
    verify(measureSetService, times(1)).deleteCmsId(measureId, cmsId, TEST_USER_ID, TEST_USER_ID);
  }

  @Test
  public void addOrganizations() throws Exception {
    List<Organization> organizationList = new ArrayList<>();
    organizationList.add(Organization.builder().name("org1").oid("1.2.3.4").build());
    organizationList.add(Organization.builder().name("org2").oid("1.2.3.5").build());
    organizationList.add(Organization.builder().name("org3").oid("1.2.3.6").build());

    doReturn(organizationList).when(organizationRepository).saveAll(any());

    MvcResult result =
        mockMvc
            .perform(
                post("/admin/organizations")
                    .with(
                        jwt()
                            .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                            .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                    .with(csrf())
                    .content(toJsonString(organizationList))
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

    // Validate the returned organizations match the input
    String content = result.getResponse().getContentAsString();
    ObjectMapper mapper = new ObjectMapper();
    List<Organization> persistedOrganizations =
        mapper.readValue(
            content, new tools.jackson.core.type.TypeReference<List<Organization>>() {});
    assertEquals(organizationList.size(), persistedOrganizations.size());
    for (int i = 0; i < organizationList.size(); i++) {
      assertEquals(organizationList.get(i).getName(), persistedOrganizations.get(i).getName());
      assertEquals(organizationList.get(i).getOid(), persistedOrganizations.get(i).getOid());
    }
  }

  @Test
  public void addOrganizationsThrowsDuplicateKeyException() throws Exception {
    doThrow(new DuplicateKeyException("DuplicateKeyException Message", "Duplicate oid found"))
        .when(organizationRepository)
        .saveAll(any());
    Organization organization = Organization.builder().name("org1").oid("1.2.3.4").build();
    MvcResult result =
        mockMvc
            .perform(
                post("/admin/organizations")
                    .with(
                        jwt()
                            .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                            .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                    .with(csrf())
                    .content(toJsonString(List.of(organization)))
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

    String content = result.getResponse().getContentAsString();
    assertTrue(content.contains("Duplicate oid found"));
  }

  @Test
  public void testGetMeasureAccessReportReturnsOkWithCorrectHeadersAndBody() throws Exception {
    byte[] expectedBytes = "test-excel-content".getBytes();
    List<String> measureIds = List.of("measure-id-1", "measure-id-2");

    when(exportService.getSharedAccessReportForMeasures(
            eq(measureIds), eq(TEST_USER_ID), anyString()))
        .thenReturn(expectedBytes);

    MvcResult result =
        mockMvc
            .perform(
                put("/admin/measures/shared-access-report")
                    .with(csrf())
                    .with(
                        jwt()
                            .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                            .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                    .header(HttpHeaders.AUTHORIZATION, "test-okta")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(toJsonString(measureIds)))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse(), is(notNullValue()));
    assertEquals(
        "attachment; filename=\"MeasureSharingExport.xlsx\"",
        result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
    assertEquals(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE));
    assertThat(result.getResponse().getContentAsByteArray(), equalTo(expectedBytes));
    verify(exportService, times(1))
        .getSharedAccessReportForMeasures(eq(measureIds), eq(TEST_USER_ID), anyString());
  }

  @Test
  public void testGetMeasureAccessReportReturnsForbiddenWhenNotAdmin() throws Exception {
    List<String> measureIds = List.of("measure-id-1");

    mockMvc
        .perform(
            put("/admin/measures/shared-access-report")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_SOME_OTHER_ROLE")))
                .header(HttpHeaders.AUTHORIZATION, "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(toJsonString(measureIds)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(exportService);
  }

  @Test
  public void testGetMeasureAccessReportReturnsBadRequestWhenServiceThrowsInvalidRequestException()
      throws Exception {
    List<String> measureIds = List.of();

    when(exportService.getSharedAccessReportForMeasures(any(), anyString(), anyString()))
        .thenThrow(
            new InvalidRequestException(
                "Please provide at least one measure id to export the shared access report."));

    MvcResult result =
        mockMvc
            .perform(
                put("/admin/measures/shared-access-report")
                    .with(csrf())
                    .with(
                        jwt()
                            .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                            .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                    .header(HttpHeaders.AUTHORIZATION, "test-okta")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(toJsonString(measureIds)))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(result.getResponse(), is(notNullValue()));
    verify(exportService, times(1))
        .getSharedAccessReportForMeasures(any(), anyString(), anyString());
  }

  @Test
  public void backfillTestCaseSetIdsReturnsForbiddenForNonAdmin() throws Exception {
    mockMvc
        .perform(
            put("/admin/measure/{measureId}/test-cases/backfill-set-ids", "measureId")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_SOME_OTHER_ROLE"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(adminService);
  }

  @Test
  public void backfillTestCaseSetIdsThrowsConflictForQdmMeasure() throws Exception {
    Measure qdmMeasure =
        Measure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .measureSet(MeasureSet.builder().id("measureSetId").owner(TEST_USER_ID).build())
            .model(ModelType.QDM_5_6.getValue())
            .testCases(List.of(TestCase.builder().id("tc1").build()))
            .build();

    when(measureService.findMeasureById("measureId")).thenReturn(qdmMeasure);

    mockMvc
        .perform(
            put("/admin/measure/{measureId}/test-cases/backfill-set-ids", "measureId")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN"))))
        .andExpect(status().isConflict());

    verifyNoInteractions(adminService);
  }

  @Test
  public void backfillTestCaseSetIdsReturnsOkOnSuccess() throws Exception {
    TestCase tc1 = TestCase.builder().id("tc1").testCaseSetId(UUID.randomUUID()).build();
    Measure updatedMeasure =
        FhirMeasure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .measureSet(MeasureSet.builder().id("measureSetId").owner(TEST_USER_ID).build())
            .model(ModelType.QI_CORE.getValue())
            .testCases(List.of(tc1))
            .build();

    when(measureService.findMeasureById("measureId")).thenReturn(updatedMeasure);
    when(adminService.backfillTestCaseSetIds(any(Measure.class), anyString()))
        .thenReturn(updatedMeasure);

    mockMvc
        .perform(
            put("/admin/measure/{measureId}/test-cases/backfill-set-ids", "measureId")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", equalTo("measureId")));

    verify(adminService, times(1)).backfillTestCaseSetIds(any(Measure.class), anyString());
  }

  @Test
  public void backfillTestCaseSetIdsReturnsOkWhenAlreadyAssigned() throws Exception {
    Measure measure =
        FhirMeasure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .measureSet(MeasureSet.builder().id("measureSetId").owner(TEST_USER_ID).build())
            .model(ModelType.QI_CORE.getValue())
            .testCases(
                List.of(TestCase.builder().id("tc1").testCaseSetId(UUID.randomUUID()).build()))
            .build();

    when(measureService.findMeasureById("measureId")).thenReturn(measure);
    when(adminService.backfillTestCaseSetIds(any(Measure.class), anyString()))
        .thenThrow(
            new TestCaseSetIdsAlreadyAssignedException(
                "One or more test cases already have a testCaseSetId."));

    MvcResult result =
        mockMvc
            .perform(
                put("/admin/measure/{measureId}/test-cases/backfill-set-ids", "measureId")
                    .with(csrf())
                    .with(
                        jwt()
                            .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                            .authorities(createAuthorityList("ROLE_MADIE-ADMIN"))))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(result.getResponse(), is(notNullValue()));
    assertTrue(
        result
            .getResponse()
            .getContentAsString()
            .contains("One or more test cases already have a testCaseSetId."));
    verify(adminService, times(1)).backfillTestCaseSetIds(any(Measure.class), anyString());
  }

  @Test
  public void backfillTestCaseSetIdsReturnsConflictWhenMeasureSetHasIds() throws Exception {
    Measure measure =
        FhirMeasure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .measureSet(MeasureSet.builder().id("measureSetId").owner(TEST_USER_ID).build())
            .model(ModelType.QI_CORE.getValue())
            .testCases(List.of(TestCase.builder().id("tc1").build()))
            .build();

    when(measureService.findMeasureById("measureId")).thenReturn(measure);
    when(adminService.backfillTestCaseSetIds(any(Measure.class), anyString()))
        .thenThrow(
            new UnsupportedTypeException(
                "One or more test cases in this measure set already have a testCaseSetId."));

    MvcResult result =
        mockMvc
            .perform(
                put("/admin/measure/{measureId}/test-cases/backfill-set-ids", "measureId")
                    .with(csrf())
                    .with(
                        jwt()
                            .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                            .authorities(createAuthorityList("ROLE_MADIE-ADMIN"))))
            .andExpect(status().isConflict())
            .andReturn();
    assertThat(result.getResponse(), is(notNullValue()));
    assertTrue(
        result
            .getResponse()
            .getContentAsString()
            .contains("One or more test cases in this measure set already have a testCaseSetId."));
    verify(adminService, times(1)).backfillTestCaseSetIds(any(Measure.class), anyString());
  }

  @Test
  public void testEvictAllCachesReturnsOkWithCacheNames() throws Exception {
    Cache mockCache = mock(Cache.class);
    when(cacheManager.getCacheNames())
        .thenReturn(Set.of("organizations", "populationBasisValues", "endorsements"));
    when(cacheManager.getCache(anyString())).thenReturn(mockCache);

    mockMvc
        .perform(
            delete("/admin/measures/cache/evict")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN"))))
        .andExpect(status().isOk());

    verify(cacheManager, times(3)).getCache(anyString());
    verify(mockCache, times(3)).clear();
  }

  @Test
  public void testEvictAllCachesReturnsForbiddenForNonAdmin() throws Exception {
    mockMvc
        .perform(
            delete("/admin/cache/evict")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-USER"))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(cacheManager);
  }

  @Test
  public void testSearchMeasuresForUserDelegatesToMeasureServiceWithHarpIdAsUsername()
      throws Exception {
    MeasureListDTO dto = MeasureListDTO.builder().id("m1").measureName("Measure One").build();
    Page<MeasureListDTO> page = new PageImpl<>(List.of(dto));
    when(measureService.getMeasuresByCriteria(any(), any(), any(Pageable.class), anyString()))
        .thenReturn(page);

    mockMvc
        .perform(
            put("/admin/userProfile/test_user/measures/searches?ownershipTypes=OWNED")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id", is("m1")))
        .andExpect(jsonPath("$.content[0].measureName", is("Measure One")));

    ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<OwnershipType>> ownershipCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<MeasureSearchCriteria> criteriaCaptor =
        ArgumentCaptor.forClass(MeasureSearchCriteria.class);
    verify(measureService)
        .getMeasuresByCriteria(
            criteriaCaptor.capture(),
            ownershipCaptor.capture(),
            any(Pageable.class),
            usernameCaptor.capture());
    assertEquals("test_user", usernameCaptor.getValue());
    assertEquals(List.of(OwnershipType.OWNED), ownershipCaptor.getValue());
  }

  @Test
  public void testSearchMeasuresForUserReturnsForbiddenForNonAdmin() throws Exception {
    mockMvc
        .perform(
            put("/admin/users/some_user/measures/searches")
                .with(csrf())
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", TEST_USER_ID))
                        .authorities(createAuthorityList("ROLE_MADIE-USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(measureService);
  }
}
