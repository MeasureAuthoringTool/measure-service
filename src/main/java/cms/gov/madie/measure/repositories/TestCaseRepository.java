package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.HapiOperationOutcome;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCaseValidationStatus;

import java.util.UUID;

public interface TestCaseRepository {
  Measure setValidationStatusToPending(String testCaseId, String measureId);

  Measure setValidationStatusToValidating(String testCaseId, String measureId, UUID taskId);

  Measure findAndUpdateValidationStatus(
      String testCaseId, String measureId, TestCaseValidationStatus status);

  Measure findAndUpdateValidationStatus(
      String testCaseId, String measureId, UUID taskId, TestCaseValidationStatus status);

  Measure findAndUpdateValidationResults(
      String testCaseId, String measureId, UUID taskId, HapiOperationOutcome validationResults);
}
