package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import cms.gov.madie.measure.factories.ModelValidatorFactory;
import cms.gov.madie.measure.factories.PackageServiceFactory;
import cms.gov.madie.measure.utils.MeasureUtil;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@AllArgsConstructor
public class ExportService {
  private final PackageServiceFactory packageServiceFactory;
  private final ModelValidatorFactory modelValidatorFactory;
  private final MeasureUtil measureUtil;

  public PackageDto getMeasureExport(Measure measure, String accessToken, String elmErrorSeverity) {
    ModelValidator modelValidator =
        modelValidatorFactory.getModelValidator(ModelType.valueOfName(measure.getModel()));
    measure = measureUtil.validateAllMeasureDependencies(measure);
    modelValidator.validateMetadata(measure);
    modelValidator.validateGroups(measure);
    modelValidator.validateCqlErrors(measure);
    PackageService packageService =
        packageServiceFactory.getPackageService(ModelType.valueOfName(measure.getModel()));
    boolean errorsOnly = elmErrorSeverity.equals("Error");
    return packageService.getMeasurePackage(measure, !errorsOnly, accessToken);
  }

  public byte[] getQRDA(QrdaRequestDTO qrdaRequestDTO, String accessToken) {
    if (CollectionUtils.isEmpty(qrdaRequestDTO.getMeasure().getTestCases())) {
      throw new InvalidResourceStateException(
          "Measure",
          qrdaRequestDTO.getMeasure().getId(),
          "since there are no test cases in the measure.");
    }
    PackageService packageService =
        packageServiceFactory.getPackageService(
            ModelType.valueOfName(qrdaRequestDTO.getMeasure().getModel()));
    return packageService.getQRDA(qrdaRequestDTO, accessToken);
  }
}
