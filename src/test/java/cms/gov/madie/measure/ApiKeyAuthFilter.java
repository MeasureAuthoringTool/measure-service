package cms.gov.madie.measure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/*
 * a custom filter that checks for the presence of an api-key header in the request.
 * If present and valid, it sets an authentication with the API_KEY authority;
 * otherwise, it blocks the request.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {
  private static final String API_KEY_HEADER = "api-key";
  private static final String TEST_USER_ID = "test-okta-user-id-123"; // apiKeyUser
  private static final String EXPECTED_API_KEY = "0a51991c"; // test key

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String apiKey = request.getHeader(API_KEY_HEADER);

    if (apiKey != null && apiKey.equals(EXPECTED_API_KEY)) {
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(
              TEST_USER_ID, null, Collections.singletonList(new SimpleGrantedAuthority("API_KEY")));
      SecurityContextHolder.getContext().setAuthentication(auth);
    }

    filterChain.doFilter(request, response);
  }
}
