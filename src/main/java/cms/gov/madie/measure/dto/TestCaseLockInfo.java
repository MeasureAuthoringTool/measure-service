package cms.gov.madie.measure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * DTO representing lock information for a test case. This is used to communicate lock details
 * between services and the frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseLockInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  private String measureId;
  private String testCaseId;
  private String lockedBy;
  private Instant lockedAt;
  private Instant expiresAt;
}
