package cms.gov.madie.measure.dto;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "testCaseLock")
public class TestCaseLock {
  private String measureId;
  @Id private String testCaseId;
  private String lockedBy;
  private Instant lockedAt;

  @Indexed(expireAfter = "0m")
  private Instant expiresAt;
}
