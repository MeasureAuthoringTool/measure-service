package cms.gov.madie.measure.services;

import cms.gov.madie.measure.clients.NotificationServiceClient;
import cms.gov.madie.measure.dto.NotificationDTO;
import cms.gov.madie.measure.utils.MeasureChangeNotificationUtil;
import gov.cms.madie.models.measure.Measure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for asynchronously building and dispatching measure-change notifications. The
 * heavy lifting (diff detection, recipient collection, HTTP call to the notification microservice)
 * runs on a dedicated thread pool so the caller (e.g. the updateMeasure API) is never blocked.
 */
@Slf4j
@Service
public class NotificationService {

  private final ThreadPoolTaskExecutor notificationExecutor;
  private final NotificationServiceClient notificationServiceClient;

  public NotificationService(
      @Qualifier("notificationExecutor") ThreadPoolTaskExecutor notificationExecutor,
      NotificationServiceClient notificationServiceClient) {
    this.notificationExecutor = notificationExecutor;
    this.notificationServiceClient = notificationServiceClient;
  }

  /**
   * Submits the notification workflow to the thread pool. The work includes:
   *
   * <ol>
   *   <li>Detecting which field changed between the existing and updating measure
   *   <li>Building a notification DTO per recipient
   *   <li>Calling the notification microservice
   * </ol>
   *
   * <p>This method returns immediately; any failure inside the async task is logged and swallowed.
   *
   * @param existingMeasure the measure as it was before the update
   * @param updatingMeasure the incoming measure with new values
   * @param username the HARP ID of the user who triggered the update
   */
  public void sendMeasureChangeNotifications(
      Measure existingMeasure, Measure updatingMeasure, String username) {
    log.info(
        "Submitting async notification task for measure [{}] (queue size: {})",
        existingMeasure.getId(),
        notificationExecutor.getQueueSize());

    notificationExecutor.submit(
        () -> {
          try {
            List<NotificationDTO> notifications =
                MeasureChangeNotificationUtil.buildNotifications(
                    existingMeasure, updatingMeasure, username);
            notificationServiceClient.sendNotifications(notifications);
          } catch (Exception e) {
            log.error(
                "Async notification task failed for measure [{}]: {}",
                existingMeasure.getId(),
                e.getMessage(),
                e);
          }
        });
  }
}
