package cms.gov.madie.measure.clients;

import cms.gov.madie.measure.dto.NotificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
   * Sends a single notification DTO (containing multiple recipient userIds) to the notification
   * microservice. The service creates one notification per userId and returns the full list.
   *
   * @param notification the notification to send
   * @param accessToken the full Authorization header value (e.g. "Bearer eyJhb...")
   * @return the list of persisted Notification objects returned by the notification-service, or
   *     empty list on failure
   */
  public List<NotificationDTO> sendNotification(NotificationDTO notification, String accessToken) {
    if (notification == null || CollectionUtils.isEmpty(notification.getUserIds())) {
      log.debug("No notification to send – skipping call to notification-service.");
      return List.of();
    }

    String url = notificationServiceBaseUrl + "/notifications";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    // accessToken already contains the "Bearer " prefix from @RequestHeader("Authorization")
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);

    HttpEntity<NotificationDTO> request = new HttpEntity<>(notification, headers);

    try {
      log.info(
          "Sending notification for {} user(s) to notification-service at [{}]",
          notification.getUserIds().size(),
          url);
      ResponseEntity<List<NotificationDTO>> response =
          notificationServiceRestTemplate.exchange(
              url, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
      log.info("Successfully sent notification for {} user(s).", notification.getUserIds().size());
      return response.getBody() != null ? response.getBody() : List.of();
    } catch (Exception e) {
      // Fire-and-forget: log and swallow so the main updateMeasure flow is not affected
      log.error("Failed to send notification to notification-service: {}", e.getMessage(), e);
      return List.of();
    }
  }
}
