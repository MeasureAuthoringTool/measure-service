package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.exceptions.InvalidCmsIdException;
import cms.gov.madie.measure.exceptions.InvalidReturnTypeException;
import cms.gov.madie.measure.exceptions.InvalidVersionIdException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.ActionLogService;
import cms.gov.madie.measure.services.GroupService;
import cms.gov.madie.measure.services.MeasureService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Organization;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureGroupTypes;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.MeasureScoring;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({MeasureController.class})
@ActiveProfiles("test")
public class MeasureControllerMvcTest {

  @MockBean private MeasureRepository measureRepository;
  @MockBean private MeasureService measureService;
  @MockBean private GroupService groupService;
  @MockBean private ActionLogService actionLogService;

  @Autowired private MockMvc mockMvc;
  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor;
  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor2;

  private static final String TEST_USER_ID = "test-okta-user-id-123";

  private static final String TEST_API_KEY_HEADER = "api-key";
  private static final String TEST_API_KEY_HEADER_VALUE = "9202c9fa";

  @Captor ArgumentCaptor<Group> groupCaptor;
  @Captor ArgumentCaptor<String> measureIdCaptor;
  @Captor ArgumentCaptor<String> usernameCaptor;
  @Captor ArgumentCaptor<PageRequest> pageRequestCaptor;
  @Captor ArgumentCaptor<Boolean> activeCaptor;
  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;
  @Captor private ArgumentCaptor<Class> targetClassArgumentCaptor;
  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;
  @Captor private ArgumentCaptor<String> performedByArgumentCaptor;

  private static final String MODEL = ModelType.QI_CORE.toString();

