package cms.gov.madie.measure.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Getter
@Configuration
public class NotificationServiceConfig {

  @Value("${madie.notification-service.base-url}")
  private String notificationServiceBaseUrl;

  @Bean
  public RestTemplate notificationServiceRestTemplate() {
    return new RestTemplate();
  }
}
