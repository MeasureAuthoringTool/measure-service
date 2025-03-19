package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import cms.gov.madie.measure.exceptions.BundleOperationException;
import cms.gov.madie.measure.repositories.ExportRepository;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.packaging.utils.PackagingUtility;
import gov.cms.madie.packaging.utils.PackagingUtilityFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class QicorePackageService implements PackageService {

  private final BundleService bundleService;
  private FhirServicesClient fhirServicesClient;
  private final ExportRepository exportRepository;

  @Override
  public PackageDto getMeasurePackage(
      Measure measure, boolean includeElmWarnings, String accessToken) {
    return bundleService.getMeasureExport(
        measure, includeElmWarnings ? "Info" : "Error", accessToken);
  }

  @Override
  public String getHumanReadable(Measure measure, String username, String accessToken) {
    String measureBundle =
        fhirServicesClient.getMeasureBundle(measure, accessToken, "export", "Info");

    String humanReadableWithCss = getHRWithCSS(measure, measureBundle);

    log.info("User [{}] get human readable with ID [{}]", username, measure.getId());
    return humanReadableWithCss;
  }

  @Override
  public byte[] getQRDA(QrdaRequestDTO qrdaRequestDTO, String accessToken) {
    // to be implemented
    throw new UnsupportedOperationException("method not yet implemented");
  }

  protected String getHRWithCSS(Measure measure, String measureBundle) {
    String humanReadableWithCss = null;
    try {
      PackagingUtility utility = PackagingUtilityFactory.getInstance(measure.getModel());
      humanReadableWithCss = utility.getHumanReadableWithCSS(measureBundle);
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException
        | ClassNotFoundException e) {
      throw new BundleOperationException("Measure", measure.getId(), e);
    }
    return humanReadableWithCss;
  }

  @Override
  public String getHumanReadableForVersionedMeasure(
      Measure measure, String username, String accessToken) {
    String humanReadableWithCss = null;
    Export export = null;
    if (measure.getMeasureMetaData() != null && !measure.getMeasureMetaData().isDraft()) {
      Optional<Export> savedExport = exportRepository.findByMeasureId(measure.getId());
      if (savedExport.isPresent()) {
        if (!StringUtils.isBlank(savedExport.get().getHumanReadable())) {
          humanReadableWithCss = savedExport.get().getHumanReadable();
        } else {
          // human readable might not exist in Export due to the change earlier
          humanReadableWithCss = getHumanReadable(measure, username, accessToken);
          export = savedExport.get();
          export.setHumanReadable(humanReadableWithCss);
          Export updatedExport = exportRepository.save(export);
          log.info(
              "User [{}] saved human readable with CSS in Export with ID [{}]",
              username,
              updatedExport.getId());
        }
      }
    }
    return humanReadableWithCss;
  }
}
