package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.SecurityConfigTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({LoggingController.class})
@ActiveProfiles("test")
@Import(SecurityConfigTest.class)
public class LoggingControllerMvcTest {

  @Autowired private MockMvc mockMvc;

  public static final String USER_INFO =
      "{\"sub\":\"testSub\",\"email\":\"test.user@test.com\",\"email_verified\":true}";

  @Test
  public void testLoginLogSuccess() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/log/login")
                    .content(USER_INFO)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn();
    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains(USER_INFO));
  }

  @Test
  public void testLoginLogBadRequest() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/log/login")
                    .content("")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andReturn();
    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Required request body is missing"));
  }

  @Test
  public void testLogoutLogSuccess() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/log/logout")
                    .content(USER_INFO)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn();
    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains(USER_INFO));
  }

  @Test
  public void testLogoutLogBadRequest() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/log/logout")
                    .content("")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andReturn();
    String response = result.getResponse().getContentAsString();
    assertTrue(response.contains("Required request body is missing"));
  }
}
