package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.config.security.SecurityConfigTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.dto.SharedUser;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.repositories.TestCasePatchRepository;
import cms.gov.madie.measure.services.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import com.google.gson.Gson;

import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Organization;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.measure.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MeasureController.class})
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@Import(SecurityConfigTest.class)
public class MeasureControllerMvcTest {

  @MockitoBean private MeasureRepository measureRepository;
  @MockitoBean private MeasureService measureService;
  @MockitoBean private GroupService groupService;
  @MockitoBean private ActionLogService actionLogService;
  @MockitoBean private MeasureSetService measureSetService;
  @Autowired private MockMvc mockMvc;
  @MockitoBean private MeasureSetRepository measureSetRepository;
  @MockitoBean private TestCasePatchRepository testCasePatchRepository;
  @MockitoBean private TestCaseService testCaseService;
  @MockitoBean private TestCaseLockService testCaseLockService;
  @MockitoBean private AppConfigService appConfigService;
  @MockitoBean private CqlDifferentiatorService cqlDifferentiatorService;
  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor;
  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor2;

  private static final String TEST_USER_ID = "test-okta-user-id-123";
  private static final String ACCESS_TOKEN = "test-okta";

  @Captor ArgumentCaptor<Group> groupCaptor;
  @Captor ArgumentCaptor<String> groupIdCaptor;
  @Captor ArgumentCaptor<String> measureIdCaptor;
  @Captor ArgumentCaptor<String> usernameCaptor;
  @Captor ArgumentCaptor<PageRequest> pageRequestCaptor;
  @Captor ArgumentCaptor<Boolean> activeCaptor;
  @Captor ArgumentCaptor<Stratification> stratificationCaptor;
  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;
  @Captor private ArgumentCaptor<Class> targetClassArgumentCaptor;
  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;
  @Captor private ArgumentCaptor<String> performedByArgumentCaptor;

  ObjectMapper objectMapper = new ObjectMapper();
  private static final String MODEL = ModelType.QI_CORE.toString();
  Gson gson = new Gson();
  private static final String LIBRARY_NAME_VALIDATION_ERROR =
      "Library name must start with an upper case letter, followed by alpha-numeric character(s) and must not contain spaces or other special characters except of underscore for QDM.";

  public String toJsonString(Object obj) throws JacksonException {
    ObjectMapper mapper =
        JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    return mapper.writeValueAsString(obj);
  }

