package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.DraftValueSetOverride;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCaseConfiguration;
import java.util.List;

public interface TestCasePatchRepository {
  Measure findAndModifyTestCaseConfig(
      TestCaseConfiguration testCaseConfiguration, String measureId);

  Measure findAndModifyDraftValueSetOverrides(
      List<DraftValueSetOverride> draftValueSetOverrides, String measureId);
}
