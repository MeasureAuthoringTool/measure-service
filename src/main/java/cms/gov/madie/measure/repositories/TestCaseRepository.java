package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCaseValidationStatus;

public interface TestCaseRepository {
  Measure findAndUpdateValidationStatus(
      String testCaseId, String measureId, TestCaseValidationStatus status);
}
