package cms.gov.madie.measure.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Measure;

public class ExportFileNamesUtilTest {
  private Measure measure;

  @BeforeEach
  public void setUp() {
    measure =
        Measure.builder()
            .active(true)
            .id("testMeasureId")
            .ecqmTitle("ecqmTitle")
            .measureSetId("testMeasureSetId")
            .measureName("Test Measure")
            .version(new Version(0, 0, 1))
            .build();
  }

  @Test
  public void testGetExportFileName() {
    measure.setModel(ModelType.QI_CORE.getValue());
    String result = ExportFileNamesUtil.getExportFileName(measure);
    assertEquals("ecqmTitle-v0.0.001-FHIR", result);
  }

  @Test
  public void testGetExportFileNameQdm() {
    measure.setModel(ModelType.QDM_5_6.getValue());
    String result = ExportFileNamesUtil.getExportFileName(measure);
    assertEquals("ecqmTitle-v0.0.001-QDM v5.6", result);
  }

  @Test
  public void testGetTestCaseExportZipName() {
    String result = ExportFileNamesUtil.getTestCaseExportZipName(measure);
    assertEquals("ecqmTitle-v0.0.001-TestCases", result);
  }

  @Test
  public void testGetOverlappingValueSetsExportZipNameQdm() {
    measure.setModel(ModelType.QDM_5_6.getValue());
    String result = ExportFileNamesUtil.getOverlappingValueSetsExportZipName(measure);
    assertEquals("ecqmTitle-v0.0.001-QDM-OverlappingCodes", result);
  }

  @Test
  public void testGetOverlappingValueSetsExportZipNameQicore() {
    measure.setModel(ModelType.QI_CORE.getValue());
    String result = ExportFileNamesUtil.getOverlappingValueSetsExportZipName(measure);
    assertEquals("ecqmTitle-v0.0.001-FHIR4-OverlappingCodes", result);
  }

  @Test
  public void testGetOverlappingValueSetsExportZipNameQicore6() {
    measure.setModel(ModelType.QI_CORE_6_0_0.getValue());
    String result = ExportFileNamesUtil.getOverlappingValueSetsExportZipName(measure);
    assertEquals("ecqmTitle-v0.0.001-FHIR6-OverlappingCodes", result);
  }
}