  public String toJsonString(Object obj) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    return mapper.writeValueAsString(obj);
  }

  @Test
  public void testGrantAccess() throws Exception {
    String measureId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    doReturn(true)
        .when(measureService)
        .grantAccess(eq(measureId), eq("akinsgre"), eq(TEST_API_KEY_HEADER_VALUE));

    mockMvc
        .perform(
            put("/measures/" + measureId + "/grant/?userid=akinsgre")
                .header(TEST_API_KEY_HEADER, TEST_API_KEY_HEADER_VALUE))
        .andExpect(status().isOk())
        .andExpect(content().string("akinsgre granted access to Measure successfully."));

    verify(measureService, times(1))
        .grantAccess(eq(measureId), eq("akinsgre"), eq(TEST_API_KEY_HEADER_VALUE));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(),
            targetClassArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            performedByArgumentCaptor.capture());
    assertNotNull(targetIdArgumentCaptor.getValue());
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.UPDATED)));
    assertThat(performedByArgumentCaptor.getValue(), is(equalTo("apiKey")));
  }

  @Test
  public void testUpdatePassed() throws Exception {

    String measureId = "f225481c-921e-4015-9e14-e5046bfac9ff";
    String measureName = "TestMeasure";
    Organization steward =
        Organization.builder().name("d0cc18ce-63fd-4b94-b713-c1d9fd6b2329").build();
    String description = "TestDescription";
    String copyright = "TestCopyright";
    String disclaimer = "TestDisclaimer";
    String rationale = "TestRationale";
    List<Organization> developers = List.of(Organization.builder().name("TestDeveloper").build());
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

    when(measureRepository.findById(eq(measureId))).thenReturn(Optional.of(priorMeasure));
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

    verify(measureRepository, times(1)).findById(eq(measureId));
    verify(measureService, times(1))
        .updateMeasure(
            measureArgumentCaptor.capture(),
            anyString(),
            measureArgumentCaptor2.capture(),
            anyString());
    assertThat(measureArgumentCaptor.getValue(), is(equalTo(priorMeasure)));
    assertThat(measureArgumentCaptor2.getValue(), is(equalTo(updatingMeasure)));

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
        Organization.builder().name("d0cc18ce-63fd-4b94-b713-c1d9fd6b2329").build();
    String description = "TestDescription";
    String copyright = "TestCopyright";
    String disclaimer = "TestDisclaimer";
    String rationale = "TestRationale";
    List<Organization> developers = List.of(Organization.builder().name("TestDeveloper").build());
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
        priorMeasure
            .toBuilder()
            .active(false)
            .measureMetaData(metaData)
            .ecqmTitle(ecqmTitle)
            .build();

    final String measureAsJson = toJsonString(updatingMeasure);

    when(measureRepository.findById(eq(measureId))).thenReturn(Optional.of(priorMeasure));
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

    verify(measureRepository, times(1)).findById(eq(measureId));

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
    final String measureAsJson = "{  }";
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
    final String measureAsJson = "{ \"id\": \"m1234\" }";
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
    final String measureAsJson = "{ \"measureName\":\"\" }";
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
    final String measureAsJson = "{ \"id\": \"m1234\", \"measureName\":\"\" }";
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
        "{ \"measureName\":\"A_Name\", \"cqlLibraryName\":\"ALib\" , \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\"}";
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
        "{ \"id\": \"m1234\", \"measureName\":\"A_Name\", \"cqlLibraryName\":\"ALib\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\" }";
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
        "{ \"measureName\":\"%s\", \"cqlLibraryName\":\"ALib\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\"  }"
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
        "{ \"id\": \"m1234\", \"measureName\":\"%s\", \"cqlLibraryName\":\"ALib\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\" }"
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
    when(measureService.createMeasure(any(Measure.class), anyString(), anyString()))
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
        .createMeasure(measureArgumentCaptor.capture(), anyString(), anyString());
    verifyNoMoreInteractions(measureRepository);
    Measure savedMeasure = measureArgumentCaptor.getValue();
    assertEquals(measureName, savedMeasure.getMeasureName());
    assertEquals(libraryName, savedMeasure.getCqlLibraryName());
    assertEquals(ecqmTitle, savedMeasure.getEcqmTitle());
    assertEquals(MODEL, savedMeasure.getModel());
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
    when(measureRepository.findById(eq(priorMeasure.getId())))
        .thenReturn(Optional.of(priorMeasure));

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

    verify(measureRepository, times(1)).findById(eq(priorMeasure.getId()));
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
    when(measureRepository.findById(eq(priorMeasure.getId())))
        .thenReturn(Optional.of(priorMeasure));

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

    verify(measureRepository, times(1)).findById(eq(priorMeasure.getId()));
    verify(measureService, times(1))
        .updateMeasure(any(Measure.class), anyString(), any(Measure.class), anyString());
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testUpdateMeasureFailsIfInvalidCMSId() throws Exception {
    Measure priorMeasure = new Measure();
    priorMeasure.setId("id0");
    priorMeasure.setMeasureName("TestMeasure");
    priorMeasure.setCqlLibraryName("TestMeasureLibrary");
    priorMeasure.setModel(MODEL);
    priorMeasure.setEcqmTitle("ecqmTitle");
    priorMeasure.setVersionId(priorMeasure.getId());
    priorMeasure.setCmsId("testCmsId");
    priorMeasure.setMeasureSetId("measureSetId");
    when(measureRepository.findById(eq(priorMeasure.getId())))
        .thenReturn(Optional.of(priorMeasure));

    Measure existingMeasure = new Measure();
    existingMeasure.setId("id0");
    existingMeasure.setMeasureName("ExistingMeasure");
    existingMeasure.setCqlLibraryName("ExistingMeasureLibrary");
    existingMeasure.setEcqmTitle("ecqmTitle");
    existingMeasure.setVersionId(priorMeasure.getVersionId());
    existingMeasure.setCmsId("newCmsId");
    existingMeasure.setMeasureSetId("measureSetId");

    when(measureService.updateMeasure(
            any(Measure.class), anyString(), any(Measure.class), anyString()))
        .thenThrow(new InvalidCmsIdException(existingMeasure.getCmsId()));

    final String updatedMeasureAsJson =
        "{\"id\": \"%s\",\"measureName\": \"%s\", \"cqlLibraryName\": \"%s\", \"ecqmTitle\": \"%s\", \"model\":\"%s\",\"versionId\":\"%s\",\"measureSetId\":\"%s\",\"cmsId\":\"%s\"}"
            .formatted(
                priorMeasure.getId(),
                priorMeasure.getMeasureName(),
                priorMeasure.getCqlLibraryName(),
                priorMeasure.getEcqmTitle(),
                priorMeasure.getModel(),
                priorMeasure.getVersionId(),
                priorMeasure.getMeasureSetId(),
                existingMeasure.getCmsId());
    mockMvc
        .perform(
            put("/measures/" + priorMeasure.getId())
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .content(updatedMeasureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest());

    verify(measureRepository, times(1)).findById(eq(priorMeasure.getId()));
    verify(measureService, times(1))
        .updateMeasure(any(Measure.class), anyString(), any(Measure.class), anyString());
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testNewMeasureNoUnderscore() throws Exception {
    final String measureAsJson =
        "{ \"id\": \"m1234\", \"measureName\":\"A_Name\", \"cqlLibraryName\":\"ALib\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\" }";
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
        "{ \"measureName\":\"AName\", \"cqlLibraryName\":\"aLib\", \"ecqmTitle\":\"ecqmTitle\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\" }";
    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName")
                .value("Measure Library Name is invalid."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testUpdateMeasureFailsIfCqlLibaryNameStartsWithLowerCase() throws Exception {
    final String measureAsJson =
        "{ \"id\": \"m1234\", \"measureName\":\"AName\", \"cqlLibraryName\":\"aLib\", \"ecqmTitle\":\"ecqmTitle\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\" }";
    mockMvc
        .perform(
            put("/measures/m1234")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName")
                .value("Measure Library Name is invalid."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewMeasureFailsIfCqlLibraryNameHasQuotes() throws Exception {
    final String measureAsJson =
        "{ \"measureName\":\"AName\", \"cqlLibraryName\":\"ALi''b\", \"ecqmTitle\":\"ecqmTitle\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\" }";
    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName")
                .value("Measure Library Name is invalid."));
    verifyNoInteractions(measureRepository);
  }

  @Test
  public void testNewMeasureFailsIfCqlLibraryNameHasUnderscore() throws Exception {
    final String measureAsJson =
        "{ \"measureName\":\"AName\", \"cqlLibraryName\":\"ALi_'b\", \"ecqmTitle\":\"ecqmTitle\", \"versionId\":\"versionId\",\"measureSetId\":\"measureSetId\" }";
    mockMvc
        .perform(
            post("/measure")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(measureAsJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName")
                .value("Measure Library Name is invalid."));
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

    when(measureRepository.findById(eq(measureId))).thenReturn(Optional.of(saved));
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

    verify(measureRepository, times(1)).findById(eq(measureId));
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
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.model")
                .value("MADiE was unable to complete your request, please try again."));
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
            .andExpect(status().isForbidden())
            .andReturn();
    String resultStr = result.getResponse().getErrorMessage();
    assertEquals("Forbidden", resultStr);
  }

  @Test
  public void testGetMeasuresNoQueryParams() throws Exception {
    Measure m1 =
        Measure.builder()
            .active(true)
            .measureName("Measure1")
            .cqlLibraryName("TestLib1")
            .createdBy("test-okta-user-id-123")
            .model(MODEL)
            .build();
    Measure m2 =
        Measure.builder()
            .active(true)
            .measureName("Measure2")
            .cqlLibraryName("TestLib2")
            .createdBy("test-okta-user-id-123")
            .model(MODEL)
            .build();
    Measure m3 =
        Measure.builder()
            .active(true)
            .measureName("Measure3")
            .cqlLibraryName("TestLib3")
            .createdBy("test-okta-user-id-999")
            .model(MODEL)
            .build();

    Page<Measure> allMeasures = new PageImpl<>(List.of(m1, m2, m3));

    when(measureService.getMeasures(any(Boolean.class), any(Pageable.class), eq(TEST_USER_ID)))
        .thenReturn(allMeasures);

    MvcResult result =
        mockMvc
            .perform(get("/measures").with(user(TEST_USER_ID)).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    ObjectMapper mapper = new ObjectMapper();
    String expectedJsonStr = mapper.writeValueAsString(allMeasures);

    assertThat(resultStr, is(equalTo(expectedJsonStr)));
    verify(measureService, times(1))
        .getMeasures(any(Boolean.class), any(Pageable.class), eq(TEST_USER_ID));
    verifyNoMoreInteractions(measureService);
  }

  @Test
  public void testGetMeasuresWithCurrentUserFalse() throws Exception {
    Measure m1 =
        Measure.builder()
            .active(true)
            .measureName("Measure1")
            .cqlLibraryName("TestLib1")
            .createdBy("test-okta-user-id-123")
            .model(MODEL)
            .build();
    Measure m2 =
        Measure.builder()
            .active(true)
            .measureName("Measure2")
            .cqlLibraryName("TestLib2")
            .createdBy("test-okta-user-id-123")
            .model(MODEL)
            .build();
    Measure m3 =
        Measure.builder()
            .active(true)
            .measureName("Measure3")
            .cqlLibraryName("TestLib3")
            .createdBy("test-okta-user-id-999")
            .model(MODEL)
            .build();

    Page<Measure> allMeasures = new PageImpl<>(List.of(m1, m2, m3));
    when(measureService.getMeasures(eq(false), any(Pageable.class), eq(TEST_USER_ID)))
        .thenReturn(allMeasures);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures")
                    .with(user(TEST_USER_ID))
                    .queryParam("currentUser", "false")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    ObjectMapper mapper = new ObjectMapper();
    String expectedJsonStr = mapper.writeValueAsString(allMeasures);

    assertThat(resultStr, is(equalTo(expectedJsonStr)));
    verify(measureService, times(1)).getMeasures(eq(false), any(Pageable.class), eq(TEST_USER_ID));

    verifyNoMoreInteractions(measureService);
  }

  @Test
  public void getMeasuresWithCustomPaging() throws Exception {
    Measure m1 =
        Measure.builder()
            .active(true)
            .measureName("Measure1")
            .cqlLibraryName("TestLib1")
            .createdBy("test-okta-user-id-123")
            .model(MODEL)
            .build();
    Measure m2 =
        Measure.builder()
            .active(true)
            .measureName("Measure2")
            .cqlLibraryName("TestLib2")
            .createdBy("test-okta-user-id-123")
            .model(MODEL)
            .build();
    Measure m3 =
        Measure.builder()
            .active(true)
            .measureName("Measure3")
            .cqlLibraryName("TestLib3")
            .createdBy("test-okta-user-id-999")
            .model(MODEL)
            .build();

    Page<Measure> allMeasures = new PageImpl<>(List.of(m1, m2, m3));
    when(measureService.getMeasures(eq(false), any(Pageable.class), eq(TEST_USER_ID)))
        .thenReturn(allMeasures);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures")
                    .with(user(TEST_USER_ID))
                    .queryParam("currentUser", "false")
                    .queryParam("limit", "25")
                    .queryParam("page", "3")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    ObjectMapper mapper = new ObjectMapper();
    String expectedJsonStr = mapper.writeValueAsString(allMeasures);

    assertThat(resultStr, is(equalTo(expectedJsonStr)));

    verify(measureService, times(1))
        .getMeasures(activeCaptor.capture(), pageRequestCaptor.capture(), eq(TEST_USER_ID));

    PageRequest pageRequestValue = pageRequestCaptor.getValue();
    assertEquals(25, pageRequestValue.getPageSize());
    assertEquals(3, pageRequestValue.getPageNumber());

    verifyNoMoreInteractions(measureService);
  }

  @Test
  public void testGetMeasuresFilterByCurrentUser() throws Exception {
    Measure m1 =
        Measure.builder()
            .active(true)
            .measureName("Measure1")
            .cqlLibraryName("TestLib1")
            .createdBy("test-okta-user-id-123")
            .model(MODEL)
            .build();
    Measure m2 =
        Measure.builder()
            .active(true)
            .measureName("Measure2")
            .cqlLibraryName("TestLib2")
            .createdBy("test-okta-user-id-123")
            .model(MODEL)
            .active(true)
            .build();

    final Page<Measure> measures = new PageImpl<>(List.of(m1, m2));

    when(measureService.getMeasures(eq(true), any(Pageable.class), eq(TEST_USER_ID)))
        .thenReturn(measures);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures")
                    .with(user(TEST_USER_ID))
                    .queryParam("currentUser", "true")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    ObjectMapper mapper = new ObjectMapper();
    String expectedJsonStr = mapper.writeValueAsString(measures);

    assertThat(resultStr, is(equalTo(expectedJsonStr)));
    verify(measureService, times(1)).getMeasures(eq(true), any(Pageable.class), eq(TEST_USER_ID));
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
                        null)))
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
                        null)))
            .measureGroupTypes(List.of(MeasureGroupTypes.PROCESS))
            .build();

    final String groupJson =
        "{\"id\":\"test-id\",\"scoring\":\"Cohort\",\"populations\":[{\"id\":\"id-2\",\"name\":\"initialPopulation\",\"definition\":\"FactorialOfFive\"}],\"measureGroupTypes\":[\"Process\"], \"populationBasis\": \"boolean\"}";
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
  public void testSearchMeasuresByMeasureNameOrEcqmTitleNoQueryParams() throws Exception {
    Measure m1 =
        Measure.builder()
            .measureName("measure-1")
            .ecqmTitle("test-ecqm-title-1")
            .createdBy("test-user-1")
            .build();
    Measure m2 =
        Measure.builder()
            .measureName("measure-2")
            .ecqmTitle("test-ecqm-title-1")
            .createdBy("test-user-2")
            .build();
    Measure m3 =
        Measure.builder()
            .measureName("measure-3")
            .ecqmTitle("test-ecqm-title-3")
            .createdBy("test-user-1")
            .build();

    Page<Measure> allMeasures = new PageImpl<>(List.of(m1, m2, m3));
    when(measureRepository.findAllByMeasureNameOrEcqmTitle(any(String.class), any(Pageable.class)))
        .thenReturn(allMeasures);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures/search/measure")
                    .with(user(TEST_USER_ID))
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    ObjectMapper mapper = new ObjectMapper();
    String expectedJsonStr = mapper.writeValueAsString(allMeasures);

    assertThat(resultStr, is(equalTo(expectedJsonStr)));
    verify(measureRepository, times(1))
        .findAllByMeasureNameOrEcqmTitle(any(String.class), any(Pageable.class));
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testSearchMeasuresByMeasureNameOrEcqmTitleWithCurrentUserFalse() throws Exception {
    Measure m1 =
        Measure.builder()
            .measureName("measure-1")
            .ecqmTitle("test-ecqm-title-1")
            .createdBy("test-user-1")
            .build();
    Measure m2 =
        Measure.builder()
            .measureName("measure-2")
            .ecqmTitle("test-ecqm-title-1")
            .createdBy("test-user-2")
            .build();
    Measure m3 =
        Measure.builder()
            .measureName("measure-3")
            .ecqmTitle("test-ecqm-title-3")
            .createdBy("test-user-1")
            .build();

    Page<Measure> allMeasures = new PageImpl<>(List.of(m1, m2, m3));
    when(measureRepository.findAllByMeasureNameOrEcqmTitle(any(String.class), any(Pageable.class)))
        .thenReturn(allMeasures);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures/search/ecqm")
                    .with(user(TEST_USER_ID))
                    .queryParam("currentUser", "false")
                    .queryParam("limit", "8")
                    .queryParam("page", "1")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    ObjectMapper mapper = new ObjectMapper();
    String expectedJsonStr = mapper.writeValueAsString(allMeasures);

    assertThat(resultStr, is(equalTo(expectedJsonStr)));
    verify(measureRepository, times(1))
        .findAllByMeasureNameOrEcqmTitle(any(String.class), any(Pageable.class));
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  public void testSearchMeasuresByMeasureNameOrEcqmTitleFilterByCurrentUser() throws Exception {
    Measure m1 =
        Measure.builder()
            .measureName("measure-1")
            .ecqmTitle("test-ecqm-title-1")
            .createdBy(TEST_USER_ID)
            .build();
    Measure m2 =
        Measure.builder()
            .measureName("measure-2")
            .ecqmTitle("test-ecqm-title-1")
            .createdBy(TEST_USER_ID)
            .build();
    Measure m3 =
        Measure.builder()
            .measureName("measure-3")
            .ecqmTitle("test-ecqm-title-3")
            .createdBy(TEST_USER_ID)
            .build();

    final Page<Measure> measures = new PageImpl<>(List.of(m1, m2, m3));

    when(measureRepository.findAllByMeasureNameOrEcqmTitleForCurrentUser(
            any(String.class), any(Pageable.class), any(String.class)))
        .thenReturn(measures);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures/search/measure")
                    .with(user(TEST_USER_ID))
                    .queryParam("currentUser", "true")
                    .queryParam("limit", "8")
                    .queryParam("page", "1")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
    String resultStr = result.getResponse().getContentAsString();

    ObjectMapper mapper = new ObjectMapper();
    String expectedJsonStr = mapper.writeValueAsString(measures);

    assertThat(resultStr, is(equalTo(expectedJsonStr)));
    verify(measureRepository, times(1))
        .findAllByMeasureNameOrEcqmTitleForCurrentUser(
            eq("measure"), any(PageRequest.class), eq(TEST_USER_ID));
    verifyNoMoreInteractions(measureRepository);
  }
}
