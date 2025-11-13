package cms.gov.madie.measure.dto;

import gov.cms.madie.models.measure.TestCase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CopyTestCaseResult {
  private List<TestCase> copiedTestCases;
  private List<TestCase> failedTestCases;
  private Boolean didClearExpectedValues;
}
