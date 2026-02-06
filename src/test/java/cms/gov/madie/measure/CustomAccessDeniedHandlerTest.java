package cms.gov.madie.measure;

import java.io.PrintWriter;
import java.io.StringWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class CustomAccessDeniedHandlerTest {
  @Test
  void testSetsForbiddenStatusAndWritesMessageWhenPrincipalNull() throws Exception {
    CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler();
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    AccessDeniedException exception = new AccessDeniedException("denied");

    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(printWriter);
    when(request.getUserPrincipal()).thenReturn(null);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/admin/measures/1");

    handler.handle(request, response, exception);

    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    printWriter.flush();
    assertTrue(stringWriter.toString().contains("Forbidden: Invalid API Key"));
  }

  @Test
  void testSetsForbiddenStatusAndWritesMessageUserWhenPrincipalNotNull() throws Exception {
    CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler();
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    AccessDeniedException exception = new AccessDeniedException("denied");

    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(printWriter);
    var principal = mock(java.security.Principal.class);
    when(principal.getName()).thenReturn("testUser");
    when(request.getUserPrincipal()).thenReturn(principal);
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/admin/measures/2");

    handler.handle(request, response, exception);

    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    printWriter.flush();
    assertTrue(stringWriter.toString().contains("Forbidden: Invalid API Key"));
  }
}
