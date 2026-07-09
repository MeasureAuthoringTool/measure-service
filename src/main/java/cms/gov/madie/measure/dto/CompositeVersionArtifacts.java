package cms.gov.madie.measure.dto;

import java.util.List;

import gov.cms.madie.models.measure.Export;

/** Composite versioning artifacts: the with-/without-warnings bundles + component HR snapshot. */
public record CompositeVersionArtifacts(
    String bundleJson,
    String bundleJsonWithoutWarnings,
    List<Export.ComponentHumanReadable> componentHumanReadables) {}
