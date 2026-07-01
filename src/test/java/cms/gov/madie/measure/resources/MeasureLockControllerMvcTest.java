package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.config.security.SecurityConfigTest;
import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.services.MeasureLockService;
import cms.gov.madie.measure.services.TestCaseLockService;
import cms.gov.madie.measure.services.VersionService;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeasureLockController.class)
@ActiveProfiles("test")
@Import(SecurityConfigTest.class)
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
class MeasureLockControllerMvcTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Autowired private MeasureLockService measureLockService;

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
}
