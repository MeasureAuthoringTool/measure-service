package cms.gov.madie.measure;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j(topic = "action_audit")
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.getWriter().write("Forbidden: Invalid API Key");
    final String username =
        request.getUserPrincipal() == null ? "" : request.getUserPrincipal().getName();
    log.info(
        "User [{}] called [{}] on path [{}] and got response code [{}]",
        username,
        request.getMethod(),
        request.getRequestURI(),
        response.getStatus());
  }
}
