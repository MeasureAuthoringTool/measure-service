package cms.gov.madie.measure.dto;

/** Feature flags relevant to the measure-service */
public enum MadieFeatureFlag {
  QDM_TEST_CASES("qdmTestCases"),
  IMPORT_TEST_CASES("importTestCases"),
  ENABLE_QDM_REPEAT_TRANSFER("enableQdmRepeatTransfer");

  private final String flag;

  MadieFeatureFlag(String flag) {
    this.flag = flag;
  }

  @Override
  public String toString() {
    return this.flag;
  }
}
