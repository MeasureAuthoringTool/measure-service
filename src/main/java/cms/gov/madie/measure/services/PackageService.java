package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import gov.cms.madie.models.measure.Measure;

public interface PackageService {
  PackageDto getMeasurePackage(Measure measure, String accessToken, boolean includeElmWarnings);

  String getHumanReadable(Measure measure, String username, String accessToken);

  String getHumanReadableForVersionedMeasure(Measure measure, String username, String accessToken);

  byte[] getQRDA(QrdaRequestDTO serviceDTO, String accessToken);
}
