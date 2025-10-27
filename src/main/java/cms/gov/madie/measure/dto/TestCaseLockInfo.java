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

  /** The ID of the measure containing the test case */
  private String measureId;

  /** The ID of the locked test case */
  private String testCaseId;

  /** The username of the user who locked the test case */
  private String lockedBy;

  /** The timestamp when the test case was locked */
  private Instant lockedAt;

  /** The timestamp when the lock will expire */
  private Instant expiresAt;
}
