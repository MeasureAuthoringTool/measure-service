package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.config.security.SecurityConfigTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import cms.gov.madie.measure.exceptions.BadVersionRequestException;
import cms.gov.madie.measure.exceptions.MeasureNotDraftableException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import cms.gov.madie.measure.services.AppConfigService;
import cms.gov.madie.measure.services.VersionService;
import cms.gov.madie.measure.services.MeasureService;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({MeasureVersionController.class})
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@Import(SecurityConfigTest.class)
public class MeasureVersionControllerMvcTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private VersionService versionService;
  @Autowired private MeasureService measureService;
  @Autowired private AppConfigService appConfigService;

  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor;

  private static final String TEST_USER_ID = "test-user-id";
  private static final String TEST_ACCESS_TOKEN = "test-user-access-token";

  @BeforeEach
  void setUp() {
    Mockito.reset(versionService, measureService, appConfigService);

    // Setup default mocks for lock check
    Measure mockMeasure = new Measure();
    mockMeasure.setId("testMeasureId");
    when(measureService.findMeasureById(anyString())).thenReturn(mockMeasure);
    when(appConfigService.isFlagEnabled(any())).thenReturn(false);
  }

  @TestConfiguration
  static class MockConfig {
    @Bean
    VersionService versionService() {
      return Mockito.mock(VersionService.class);
    }

    @Bean
    MeasureService measureService() {
      return Mockito.mock(MeasureService.class);
    }

    @Bean
    AppConfigService appConfigService() {
      return Mockito.mock(AppConfigService.class);
    }
  }

  @Test
  public void testCreateVersionReturnsResourceNotFoundException() throws Exception {
    when(versionService.createVersion(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new ResourceNotFoundException("Measure", "testMeasureId"));
    mockMvc
        .perform(
            put("/measures/testMeasureId/version?versionType=MAJOR")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta-token")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));

    verify(versionService, times(1))
        .createVersion(eq("testMeasureId"), eq("MAJOR"), eq(TEST_USER_ID), eq("test-okta-token"));
  }

  @Test
  public void testCreateVersionReturnsBadVersionRequestException() throws Exception {
    doThrow(
            new BadVersionRequestException(
                "Measure", "testMeasureId", TEST_USER_ID, "Invalid version request."))
        .when(versionService)
        .createVersion(anyString(), anyString(), anyString(), anyString());

    mockMvc
        .perform(
            put("/measures/testMeasureId/version?versionType=NOTVALIDVERSIONTYPE")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta-token")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));

    verify(versionService, times(1))
        .createVersion(
            eq("testMeasureId"),
            eq("NOTVALIDVERSIONTYPE"),
            eq(TEST_USER_ID),
            eq("test-okta-token"));
  }

  @Test
  public void testCreateVersionReturnsUnauthorizedException() throws Exception {
    doThrow(new UnauthorizedException("Measure", "testMeasureId", TEST_USER_ID))
        .when(versionService)
        .createVersion(anyString(), anyString(), anyString(), anyString());

    mockMvc
        .perform(
            put("/measures/testMeasureId/version?versionType=MAJOR")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta-token")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));

    verify(versionService, times(1))
        .createVersion(eq("testMeasureId"), eq("MAJOR"), eq(TEST_USER_ID), eq("test-okta-token"));
  }

  @Test
  public void testCreateVersionReturnsCreatedVersion() throws Exception {
    Measure updatedMeasure = Measure.builder().id("testMeasureId").createdBy("testUser").build();
    Version updatedVersion = Version.builder().major(3).minor(0).revisionNumber(0).build();
    updatedMeasure.setVersion(updatedVersion);
    MeasureMetaData updatedMetaData = new MeasureMetaData();
    updatedMetaData.setDraft(false);
    updatedMeasure.setMeasureMetaData(updatedMetaData);
    when(versionService.createVersion(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(updatedMeasure);

    mockMvc
        .perform(
            put("/measures/testMeasureId/version?versionType=MAJOR")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta-token")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));

    verify(versionService, times(1))
        .createVersion(eq("testMeasureId"), eq("MAJOR"), eq(TEST_USER_ID), eq("test-okta-token"));
  }

  @Test
  public void testCreateDraftSuccessfully() throws Exception {
    Measure measure =
        Measure.builder().id("testMeasureId").createdBy("testUser").model("QI-Core v4.1.1").build();
    measure.setMeasureName("Test");
    when(versionService.createDraft(
            anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(measure);

    mockMvc
        .perform(
            post("/measures/testMeasureId/draft")
                .content("{\"measureName\": \"Test\", \"model\":\"QI-Core v4.1.1\"}")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", TEST_ACCESS_TOKEN)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));

    verify(versionService, times(1))
        .createDraft(
            eq("testMeasureId"),
            eq("Test"),
            eq("QI-Core v4.1.1"),
            eq(TEST_USER_ID),
            eq(TEST_ACCESS_TOKEN));
  }

  @Test
  public void testCreateDraftWhenMeasureNameEmpty() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/measures/testMeasureId/draft")
                    .content("{\"measureName\": \"\", \"model\":\"QDM v5.6\"}")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", TEST_ACCESS_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();
    verifyNoInteractions(versionService);
    assertThat(
        result.getResponse().getContentAsString(), containsString("Measure name is required."));
  }

  @Test
  public void testCreateDraftWhenMeasureNotDraftable() throws Exception {
    when(versionService.createDraft(
            anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new MeasureNotDraftableException("Test", "Only one draft is permitted per measure."));
    MvcResult result =
        mockMvc
            .perform(
                post("/measures/testMeasureId/draft")
                    .content("{\"measureName\": \"Test\", \"model\":\"QI-Core v4.1.1\"}")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", TEST_ACCESS_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertThat(
        result.getResponse().getContentAsString(),
        containsString("Only one draft is permitted per measure."));
  }
}
