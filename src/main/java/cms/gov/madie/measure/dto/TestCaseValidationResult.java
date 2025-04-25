package cms.gov.madie.measure.dto;

import gov.cms.madie.models.measure.HapiOperationOutcome;
import lombok.Data;

@Data
public class TestCaseValidationResult {
  private String testCaseId;
  private boolean validResource;
  private HapiOperationOutcome operationOutcome;
}
