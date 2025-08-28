package cms.gov.madie.measure.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import gov.cms.madie.models.common.IncludedLibrary;
import gov.cms.mat.cql.CqlTextParser;
import gov.cms.mat.cql.elements.IncludeProperties;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import cms.gov.madie.measure.validations.CqlDefinitionReturnTypeService;
import cms.gov.madie.measure.validations.CqlObservationFunctionService;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.DefDescPair;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureErrorType;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.QdmMeasure;
import gov.cms.madie.models.validators.ValidLibraryNameValidator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class MeasureUtil {
  private final CqlDefinitionReturnTypeService cqlDefinitionReturnTypeService;
  private final CqlObservationFunctionService cqlObservationFunctionService;
  private ValidLibraryNameValidator validLibraryNameValidator;

  /**
   * Validates measure group population define return types and observation function return types
   * compared to the population basis of the group. If any mismatches are found, an error is added
   * to the list of errors on the measure object
   *
   * <p>Validates dependencies in Supplemental Data
   *
   * <p>TODO Make this a more generic validation that calls other validation components
   *
   * @param measure
   * @return
   */
  public Measure validateAllMeasureDependencies(Measure measure) {
    if (measure == null) {
      return null;
    }
    final String elmJson = measure.getElmJson();
    boolean groupsExistWithPopulations = isGroupsExistWithPopulations(measure);
    Measure.MeasureBuilder<?, ?> measureBuilder = measure.toBuilder();
    measureBuilder.clearErrors();
    Set<MeasureErrorType> errors = new HashSet<>();

    if (elmJson == null) {
      errors.add(MeasureErrorType.MISSING_ELM);
    }

    if (!validLibraryNameValidator.isValid(measure, null)
        || measure.getCqlLibraryName() == null
        || measure.getCqlLibraryName().length() > 64) {
      errors.add(MeasureErrorType.INVALID_LIBRARY_NAME);
    }

    if (ModelType.QI_CORE.getValue().equalsIgnoreCase(measure.getModel())
        || ModelType.QI_CORE_6_0_0.getValue().equals(measure.getModel())) {
      if (groupsExistWithPopulations
          && measure.getGroups().stream()
              .anyMatch(group -> !isGroupReturnTypesValid(group, elmJson))) {
        errors.add(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES);
      }
    } else if (ModelType.QDM_5_6.getValue().equalsIgnoreCase(measure.getModel())) {

      QdmMeasure qdmMeasure = (QdmMeasure) measure;
      if (groupsExistWithPopulations
          && measure.getGroups().stream()
              .anyMatch(
                  group ->
                      !isQDMGroupReturnTypesValid(group, elmJson, qdmMeasure.isPatientBasis()))) {

        errors.add(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES);
      }
    }

    // MAT-5369  Adding checks for CQL Definitions present in SupplementalData
    // if the def in supplemental data isn't in the cql
    if (isCqlDefsMismatched(measure.getSupplementalData(), elmJson)) {
      errors.add(MeasureErrorType.MISMATCH_CQL_SUPPLEMENTAL_DATA);
    }

    // MAT-5464  Adding checks for CQL Definitions present in Risk Adjustment Variables
    // if the def in Risk Adjustment Variables isn't in the cql
    if (isCqlDefsMismatched(measure.getRiskAdjustments(), elmJson)) {
      errors.add(MeasureErrorType.MISMATCH_CQL_RISK_ADJUSTMENT);
    }

    // MAT-5369 If the only error on the stack is MISSING_ELM then remove it and set cqlErrors =
    // false
    if (errors.size() == 1
        && (errors.stream().anyMatch(error -> MeasureErrorType.MISSING_ELM.equals(error)))) {
      measureBuilder.clearErrors();
      measureBuilder.cqlErrors(false);
    } else {
      measureBuilder.errors(errors);
    }
    return measureBuilder.build();
  }

  private boolean isCqlDefsMismatched(List<DefDescPair> defDescPairs, String elmJson) {
    boolean result = false;
    if (CollectionUtils.isEmpty(defDescPairs)) {
      result = false;
    } else {
      result =
          defDescPairs.stream()
              .anyMatch(def -> !cqlDefinitionReturnTypeService.isDefineInElm(def, elmJson));
    }
    return result;
  }

  /**
   * Functional method to remove an error from a set. If the errors set is not null and not empty,
   * and if the error is not null a new set will be returned with the error removed (if found).
   *
   * @param errors
   * @param error
   * @return
   */
  public Set<MeasureErrorType> removeError(Set<MeasureErrorType> errors, MeasureErrorType error) {
    if (errors == null) {
      return Set.of();
    } else if (errors.isEmpty() || error == null) {
      return errors;
    }
    return errors.stream().filter(e -> !error.equals(e)).collect(Collectors.toSet());
  }

  public boolean isGroupsExistWithPopulations(Measure measure) {
    if (measure == null || measure.getGroups() == null || measure.getGroups().isEmpty()) {
      return false;
    }
    return measure.getGroups().stream()
        .anyMatch(
            (group) -> {
              final List<Population> populations = group.getPopulations();
              if (populations == null) {
                return false;
              }
              return populations.stream()
                  .anyMatch(population -> StringUtils.isNotBlank(population.getDefinition()));
            });
  }

  public boolean isGroupReturnTypesValid(final Group group, final String elmJson) {
    try {
      cqlDefinitionReturnTypeService.validateCqlDefinitionReturnTypes(group, elmJson);
    } catch (Exception ex) {
      // Either no return types were found in ELM, or return type mismatch exists
      log.error("An error occurred while validating population return types", ex);
      return false;
    }
    try {
      cqlObservationFunctionService.validateObservationFunctions(group, elmJson);
    } catch (Exception ex) {
      // Either no return types were found in ELM, or return type mismatch exists
      log.error("An error occurred while validating observation return types", ex);
      return false;
    }
    return true;
  }

  public boolean isMeasureCqlChanged(final Measure original, final Measure updated) {
    if (original == null && updated == null) {
      return false;
    } else if (original == null || updated == null) {
      return true;
    }
    return !StringUtils.equals(original.getCql(), updated.getCql());
  }

  public boolean isCqlLibraryNameChanged(Measure measure, Measure persistedMeasure) {
    return !Objects.equals(persistedMeasure.getCqlLibraryName(), measure.getCqlLibraryName());
  }

  public static boolean isTestCaseConfigurationChanged(Measure updatingMeasure, Measure existingMeasure) {
    return !Objects.deepEquals(
        updatingMeasure.getTestCaseConfiguration(), existingMeasure.getTestCaseConfiguration());
  }

  public boolean isMeasurementPeriodChanged(Measure measure, Measure persistedMeasure) {
    return !Objects.equals(
            persistedMeasure.getMeasurementPeriodStart(), measure.getMeasurementPeriodStart())
        || !Objects.equals(
            persistedMeasure.getMeasurementPeriodEnd(), measure.getMeasurementPeriodEnd());
  }

  public boolean isSupplementalDataChanged(Measure changed, Measure original) {
    if (CollectionUtils.isEmpty(original.getSupplementalData())) {
      return !CollectionUtils.isEmpty(changed.getSupplementalData());
    }
    if (CollectionUtils.isEmpty(changed.getSupplementalData())) {
      return !CollectionUtils.isEmpty(original.getSupplementalData());
    }
    // If the lists match, then we didn't change anything
    // changed<sde>[] == original<sde>[]
    return !CollectionUtils.isEqualCollection(
        changed.getSupplementalData(), original.getSupplementalData());
  }

  public boolean isRiskAdjustmentChanged(Measure changed, Measure original) {
    if (CollectionUtils.isEmpty(original.getRiskAdjustments())) {
      return !CollectionUtils.isEmpty(changed.getRiskAdjustments());
    }
    if (CollectionUtils.isEmpty(changed.getRiskAdjustments())) {
      return !CollectionUtils.isEmpty(original.getRiskAdjustments());
    }
    // If the lists match, then we didn't change anything
    // changed<sde>[] == original<sde>[]
    return !CollectionUtils.isEqualCollection(
        changed.getRiskAdjustments(), original.getRiskAdjustments());
  }

  public boolean isQDMGroupReturnTypesValid(
      final Group group, final String elmJson, boolean patientBasis) {

    String cqlDefinitionReturnType = null;
    try {
      cqlDefinitionReturnType =
          cqlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
              group, elmJson, patientBasis);
    } catch (Exception ex) {
      // Either no return types were found in ELM, or return type mismatch exists
      log.error("An error occurred while validating QDM population return types", ex);
      return false;
    }
    try {
      cqlObservationFunctionService.validateObservationFunctionsForQdm(
          group, elmJson, patientBasis, cqlDefinitionReturnType);
    } catch (Exception ex) {
      // Either no return types were found in ELM, or return type mismatch exists
      log.error("An error occurred while validating QDM observation return types", ex);
      return false;
    }
    return true;
  }

  public static List<IncludedLibrary> getIncludedLibraries(String cql) {
    if (StringUtils.isBlank(cql)) {
      return List.of();
    }
    CqlTextParser cqlTextParser = new CqlTextParser(cql);
    List<IncludeProperties> includeProperties = cqlTextParser.getIncludes();
    return includeProperties.stream()
        .map(
            include ->
                IncludedLibrary.builder()
                    .name(StringUtils.trim(include.getName()))
                    .version(include.getVersion())
                    .build())
        .toList();
  }
}
