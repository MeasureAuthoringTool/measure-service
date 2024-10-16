package cms.gov.madie.measure.services;

import java.lang.reflect.InvocationTargetException;

import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.exceptions.CqlElmTranslationErrorException;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import gov.cms.madie.models.measure.ElmJson;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
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
  private final ElmTranslatorClient elmTranslatorClient;
  private final ExportRepository exportRepository;

  /**
   * Get the bundle for measure. For draft measure- generate bundle because for draft measure,
   * bundles are not available in DB. For versioned measure- fetch the bundle from measure export
   * that was saved in DB.
   */
  public String bundleMeasure(Measure measure, String accessToken, String bundleType) {
    if (measure == null) {
      return null;
    }
    // for draft measures
    if (measure.getMeasureMetaData().isDraft()) {
      try {
        retrieveElmJson(measure, accessToken);
        return fhirServicesClient.getMeasureBundle(measure, accessToken, bundleType);
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
    return export.getMeasureBundleJson();
  }

  public PackageDto getMeasureExport(Measure measure, String accessToken) {
    if (measure == null) {
      return null;
    }
    try {
      // for draft measures
      if (measure.getMeasureMetaData().isDraft()) {
        try {
          retrieveElmJson(measure, accessToken);
          return PackageDto.builder()
              .fromStorage(false)
              .exportPackage(fhirServicesClient.getMeasureBundleExport(measure, accessToken))
              .build();
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
      String exportFileName = ExportFileNamesUtil.getExportFileName(measure);

      // get a Utility for this model
      String model = measure.getModel();

      PackagingUtility utility = PackagingUtilityFactory.getInstance(model);
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

  protected void retrieveElmJson(Measure measure, String accessToken) {
    if (StringUtils.isBlank(measure.getCql())) {
      throw new InvalidResourceStateException(
          "Measure", measure.getId(), "since there is no associated CQL.");
    }

    if (measure.isCqlErrors()) {
      throw new InvalidResourceStateException(
          "Measure", measure.getId(), "since CQL errors exist.");
    }

    if (CollectionUtils.isEmpty(measure.getGroups())) {
      throw new InvalidResourceStateException(
          "Measure", measure.getId(), "since there are no associated population criteria.");
    }

    final ElmJson elmJson =
        elmTranslatorClient.getElmJson(measure.getCql(), measure.getModel(), accessToken);
    if (elmTranslatorClient.hasErrors(elmJson)) {
      throw new CqlElmTranslationErrorException(measure.getMeasureName());
    }
    measure.setElmJson(elmJson.getJson());
    measure.setElmXml(elmJson.getXml());
  }
}
