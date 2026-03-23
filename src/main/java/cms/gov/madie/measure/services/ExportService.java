package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.dto.excel.MeasureAccessReportDTO;
import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import cms.gov.madie.measure.exceptions.InvalidRequestException;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import cms.gov.madie.measure.factories.ModelValidatorFactory;
import cms.gov.madie.measure.factories.PackageServiceFactory;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.MeasureUtil;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class ExportService {
  private final PackageServiceFactory packageServiceFactory;
  private final ModelValidatorFactory modelValidatorFactory;
  private final ExcelClient excelClient;
  private final MeasureRepository measureRepository;
  private final MeasureSetService measureSetService;
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

  public byte[] getSharedAccessReportForMeasures(
      List<String> measureIds, String userName, String accessToken) {
    if (CollectionUtils.isEmpty(measureIds)) {
      throw new InvalidRequestException(
          "Please provide at least one measure id to export the shared access report.");
    }

    String ids = String.join(",", measureIds);
    log.info("User [{}] is attempting to export Access report for measures [{}]", userName, ids);

    List<MeasureListDTO> measures = measureRepository.findAllByIdInWithMeasureSet(measureIds);
    List<MeasureAccessReportDTO> accessReportDTOS =
        measures.stream()
            .map(
                measure -> {
                  MeasureAccessReportDTO accessReportDTO =
                      MeasureAccessReportDTO.builder()
                          .id(measure.getId())
                          .measureName(measure.getMeasureName())
                          .measureModel(measure.getModel())
                          .build();
                  MeasureSet measureSet = measure.getMeasureSet();
                  if (measureSet != null) {
                    accessReportDTO.setCmsId(
                        MeasureUtil.getCmsIdDisplay(measureSet, measure.getModel()));
                    accessReportDTO.setOwner(measureSet.getOwner());
                    accessReportDTO.setSharedWith(
                        measureSetService.getSharedUsersForMeasureSet(measureSet));
                  }
                  return accessReportDTO;
                })
            .toList();

    byte[] export = excelClient.getSharedAccessReportForMeasures(accessReportDTOS, accessToken);
    log.info("Access report successful for measures [{}] by user [{}]", ids, userName);

    return export;
  }
}
