package cms.gov.madie.measure.locks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "measureLock")
public class MeasureLock {
  @Id private String id;

  @Indexed(unique = true)
  private String measureId;

  private String lockedBy;
  private Instant lockedAt;

  @Indexed(expireAfter = "0s")
  private Instant expiresAt;
}
