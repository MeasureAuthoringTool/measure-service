package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.LockResponse;
import cms.gov.madie.measure.services.MeasureLockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeasureLockController.class)
@ActiveProfiles("test")
// @Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
class MeasureLockControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private MeasureLockService measureLockService;

  private Principal mockPrincipal;
  private final String harpId = "test-user";
  private final String measureId = "measure-123";

  @BeforeEach
  void setup() {
    mockPrincipal = Mockito.mock(Principal.class);
    when(mockPrincipal.getName()).thenReturn(harpId);
  }

  @Test
  void testUpdateMeasureLockReturns200AndLockResponse() throws Exception {
    LockResponse mockResponse = new LockResponse(true, harpId);
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
    LockResponse actualResponse = objectMapper.readValue(jsonResponse, LockResponse.class);

    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.isLocked()).isTrue();
    assertThat(actualResponse.getLockedBy()).isEqualTo(harpId);
  }

  @Test
  void testUnlockMeasureReturns200AndLockResponse() throws Exception {
    LockResponse mockResponse = new LockResponse(false, harpId);
    when(measureLockService.unlockMeasure(eq(measureId), eq(harpId))).thenReturn(mockResponse);

    String jsonResponse =
        mockMvc
            .perform(
                delete("/measures/{measureId}/measure-lock", measureId).principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    LockResponse actualResponse = objectMapper.readValue(jsonResponse, LockResponse.class);

    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.isLocked()).isFalse();
    assertThat(actualResponse.getLockedBy()).isEqualTo(harpId);
  }
}
