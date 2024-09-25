package cms.gov.madie.measure.dto;

/** Feature flags relevant to the measure-service */
public enum MadieFeatureFlag {
  QDM_EXPORT("qdmExport"),
  QDM_TEST_CASES("qdmTestCases"),
  IMPORT_TEST_CASES("importTestCases"),
  TEST_CASE_ID("TestCaseID");

  private final String flag;

  MadieFeatureFlag(String flag) {
    this.flag = flag;
  }

  @Override
  public String toString() {
    return this.flag;
  }
}
