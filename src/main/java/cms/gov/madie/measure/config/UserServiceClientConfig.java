package cms.gov.madie.measure.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@Getter
@Configuration
public class UserServiceClientConfig {

  @Value("${madie.user-service.base-url}")
  private String userServiceBaseUrl;

  @Bean
  public RestTemplate userServiceRestTemplate(ClientHttpRequestInterceptor bearerTokenInterceptor) {
    return new RestTemplateBuilder().additionalInterceptors(bearerTokenInterceptor).build();
  }
}
