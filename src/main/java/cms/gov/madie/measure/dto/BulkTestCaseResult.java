package cms.gov.madie.measure.dto;

import gov.cms.madie.models.measure.TestCase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for bulk test case operations. Contains successfully processed test cases and IDs of
 * failed operations.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkTestCaseResult {
  /** List of successfully added/updated test cases */
  private List<TestCase> testCases;

  /** List of test case IDs that could not be processed (e.g., due to locks) */
  private List<String> failed;
}
