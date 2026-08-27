package cms.gov.madie.measure.services;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import cms.gov.madie.measure.dto.CompositeVersionArtifacts;
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
import static cms.gov.madie.measure.constants.BundleTypeConstants.CALCULATION;
import static cms.gov.madie.measure.constants.BundleTypeConstants.EXPORT;
import static cms.gov.madie.measure.constants.BundleTypeConstants.PUBLISH;

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
    String resolvedBundleType = StringUtils.isNotBlank(bundleType) ? bundleType : CALCULATION;
    // for draft measures
    if (measure.getMeasureMetaData().isDraft()) {
      if (measure.getMeasureMetaData().isComposite()) {
        return bundleCompositeMeasure(measure, accessToken, resolvedBundleType, elmErrorSeverity);
      }
      return bundleStandardMeasure(measure, accessToken, resolvedBundleType, elmErrorSeverity);
    }
    // for versioned measures
    return getVersionedMeasureBundle(measure, resolvedBundleType);
  }

  public PackageDto getMeasureExport(Measure measure, String elmErrorSeverity, String accessToken) {
    return getMeasureExport(measure, null, elmErrorSeverity, accessToken);
  }

  public PackageDto getMeasureExport(
      Measure measure, String bundleType, String elmErrorSeverity, String accessToken) {
    if (measure == null) {
      return null;
    }
    String resolvedBundleType =
        StringUtils.isNotBlank(bundleType)
            ? bundleType
            : "Error".equalsIgnoreCase(elmErrorSeverity) ? PUBLISH : EXPORT;
    if (measure.getMeasureMetaData().isDraft()) {
      if (measure.getMeasureMetaData().isComposite()) {
        return getMeasureExportForCompositeDraft(
            measure, resolvedBundleType, elmErrorSeverity, accessToken);
      }
      return getMeasureExportForDraft(measure, resolvedBundleType, elmErrorSeverity, accessToken);
    }
    return getMeasureExportForVersion(measure, resolvedBundleType);
  }

  PackageDto getMeasureExportForDraft(
      Measure measure, String bundleType, String elmErrorSeverity, String accessToken) {
    try {
      elmToJsonService.retrieveElmJson(measure, elmErrorSeverity);
      byte[] exportPackage =
          EXPORT.equals(bundleType)
              ? fhirServicesClient.getMeasureBundleExport(measure, elmErrorSeverity, accessToken)
              : fhirServicesClient.getMeasureBundleExport(
                  measure, bundleType, elmErrorSeverity, accessToken);
      return PackageDto.builder().fromStorage(false).exportPackage(exportPackage).build();
    } catch (RestClientException | IllegalArgumentException ex) {
      log.error("An error occurred while bundling measure {}", measure.getId(), ex);
      throw new BundleOperationException("Measure", measure.getId(), ex);
    }
  }

  PackageDto getMeasureExportForCompositeDraft(
      Measure measure, String bundleType, String elmErrorSeverity, String accessToken) {
    try {
      populateComponentDetails(measure);
      String compositeBundle =
          fhirServicesClient.getMeasureBundle(measure, accessToken, bundleType, elmErrorSeverity);
      List<Export> componentExports = getComponentExports(measure, bundleType);
      List<Export.ComponentHumanReadable> componentHumanReadables =
          buildComponentHumanReadables(componentExports);

      String exportFileName = ExportFileNamesUtil.getExportFileName(measure);
      PackagingUtility utility = getPackagingUtility(measure.getModel());
      return PackageDto.builder()
          .fromStorage(false)
          .exportPackage(
              utility.buildCompositeExport(
                  compositeBundle, componentExports, componentHumanReadables, exportFileName))
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

  PackageDto getMeasureExportForVersion(Measure measure, String bundleType) {
    try {
      PackagingUtility utility = getPackagingUtility(measure.getModel());
      String exportFileName = ExportFileNamesUtil.getExportFileName(measure);
      Export export = fetchExportForMeasure(measure.getId(), bundleType);
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

  private String bundleCompositeMeasure(
      Measure measure, String accessToken, String bundleType, String elmErrorSeverity) {
    return generateCompositeBundle(measure, accessToken, bundleType, elmErrorSeverity).bundleJson();
  }

  private String bundleStandardMeasure(
      Measure measure, String accessToken, String bundleType, String elmErrorSeverity) {
    try {
      elmToJsonService.retrieveElmJson(measure, elmErrorSeverity);
      return fhirServicesClient.getMeasureBundle(
          measure, accessToken, bundleType, elmErrorSeverity);
    } catch (RestClientException | IllegalArgumentException ex) {
      log.error("An error occurred while bundling measure {}", measure.getId(), ex);
      throw new BundleOperationException("Measure", measure.getId(), ex);
    }
  }

  private String getVersionedMeasureBundle(Measure measure, String bundleType) {
    Export export = exportRepository.findByMeasureId(measure.getId()).orElse(null);
    if (export == null) {
      log.error("Export not available for versioned measure with id: {}", measure.getId());
      throw new BundleOperationException("Measure", measure.getId(), null);
    }
    if (PUBLISH.equalsIgnoreCase(bundleType)) {
      String publishableBundle =
          StringUtils.isNotBlank(export.getMeasureBundleWithoutWarningsGridFsId())
              ? mongoGridFsService.findById(export.getMeasureBundleWithoutWarningsGridFsId())
              : null;
      if (StringUtils.isNotBlank(publishableBundle)) {
        return publishableBundle;
      }
      log.error(
          "Publishable export not available for versioned measure with id: {}", measure.getId());
      throw new ResourceNotFoundException(LEGACY_MEASURE_EXPORT_WARNING);
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

  /**
   * Builds the artifacts persisted when versioning a composite: the merged composite bundle both
   * with ELM warnings ("Info" severity) and without ("Error" severity) and a snapshot of each
   * component's human-readable.
   */
  CompositeVersionArtifacts buildCompositeVersionArtifacts(Measure measure, String accessToken) {
    CompositeBundleResult withWarnings =
        generateCompositeBundle(measure, accessToken, EXPORT, "Info");
    CompositeBundleResult withoutWarnings =
        generateCompositeBundle(measure, accessToken, PUBLISH, "Error");
    List<Export.ComponentHumanReadable> componentHumanReadables =
        buildComponentHumanReadables(withWarnings.componentExports());
    return new CompositeVersionArtifacts(
        withWarnings.bundleJson(), withoutWarnings.bundleJson(), componentHumanReadables);
  }

  private CompositeBundleResult generateCompositeBundle(
      Measure measure, String accessToken, String bundleType, String elmErrorSeverity) {
    try {
      populateComponentDetails(measure);
      String compositeBundle =
          fhirServicesClient.getMeasureBundle(measure, accessToken, bundleType, elmErrorSeverity);
      List<Export> componentExports = getComponentExports(measure, bundleType);
      PackagingUtility utility = getPackagingUtility(measure.getModel());
      String bundleJson = utility.buildCompositeMeasureBundle(compositeBundle, componentExports);
      return new CompositeBundleResult(bundleJson, componentExports);
    } catch (RestClientException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException
        | ClassNotFoundException ex) {
      log.error("An error occurred while bundling composite measure {}", measure.getId(), ex);
      throw new BundleOperationException("Measure", measure.getId(), ex);
    }
  }

  /** Snapshots each component's stored human-readable */
  private List<Export.ComponentHumanReadable> buildComponentHumanReadables(
      List<Export> componentExports) {
    List<Export.ComponentHumanReadable> componentHumanReadables = new ArrayList<>();
    if (CollectionUtils.isEmpty(componentExports)) {
      return componentHumanReadables;
    }
    Map<String, Measure> measuresById = new HashMap<>();
    measureRepository
        .findAllById(componentExports.stream().map(Export::getMeasureId).toList())
        .forEach(componentMeasure -> measuresById.put(componentMeasure.getId(), componentMeasure));
    for (Export componentExport : componentExports) {
      Measure componentMeasure = measuresById.get(componentExport.getMeasureId());
      if (componentMeasure != null) {
        componentHumanReadables.add(
            Export.ComponentHumanReadable.builder()
                .componentId(componentExport.getMeasureId())
                .fileName(ExportFileNamesUtil.getExportFileName(componentMeasure))
                .humanReadable(componentExport.getHumanReadable())
                .build());
      }
    }
    return componentHumanReadables;
  }

  private Export fetchExportForMeasure(String measureId, String bundleType) {
    Export export =
        exportRepository
            .findByMeasureId(measureId)
            .orElseThrow(
                () -> {
                  log.error("Export not available for versioned measure with id: {}", measureId);
                  return new ResourceNotFoundException("saved export for Measure", measureId);
                });

    String measureBundle;
    if (PUBLISH.equalsIgnoreCase(bundleType)) {
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
  private List<Export> getComponentExports(Measure measure, String bundleType) {
    if (CollectionUtils.isEmpty(measure.getGroups())) {
      return new ArrayList<>();
    }
    return measure.getGroups().stream()
        .filter(group -> !CollectionUtils.isEmpty(group.getComponents()))
        .flatMap(group -> group.getComponents().stream())
        .filter(component -> StringUtils.isNotBlank(component.getMeasureId()))
        .map(component -> fetchExportForMeasure(component.getMeasureId(), bundleType))
        .toList();
  }

  /**
   * Populates transient fields (measureName, measureLibraryName, measureVersion, draft,
   * multiGroupComponent, groupDisplayId) on each Component by fetching the referenced measure.
   */
  private void populateComponentDetails(Measure measure) {
    if (CollectionUtils.isEmpty(measure.getGroups())) {
      return;
    }
    streamComponents(measure).forEach(this::populateSingleComponent);
  }

  private void populateSingleComponent(Component component) {
    measureRepository
        .findById(component.getMeasureId())
        .ifPresent(
            componentMeasure -> {
              component.setMeasureName(componentMeasure.getMeasureName());
              component.setMeasureLibraryName(componentMeasure.getCqlLibraryName());
              component.setMeasureVersion(
                  componentMeasure.getVersion() != null
                      ? componentMeasure.getVersion().toString()
                      : null);
              component.setDraft(componentMeasure.getMeasureMetaData().isDraft());
              component.setMultiGroupComponent(componentMeasure.getGroups().size() > 1);
              populateGroupDisplayId(component, componentMeasure);
            });
  }

  private void populateGroupDisplayId(Component component, Measure componentMeasure) {
    if (StringUtils.isBlank(component.getGroupId())
        || CollectionUtils.isEmpty(componentMeasure.getGroups())) {
      return;
    }
    componentMeasure.getGroups().stream()
        .filter(g -> component.getGroupId().equals(g.getId()))
        .findFirst()
        .ifPresent(g -> component.setGroupDisplayId(g.getDisplayId()));
  }

  /** Returns a stream of all components with non-blank measureId across all groups. */
  private Stream<Component> streamComponents(Measure measure) {
    return measure.getGroups().stream()
        .map(Group::getComponents)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .filter(component -> StringUtils.isNotBlank(component.getMeasureId()));
  }

  private PackagingUtility getPackagingUtility(String model)
      throws InstantiationException,
          IllegalAccessException,
          InvocationTargetException,
          NoSuchMethodException,
          ClassNotFoundException {
    return PackagingUtilityFactory.getInstance(model);
  }

  /** The merged composite bundle plus the component exports it was assembled from. */
  private record CompositeBundleResult(String bundleJson, List<Export> componentExports) {}
}
