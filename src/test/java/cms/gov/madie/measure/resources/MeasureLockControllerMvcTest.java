package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.MeasureLockService;
import cms.gov.madie.measure.services.TestCaseLockService;
import cms.gov.madie.measure.services.VersionService;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(MeasureLockController.class)
@ActiveProfiles("test")
// @Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
class MeasureLockControllerMvcTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Autowired private MeasureLockService measureLockService;
  @Autowired private TestCaseLockService testCaseLockService;
  @Autowired private MeasureRepository measureRepository;

  private Principal mockPrincipal;
  private final String harpId = "test-user";
  private final String measureId = "measure-123";

  @BeforeEach
  void setup() {
    mockPrincipal = Mockito.mock(Principal.class);
    when(mockPrincipal.getName()).thenReturn(harpId);
  }

  @TestConfiguration
  static class MockConfig {
    @Bean
    VersionService versionService() {
      return Mockito.mock(VersionService.class);
    }

    @Bean
    TestCaseLockService testCaseLockService() {
      return Mockito.mock(TestCaseLockService.class);
    }

    @Bean
    MeasureLockService measureLockService() {
      return Mockito.mock(MeasureLockService.class);
    }

    @Bean
    MeasureRepository measureRepository() {
      return Mockito.mock(MeasureRepository.class);
    }
  }

  @Test
  void testUpdateMeasureLockReturns200AndLockResponse() throws Exception {
    LockInfo mockResponse = new LockInfo(true, harpId, measureId);
    when(measureLockService.lockMeasure(eq(measureId), eq(harpId))).thenReturn(mockResponse);

    String jsonResponse =
        mockMvc
            .perform(
                put("/measures/{measureId}/measure-lock", measureId)
                    .principal(mockPrincipal)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    LockInfo actualResponse = objectMapper.readValue(jsonResponse, LockInfo.class);

    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.isLocked()).isTrue();
    assertThat(actualResponse.getLockedBy()).isEqualTo(harpId);
  }

  @Test
  void testUnlockMeasureReturns200AndLockResponse() throws Exception {
    LockInfo mockResponse = new LockInfo(false, harpId, measureId);
    when(measureLockService.unlockMeasure(eq(measureId), eq(harpId))).thenReturn(mockResponse);

    String jsonResponse =
        mockMvc
            .perform(
                delete("/measures/{measureId}/measure-lock", measureId).principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    LockInfo actualResponse = objectMapper.readValue(jsonResponse, LockInfo.class);

    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.isLocked()).isFalse();
    assertThat(actualResponse.getLockedBy()).isEqualTo(harpId);
  }

  @Test
  void testUpdateMeasureLockWhenAlreadyLockedByAnotherUser() throws Exception {
    String otherUser = "other-user";
    LockInfo mockResponse = new LockInfo(true, otherUser, measureId);
    when(measureLockService.lockMeasure(eq(measureId), eq(harpId))).thenReturn(mockResponse);
    // If the locks don't belong to this user, we're not doing anything
    mockMvc
        .perform(
            put("/measures/{measureId}/measure-lock", measureId)
                .principal(mockPrincipal)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  void testUnlockMeasureWhenLockedByDifferentUser() throws Exception {
    String otherUser = "other-user";
    LockInfo mockResponse = new LockInfo(true, otherUser, measureId);
    when(measureLockService.unlockMeasure(eq(measureId), eq(harpId))).thenReturn(mockResponse);
    // If the locks don't belong to this user, we're not doing anything
    mockMvc
        .perform(delete("/measures/{measureId}/measure-lock", measureId).principal(mockPrincipal))
        .andExpect(status().isOk());
  }

  @Test
  void testMeasureIsLockedByOtherUser() throws Exception {
    String otherUser = "other-user";
    LockInfo mockResponse = new LockInfo(true, otherUser, measureId);
    when(measureLockService.getMeasureLock(anyString())).thenReturn(mockResponse);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures/{measureId}/lock-by-other-user", measureId).principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals("other-user", result.getResponse().getContentAsString());
  }

  @Test
  void testTestCasesLockedByOtherUser() throws Exception {
    LockInfo mockResponse = new LockInfo(true, harpId, measureId);
    when(measureLockService.getMeasureLock(anyString())).thenReturn(mockResponse);
    TestCase testCase = TestCase.builder().id("testCaseId").build();
    Measure measure = Measure.builder().id("measureId").testCases(List.of(testCase)).build();
    when(measureRepository.findByIdAndActive(anyString(), any(Boolean.class)))
        .thenReturn(Optional.of(measure));
    when(testCaseLockService.testCaseLocksByOtherUser(anyString(), any(List.class), anyString()))
        .thenReturn(true);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures/{measureId}/lock-by-other-user", measureId).principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();

    assertEquals(
        "One or more test cases are locked by another user.",
        result.getResponse().getContentAsString());
  }

  @Test
  void testIsMeasureLockedByOtherUserLockInfoNotLocked() throws Exception {
    LockInfo mockResponse = new LockInfo(false, null, null);
    when(measureLockService.getMeasureLock(anyString())).thenReturn(mockResponse);
    TestCase testCase = TestCase.builder().id("testCaseId").build();
    Measure measure = Measure.builder().id("measureId").testCases(List.of(testCase)).build();
    when(measureRepository.findByIdAndActive(anyString(), any(Boolean.class)))
        .thenReturn(Optional.of(measure));
    when(testCaseLockService.testCaseLocksByOtherUser(anyString(), any(List.class), anyString()))
        .thenReturn(false);

    MvcResult result =
        mockMvc
            .perform(
                get("/measures/{measureId}/lock-by-other-user", measureId).principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();
    verify(measureLockService, times(1)).getMeasureLock(anyString());
    assertEquals("OK to proceed", result.getResponse().getContentAsString());
  }

  @Test
  void testIsMeasureLockedByOtherUserNoTestCases() throws Exception {
    LockInfo mockResponse = new LockInfo(false, null, null);
    when(measureLockService.getMeasureLock(anyString())).thenReturn(mockResponse);
    Measure measure = Measure.builder().id("measureId").testCases(Collections.emptyList()).build();
    when(measureRepository.findByIdAndActive(anyString(), any(Boolean.class)))
        .thenReturn(Optional.of(measure));

    MvcResult result =
        mockMvc
            .perform(
                get("/measures/{measureId}/lock-by-other-user", measureId).principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();

    assertEquals("OK to proceed", result.getResponse().getContentAsString());
  }

  @Test
  void testIsMeasureLockedByOtherUserMeasureNotFound() throws Exception {
    LockInfo mockResponse = new LockInfo(false, null, null);
    when(measureLockService.getMeasureLock(anyString())).thenReturn(mockResponse);
    when(measureRepository.findByIdAndActive(anyString(), any(Boolean.class)))
        .thenReturn(Optional.empty());

    MvcResult result =
        mockMvc
            .perform(
                get("/measures/{measureId}/lock-by-other-user", measureId).principal(mockPrincipal))
            .andExpect(status().isNotFound())
            .andReturn();
    assertTrue(
        result
            .getResponse()
            .getContentAsString()
            .contains("Could not find Measure with id: measure-123"));
  }
}
