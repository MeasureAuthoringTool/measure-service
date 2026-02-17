package cms.gov.madie.measure;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

public class JwtRoleTestFilter implements Filter {
  private final String requiredRole;

  public JwtRoleTestFilter(String requiredRole) {
    this.requiredRole = requiredRole;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    Authentication authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    String uri = httpRequest.getRequestURI();

    boolean hasRole = false;
    if (authentication instanceof JwtAuthenticationToken) {
      for (GrantedAuthority authority : authentication.getAuthorities()) {
        if (requiredRole.equals(authority.getAuthority())) {
          hasRole = true;
          break;
        }
      }
    }
    if (uri.startsWith("/admin/") && !hasRole) {
      ((HttpServletResponse) response)
          .sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: Invalid user role");
      return;
    }
    chain.doFilter(request, response);
  }
}