  @Test
  public void testUpdatePassed() throws Exception {

    String measureId = "f225481c-921e-4015-9e14-e5046bfac9ff";
    String measureName = "TestMeasure";
    Organization steward =
        Organization.builder().id("d0cc18ce-63fd-4b94-b713-c1d9fd6b2329").name("ICF").build();
    String description = "TestDescription";
    String copyright = "TestCopyright";
    String disclaimer = "TestDisclaimer";
    String rationale = "TestRationale";
    List<Organization> developers =
        List.of(Organization.builder().id("12-34-45").name("TestDeveloper").build());
    String guidance = "TestGuidance";
    String libName = "TestLib";
    String ecqmTitle = "ecqmTitle";
    String measureSetId = "measureSetId";

    final Measure priorMeasure =
        Measure.builder()
            .id(measureId)
            .active(true)
            .measureName(measureName)
            .cqlLibraryName(libName)
            .model(MODEL)
            .versionId(measureId)
            .measureSetId(measureSetId)
            .measureMetaData((MeasureMetaData.builder().draft(true).build()))
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setSteward(steward);
    metaData.setDescription(description);
    metaData.setCopyright(copyright);
    metaData.setDisclaimer(disclaimer);
    metaData.setRationale(rationale);
    metaData.setDevelopers(developers);
    metaData.setGuidance(guidance);
    final Measure updatingMeasure =
        priorMeasure.toBuilder().ecqmTitle(ecqmTitle).measureMetaData(metaData).build();

    when(measureService.findMeasureById(anyString())).thenReturn(priorMeasure);
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));
    when(measureService.updateMeasure(
            any(Measure.class), anyString(), any(Measure.class), anyString()))
        .thenReturn(updatingMeasure);

    final String measureAsJson = toJsonString(updatingMeasure);
    mockMvc
        .perform(
            put("/measures/" + measureId)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(measureId))
        .andExpect(jsonPath("$.measureName").value(measureName))
        .andExpect(jsonPath("$.cqlLibraryName").value(libName))
        .andExpect(jsonPath("$.model").value(MODEL))
        .andExpect(jsonPath("$.versionId").value(measureId))
        .andExpect(jsonPath("$.measureSetId").value(measureSetId))
        .andExpect(jsonPath("$.measureMetaData.steward.name").value(steward.getName()))
        .andExpect(jsonPath("$.measureMetaData.description").value(description))
        .andExpect(jsonPath("$.measureMetaData.copyright").value(copyright))
        .andExpect(jsonPath("$.measureMetaData.disclaimer").value(disclaimer));

    verify(measureService, times(1)).findMeasureById(eq(measureId));
    verify(measureService, times(1))
        .updateMeasure(
            measureArgumentCaptor.capture(),
            anyString(),
            measureArgumentCaptor2.capture(),
            anyString());
    assertThat(measureArgumentCaptor.getValue(), is(equalTo(priorMeasure)));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(),
            targetClassArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            performedByArgumentCaptor.capture());
    assertNotNull(targetIdArgumentCaptor.getValue());
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.UPDATED)));
    assertThat(performedByArgumentCaptor.getValue(), is(equalTo(TEST_USER_ID)));
  }

  @Test
  public void testUpdatePassedLogDeleted() throws Exception {
    String measureId = "f225481c-921e-4015-9e14-e5046bfac9ff";
    String measureName = "TestMeasure";
    Organization steward =
        Organization.builder().id("d0cc18ce-63fd-4b94-b713-c1d9fd6b2329").name("ICF").build();
    String description = "TestDescription";
    String copyright = "TestCopyright";
    String disclaimer = "TestDisclaimer";
    String rationale = "TestRationale";
    List<Organization> developers =
        List.of(Organization.builder().id("12-34-45").name("TestDeveloper").build());
    String guidance = "TestGuidance";
    String libName = "TestLib";
    String ecqmTitle = "ecqmTitle";
    String measureSetId = "measureSetId";

    final Measure priorMeasure =
        Measure.builder()
            .id(measureId)
            .active(true)
            .measureName(measureName)
            .cqlLibraryName(libName)
            .model(MODEL)
            .versionId(measureId)
            .measureSetId(measureSetId)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setSteward(steward);
    metaData.setDescription(description);
    metaData.setCopyright(copyright);
    metaData.setDisclaimer(disclaimer);
    metaData.setRationale(rationale);
    metaData.setDevelopers(developers);
    metaData.setGuidance(guidance);
    final Measure updatingMeasure =
        priorMeasure.toBuilder()
            .active(false)
            .measureMetaData(metaData)
            .ecqmTitle(ecqmTitle)
            .build();

    final String measureAsJson = toJsonString(updatingMeasure);

    when(measureService.findMeasureById(anyString())).thenReturn(priorMeasure);
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));
    when(measureService.updateMeasure(
            any(Measure.class), anyString(), any(Measure.class), anyString()))
        .thenReturn(updatingMeasure);

    mockMvc
        .perform(
            put("/measures/" + measureId)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(measureId))
        .andExpect(jsonPath("$.measureName").value(measureName))
        .andExpect(jsonPath("$.cqlLibraryName").value(libName))
        .andExpect(jsonPath("$.model").value(MODEL))
        .andExpect(jsonPath("$.versionId").value(measureId))
        .andExpect(jsonPath("$.measureSetId").value(measureSetId))
        .andExpect(jsonPath("$.measureMetaData.steward.name").value(steward.getName()))
        .andExpect(jsonPath("$.measureMetaData.description").value(description))
        .andExpect(jsonPath("$.measureMetaData.copyright").value(copyright))
        .andExpect(jsonPath("$.measureMetaData.disclaimer").value(disclaimer));

    verify(measureService, times(1)).findMeasureById(eq(measureId));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(),
            targetClassArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            performedByArgumentCaptor.capture());
    assertNotNull(targetIdArgumentCaptor.getValue());
    assertThat(targetClassArgumentCaptor.getValue(), is(equalTo(Measure.class)));
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.DELETED)));
    assertThat(performedByArgumentCaptor.getValue(), is(equalTo(TEST_USER_ID)));
  }

  @Test
  public void testNewMeasureNameMustNotBeNull() throws Exception {
    final String measureAsJson = "{ \"model\":\"QI-Core v4.1.1\" }";
    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.measureName").value("Measure Name is required."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testUpdateMeasureNameMustNotBeNull() throws Exception {
    final String measureAsJson = "{ \"id\": \"m1234\", \"model\":\"QI-Core v4.1.1\" }";
    mockMvc
        .perform(
            put("/measures/m1234")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.measureName").value("Measure Name is required."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewMeasureNameMustNotBeEmpty() throws Exception {
    final String measureAsJson = "{ \"model\":\"QI-Core v4.1.1\" }";
    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.measureName").value("Measure Name is required."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testUpdateMeasureNameMustNotBeEmpty() throws Exception {
    final String measureAsJson = "{ \"id\": \"m1234\",  \"model\":\"QI-Core v4.1.1\" }";
    mockMvc
        .perform(
            put("/measures/m1234")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.measureName").value("Measure Name is required."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewMeasureFailsIfUnderscoreInMeasureName() throws Exception {
    final String measureAsJson =
        "{ \"measureName\":\"A_Name\", \"cqlLibraryName\":\"ALib\" , \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\", \"model\":\"QI-Core v4.1.1\"}";
    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.measureName")
                .value("Measure Name can not contain underscores."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testUpdateMeasureFailsIfUnderscoreInMeasureName() throws Exception {
    final String measureAsJson =
        "{ \"id\": \"m1234\", \"measureName\":\"A_Name\", \"cqlLibraryName\":\"ALib\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\", \"model\":\"QI-Core v4.1.1\" }";
    mockMvc
        .perform(
            put("/measures/m1234")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.measureName")
                .value("Measure Name can not contain underscores."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewMeasureNameMaxLengthFailed() throws Exception {
    final String measureName = "A".repeat(501);
    final String measureAsJson =
        "{ \"measureName\":\"%s\", \"cqlLibraryName\":\"ALib\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\", \"model\":\"QI-Core v4.1.1\"  }"
            .formatted(measureName);
    verifyNoInteractions(measureRepository);
    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.measureName")
                .value("Measure Name can not be more than 500 characters."));
  }

  @Test
  public void testUpdateMeasureNameMaxLengthFailed() throws Exception {
    final String measureName = "A".repeat(501);
    final String measureAsJson =
        "{ \"id\": \"m1234\", \"measureName\":\"%s\", \"cqlLibraryName\":\"ALib\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\", \"model\":\"QI-Core v4.1.1\" }"
            .formatted(measureName);
    mockMvc
        .perform(
            put("/measures/m1234")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.measureName")
                .value("Measure Name can not be more than 500 characters."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testUpdateMeasureECQMTitleNullFailed() throws Exception {
    final String measureAsJson =
        "{ \"id\": \"m1234\", \"measureName\":\"TestMeasure\", \"cqlLibraryName\":\"ALib\",\"model\": \"%s\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\" }"
            .formatted(MODEL);
    mockMvc
        .perform(
            put("/measures/m1234")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.ecqmTitle").value("eCQM Abbreviated Title is required."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testUpdateMeasureECQMTitleMaxLengthFailed() throws Exception {
    final String ecqmTitle = "A".repeat(33);
    final String measureAsJson =
        "{ \"id\": \"m1234\", \"measureName\":\"TestMeasure\", \"cqlLibraryName\":\"ALib\", \"ecqmTitle\":\"%s\", \"model\":\"%s\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\" }"
            .formatted(ecqmTitle, MODEL);
    mockMvc
        .perform(
            put("/measures/m1234")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.ecqmTitle")
                .value("eCQM Abbreviated Title cannot be more than 32 characters."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewMeasurePassed() throws Exception {
    Measure saved = new Measure();
    String measureId = "id123";
    saved.setId(measureId);
    String measureName = "SavedMeasure";
    String libraryName = "Lib1";
    String ecqmTitle = "ecqmTitle";
    String measureSetId = "cooltime";
    saved.setMeasureName(measureName);
    saved.setCqlLibraryName(libraryName);
    saved.setModel(MODEL);
    saved.setEcqmTitle(ecqmTitle);
    saved.setVersionId(measureId);
    when(measureService.createMeasure(
            any(Measure.class), anyString(), anyString(), any(Boolean.class)))
        .thenReturn(saved);

    final String measureAsJson =
        "{\"measureName\": \"%s\",\"measureSetId\":\"%s\", \"cqlLibraryName\": \"%s\" , \"ecqmTitle\": \"%s\", \"model\": \"%s\", \"versionId\":\"%s\"}"
            .formatted(measureName, measureSetId, libraryName, ecqmTitle, MODEL, measureId);

    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", TEST_USER_ID)
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.measureName").value(measureName))
        .andExpect(jsonPath("$.cqlLibraryName").value(libraryName))
        .andExpect(jsonPath("$.ecqmTitle").value(ecqmTitle))
        .andExpect(jsonPath("$.model").value(MODEL))
        .andExpect(jsonPath("$.id").value(measureId))
        .andExpect(jsonPath("$.versionId").value(measureId));

    verify(measureService, times(1))
        .createMeasure(
            measureArgumentCaptor.capture(), anyString(), anyString(), any(Boolean.class));
    verifyNoMoreInteractions(measureRepository);
    Measure savedMeasure = measureArgumentCaptor.getValue();
    assertEquals(measureName, savedMeasure.getMeasureName());
    assertEquals(libraryName, savedMeasure.getCqlLibraryName());
    assertEquals(ecqmTitle, savedMeasure.getEcqmTitle());
    assertEquals(MODEL, savedMeasure.getModel());
    assertNotEquals(measureId, savedMeasure.getId());
  }

  @Test
  public void testCreateNewQDMMeasureWithPurpose() throws Exception {
    Measure measure =
        Measure.builder()
            .measureName("M1")
            .cqlLibraryName("L1")
            .ecqmTitle("M1Li")
            .model(ModelType.QDM_5_6.getValue())
            .measureSetId("S1")
            .versionId("V1")
            .measureMetaData(MeasureMetaData.builder().purpose("Test").build())
            .build();
    final String measureAsJson = toJsonString(measure);
    MvcResult result =
        mockMvc
            .perform(
                post("/measure")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", TEST_USER_ID)
                    .content(measureAsJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertThat(
        result
            .getResponse()
            .getContentAsString()
            .contains("Purpose is not allowed for a QDM measure"),
        is(equalTo(true)));
  }

  @Test
  public void testNewQdmMeasureScoringInValid() throws Exception {
    Measure saved = new Measure();
    String measureId = "id456";
    saved.setId(measureId);
    String measureName = "SavedMeasureQDM";
    String libraryName = "QDMLib1";
    String ecqmTitle = "ecqmTitleQDM";
    String measureSetId = "cooltimeQDM";
    saved.setMeasureName(measureName);
    saved.setCqlLibraryName(libraryName);
    saved.setModel(ModelType.QDM_5_6.toString());
    saved.setEcqmTitle(ecqmTitle);
    saved.setVersionId(measureId);
    when(measureService.createMeasure(
            any(Measure.class), anyString(), anyString(), any(Boolean.class)))
        .thenReturn(saved);

    final String measureAsJson =
        "{\"measureName\": \"%s\",\"measureSetId\":\"%s\", \"cqlLibraryName\": \"%s\" , \"ecqmTitle\": \"%s\", \"model\": \"%s\", \"versionId\":\"%s\"}"
            .formatted(
                measureName, measureSetId, libraryName, ecqmTitle, "invalidModel", measureId);

    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", TEST_USER_ID)
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewQdmMeasurePassed() throws Exception {
    Measure saved = new Measure();
    String measureId = "id456";
    saved.setId(measureId);
    String measureName = "SavedMeasureQDM";
    String libraryName = "QDMLib1";
    String ecqmTitle = "ecqmTitleQDM";
    String measureSetId = "cooltimeQDM";
    saved.setMeasureName(measureName);
    saved.setCqlLibraryName(libraryName);
    saved.setModel(ModelType.QDM_5_6.toString());
    saved.setEcqmTitle(ecqmTitle);
    saved.setVersionId(measureId);
    when(measureService.createMeasure(
            any(Measure.class), anyString(), anyString(), any(Boolean.class)))
        .thenReturn(saved);

    final String measureAsJson =
        "{\"measureName\": \"%s\",\"measureSetId\":\"%s\", \"cqlLibraryName\": \"%s\" , \"ecqmTitle\": \"%s\", \"model\": \"%s\", \"versionId\":\"%s\", \"scoring\":\"Cohort\"}"
            .formatted(
                measureName,
                measureSetId,
                libraryName,
                ecqmTitle,
                ModelType.QDM_5_6.toString(),
                measureId);

    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", TEST_USER_ID)
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.measureName").value(measureName))
        .andExpect(jsonPath("$.cqlLibraryName").value(libraryName))
        .andExpect(jsonPath("$.ecqmTitle").value(ecqmTitle))
        .andExpect(jsonPath("$.model").value(ModelType.QDM_5_6.toString()))
        .andExpect(jsonPath("$.id").value(measureId))
        .andExpect(jsonPath("$.versionId").value(measureId));

    verify(measureService, times(1))
        .createMeasure(
            measureArgumentCaptor.capture(), anyString(), anyString(), any(Boolean.class));
    verifyNoMoreInteractions(measureRepository);
    Measure savedMeasure = measureArgumentCaptor.getValue();
    assertEquals(measureName, savedMeasure.getMeasureName());
    assertEquals(libraryName, savedMeasure.getCqlLibraryName());
    assertEquals(ecqmTitle, savedMeasure.getEcqmTitle());
    assertEquals(ModelType.QDM_5_6.toString(), savedMeasure.getModel());
    assertNotEquals(measureId, savedMeasure.getId());
  }

  @Test
  public void testNewMeasureFailsForMeasureSetIdRequired() throws Exception {
    Measure saved = new Measure();
    String measureId = "id123";
    saved.setId(measureId);
    String measureName = "SavedMeasure";
    String libraryName = "Lib1";
    String ecqmTitle = "ecqmTitle";
    saved.setMeasureName(measureName);
    saved.setCqlLibraryName(libraryName);
    saved.setModel(MODEL);
    saved.setEcqmTitle(ecqmTitle);
    saved.setVersionId(measureId);
    when(measureRepository.save(any(Measure.class))).thenReturn(saved);
    doNothing().when(measureService).checkDuplicateCqlLibraryName(any(String.class));

    final String measureAsJson =
        "{\"measureName\": \"%s\", \"cqlLibraryName\": \"%s\" , \"ecqmTitle\": \"%s\", \"model\": \"%s\", \"versionId\":\"%s\"}"
            .formatted(measureName, libraryName, ecqmTitle, MODEL, measureId);

    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.measureSetId").value("Measure Set ID is required."));

    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testUpdateMeasureFailsIfDuplicatedLibraryName() throws Exception {
    Measure priorMeasure = new Measure();
    priorMeasure.setId("id0");
    priorMeasure.setMeasureName("TestMeasure");
    priorMeasure.setCqlLibraryName("TestMeasureLibrary");
    priorMeasure.setModel(MODEL);
    priorMeasure.setEcqmTitle("ecqmTitle");
    priorMeasure.setVersionId(priorMeasure.getId());
    priorMeasure.setMeasureSetId("measureSetId");
    priorMeasure.setMeasureMetaData(MeasureMetaData.builder().draft(true).build());
    when(measureService.findMeasureById(anyString())).thenReturn(priorMeasure);
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));

    Measure existingMeasure = new Measure();
    existingMeasure.setId("id1");
    existingMeasure.setMeasureName("ExistingMeasure");
    existingMeasure.setCqlLibraryName("ExistingMeasureLibrary");
    existingMeasure.setEcqmTitle("ecqmTitle");
    existingMeasure.setVersionId(existingMeasure.getId());

    when(measureService.updateMeasure(
            any(Measure.class), anyString(), any(Measure.class), anyString()))
        .thenThrow(
            new DuplicateKeyException(
                "cqlLibraryName", "CQL library with given name already exists."));

    final String updatedMeasureAsJson =
        "{\"id\": \"%s\",\"measureName\": \"%s\", \"cqlLibraryName\": \"%s\", \"ecqmTitle\": \"%s\", \"model\":\"%s\",\"versionId\":\"%s\",\"measureSetId\":\"%s\"}"
            .formatted(
                priorMeasure.getId(),
                priorMeasure.getMeasureName(),
                existingMeasure.getCqlLibraryName(),
                priorMeasure.getEcqmTitle(),
                priorMeasure.getModel(),
                priorMeasure.getVersionId(),
                priorMeasure.getMeasureSetId());
    mockMvc
        .perform(
            put("/measures/" + priorMeasure.getId())
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(updatedMeasureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName")
                .value("CQL library with given name already exists."));

    verify(measureService, times(1)).findMeasureById(eq(priorMeasure.getId()));
    verify(measureService, times(1))
        .updateMeasure(eq(priorMeasure), anyString(), any(Measure.class), anyString());
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testUpdateMeasureFailsIfInvalidVersionId() throws Exception {
    Measure priorMeasure = new Measure();
    priorMeasure.setId("id0");
    priorMeasure.setMeasureName("TestMeasure");
    priorMeasure.setCqlLibraryName("TestMeasureLibrary");
    priorMeasure.setModel(MODEL);
    priorMeasure.setEcqmTitle("ecqmTitle");
    priorMeasure.setMeasureSetId("measureSetId");
    priorMeasure.setVersionId(priorMeasure.getId());
    priorMeasure.setMeasureMetaData(MeasureMetaData.builder().draft(true).build());
    when(measureService.findMeasureById(anyString())).thenReturn(priorMeasure);
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));

    Measure existingMeasure = new Measure();
    existingMeasure.setId("id0");
    existingMeasure.setMeasureName("ExistingMeasure");
    existingMeasure.setCqlLibraryName("ExistingMeasureLibrary");
    existingMeasure.setEcqmTitle("ecqmTitle");
    existingMeasure.setMeasureSetId("measureSetId");
    existingMeasure.setVersionId("newVersionID");

    when(measureService.updateMeasure(
            any(Measure.class), anyString(), any(Measure.class), anyString()))
        .thenThrow(new InvalidVersionIdException("newVersionId"));

    final String updatedMeasureAsJson =
        "{\"id\": \"%s\",\"measureName\": \"%s\", \"cqlLibraryName\": \"%s\", \"ecqmTitle\": \"%s\", \"model\":\"%s\",\"versionId\":\"%s\",\"measureSetId\":\"%s\"}"
            .formatted(
                priorMeasure.getId(),
                priorMeasure.getMeasureName(),
                priorMeasure.getCqlLibraryName(),
                priorMeasure.getEcqmTitle(),
                priorMeasure.getModel(),
                existingMeasure.getVersionId(),
                priorMeasure.getMeasureSetId());
    mockMvc
        .perform(
            put("/measures/" + priorMeasure.getId())
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(updatedMeasureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest());

    verify(measureService, times(1)).findMeasureById(eq(priorMeasure.getId()));
    verify(measureService, times(1))
        .updateMeasure(any(Measure.class), anyString(), any(Measure.class), anyString());
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testUpdateQDMMeasureFailsIfScoringNotMatching() throws Exception {
    String qdmMeasureString =
        "{\n"
            + "    \"id\": \"testMeasureId\",\n"
            + "    \"model\": \"QDM v5.6\",\n"
            + "    \"measureSetId\":\"testMeasureSetId\",\n"
            + "    \"cqlLibraryName\": \"TestLibraryName\",\n"
            + "    \"ecqmTitle\":  \"testEcqmTitle\",\n"
            + "    \"measureName\": \"test QDM measure\",\n"
            + "    \"versionId\": \"0.0.000\",    \n"
            + "    \"scoring\": \"Proportion\",\n"
            + "    \"groups\": [\n"
            + "        {\n"
            + "            \"populationBasis\": \"boolean\",\n"
            + "            \"scoring\":\"Cohort\",\n"
            + "            \"populations\":[\n"
            + "                {\n"
            + "                    \"id\":\"4b990763-860b-4ad5-aa05-f23bceb43618\",\n"
            + "                    \"name\":\"initialPopulation\",\n"
            + "                    \"definition\":\"boolIpp\",\n"
            + "                    \"associationType\":null,\n"
            + "                    \"description\":\"\"\n"
            + "                }\n"
            + "            ],\n"
            + "            \"measureGroupTypes\": [\"Outcome\"]\n"
            + "            \n"
            + "        },\n"
            + "        {\n"
            + "            \"populationBasis\": \"boolean\",\n"
            + "            \"scoring\":\"Cohort\",\n"
            + "            \"populations\":[\n"
            + "                {\n"
            + "                    \"id\":\"4b990763-860b-4ad5-aa05-f23bceb43619\",\n"
            + "                    \"name\":\"initialPopulation\",\n"
            + "                    \"definition\":\"boolIpp\",\n"
            + "                    \"associationType\":null,\n"
            + "                    \"description\":\"\"\n"
            + "                }\n"
            + "            ],\n"
            + "            \"measureGroupTypes\": [\"Outcome\"]\n"
            + "            \n"
            + "        }\n"
            + "    ]\n"
            + "}";
    mockMvc
        .perform(
            put("/measures/testMeasureId")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(qdmMeasureString)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest());
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testUpdateQDMMeasurePassesWithImprovementNotation() throws Exception {
    QdmMeasure saved = new QdmMeasure();
    String measureId = "id456";
    saved.setId(measureId);
    String measureName = "SavedMeasureQDM";
    String libraryName = "QDMLib1";
    String ecqmTitle = "ecqmTitleQDM";
    String measureSetId = "cooltimeQDM";
    saved.setMeasureName(measureName);
    saved.setCqlLibraryName(libraryName);
    saved.setModel(ModelType.QDM_5_6.toString());
    saved.setEcqmTitle(ecqmTitle);
    saved.setVersionId(measureId);
    saved.setImprovementNotation("Other");
    saved.setImprovementNotationDescription("TestingOther");
    saved.setMeasureMetaData(MeasureMetaData.builder().draft(true).build());
    when(measureService.findMeasureById(anyString())).thenReturn(saved);
    when(measureService.updateMeasure(
            any(Measure.class), anyString(), any(Measure.class), anyString()))
        .thenReturn(saved);

    final String measureAsJson =
        "{\"measureName\": \"%s\",\"measureSetId\":\"%s\", \"cqlLibraryName\": \"%s\" , \"ecqmTitle\": \"%s\", \"model\": \"%s\", \"id\":\"%s\", \"versionId\":\"%s\", \"scoring\":\"Cohort\",\"improvementNotation\": \"%s\",\"improvementNotationDescription\": \"%s\"}"
            .formatted(
                measureName,
                measureSetId,
                libraryName,
                ecqmTitle,
                ModelType.QDM_5_6.toString(),
                measureId,
                measureId,
                "Other",
                "TestingOther");
    mockMvc
        .perform(
            put("/measures/id456")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.improvementNotation").value("Other"))
        .andExpect(jsonPath("$.improvementNotationDescription").value("TestingOther"));
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testUpdateQDMMeasureFailsIfBaseConfigurationTypesAreInvalid() throws Exception {
    String qdmMeasureString =
        "{\n"
            + "    \"id\": \"testMeasureId\",\n"
            + "    \"model\": \"QDM v5.6\",\n"
            + "    \"measureSetId\":\"testMeasureSetId\",\n"
            + "    \"cqlLibraryName\": \"TestLibraryName\",\n"
            + "    \"ecqmTitle\":  \"testEcqmTitle\",\n"
            + "    \"measureName\": \"test QDM measure\",\n"
            + "    \"versionId\": \"0.0.000\",    \n"
            + "    \"scoring\": \"Proportion\",\n"
            + "    \"baseConfigurationTypes\": [\n"
            + "            \"invalidBaseConfigurationType\", \"\"   \n"
            + "    ]\n"
            + "}";
    mockMvc
        .perform(
            put("/measures/testMeasureId")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(qdmMeasureString)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest());
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testNewMeasureNoUnderscore() throws Exception {
    final String measureAsJson =
        "{ \"id\": \"m1234\", \"measureName\":\"A_Name\", \"cqlLibraryName\":\"ALib\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\", \"model\":\"QI-Core v4.1.1\" }";
    mockMvc
        .perform(
            put("/measures/m1234")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.measureName")
                .value("Measure Name can not contain underscores."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewMeasureFailsIfCqlLibaryNameStartsWithLowerCase() throws Exception {
    final String measureAsJson =
        "{ \"measureName\":\"AName\", \"cqlLibraryName\":\"aLib\", \"ecqmTitle\":\"ecqmTitle\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\", \"model\":\"QI-Core v4.1.1\" }";
    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.measure").value(LIBRARY_NAME_VALIDATION_ERROR));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testUpdateMeasureFailsIfCqlLibaryNameStartsWithLowerCase() throws Exception {
    final String measureAsJson =
        "{ \"id\": \"m1234\", \"measureName\":\"AName\", \"cqlLibraryName\":\"aLib\", \"ecqmTitle\":\"ecqmTitle\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\", \"model\":\"QI-Core v4.1.1\" }";
    mockMvc
        .perform(
            put("/measures/m1234")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.measure").value(LIBRARY_NAME_VALIDATION_ERROR));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewMeasureFailsIfCqlLibraryNameHasQuotes() throws Exception {
    final String measureAsJson =
        "{ \"measureName\":\"AName\", \"cqlLibraryName\":\"ALi''b\", \"ecqmTitle\":\"ecqmTitle\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\", \"model\":\"QI-Core v4.1.1\" }";
    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.measure").value(LIBRARY_NAME_VALIDATION_ERROR));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewMeasureFailsIfCqlLibraryNameHasUnderscore() throws Exception {
    final String measureAsJson =
        "{ \"measureName\":\"AName\", \"cqlLibraryName\":\"ALi_'b\", \"ecqmTitle\":\"ecqmTitle\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\", \"model\":\"QI-Core v4.1.1\" }";
    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.measure").value(LIBRARY_NAME_VALIDATION_ERROR));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void
      testUpdateMeasurePassesIfCqlLibraryNameStartsWithCapitalCharAndFollowedByAlphaNumeric()
          throws Exception {
    String measureId = "id123";
    Measure saved = new Measure();
    saved.setId(measureId);
    String measureName = "SavedMeasure";
    String libraryName = "ALi12aAccllklk6U";
    String ecqmTitle = "ecqmTitle";
    String measureSetId = "measureSetId";
    saved.setMeasureName(measureName);
    saved.setCqlLibraryName(libraryName);
    saved.setEcqmTitle(ecqmTitle);
    saved.setModel(MODEL);
    saved.setVersionId(measureId);
    saved.setMeasureSetId(measureSetId);
    saved.setMeasureMetaData(MeasureMetaData.builder().draft(true).build());

    when(measureService.findMeasureById(anyString())).thenReturn(saved);
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));
    when(measureService.updateMeasure(
            any(Measure.class), anyString(), any(Measure.class), anyString()))
        .thenReturn(saved);

    final String measureAsJson =
        "{ \"id\": \"%s\", \"measureName\":\"%s\", \"cqlLibraryName\":\"%s\" , \"ecqmTitle\":\"%s\", \"model\":\"%s\", \"versionId\":\"%s\",\"measureSetId\":\"%s\"}"
            .formatted(
                measureId, measureName, libraryName, ecqmTitle, MODEL, measureId, measureSetId);
    mockMvc
        .perform(
            put("/measures/" + measureId)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.measureName").value(measureName));

    verify(measureService, times(1)).findMeasureById(eq(measureId));
    verify(measureService, times(1))
        .updateMeasure(any(Measure.class), anyString(), any(Measure.class), anyString());
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testUpdateMeasureReturnsBadRequestWhenIdsDoNotMatch() throws Exception {
    String measureId = "id123";
    Measure saved = new Measure();
    saved.setId(measureId);
    String measureName = "SavedMeasure";
    String libraryName = "ALi12aAccllklk6U";
    saved.setMeasureName(measureName);
    saved.setCqlLibraryName(libraryName);
    saved.setModel(MODEL);
    String scoring = MeasureScoring.CONTINUOUS_VARIABLE.toString();

    when(measureRepository.findById(eq(measureId))).thenReturn(Optional.of(saved));
    when(measureRepository.save(any(Measure.class))).thenReturn(saved);

    final String measureAsJson =
        "{ \"id\": \"id1234\", \"measureName\":\"%s\", \"cqlLibraryName\":\"%s\", \"model\":\"%s\", \"measureScoring\":\"%s\"}"
            .formatted(measureName, libraryName, MODEL, scoring);
    mockMvc
        .perform(
            put("/measures/" + measureId)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testUpdateMeasureReturnsBadRequestWhenIdInObjectIsNull() throws Exception {
    String measureId = "id123";
    Measure saved = new Measure();
    saved.setId(measureId);
    String measureName = "SavedMeasure";
    String libraryName = "ALi12aAccllklk6U";
    saved.setMeasureName(measureName);
    saved.setCqlLibraryName(libraryName);
    saved.setModel(MODEL);
    String scoring = MeasureScoring.CONTINUOUS_VARIABLE.toString();

    when(measureRepository.findById(eq(measureId))).thenReturn(Optional.of(saved));
    when(measureRepository.save(any(Measure.class))).thenReturn(saved);

    final String measureAsJson =
        "{ \"id\": null, \"measureName\":\"%s\", \"cqlLibraryName\":\"%s\", \"model\":\"%s\", \"measureScoring\":\"%s\"}"
            .formatted(measureName, libraryName, MODEL, scoring);
    mockMvc
        .perform(
            put("/measures/" + measureId)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewMeasureFailsWithInvalidModelType() throws Exception {
    final String measureAsJson =
        "{ \"measureName\":\"TestName\", \"cqlLibraryName\":\"TEST1\", \"model\":\"Test\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\" }";
    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewMeasureFailedWithoutSecurityToken() throws Exception {
    final String measureAsJson =
        "{\"measureName\": \"%s\", \"cqlLibraryName\": \"%s\", \"model\": \"%s\", \"measureScoring\": \"%s\" }"
            .formatted(
                "testMeasureName",
                "testLibraryName",
                ModelType.QI_CORE.toString(),
                MeasureScoring.PROPORTION.toString());

    MvcResult result =
        mockMvc
            .perform(
                post("/measure")
                    .content(measureAsJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andReturn();
    String resultStr = result.getResponse().getErrorMessage();
    assertNull(resultStr);
  }

  @Test
  public void testNewQdmMeasureFailsWithValidIntendedVenueOfEh() throws Exception {
    CodeConcept eh =
        CodeConcept.builder()
            .code("eh")
            .codeSystem("http://hl7.org/fhir/us/cqfmeasures/CodeSystem/intended-venue-codes")
            .display("EH")
            .definition(
                "An eligible hospital is an acute care facility that is eligible to participate in a quality measurement initiative.")
            .build();

    Measure measure =
        Measure.builder()
            .id("testId")
            .model(ModelType.QDM_5_6.getValue())
            .measureSetId("testMeasureSetId")
            .cqlLibraryName("TestCqlLibraryName")
            .ecqmTitle("testECqm")
            .measureName("testMeasureName")
            .versionId("0.0.000")
            .measureMetaData(MeasureMetaData.builder().intendedVenue(eh).build())
            .build();

    final String measureAsJson = toJsonString(measure);

    when(measureService.createMeasure(
            any(Measure.class), anyString(), anyString(), any(Boolean.class)))
        .thenReturn(measure);

    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", TEST_USER_ID)
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.measure").value("Intended Venue is invalid"));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewQiCoreMeasurePassesWithValidIntendedVenueOfEh() throws Exception {
    CodeConcept eh =
        CodeConcept.builder()
            .code("eh")
            .codeSystem("http://hl7.org/fhir/us/cqfmeasures/CodeSystem/intended-venue-codes")
            .display("EH")
            .definition(
                "An eligible hospital is an acute care facility that is eligible to participate in a quality measurement initiative.")
            .build();

    Measure measure =
        Measure.builder()
            .id("testId")
            .model(String.valueOf(ModelType.QI_CORE))
            .measureSetId("testMeasureSetId")
            .cqlLibraryName("TestCqlLibraryName")
            .ecqmTitle("testECqm")
            .measureName("testMeasureName")
            .versionId("0.0.000")
            .measureMetaData(MeasureMetaData.builder().intendedVenue(eh).build())
            .build();

    final String measureAsJson = toJsonString(measure);

    when(measureService.createMeasure(
            any(Measure.class), anyString(), anyString(), any(Boolean.class)))
        .thenReturn(measure);

    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", TEST_USER_ID)
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated());

    verify(measureService, times(1))
        .createMeasure(
            measureArgumentCaptor.capture(), anyString(), anyString(), any(Boolean.class));
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testNewQiCoreMeasurePassesWithValidIntendedVenueOfEc() throws Exception {
    CodeConcept ec =
        CodeConcept.builder()
            .code("ec")
            .codeSystem("http://hl7.org/fhir/us/cqfmeasures/CodeSystem/intended-venue-codes")
            .display("EC")
            .definition(
                "An eligible clinician is a clinician who is eligible to participate in a quality measurement initiative.")
            .build();

    Measure measure =
        Measure.builder()
            .id("testId")
            .model(String.valueOf(ModelType.QI_CORE))
            .measureSetId("testMeasureSetId")
            .cqlLibraryName("TestCqlLibraryName")
            .ecqmTitle("testECqm")
            .measureName("testMeasureName")
            .versionId("0.0.000")
            .measureMetaData(MeasureMetaData.builder().intendedVenue(ec).purpose("Test").build())
            .build();

    final String measureAsJson = toJsonString(measure);

    when(measureService.createMeasure(
            any(Measure.class), anyString(), anyString(), any(Boolean.class)))
        .thenReturn(measure);

    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", TEST_USER_ID)
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated());

    verify(measureService, times(1))
        .createMeasure(
            measureArgumentCaptor.capture(), anyString(), anyString(), any(Boolean.class));
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testNewQiCoreMeasureFailsWithInvalidIntendedVenueOfEh() throws Exception {
    CodeConcept invalidEh =
        CodeConcept.builder()
            .code("invalidEh")
            .codeSystem("http://hl7.org/fhir/us/cqfmeasures/CodeSystem/intended-venue-codes")
            .display("EH")
            .definition(
                "An eligible hospital is an acute care facility that is eligible to participate in a quality measurement initiative.")
            .build();

    Measure measure =
        Measure.builder()
            .id("testId")
            .model(String.valueOf(ModelType.QI_CORE))
            .measureSetId("testMeasureSetId")
            .cqlLibraryName("TestCqlLibraryName")
            .ecqmTitle("testECqm")
            .measureName("testMeasureName")
            .versionId("0.0.000")
            .measureMetaData(MeasureMetaData.builder().intendedVenue(invalidEh).build())
            .build();

    final String measureAsJson = toJsonString(measure);

    when(measureService.createMeasure(
            any(Measure.class), anyString(), anyString(), any(Boolean.class)))
        .thenReturn(measure);

    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", TEST_USER_ID)
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.measure").value("Intended Venue is invalid"));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewQiCoreMeasureFailsWithInvalidIntendedVenueOfEc() throws Exception {
    CodeConcept invalidEc =
        CodeConcept.builder()
            .code("invalidEc")
            .codeSystem("http://hl7.org/fhir/us/cqfmeasures/CodeSystem/intended-venue-codes")
            .display("EH")
            .definition(
                "An eligible hospital is an acute care facility that is eligible to participate in a quality measurement initiative.")
            .build();

    Measure measure =
        Measure.builder()
            .id("testId")
            .model(String.valueOf(ModelType.QI_CORE))
            .measureSetId("testMeasureSetId")
            .cqlLibraryName("TestCqlLibraryName")
            .ecqmTitle("testECqm")
            .measureName("testMeasureName")
            .versionId("0.0.000")
            .measureMetaData(MeasureMetaData.builder().intendedVenue(invalidEc).build())
            .build();

    final String measureAsJson = toJsonString(measure);

    when(measureService.createMeasure(
            any(Measure.class), anyString(), anyString(), any(Boolean.class)))
        .thenReturn(measure);

    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", TEST_USER_ID)
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.measure").value("Intended Venue is invalid"));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testGetAllMeasuresNoQueryParams() throws Exception {
    MeasureListDTO m1 =
        MeasureListDTO.builder().active(true).measureName("Measure1").model(MODEL).build();
    MeasureListDTO m2 =
        MeasureListDTO.builder().active(true).measureName("Measure2").model(MODEL).build();
    MeasureListDTO m3 =
        MeasureListDTO.builder().active(true).measureName("Measure3").model(MODEL).build();

    Page<MeasureListDTO> allMeasures = new PageImpl<>(List.of(m1, m2, m3));

    when(measureService.getMeasuresByCriteria(
            eq(null), eq(null), any(Pageable.class), eq(TEST_USER_ID)))
        .thenReturn(allMeasures);

    MvcResult result =
        mockMvc
            .perform(get("/measures").with(user(TEST_USER_ID)).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    assertTrue(resultStr.contains("Measure1"));
    assertTrue(resultStr.contains("Measure2"));
    assertTrue(resultStr.contains("Measure3"));

    verify(measureService, times(1))
        .getMeasuresByCriteria(eq(null), eq(null), any(Pageable.class), eq(TEST_USER_ID));
    verifyNoMoreInteractions(measureService);
  }

  @Test
  public void testGetAllMeasures() throws Exception {
    MeasureListDTO m1 =
        MeasureListDTO.builder().active(true).measureName("Measure1").model(MODEL).build();
    MeasureListDTO m2 =
        MeasureListDTO.builder().active(true).measureName("Measure2").model(MODEL).build();
    MeasureListDTO m3 =
        MeasureListDTO.builder().active(true).measureName("Measure3").model(MODEL).build();

    Page<MeasureListDTO> allMeasures = new PageImpl<>(List.of(m1, m2, m3));

    when(measureService.getMeasuresByCriteria(
            eq(null), eq(List.of(OwnershipType.ALL)), any(Pageable.class), eq(TEST_USER_ID)))
        .thenReturn(allMeasures);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures")
                    .with(user(TEST_USER_ID))
                    .queryParam("ownershipTypes", "ALL")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

    String resultStr = result.getResponse().getContentAsString();
    assertTrue(resultStr.contains("Measure1"));
    assertTrue(resultStr.contains("Measure2"));
    assertTrue(resultStr.contains("Measure3"));

    verify(measureService, times(1))
        .getMeasuresByCriteria(
            eq(null), eq(List.of(OwnershipType.ALL)), any(Pageable.class), eq(TEST_USER_ID));

    verifyNoMoreInteractions(measureService);
  }

  @Test
  public void getAllMeasuresWithCustomPaging() throws Exception {
    MeasureListDTO m1 =
        MeasureListDTO.builder().active(true).measureName("Measure1").model(MODEL).build();
    MeasureListDTO m2 =
        MeasureListDTO.builder().active(true).measureName("Measure2").model(MODEL).build();
    MeasureListDTO m3 =
        MeasureListDTO.builder().active(true).measureName("Measure3").model(MODEL).build();

    Page<MeasureListDTO> allMeasures = new PageImpl<>(List.of(m1, m2, m3));
    when(measureService.getMeasuresByCriteria(
            eq(null), eq(List.of(OwnershipType.ALL)), any(Pageable.class), eq(TEST_USER_ID)))
        .thenReturn(allMeasures);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures")
                    .with(user(TEST_USER_ID))
                    .queryParam("ownershipTypes", "ALL")
                    .queryParam("limit", "25")
                    .queryParam("page", "3")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    assertTrue(resultStr.contains("Measure1"));
    assertTrue(resultStr.contains("Measure2"));
    assertTrue(resultStr.contains("Measure3"));

    verify(measureService, times(1))
        .getMeasuresByCriteria(
            eq(null),
            eq(List.of(OwnershipType.ALL)),
            pageRequestCaptor.capture(),
            eq(TEST_USER_ID));

    PageRequest pageRequestValue = pageRequestCaptor.getValue();
    assertEquals(25, pageRequestValue.getPageSize());
    assertEquals(3, pageRequestValue.getPageNumber());

    verifyNoMoreInteractions(measureService);
  }

  @Test
  public void testGetOwnedMeasures() throws Exception {
    MeasureListDTO m1 =
        MeasureListDTO.builder().active(true).measureName("Measure1").model(MODEL).build();
    MeasureListDTO m2 =
        MeasureListDTO.builder()
            .active(true)
            .measureName("Measure2")
            .model(MODEL)
            .active(true)
            .build();

    final Page<MeasureListDTO> measures = new PageImpl<>(List.of(m1, m2));

    when(measureService.getMeasuresByCriteria(
            eq(null), eq(List.of(OwnershipType.OWNED)), any(Pageable.class), eq(TEST_USER_ID)))
        .thenReturn(measures);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures")
                    .with(user(TEST_USER_ID))
                    .queryParam("ownershipTypes", "OWNED")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    assertTrue(resultStr.contains("Measure1"));
    assertTrue(resultStr.contains("Measure2"));

    verify(measureService, times(1))
        .getMeasuresByCriteria(
            eq(null), eq(List.of(OwnershipType.OWNED)), any(Pageable.class), eq(TEST_USER_ID));
    verifyNoMoreInteractions(measureService);
  }

  @Test
  public void testGetSharedMeasuresList() throws Exception {
    MeasureListDTO m1 =
        MeasureListDTO.builder().active(true).measureName("Measure1").model(MODEL).build();
    MeasureListDTO m2 =
        MeasureListDTO.builder()
            .active(true)
            .measureName("Measure2")
            .model(MODEL)
            .active(true)
            .build();

    final Page<MeasureListDTO> measures = new PageImpl<>(List.of(m1, m2));

    when(measureService.getMeasuresByCriteria(
            eq(null), eq(List.of(OwnershipType.SHARED)), any(Pageable.class), eq(TEST_USER_ID)))
        .thenReturn(measures);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures")
                    .with(user(TEST_USER_ID))
                    .queryParam("ownershipTypes", "SHARED")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    assertTrue(resultStr.contains("Measure1"));
    assertTrue(resultStr.contains("Measure2"));

    verify(measureService, times(1))
        .getMeasuresByCriteria(
            eq(null), eq(List.of(OwnershipType.SHARED)), any(Pageable.class), eq(TEST_USER_ID));
    verifyNoMoreInteractions(measureService);
  }

  @Test
  public void testCreateGroup() throws Exception {
    Group group =
        Group.builder()
            .scoring("Cohort")
            .id("test-id")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1")))
            .measureGroupTypes(List.of(MeasureGroupTypes.PROCESS))
            .build();
    final String groupJson =
        "{\"scoring\":\"Cohort\",\"populations\":[{\"id\":\"id-1\",\"name\":\"initialPopulation\",\"definition\":\"Initial Population\"}],\"measureGroupTypes\":[\"Process\"],\"populationBasis\": \"boolean\"}";
    when(groupService.createOrUpdateGroup(any(Group.class), any(String.class), any(String.class)))
        .thenReturn(group);

    mockMvc
        .perform(
            post("/measures/1234/groups")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(groupJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isCreated());

    verify(groupService, times(1))
        .createOrUpdateGroup(
            groupCaptor.capture(), measureIdCaptor.capture(), usernameCaptor.capture());

    Group persistedGroup = groupCaptor.getValue();
    assertEquals(group.getScoring(), persistedGroup.getScoring());
    assertEquals("Initial Population", persistedGroup.getPopulations().get(0).getDefinition());
    assertEquals(
        PopulationType.INITIAL_POPULATION, persistedGroup.getPopulations().get(0).getName());
    assertEquals(group.getMeasureGroupTypes().get(0), persistedGroup.getMeasureGroupTypes().get(0));
  }

  @Test
  public void testUpdateGroup() throws Exception {
    String updateIppDefinition = "FactorialOfFive";
    Group group =
        Group.builder()
            .scoring("Cohort")
            .id("test-id")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        updateIppDefinition,
                        null,
                        null,
                        "IntialPopulation_1")))
            .measureGroupTypes(List.of(MeasureGroupTypes.PROCESS))
            .build();

    final String groupJson =
        "{\"id\":\"test-id\",\"scoring\":\"Cohort\",\"populations\":[{\"id\":\"id-2\",\"name\":\"initialPopulation\",\"definition\":\"FactorialOfFive\"}],\"measureGroupTypes\":[\"Process\"], \"populationBasis\": \"boolean\"}";
    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("1234").build());
    when(groupService.createOrUpdateGroup(any(Group.class), any(String.class), any(String.class)))
        .thenReturn(group);

    mockMvc
        .perform(
            put("/measures/1234/groups")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(groupJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk());

    verify(groupService, times(1))
        .createOrUpdateGroup(
            groupCaptor.capture(), measureIdCaptor.capture(), usernameCaptor.capture());

    Group persistedGroup = groupCaptor.getValue();
    assertEquals(group.getScoring(), persistedGroup.getScoring());
    assertEquals(updateIppDefinition, persistedGroup.getPopulations().get(0).getDefinition());
    assertEquals(
        PopulationType.INITIAL_POPULATION, persistedGroup.getPopulations().get(0).getName());
    assertEquals(group.getMeasureGroupTypes().get(0), persistedGroup.getMeasureGroupTypes().get(0));
  }

  @Test
  public void testUpdateGroupIfPopulationDefinitionReturnTypesAreInvalid() throws Exception {
    final String groupJson =
        "{\"id\":\"test-id\",\"scoring\":\"Cohort\",\"populations\":[{\"id\":\"id-2\",\"name\":\"initialPopulation\",\"definition\":\"FactorialOfFive\"}],\"measureGroupTypes\":[\"Process\"], \"populationBasis\": \"boolean\"}";
    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("1234").build());
    when(groupService.createOrUpdateGroup(any(Group.class), any(String.class), any(String.class)))
        .thenThrow(new InvalidReturnTypeException("Initial Population"));

    mockMvc
        .perform(
            put("/measures/1234/groups")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(groupJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Return type for the CQL definition selected for the Initial Population does not match with population basis."));

    verify(groupService, times(1))
        .createOrUpdateGroup(
            groupCaptor.capture(), measureIdCaptor.capture(), usernameCaptor.capture());
  }

  @Test
  public void testUpdateGroupIfPopulationFunctionReturnTypesAreInvalid() throws Exception {
    final String groupJson =
        "{\"scoring\":\"Cohort\",\"populations\":[{\"id\":\"id-1\",\"name\":\"initialPopulation\",\"definition\":\"Initial Population\"}],\"measureGroupTypes\":[\"Process\"],\"populationBasis\": \"boolean\"}";
    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("1234").build());
    when(groupService.createOrUpdateGroup(any(Group.class), any(String.class), any(String.class)))
        .thenThrow(
            new InvalidReturnTypeException(
                "Selected observation function '%s' can not have parameters", "fun"));

    mockMvc
        .perform(
            put("/measures/1234/groups")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(groupJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value("Selected observation function 'fun' can not have parameters"));

    verify(groupService, times(1))
        .createOrUpdateGroup(
            groupCaptor.capture(), measureIdCaptor.capture(), usernameCaptor.capture());
  }

  @Test
  void getMeasureGroupsReturnsNotFound() throws Exception {
    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());
    mockMvc
        .perform(
            get("/measures/1234/groups")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
    verify(measureRepository, times(1)).findById(eq("1234"));
    verifyNoInteractions(measureService);
  }

  @Test
  void testGetMeasureBundleReturnsEmptyArray() throws Exception {
    Measure measure = new Measure();
    measure.setCreatedBy(TEST_USER_ID);
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    mockMvc
        .perform(
            get("/measures/1234/groups")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("[]"));
    verify(measureRepository, times(1)).findById(eq("1234"));
    verifyNoInteractions(measureService);
  }

  @Test
  void testGetMeasureBundleReturnsGroupsArray() throws Exception {
    Measure measure =
        Measure.builder()
            .createdBy(TEST_USER_ID)
            .groups(
                List.of(
                    Group.builder()
                        .groupDescription("Group1")
                        .scoring(MeasureScoring.RATIO.toString())
                        .build()))
            .build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    mockMvc
        .perform(
            get("/measures/1234/groups")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].groupDescription").value("Group1"))
        .andExpect(jsonPath("$[0].scoring").value("Ratio"));
    verify(measureRepository, times(1)).findById(eq("1234"));
    verifyNoInteractions(measureService);
  }

  @Test
  public void testSearchAllMeasuresByMeasureNameOrEcqmTitleNoQueryParams() throws Exception {
    MeasureListDTO m1 =
        MeasureListDTO.builder().measureName("measure-1").ecqmTitle("test-ecqm-title-1").build();
    MeasureListDTO m2 =
        MeasureListDTO.builder().measureName("measure-2").ecqmTitle("test-ecqm-title-1").build();
    MeasureListDTO m3 =
        MeasureListDTO.builder().measureName("measure-3").ecqmTitle("test-ecqm-title-3").build();

    Page<MeasureListDTO> allMeasures = new PageImpl<>(List.of(m1, m2, m3));

    doReturn(allMeasures)
        .when(measureService)
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class), eq(null), any(Pageable.class), eq(TEST_USER_ID));
    MvcResult result =
        mockMvc
            .perform(
                put("/measures/searches")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .content(
                        objectMapper.writeValueAsString(
                            MeasureSearchCriteria.builder().searchField("measure").build()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    assertTrue(resultStr.contains("measure-1"));
    assertTrue(resultStr.contains("measure-2"));
    assertTrue(resultStr.contains("measure-3"));

    verify(measureService, times(1))
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class), eq(null), any(Pageable.class), eq(TEST_USER_ID));
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testSearchAllMeasuresByMeasureNameOrEcqmTitle() throws Exception {
    MeasureListDTO m1 =
        MeasureListDTO.builder().measureName("measure-1").ecqmTitle("test-ecqm-title-1").build();
    MeasureListDTO m2 =
        MeasureListDTO.builder().measureName("measure-2").ecqmTitle("test-ecqm-title-1").build();
    MeasureListDTO m3 =
        MeasureListDTO.builder().measureName("measure-3").ecqmTitle("test-ecqm-title-3").build();

    Page<MeasureListDTO> allMeasures = new PageImpl<>(List.of(m1, m2, m3));

    doReturn(allMeasures)
        .when(measureService)
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.ALL)),
            any(Pageable.class),
            eq(TEST_USER_ID));
    MvcResult result =
        mockMvc
            .perform(
                put("/measures/searches")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .queryParam("ownershipTypes", "ALL")
                    .queryParam("limit", "8")
                    .queryParam("page", "1")
                    .content(
                        objectMapper.writeValueAsString(
                            MeasureSearchCriteria.builder().searchField("ecqm").build()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    assertTrue(resultStr.contains("measure-1"));
    assertTrue(resultStr.contains("measure-2"));
    assertTrue(resultStr.contains("measure-3"));

    verify(measureService, times(1))
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.ALL)),
            any(Pageable.class),
            eq(TEST_USER_ID));
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testSearchMeasuresByMeasureNameOrEcqmTitleFilterByCurrentUser() throws Exception {
    MeasureListDTO m1 =
        MeasureListDTO.builder().measureName("measure-1").ecqmTitle("test-ecqm-title-1").build();
    MeasureListDTO m2 =
        MeasureListDTO.builder().measureName("measure-2").ecqmTitle("test-ecqm-title-1").build();
    MeasureListDTO m3 =
        MeasureListDTO.builder().measureName("measure-3").ecqmTitle("test-ecqm-title-3").build();

    final Page<MeasureListDTO> measures = new PageImpl<>(List.of(m1, m2, m3));

    doReturn(measures)
        .when(measureService)
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.OWNED)),
            any(Pageable.class),
            eq(TEST_USER_ID));
    MvcResult result =
        mockMvc
            .perform(
                put("/measures/searches")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .queryParam("ownershipTypes", "OWNED")
                    .queryParam("limit", "8")
                    .queryParam("page", "1")
                    .content(
                        objectMapper.writeValueAsString(
                            MeasureSearchCriteria.builder().searchField("measure").build()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    assertTrue(resultStr.contains("measure-1"));
    assertTrue(resultStr.contains("measure-2"));
    assertTrue(resultStr.contains("measure-3"));

    verify(measureService, times(1))
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.OWNED)),
            any(Pageable.class),
            eq(TEST_USER_ID));

    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testCreateStratification() throws Exception {
    Stratification stratification = new Stratification();
    stratification.setCqlDefinition("Initial Population");
    stratification.setAssociation(PopulationType.INITIAL_POPULATION);
    stratification.setAssociations(List.of(PopulationType.INITIAL_POPULATION));
    final String stratificationJson =
        "{\n"
            + "    \"id\": \"id-1\",\n"
            + "    \"description\": \"\",\n"
            + "    \"cqlDefinition\": \"Initial Population\",\n"
            + "    \"association\": \"initialPopulation\",\n"
            + "    \"associations\": [\n"
            + "        \"initialPopulation\",\n"
            + "        \"numerator\"\n"
            + "    ]\n"
            + "}";
    when(groupService.createOrUpdateStratification(
            any(String.class), any(String.class), any(Stratification.class), any(String.class)))
        .thenReturn(stratification);

    mockMvc
        .perform(
            post("/measures/1234/groups/id-1/stratification")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(stratificationJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isCreated());

    verify(groupService, times(1))
        .createOrUpdateStratification(
            groupIdCaptor.capture(),
            measureIdCaptor.capture(),
            stratificationCaptor.capture(),
            usernameCaptor.capture());

    Stratification persistedStratification = stratificationCaptor.getValue();
    assertEquals(stratification.getCqlDefinition(), persistedStratification.getCqlDefinition());
    assertEquals(PopulationType.INITIAL_POPULATION, persistedStratification.getAssociation());
    assertEquals(2, persistedStratification.getAssociations().size());
    assertTrue(
        persistedStratification.getAssociations().contains(PopulationType.INITIAL_POPULATION));
  }

  @Test
  public void testUpdateStratification() throws Exception {
    Stratification stratification =
        Stratification.builder()
            .cqlDefinition("Initial Population")
            .association(PopulationType.INITIAL_POPULATION)
            .associations(List.of(PopulationType.INITIAL_POPULATION, PopulationType.NUMERATOR))
            .build();
    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("1234").build());
    final String stratificationJson =
        "{\n"
            + "    \"id\": \"id-1\",\n"
            + "    \"description\": \"\",\n"
            + "    \"cqlDefinition\": \"Initial Population\",\n"
            + "    \"association\": \"initialPopulation\",\n"
            + "    \"associations\": [\n"
            + "        \"initialPopulation\",\n"
            + "        \"numerator\"\n"
            + "    ]\n"
            + "}";
    when(groupService.createOrUpdateStratification(
            any(String.class), any(String.class), any(Stratification.class), any(String.class)))
        .thenReturn(stratification);

    mockMvc
        .perform(
            put("/measures/1234/groups/id-1/stratification")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(stratificationJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk());

    verify(groupService, times(1))
        .createOrUpdateStratification(
            groupIdCaptor.capture(),
            measureIdCaptor.capture(),
            stratificationCaptor.capture(),
            usernameCaptor.capture());

    Stratification persistedStratification = stratificationCaptor.getValue();
    assertEquals(stratification.getCqlDefinition(), persistedStratification.getCqlDefinition());
    assertEquals(PopulationType.INITIAL_POPULATION, persistedStratification.getAssociation());
    assertEquals(2, persistedStratification.getAssociations().size());
    assertTrue(
        persistedStratification.getAssociations().contains(PopulationType.INITIAL_POPULATION));
  }

  @Test
  void testGetLibraryUsage() throws Exception {
    String libraryName = "Helper";
    String owner = "john";
    LibraryUsage libraryUsage = LibraryUsage.builder().name(libraryName).owner(owner).build();
    when(measureService.findLibraryUsage(anyString())).thenReturn(List.of(libraryUsage));
    MvcResult result =
        mockMvc
            .perform(
                get("/measures/library/usage?libraryName=Test")
                    .with(user(TEST_USER_ID))
                    .with(csrf()))
            .andReturn();
    assertEquals(result.getResponse().getStatus(), HttpStatus.OK.value());
    JSONAssert.assertEquals(
        "[{\"name\":\"Helper\",\"version\":null,\"owner\":\"john\"}]",
        result.getResponse().getContentAsString(),
        JSONCompareMode.STRICT);
  }

  @Test
  public void testGetSharedMeasures() throws Exception {
    String measureId1 = "measureId1";
    String measureId2 = "measureId2";

    Instant fixedInstant = Instant.parse("2025-03-17T10:00:00Z");
    ZoneId utc = ZoneId.of("UTC");
    Clock fixedClock = Clock.fixed(fixedInstant, utc);

    List<String> measureIds = List.of(measureId1, measureId2);
    SharedUser sharedUser1 =
        SharedUser.builder()
            .userId("userId1")
            .displayName("John Doe (userId1)")
            .performedAt(fixedClock.instant())
            .build();
    SharedUser sharedUser2 =
        SharedUser.builder()
            .userId("userId2")
            .displayName("Jane Doe (userId2)")
            .performedAt(fixedClock.instant())
            .build();

    Map<String, List<SharedUser>> sharedMeasures = new HashMap<>();
    sharedMeasures.put(measureId1, List.of(sharedUser1));
    sharedMeasures.put(measureId2, List.of(sharedUser1, sharedUser2));

    doReturn(sharedMeasures).when(measureService).getSharedMeasures(eq(measureIds), anyString());

    mockMvc
        .perform(
            get(String.format("/measures/shared?measureIds=%s", String.join(",", measureIds)))
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    "{\"measureId1\":[{\"userId\":\"userId1\",\"displayName\":\"John Doe (userId1)\",\"performedAt\":\"2025-03-17T10:00:00Z\"}],\"measureId2\":[{\"userId\":\"userId1\",\"displayName\":\"John Doe (userId1)\",\"performedAt\":\"2025-03-17T10:00:00Z\"},{\"userId\":\"userId2\",\"displayName\":\"Jane Doe (userId2)\",\"performedAt\":\"2025-03-17T10:00:00Z\"}]}"));

    verify(measureService, times(1)).getSharedMeasures(eq(measureIds), anyString());
  }

  @Test
  public void testShareMeasures() throws Exception {
    AclSpecification aclSpecification1 = new AclSpecification();
    aclSpecification1.setUserId("userId1");
    aclSpecification1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    AclSpecification aclSpecification2 = new AclSpecification();
    aclSpecification2.setUserId("userId2");
    aclSpecification2.setRoles(Set.of(RoleEnum.SHARED_WITH));

    Map<String, List<AclSpecification>> measureIdToAclSpecification = new HashMap<>();
    measureIdToAclSpecification.put("measureId1", List.of(aclSpecification1));
    measureIdToAclSpecification.put("measureId2", List.of(aclSpecification1, aclSpecification2));

    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("measureId1").build());
    doReturn(measureIdToAclSpecification)
        .when(measureService)
        .shareMeasures(any(), anyString(), anyString());

    MvcResult result =
        mockMvc
            .perform(
                put("/measures/shared")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .content("{\"measureId1\": [\"userId1\"],\"measureId2\": [\"userId1\"]}")
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    verify(measureService, times(1)).shareMeasures(any(), anyString(), anyString());
    JSONAssert.assertEquals(
        "{\"measureId1\":[{\"userId\":\"userId1\",\"roles\":[\"SHARED_WITH\"]}],\"measureId2\":[{\"userId\":\"userId1\",\"roles\":[\"SHARED_WITH\"]},{\"userId\":\"userId2\",\"roles\":[\"SHARED_WITH\"]}]}",
        result.getResponse().getContentAsString(),
        JSONCompareMode.STRICT);
  }

  @Test
  public void testShareMeasuresReturns400WhenHarpIdNotActiveMADiEUser() throws Exception {
    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("measureId1").build());
    doThrow(
            new InvalidIdException(
                "The provided HARP ID (invalidUser) is not associated with an active MADiE user."))
        .when(measureService)
        .shareMeasures(any(), anyString(), anyString());

    MvcResult result =
        mockMvc
            .perform(
                put("/measures/shared")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .content("{\"measureId1\": [\"invalidUser\"]}")
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertEquals(400, result.getResponse().getStatus());
  }

  @Test
  public void testUnshareMeasures() throws Exception {
    AclSpecification aclSpecification2 = new AclSpecification();
    aclSpecification2.setUserId("userId2");
    aclSpecification2.setRoles(Set.of(RoleEnum.SHARED_WITH));

    Map<String, List<AclSpecification>> measureIdToAclSpecification = new HashMap<>();
    measureIdToAclSpecification.put("measureId2", List.of(aclSpecification2));

    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("measureId1").build());
    doReturn(measureIdToAclSpecification)
        .when(measureService)
        .unshareMeasures(any(), anyString(), anyString());

    MvcResult result =
        mockMvc
            .perform(
                put("/measures/unshared")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .content("{\"measureId1\": [\"userId1\"],\"measureId2\": [\"userId1\"]}")
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    verify(measureService, times(1)).unshareMeasures(any(), anyString(), anyString());
    JSONAssert.assertEquals(
        "{\"measureId2\":[{\"userId\":\"userId2\",\"roles\":[\"SHARED_WITH\"]}]}",
        result.getResponse().getContentAsString(),
        JSONCompareMode.STRICT);
  }

  @Test
  public void testGetRecentMeasuresByMeasureSetId() throws Exception {
    // Create sample measures
    Measure measure1 = new Measure();
    measure1.setId("m1");
    measure1.setMeasureName("Measure 1");

    Measure measure2 = new Measure();
    measure2.setId("m2");
    measure2.setMeasureName("Measure 2");

    List<Measure> recentMeasures = List.of(measure1, measure2);

    // Stub the measureSetService call
    when(measureSetService.getRecentMeasuresByMeasureSetId(eq(List.of("set1", "set2"))))
        .thenReturn(recentMeasures);

    mockMvc
        .perform(
            get("/measures/recentsByMeasureSetId")
                .with(user(TEST_USER_ID))
                .queryParam("measureSetIds", "set1", "set2")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("m1"))
        .andExpect(jsonPath("$[0].measureName").value("Measure 1"))
        .andExpect(jsonPath("$[1].id").value("m2"))
        .andExpect(jsonPath("$[1].measureName").value("Measure 2"));

    verify(measureSetService, times(1))
        .getRecentMeasuresByMeasureSetId(eq(List.of("set1", "set2")));
  }

  @Test
  public void testGetCounts() throws Exception {
    when(measureService.countMeasuresByOwnership(
            eq(true), eq(TEST_USER_ID), eq(List.of(OwnershipType.OWNED))))
        .thenReturn(5);
    when(measureService.countMeasuresByOwnership(
            eq(true), eq(TEST_USER_ID), eq(List.of(OwnershipType.SHARED))))
        .thenReturn(3);
    when(measureService.countMeasuresByOwnership(
            eq(true), eq(TEST_USER_ID), eq(List.of(OwnershipType.ALL))))
        .thenReturn(8);

    mockMvc
        .perform(get("/measures/count").with(user(TEST_USER_ID)).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.ownedMeasures").value(5))
        .andExpect(jsonPath("$.sharedMeasures").value(3))
        .andExpect(jsonPath("$.allMeasures").value(8));

    verify(measureService, times(1))
        .countMeasuresByOwnership(eq(true), eq(TEST_USER_ID), eq(List.of(OwnershipType.OWNED)));
    verify(measureService, times(1))
        .countMeasuresByOwnership(eq(true), eq(TEST_USER_ID), eq(List.of(OwnershipType.SHARED)));
    verify(measureService, times(1))
        .countMeasuresByOwnership(eq(true), eq(TEST_USER_ID), eq(List.of(OwnershipType.ALL)));

    verifyNoMoreInteractions(measureService);
  }

  @Test
  public void testTransferMeasures() throws Exception {
    String measureId = "f225481c-921e-4015-9e14-e5046bfac9ff";
    Measure measure = Measure.builder().id(measureId).build();
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    doReturn(Collections.emptyList())
        .when(measureService)
        .transferMeasures(
            eq(List.of(measureId)), eq("testUser"), eq(true), eq(TEST_USER_ID), eq(ACCESS_TOKEN));

    mockMvc
        .perform(
            put("/measures/transfer")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("harpId", "testUser")
                .header("Authorization", ACCESS_TOKEN)
                .queryParam("retainShareAccess", "true")
                .content(gson.toJson(List.of(measureId)))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk());

    verify(measureService, times(1))
        .transferMeasures(
            eq(List.of(measureId)), eq("testuser"), eq(true), eq(TEST_USER_ID), eq(ACCESS_TOKEN));
  }

  @Test
  public void testTransferMeasuresPartialFailure() throws Exception {
    Measure measure1 = Measure.builder().id("f225481c").build();
    Measure measure2 = Measure.builder().id("921e4015").build();
    // mock measure retrieval for all measures
    when(measureService.findMeasureById(measure1.getId())).thenReturn(measure1);
    when(measureService.findMeasureById(measure2.getId())).thenReturn(measure2);
    doReturn(List.of(measure2.getId()))
        .when(measureService)
        .transferMeasures(
            eq(List.of(measure1.getId(), measure2.getId())),
            eq("testuser"),
            eq(false),
            eq(TEST_USER_ID),
            eq(ACCESS_TOKEN));

    MvcResult result =
        mockMvc
            .perform(
                put("/measures/transfer")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("harpId", "testUser")
                    .header("Authorization", ACCESS_TOKEN)
                    .queryParam("retainShareAccess", "false")
                    .content(gson.toJson(List.of(measure1.getId(), measure2.getId())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isMultiStatus())
            .andReturn();

    verify(measureService, times(1))
        .transferMeasures(
            eq(List.of(measure1.getId(), measure2.getId())),
            eq("testuser"),
            eq(false),
            eq(TEST_USER_ID),
            eq(ACCESS_TOKEN));
    // failed measure should be in the response
    assertThat(result.getResponse().getContentAsString(), containsString(measure2.getId()));
  }

  @Test
  public void testTransferMeasuresNullMeasureIds() throws Exception {
    String measureId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    mockMvc
        .perform(
            put("/measures/transfer")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("harpId", "testUser")
                .header("Authorization", ACCESS_TOKEN)
                .queryParam("retainShareAccess", "true")
                .content(gson.toJson(Collections.emptyList()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isBadRequest());

    verify(measureService, times(0))
        .transferMeasures(
            eq(List.of(measureId)), eq("testUser"), eq(true), eq(TEST_USER_ID), eq(ACCESS_TOKEN));
  }

  @Test
  public void testDeactivateMeasureSuccessfully() throws Exception {
    String measureId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    when(measureService.deactivateMeasure(eq(measureId), eq(TEST_USER_ID)))
        .thenReturn(Measure.builder().active(false).id(measureId).build());
    MvcResult result =
        mockMvc
            .perform(
                delete("/measures/" + measureId + "/delete")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("harpId", "testUser")
                    .header("Authorization", ACCESS_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

    verify(measureService, times(1)).deactivateMeasure(eq(measureId), eq(TEST_USER_ID));
    assertThat(result.getResponse().getContentAsString(), containsString("\"active\":false"));
  }

  @Test
  public void testDeactivateMeasureFailed() throws Exception {
    String measureId = "f225481c-921e-4015-9e14-e5046bfac9ff";
    String reason = "Lock can't be obtained to deactivate";
    doThrow(new LockNotObtainedException(reason))
        .when(measureService)
        .deactivateMeasure(eq(measureId), eq(TEST_USER_ID));
    MvcResult result =
        mockMvc
            .perform(
                delete("/measures/" + measureId + "/delete")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("harpId", "testUser")
                    .header("Authorization", "test-okta")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isLocked())
            .andReturn();

    verify(measureService, times(1)).deactivateMeasure(eq(measureId), eq(TEST_USER_ID));
    assertThat(result.getResponse().getContentAsString(), containsString(reason));
  }
}
