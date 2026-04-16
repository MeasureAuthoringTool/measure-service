package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.HapiOperationOutcome;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.TestCaseValidationStatus;

import java.util.UUID;

public interface TestCaseRepository {
  Measure addOrUpdateTestCase(String measureId, TestCase testCase);

  Measure removeTestCase(String measureId, String testCaseId);

  Measure setValidationStatusToPending(String testCaseId, String measureId);

  Measure setValidationStatusToValidating(String testCaseId, String measureId, UUID taskId);

  void setValidationStatusToNotComplete(
      String testCaseId, String measureId, TestCaseValidationStatus status);

  Measure findAndUpdateValidationResults(
      String testCaseId, String measureId, UUID taskId, HapiOperationOutcome validationResults);

  boolean testCaseSetIdExistsInSet(String measureId);
}
