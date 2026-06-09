package cms.gov.madie.measure.dto;

import lombok.*;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class SharedUser {
  private String userId;
  private String displayName;
  private Instant performedAt;
}
