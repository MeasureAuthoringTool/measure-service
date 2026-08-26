package cms.gov.madie.measure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BearerTokenInterceptorConfigTest {

  private BearerTokenInterceptorConfig config;

  @BeforeEach
  void setUp() {
    config = new BearerTokenInterceptorConfig();
  }

  @Test
  void testInterceptorAddsBearerTokenWhenJwtAuthentication() throws IOException {
    // given
    ClientHttpRequestInterceptor interceptor = config.bearerTokenInterceptor();

    Jwt jwt = mock(Jwt.class);
    when(jwt.getTokenValue()).thenReturn("test-token-value");
    JwtAuthenticationToken jwtToken = new JwtAuthenticationToken(jwt);

    SecurityContext securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(jwtToken);
    SecurityContextHolder.setContext(securityContext);

    MockClientHttpRequest request = new MockClientHttpRequest();
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse response = mock(ClientHttpResponse.class);
    when(execution.execute(any(HttpRequest.class), any(byte[].class))).thenReturn(response);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    assertThat(
        request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION), is("Bearer test-token-value"));
    verify(execution).execute(request, new byte[0]);

    SecurityContextHolder.clearContext();
  }

  @Test
  void testInterceptorSkipsTokenWhenNoAuthentication() throws IOException {
    // given
    ClientHttpRequestInterceptor interceptor = config.bearerTokenInterceptor();

    SecurityContext securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(null);
    SecurityContextHolder.setContext(securityContext);

    MockClientHttpRequest request = new MockClientHttpRequest();
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse response = mock(ClientHttpResponse.class);
    when(execution.execute(any(HttpRequest.class), any(byte[].class))).thenReturn(response);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    assertThat(request.getHeaders().get(HttpHeaders.AUTHORIZATION), is(nullValue()));
    verify(execution).execute(request, new byte[0]);

    SecurityContextHolder.clearContext();
  }
}
