package cms.gov.madie.measure.config;

import com.okta.jwt.*;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
  @Value("${okta.oauth2.issuer}")
  private String oktaIssuer;

  @Value("${okta.oauth2.audience}")
  private String oktaAudience;

  private AccessTokenVerifier jwtVerifier;

  @PostConstruct
  public void init() {
    jwtVerifier =
        JwtVerifiers.accessTokenVerifierBuilder()
            .setIssuer(oktaIssuer)
            .setAudience(oktaAudience)
            .build();
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {

    if (request instanceof ServletServerHttpRequest servletRequest) {
      HttpServletRequest httpServletRequest = servletRequest.getServletRequest();

      String token = httpServletRequest.getParameter("access_token");
      if (token != null && token.startsWith("Bearer ")) {
        token = token.substring(7);
        try {
          jwtVerifier.decode(token);
          return true;
        } catch (JwtVerificationException e) {
          log.error("Exception occurred while decoding JWT: {}", e.getMessage(), e);
        }
      }
    }
    return false;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception ex) {}
}
