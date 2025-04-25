package cms.gov.madie.measure.utils;

import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;

public class ExportFileNamesUtil {

  public static String getExportFileName(Measure measure) {
    if (measure.getModel().startsWith("QI-Core")) {
      return measure.getEcqmTitle().trim() + "-v" + measure.getVersion() + "-FHIR";
    }
    return measure.getEcqmTitle().trim() + "-v" + measure.getVersion() + "-" + measure.getModel();
  }

  public static String getTestCaseExportZipName(Measure measure) {
    return measure.getEcqmTitle().trim() + "-v" + measure.getVersion().toString() + "-TestCases";
  }

  public static String getOverlappingValueSetsExportZipName(Measure measure) {
    String fileName = "";
    if (measure.getModel().equalsIgnoreCase(ModelType.QDM_5_6.getValue())) {
      fileName =
          measure.getEcqmTitle() + "-v" + measure.getVersion().toString() + "-QDM-OverlappingCodes";
    } else if (measure.getModel().equalsIgnoreCase(ModelType.QI_CORE_6_0_0.getValue())) {
      fileName =
          measure.getEcqmTitle()
              + "-v"
              + measure.getVersion().toString()
              + "-FHIR6-OverlappingCodes";
    } else {
      fileName =
          measure.getEcqmTitle()
              + "-v"
              + measure.getVersion().toString()
              + "-FHIR4-OverlappingCodes";
    }
    return fileName;
  }
}
