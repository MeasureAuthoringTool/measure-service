package cms.gov.madie.measure.dto;

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
  @Id private String id; // MongoDB document ID

  @Indexed(unique = true)
  private String measureId; // ID of the measure being locked; indexed to prevent duplicates

  private String lockedBy; // ID of the user locking the measure
  private Instant lockedAt; // Timestamp of when the lock was created

  @Indexed(expireAfterSeconds = 0)
  private Instant expiresAt;
}
