package cms.gov.madie.measure.services;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Component;
import gov.cms.madie.models.measure.Group;
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

import static cms.gov.madie.measure.utils.ServiceConstants.LEGACY_MEASURE_EXPORT_WARNING;

@Slf4j
@Service
@AllArgsConstructor
public class BundleService {

  private final FhirServicesClient fhirServicesClient;
  private final ExportRepository exportRepository;
  private final ElmToJsonService elmToJsonService;
  private final MongoGridFsService mongoGridFsService;
  private final MeasureRepository measureRepository;

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
      if (measure.getMeasureMetaData().isComposite()) {
        return getMeasureExportForCompositeDraft(measure, elmErrorSeverity, accessToken);
      }
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

  PackageDto getMeasureExportForCompositeDraft(
      Measure measure, String elmErrorSeverity, String accessToken) {
    try {
      // populate all the component details required for composite measure resource generation
      populateComponentDetails(measure);
      // get the composite measure bundle
      String compositeBundle =
          fhirServicesClient.getMeasureBundle(measure, accessToken, "export", elmErrorSeverity);

      // fetch component measure bundles from database
      List<Export> componentExports = getComponentExports(measure, elmErrorSeverity);

      // get the Packaging Utility for measure model
      String exportFileName = ExportFileNamesUtil.getExportFileName(measure);
      PackagingUtility utility = PackagingUtilityFactory.getInstance(measure.getModel());
      // Add component resources to the composite bundle and generate the composite export package
      return PackageDto.builder()
          .fromStorage(false)
          .exportPackage(
              utility.buildCompositeExport(compositeBundle, componentExports, exportFileName))
          .build();
    } catch (RestClientException
        | ClassNotFoundException
        | InvocationTargetException
        | InstantiationException
        | IllegalAccessException
        | NoSuchMethodException ex) {
      log.error("An error occurred while bundling measure {}", measure.getId(), ex);
      throw new BundleOperationException("Measure", measure.getId(), ex);
    }
  }

  PackageDto getMeasureExportForVersion(Measure measure, String elmErrorSeverity) {
    try {
      // get the Packaging Utility for measure model
      PackagingUtility utility = PackagingUtilityFactory.getInstance(measure.getModel());
      String exportFileName = ExportFileNamesUtil.getExportFileName(measure);
      Export export = fetchExportForMeasure(measure.getId(), elmErrorSeverity);
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

  private Export fetchExportForMeasure(String measureId, String elmErrorSeverity) {
    Export export = exportRepository.findByMeasureId(measureId).orElse(null);
    if (export == null) {
      log.error("Export not available for versioned measure with id: {}", measureId);
      throw new ResourceNotFoundException("saved export for Measure", measureId);
    }
    String measureBundle;
    if ("Error".equalsIgnoreCase(elmErrorSeverity)) {
      measureBundle = mongoGridFsService.findById(export.getMeasureBundleWithoutWarningsGridFsId());
      if (StringUtils.isEmpty(measureBundle)) {
        log.error("Publishable export not available for versioned measure with id: {}", measureId);
        throw new ResourceNotFoundException(LEGACY_MEASURE_EXPORT_WARNING);
      }
    } else {
      measureBundle = mongoGridFsService.findById(export.getMeasureBundleGridFsId());
      if (StringUtils.isEmpty(measureBundle)) {
        measureBundle = export.getMeasureBundleJson();
        if (StringUtils.isEmpty(measureBundle)) {
          log.error("Export not available for versioned measure with id: {}", measureId);
          throw new ResourceNotFoundException("saved export for Measure", measureId);
        }
      }
    }
    export.setMeasureBundleJson(measureBundle);
    return export;
  }

  /** Fetches the measure bundle for each component across all groups from the export repository. */
  private List<Export> getComponentExports(Measure measure, String elmErrorSeverity) {
    List<Export> exports = new ArrayList<>();
    if (CollectionUtils.isEmpty(measure.getGroups())) {
      return exports;
    }
    for (Group group : measure.getGroups()) {
      if (CollectionUtils.isEmpty(group.getComponents())) {
        continue;
      }
      for (Component component : group.getComponents()) {
        if (StringUtils.isBlank(component.getMeasureId())) {
          continue;
        }
        Export componentExport = fetchExportForMeasure(component.getMeasureId(), elmErrorSeverity);
        exports.add(componentExport);
      }
    }
    return exports;
  }

  /**
   * Populates transient fields (measureName, measureLibraryName, measureVersion, draft,
   * groupDisplayId) on each Component by fetching the referenced measure from the database.
   */
  private void populateComponentDetails(Measure measure) {
    if (CollectionUtils.isEmpty(measure.getGroups())) {
      return;
    }
    for (Group group : measure.getGroups()) {
      if (CollectionUtils.isEmpty(group.getComponents())) {
        continue;
      }
      for (Component component : group.getComponents()) {
        if (StringUtils.isBlank(component.getMeasureId())) {
          continue;
        }
        Measure componentMeasure =
          measureRepository.findById(component.getMeasureId()).orElse(null);
        if (componentMeasure != null) {
          component.setMeasureName(componentMeasure.getMeasureName());
          component.setMeasureLibraryName(componentMeasure.getCqlLibraryName());
          component.setMeasureVersion(
            componentMeasure.getVersion() != null
              ? componentMeasure.getVersion().toString()
              : null);
          component.setDraft(componentMeasure.getMeasureMetaData().isDraft());
          component.setMultiGroupComponent(componentMeasure.getGroups().size() > 1);
          if (StringUtils.isNotBlank(component.getGroupId())
            && !CollectionUtils.isEmpty(componentMeasure.getGroups())) {
            componentMeasure.getGroups().stream()
              .filter(g -> component.getGroupId().equals(g.getId()))
              .findFirst()
              .ifPresent(g -> component.setGroupDisplayId(g.getDisplayId()));
          }
        }
      }
    }
  }
}
