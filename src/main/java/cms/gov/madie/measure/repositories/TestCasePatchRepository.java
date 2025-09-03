package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCaseConfiguration;

public interface TestCasePatchRepository {
  Measure findAndModifyTestCaseConfig(
      TestCaseConfiguration testCaseConfiguration, String measureId);
}
