package cms.gov.madie.measure.dto;

/** Feature flags relevant to the measure-service */
public enum MadieFeatureFlag {
  QDM_EXPORT("qdmExport"),
  QDM_TEST_CASES("qdmTestCases"),
  IMPORT_TEST_CASES("importTestCases"),
  MEASURE_SEARCH("MeasureSearch"),
  EDIT_TESTS_ON_VERSIONED_MEASURES("EditTestsOnVersionedMeasures"),
  STU_6_TEST_CASE_VALIDATION("stu6TestCaseValidation"),
  LOCKING("Locking");

  private final String flag;

  MadieFeatureFlag(String flag) {
    this.flag = flag;
  }

  @Override
  public String toString() {
    return this.flag;
  }
}
