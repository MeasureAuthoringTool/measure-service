package cms.gov.madie.measure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class UserServiceConfig {

  @Bean
  public RestTemplate userServiceRestTemplate(
      RestTemplateBuilder builder, ObjectMapper objectMapper) {
    MappingJackson2HttpMessageConverter messageConverter =
        new MappingJackson2HttpMessageConverter();
    messageConverter.setObjectMapper(objectMapper);

    return builder
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(10))
        .additionalMessageConverters(messageConverter)
        .additionalInterceptors(bearerTokenInterceptor())
        .build();
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
