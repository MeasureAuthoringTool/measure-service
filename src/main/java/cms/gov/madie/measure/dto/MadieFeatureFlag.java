package cms.gov.madie.measure.dto;

/** Feature flags relevant to the measure-service */
public enum MadieFeatureFlag {
  QDM_EXPORT("qdmExport"),
  QDM_TEST_CASES("qdmTestCases"),
  IMPORT_TEST_CASES("importTestCases"),
  STU_6_TEST_CASE_VALIDATION("stu6TestCaseValidation"),
  MEASURE_SEARCH("MeasureSearch");

  private final String flag;

  MadieFeatureFlag(String flag) {
    this.flag = flag;
  }

  @Override
  public String toString() {
    return this.flag;
  }
}
