package cms.gov.madie.measure.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestTemplate;

@Getter
@Configuration
public class UserServiceClientConfig {

  @Value("${madie.user-service.base-url}")
  private String userServiceBaseUrl;

  @Bean
  public RestTemplate userServiceRestTemplate() {
    return new RestTemplateBuilder().additionalInterceptors(bearerTokenInterceptor()).build();
  }

  private ClientHttpRequestInterceptor bearerTokenInterceptor() {
    return (HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication instanceof JwtAuthenticationToken jwtToken) {
        String token = jwtToken.getToken().getTokenValue();
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
      }
      return execution.execute(request, body);
    };
  }
}
