package cms.gov.madie.measure.services;

import java.lang.reflect.InvocationTargetException;

import cms.gov.madie.measure.dto.PackageDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import cms.gov.madie.measure.exceptions.BundleOperationException;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.utils.ExportFileNamesUtil;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.packaging.utils.PackagingUtility;
import gov.cms.madie.packaging.utils.PackagingUtilityFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class BundleService {

  private final FhirServicesClient fhirServicesClient;
  private final ExportRepository exportRepository;
  private final ElmToJsonService elmToJsonService;
  private final MongoGridFsService mongoGridFsService;

  /**
   * Get the bundle for measure. For draft measure, generate bundle For versioned measure, fetch the
   * bundle from measure export/gridFs collections
   */
  public String bundleMeasure(
      Measure measure, String accessToken, String bundleType, String elmErrorSeverity) {
    if (measure == null) {
      return null;
    }
    // for draft measures
    if (measure.getMeasureMetaData().isDraft()) {
      try {
        elmToJsonService.retrieveElmJson(measure, elmErrorSeverity, accessToken);
        return fhirServicesClient.getMeasureBundle(
            measure, accessToken, bundleType, elmErrorSeverity);
      } catch (RestClientException | IllegalArgumentException ex) {
        log.error("An error occurred while bundling measure {}", measure.getId(), ex);
        throw new BundleOperationException("Measure", measure.getId(), ex);
      }
    }
    // for versioned measures
    Export export = exportRepository.findByMeasureId(measure.getId()).orElse(null);
    if (export == null) {
      log.error("Export not available for versioned measure with id: {}", measure.getId());
      throw new BundleOperationException("Measure", measure.getId(), null);
    }

    if (StringUtils.isNotBlank(export.getMeasureBundleJson())) {
      return export.getMeasureBundleJson();
    }
    if (StringUtils.isNotBlank(export.getMeasureBundleGridFsId())) {
      return mongoGridFsService.findById(export.getMeasureBundleGridFsId());
    }
    log.error(
        "Bundle with warnings is not available for versioned measure with id: {}", measure.getId());
    throw new BundleOperationException("Measure", measure.getId(), null);
  }

  public PackageDto getMeasureExport(Measure measure, String elmErrorSeverity, String accessToken) {
    if (measure == null) {
      return null;
    }
    if (measure.getMeasureMetaData().isDraft()) {
      return getMeasureExportForDraft(measure, elmErrorSeverity, accessToken);
    }
    return getMeasureExportForVersion(measure, elmErrorSeverity);
  }

  PackageDto getMeasureExportForDraft(
      Measure measure, String elmErrorSeverity, String accessToken) {
    try {
      elmToJsonService.retrieveElmJson(measure, elmErrorSeverity, accessToken);
      return PackageDto.builder()
          .fromStorage(false)
          .exportPackage(
              fhirServicesClient.getMeasureBundleExport(measure, elmErrorSeverity, accessToken))
          .build();
    } catch (RestClientException | IllegalArgumentException ex) {
      log.error("An error occurred while bundling measure {}", measure.getId(), ex);
      throw new BundleOperationException("Measure", measure.getId(), ex);
    }
  }

  PackageDto getMeasureExportForVersion(Measure measure, String elmErrorSeverity) {
    try {
      // get the Packaging Utility for measure model
      PackagingUtility utility = PackagingUtilityFactory.getInstance(measure.getModel());
      String exportFileName = ExportFileNamesUtil.getExportFileName(measure);

      Export export = exportRepository.findByMeasureId(measure.getId()).orElse(null);
      if (export == null) {
        log.error("Export not available for versioned measure with id: {}", measure.getId());
        throw new BundleOperationException("Measure", measure.getId(), null);
      }

      // Original implementation where everything exists on export object
      if (StringUtils.isNotEmpty(export.getMeasureBundleJson())) {
        return PackageDto.builder()
            .fromStorage(true)
            .exportPackage(utility.getZipBundle(export, exportFileName))
            .build();
      }
      // Fetch content from GridFS if IDs exist
      String measureBundle = null;
      if (StringUtils.isNotBlank(elmErrorSeverity)) {
        if (elmErrorSeverity.equals("Error")
            && export.getMeasureBundleWithoutWarningsGridFsId() != null) {
          measureBundle =
              mongoGridFsService.findById(export.getMeasureBundleWithoutWarningsGridFsId());
        } else if (export.getMeasureBundleGridFsId() != null) {
          measureBundle = mongoGridFsService.findById(export.getMeasureBundleGridFsId());
        }
      }
      export.setMeasureBundleJson(measureBundle);
      return PackageDto.builder()
          .fromStorage(true)
          .exportPackage(utility.getZipBundle(export, exportFileName))
          .build();
    } catch (RestClientException
        | IllegalArgumentException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException
        | SecurityException
        | ClassNotFoundException ex) {
      log.error("An error occurred while bundling measure {}", measure.getId(), ex);
      throw new BundleOperationException("Measure", measure.getId(), ex);
    }
  }
}
