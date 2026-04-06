package cms.gov.madie.measure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class NotificationDTO {
  private String userId;
  private String message;
  private String additionalLink;
}
