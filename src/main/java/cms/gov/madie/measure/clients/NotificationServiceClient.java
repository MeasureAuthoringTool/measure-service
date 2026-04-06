package cms.gov.madie.measure.clients;

import cms.gov.madie.measure.dto.NotificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationServiceClient {

  private final RestTemplate notificationServiceRestTemplate;

  @Value("${madie.notification-service.base-url}")
  private String notificationServiceBaseUrl;

  /**
   * Sends a batch of notification DTOs to the notification microservice. Each DTO represents a
   * single notification for a single user.
   *
   * @param notifications the list of notifications to send
   */
  public void sendNotifications(List<NotificationDTO> notifications) {
    if (CollectionUtils.isEmpty(notifications)) {
      log.debug("No notifications to send – skipping call to notification-service.");
      return;
    }

    String url = notificationServiceBaseUrl + "/notifications";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<List<NotificationDTO>> request = new HttpEntity<>(notifications, headers);

    try {
      log.info(
          "Sending {} notification(s) to notification-service at [{}]", notifications.size(), url);
      notificationServiceRestTemplate.postForEntity(url, request, Void.class);
      log.info("Successfully sent {} notification(s).", notifications.size());
    } catch (Exception e) {
      // Fire-and-forget: log and swallow so the main updateMeasure flow is not affected
      log.error("Failed to send notifications to notification-service: {}", e.getMessage(), e);
    }
  }
}
