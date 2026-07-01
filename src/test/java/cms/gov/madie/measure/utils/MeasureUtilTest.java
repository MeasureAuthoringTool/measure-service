package cms.gov.madie.measure.utils;

import cms.gov.madie.measure.exceptions.InvalidMeasureObservationException;
import cms.gov.madie.measure.exceptions.InvalidReturnTypeException;
import cms.gov.madie.measure.validations.CqlDefinitionReturnTypeService;
import cms.gov.madie.measure.validations.CqlObservationFunctionService;
import gov.cms.madie.models.measure.*;
import gov.cms.madie.models.validators.ValidLibraryNameValidator;
import gov.cms.madie.models.common.IncludedLibrary;
import gov.cms.madie.models.common.ModelType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeasureUtilTest {

  @Mock private CqlDefinitionReturnTypeService cqlDefinitionReturnTypeService;
  @Mock private CqlObservationFunctionService cqlObservationFunctionService;
  @Mock private ValidLibraryNameValidator validLibraryNameValidator;
  @InjectMocks private MeasureUtil measureUtil;

  @Test
  public void testValidateAllMeasureGroupReturnTypesReturnsNullForNullInput() {
    Measure output = measureUtil.validateAllMeasureDependencies(null);
    assertThat(output, is(nullValue()));
  }

  @Test
  public void testNoSupplementalData_ValidJson() {

    Measure measure = Measure.builder().elmJson("{}").build();

    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.isCqlErrors(), is(false));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(
        output.getErrors().contains(MeasureErrorType.MISMATCH_CQL_SUPPLEMENTAL_DATA), is(false));
    assertThat(output.getErrors().contains(MeasureErrorType.MISSING_ELM), is(false));
  }

  @Test
  public void testCqlDefinitionNotPresentForSupplementalData_ValidJson() {
    DefDescPair supplementalData =
        DefDescPair.builder()
            .definition("THIS_DEFINITION")
            .description("Just a dumb definition")
            .build();
    List<DefDescPair> sdes =
        new ArrayList<>() {
          {
            add(supplementalData);
          }
        };

    Measure measure = Measure.builder().elmJson("{}").supplementalData(sdes).build();

    doReturn(false)
        .when(cqlDefinitionReturnTypeService)
        .isDefineInElm(any(DefDescPair.class), anyString());

    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.isCqlErrors(), is(false));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(
        output.getErrors().contains(MeasureErrorType.MISMATCH_CQL_SUPPLEMENTAL_DATA), is(true));
    assertThat(output.getErrors().contains(MeasureErrorType.MISSING_ELM), is(false));
  }

  @Test
  public void testCqlDefinitionNotPresentForRiskAdjustmentValidJson() {
    DefDescPair riskAdjustmentVariables =
        DefDescPair.builder()
            .definition("THIS_DEFINITION")
            .description("Just a dumb definition")
            .build();
    List<DefDescPair> ravs =
        new ArrayList<>() {
          {
            add(riskAdjustmentVariables);
          }
        };

    Measure measure = Measure.builder().elmJson("{}").riskAdjustments(ravs).build();

    doReturn(false)
        .when(cqlDefinitionReturnTypeService)
        .isDefineInElm(any(DefDescPair.class), anyString());
    when(validLibraryNameValidator.isValid(any(Measure.class), isNull())).thenReturn(true);
    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.isCqlErrors(), is(false));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(
        output.getErrors().contains(MeasureErrorType.MISMATCH_CQL_RISK_ADJUSTMENT), is(true));
    assertThat(output.getErrors().contains(MeasureErrorType.MISSING_ELM), is(false));
  }

  @Test
  public void testCqlDefinitionPresentForSupplementalDataValidJson() {
    DefDescPair supplementalData =
        DefDescPair.builder()
            .definition("THIS_DEFINITION")
            .description("Just a dumb definition")
            .build();
    List<DefDescPair> sdes =
        new ArrayList<>() {
          {
            add(supplementalData);
          }
        };

    Measure measure = Measure.builder().elmJson("{}").supplementalData(sdes).build();

    doReturn(true)
        .when(cqlDefinitionReturnTypeService)
        .isDefineInElm(any(DefDescPair.class), anyString());

    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.isCqlErrors(), is(false));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(
        output.getErrors().contains(MeasureErrorType.MISMATCH_CQL_SUPPLEMENTAL_DATA), is(false));
    assertThat(output.getErrors().contains(MeasureErrorType.MISSING_ELM), is(false));
  }

  @Test
  public void testCqlDefinitionNotPresentForSupplementalDataNullElm() {
    DefDescPair supplementalData =
        DefDescPair.builder()
            .definition("THIS_DEFINITION")
            .description("Just a dumb definition")
            .build();
    List<DefDescPair> sdes =
        new ArrayList<>() {
          {
            add(supplementalData);
          }
        };

    Measure measure = Measure.builder().elmJson(null).supplementalData(sdes).build();

    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.isCqlErrors(), is(false));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(
        output.getErrors().contains(MeasureErrorType.MISMATCH_CQL_SUPPLEMENTAL_DATA), is(true));
    assertThat(output.getErrors().contains(MeasureErrorType.MISSING_ELM), is(true));
  }

  @Test
  public void testValidateAllMeasureGroupReturnTypesReturnsNoErrorsForNoGroups() {
    Measure measure =
        Measure.builder().elmJson("{}").cqlLibraryName("NotNull").groups(List.of()).build();
    when(validLibraryNameValidator.isValid(any(Measure.class), isNull())).thenReturn(true);
    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(output.getErrors().isEmpty(), is(true));
  }

  @Test
  public void testValidateAllMeasureGroupReturnTypesReturnsMeasureNoErrors() throws Exception {
    Measure measure =
        Measure.builder()
            .elmJson("{}")
            .cqlLibraryName("NotNull")
            .groups(
                List.of(
                    Group.builder().id("Group1").populations(null).build(),
                    Group.builder()
                        .id("Group2")
                        .populations(
                            List.of(
                                Population.builder().definition("").build(),
                                Population.builder().definition("GOOD DEFINE HERE").build()))
                        .build()))
            .build();
    when(validLibraryNameValidator.isValid(any(Measure.class), isNull())).thenReturn(true);
    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(output.getErrors().isEmpty(), is(true));
  }

  @Test
  public void testValidateAllMeasureGroupReturnTypesReturnsMeasureWithErrorsRemoved()
      throws Exception {
    Measure measure =
        Measure.builder()
            .elmJson("{}")
            .cqlLibraryName("NotNull")
            .model(ModelType.QI_CORE.getValue())
            .error(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES)
            .groups(
                List.of(
                    Group.builder().id("Group1").populations(null).build(),
                    Group.builder()
                        .id("Group2")
                        .populations(
                            List.of(
                                Population.builder().definition("").build(),
                                Population.builder().definition("GOOD DEFINE HERE").build()))
                        .build()))
            .build();
    doNothing()
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypes(any(Group.class), anyString());
    doNothing()
        .when(cqlObservationFunctionService)
        .validateObservationFunctions(any(Group.class), anyString());
    when(validLibraryNameValidator.isValid(any(Measure.class), isNull())).thenReturn(true);
    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(output.getErrors().isEmpty(), is(true));
  }

  @Test
  public void testValidateAllMeasureGroupReturnTypesReturnsMeasureWithErrorAddedForPopulation()
      throws Exception {
    Measure measure =
        Measure.builder()
            .elmJson("{}")
            .model(ModelType.QI_CORE.getValue())
            .groups(
                List.of(
                    Group.builder().id("Group1").populations(null).build(),
                    Group.builder()
                        .id("Group2")
                        .populations(
                            List.of(
                                Population.builder().definition("").build(),
                                Population.builder().definition("GOOD DEFINE HERE").build()))
                        .build()))
            .build();
    doThrow(new InvalidReturnTypeException("DEFINITIONS"))
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypes(any(Group.class), anyString());

    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(
        output.getErrors().contains(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES),
        is(true));
  }

  @Test
  public void testValidateAllMeasureGroupReturnTypesReturnsMeasureWithErrorAddedForObservation()
      throws Exception {
    Measure measure =
        Measure.builder()
            .elmJson("{}")
            .model(ModelType.QI_CORE.getValue())
            .groups(
                List.of(
                    Group.builder().id("Group1").populations(null).build(),
                    Group.builder()
                        .id("Group2")
                        .populations(
                            List.of(
                                Population.builder().definition("").build(),
                                Population.builder().definition("GOOD DEFINE HERE").build()))
                        .build()))
            .build();
    doNothing()
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypes(any(Group.class), anyString());
    doThrow(new InvalidReturnTypeException("OBSERVATIONS"))
        .when(cqlObservationFunctionService)
        .validateObservationFunctions(any(Group.class), anyString());

    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(
        output.getErrors().contains(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES),
        is(true));
  }

  @Test
  public void
      testValidateAllMeasureGroupReturnTypesReturnsForQiCore6MeasureWithErrorAddedForPopulation()
          throws Exception {
    Measure measure =
        Measure.builder()
            .elmJson("{}")
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .groups(
                List.of(
                    Group.builder().id("Group1").populations(null).build(),
                    Group.builder()
                        .id("Group2")
                        .populations(
                            List.of(
                                Population.builder().definition("").build(),
                                Population.builder().definition("GOOD DEFINE HERE").build()))
                        .build()))
            .build();
    doThrow(new InvalidReturnTypeException("DEFINITIONS"))
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypes(any(Group.class), anyString());

    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(
        output.getErrors().contains(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES),
        is(true));
  }

  @Test
  public void
      testValidateAllMeasureGroupReturnTypesReturnsMeasureWithErrorForGroupsExistButNoElm() {
    Measure measure =
        Measure.builder()
            .elmJson(null)
            .model(ModelType.QI_CORE.getValue())
            .groups(
                List.of(
                    Group.builder().id("Group1").populations(null).build(),
                    Group.builder()
                        .id("Group2")
                        .populations(
                            List.of(
                                Population.builder().definition("").build(),
                                Population.builder().definition("GOOD DEFINE HERE").build()))
                        .build()))
            .build();

    doThrow(new InvalidReturnTypeException("DEFINITIONS"))
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypes(any(Group.class), anyString());

    Measure output = measureUtil.validateAllMeasureDependencies(measure);

    assertThat(output, is(notNullValue()));
    assertThat(output.isCqlErrors(), is(false));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(
        output.getErrors().contains(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES),
        is(true));
    assertThat(output.getErrors().contains(MeasureErrorType.MISSING_ELM), is(true));
  }

  @Test
  public void
      testValidateAllMeasureGroupReturnTypesReturnsMeasureWithNoErrorForNoGroupsExistWithNoElm() {
    Measure measure =
        Measure.builder().elmJson(null).cqlLibraryName("NotNull").groups(List.of()).build();
    when(validLibraryNameValidator.isValid(any(Measure.class), isNull())).thenReturn(true);
    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.isCqlErrors(), is(false));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(output.getErrors().isEmpty(), is(true));
  }

  @Test
  public void
      testValidateAllMeasureGroupReturnTypesReturnsMeasureWithErrorsRemovedForNoGroupsExistWithNoElm() {
    Measure measure =
        Measure.builder()
            .elmJson(null)
            .cqlLibraryName("NotNull")
            .groups(List.of())
            .error(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES)
            .build();
    when(validLibraryNameValidator.isValid(any(Measure.class), isNull())).thenReturn(true);
    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.isCqlErrors(), is(false));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(output.getErrors().isEmpty(), is(true));
  }

  @Test
  public void testIsGroupsExistWithPopulationsReturnsTrue() {
    Measure measure =
        Measure.builder()
            .groups(
                List.of(
                    Group.builder().id("Group1").populations(null).build(),
                    Group.builder()
                        .id("Group2")
                        .populations(
                            List.of(
                                Population.builder().definition("").build(),
                                Population.builder().definition("DEFINE HERE").build()))
                        .build()))
            .build();
    boolean output = measureUtil.isGroupsExistWithPopulations(measure);
    assertThat(output, is(true));
  }

  @Test
  public void testIsGroupReturnTypesValidReturnsTrue() {
    doNothing()
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypes(any(Group.class), anyString());
    doNothing()
        .when(cqlObservationFunctionService)
        .validateObservationFunctions(any(Group.class), anyString());

    boolean output = measureUtil.isGroupReturnTypesValid(Group.builder().build(), "");
    assertThat(output, is(true));
  }

  @Test
  public void testIsGroupReturnTypesValidReturnsFalseForCqlDefinitionReturnTypesException() {
    doThrow(new InvalidReturnTypeException("DEFINITIONS"))
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypes(any(Group.class), anyString());

    boolean output = measureUtil.isGroupReturnTypesValid(Group.builder().build(), "");
    assertThat(output, is(false));
    verify(cqlDefinitionReturnTypeService, times(1))
        .validateCqlDefinitionReturnTypes(any(Group.class), anyString());
    verifyNoInteractions(cqlObservationFunctionService);
  }

  @Test
  public void testIsGroupReturnTypesValidReturnsFalseForObservationsReturnTypesException() {
    doNothing()
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypes(any(Group.class), anyString());
    doThrow(new InvalidReturnTypeException("OBSERVATIONS"))
        .when(cqlObservationFunctionService)
        .validateObservationFunctions(any(Group.class), anyString());

    boolean output = measureUtil.isGroupReturnTypesValid(Group.builder().build(), "");
    assertThat(output, is(false));
    verify(cqlDefinitionReturnTypeService, times(1))
        .validateCqlDefinitionReturnTypes(any(Group.class), anyString());
    verify(cqlObservationFunctionService, times(1))
        .validateObservationFunctions(any(Group.class), anyString());
  }

  @Test
  public void testRemoveErrorReturnsInputForNullErrors() {
    Set<MeasureErrorType> errors = null;
    MeasureErrorType error = MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES;
    Set<MeasureErrorType> output = measureUtil.removeError(errors, error);
    assertThat(output, is(notNullValue()));
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  public void testRemoveErrorReturnsInputForEmpty() {
    Set<MeasureErrorType> errors = new HashSet<>();
    MeasureErrorType error = MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES;
    Set<MeasureErrorType> output = measureUtil.removeError(errors, error);
    assertThat(output, is(equalTo(errors)));
  }

  @Test
  public void testRemoveErrorReturnsInputForNullError() {
    Set<MeasureErrorType> errors = new HashSet<>();
    errors.add(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES);
    MeasureErrorType error = null;
    Set<MeasureErrorType> output = measureUtil.removeError(errors, error);
    assertThat(output, is(equalTo(errors)));
  }

  @Test
  public void testRemoveErrorReturnsEmptySetWithErrorRemoved() {
    Set<MeasureErrorType> errors = new HashSet<>();
    errors.add(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES);
    MeasureErrorType error = MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES;
    Set<MeasureErrorType> output = measureUtil.removeError(errors, error);
    assertThat(output, is(notNullValue()));
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  public void testRemoveErrorReturnsSetWithErrorRemoved() {
    Set<MeasureErrorType> errors = new HashSet<>();
    errors.add(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES);
    errors.add(MeasureErrorType.ERRORS_ELM_JSON);
    MeasureErrorType error = MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES;
    Set<MeasureErrorType> output = measureUtil.removeError(errors, error);
    assertThat(output, is(notNullValue()));
    assertThat(output.size(), is(equalTo(1)));
    assertThat(output.contains(MeasureErrorType.ERRORS_ELM_JSON), is(true));
    assertThat(errors.size(), is(equalTo(2)));
  }

  @Test
  public void testIsGroupsExistWithPopulationsReturnsFalseForNullMeasure() {
    boolean output = measureUtil.isGroupsExistWithPopulations(null);
    assertThat(output, is(false));
  }

  @Test
  public void testIsGroupsExistWithPopulationsReturnsFalseForNullGroups() {
    Measure measure = new Measure();
    measure.setGroups(null);
    boolean output = measureUtil.isGroupsExistWithPopulations(measure);
    assertThat(output, is(false));
  }

  @Test
  public void testIsGroupsExistWithPopulationsReturnsFalseForEmptyGroups() {
    Measure measure = Measure.builder().groups(List.of()).build();
    boolean output = measureUtil.isGroupsExistWithPopulations(measure);
    assertThat(output, is(false));
  }

  @Test
  public void testIsGroupsExistWithPopulationsReturnsFalseForGroupsWithNoPopulations() {
    Measure measure =
        Measure.builder()
            .groups(
                List.of(
                    Group.builder().id("Group1").populations(null).build(),
                    Group.builder().id("Group2").populations(null).build()))
            .build();
    boolean output = measureUtil.isGroupsExistWithPopulations(measure);
    assertThat(output, is(false));
  }

  @Test
  public void
      testIsGroupsExistWithPopulationsReturnsFalseForGroupsWithPopulationsButAllNullDefines() {
    Measure measure =
        Measure.builder()
            .groups(
                List.of(
                    Group.builder().id("Group1").populations(null).build(),
                    Group.builder()
                        .id("Group2")
                        .populations(
                            List.of(
                                Population.builder().definition(null).build(),
                                Population.builder().definition(null).build()))
                        .build()))
            .build();
    boolean output = measureUtil.isGroupsExistWithPopulations(measure);
    assertThat(output, is(false));
  }

  @Test
  public void
      testIsGroupsExistWithPopulationsReturnsFalseForGroupsWithPopulationsButAllBlankDefines() {
    Measure measure =
        Measure.builder()
            .groups(
                List.of(
                    Group.builder().id("Group1").populations(null).build(),
                    Group.builder()
                        .id("Group2")
                        .populations(
                            List.of(
                                Population.builder().definition("").build(),
                                Population.builder().definition("").build()))
                        .build()))
            .build();
    boolean output = measureUtil.isGroupsExistWithPopulations(measure);
    assertThat(output, is(false));
  }

  @Test
  public void testIsMesureCqlChangedReturnsFalseForOriginalAndChangedMeasureBothNull() {
    boolean output = measureUtil.isMeasureCqlChanged(null, null);
    assertThat(output, is(false));
  }

  @Test
  public void testIsMeasureCqlChangedReturnsTrueForNullOriginalMeasure() {
    final Measure changed = Measure.builder().cql("this is the changed cql").build();
    boolean output = measureUtil.isMeasureCqlChanged(null, changed);
    assertThat(output, is(true));
  }

  @Test
  public void testIsMeasureCqlChangedReturnsTrueForNullChangedMeasure() {
    final Measure original = Measure.builder().cql("THIS IS THE ORIGINAL CQL").build();
    boolean output = measureUtil.isMeasureCqlChanged(original, null);
    assertThat(output, is(true));
  }

  @Test
  public void testIsMeasureCqlChangedReturnsTrueForNullOriginalCql() {
    final Measure original = Measure.builder().cql(null).build();
    final Measure changed = Measure.builder().cql("this is the changed cql").build();
    boolean output = measureUtil.isMeasureCqlChanged(original, changed);
    assertThat(output, is(true));
  }

  @Test
  public void testIsMeasureCqlChangedReturnsTrueForNullChangedCql() {
    final Measure original = Measure.builder().cql("THIS IS THE ORIGINAL CQL").build();
    final Measure changed = Measure.builder().cql(null).build();
    boolean output = measureUtil.isMeasureCqlChanged(original, changed);
    assertThat(output, is(true));
  }

  @Test
  public void testIsMeasureCqlChangedReturnsTrueForModifiedCql() {
    final Measure original = Measure.builder().cql("THIS IS THE ORIGINAL CQL").build();
    final Measure changed = Measure.builder().cql("this is the changed cql").build();
    boolean output = measureUtil.isMeasureCqlChanged(original, changed);
    assertThat(output, is(true));
  }

  @Test
  public void testIsMeasureCqlChangedReturnsFalseForUnchangedCql() {
    final Measure original = Measure.builder().cql("THIS IS THE ORIGINAL CQL").build();
    final Measure changed = Measure.builder().cql("THIS IS THE ORIGINAL CQL").build();
    boolean output = measureUtil.isMeasureCqlChanged(original, changed);
    assertThat(output, is(false));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsFalseForBothNulls() {
    final Measure original = Measure.builder().cqlLibraryName("ORIGINAL").build();
    final Measure changed = original.toBuilder().build();
    boolean output = measureUtil.isCqlLibraryNameChanged(changed, original);
    assertThat(output, is(false));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsFalseForBothEmpty() {
    final Measure original = Measure.builder().cqlLibraryName("").build();
    final Measure changed = original.toBuilder().build();
    boolean output = measureUtil.isCqlLibraryNameChanged(changed, original);
    assertThat(output, is(false));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsTrueForChange() {
    final Measure original = Measure.builder().cqlLibraryName("ORIGINAL").build();
    final Measure changed = original.toBuilder().cqlLibraryName("CHANGED").build();
    boolean output = measureUtil.isCqlLibraryNameChanged(changed, original);
    assertThat(output, is(true));
  }

  @Test
  public void testIsSupplementalDataChanged_ReturnsFalseForNullSupplementalData() {
    boolean output = measureUtil.isSupplementalDataChanged(new Measure(), new Measure());
    assertThat(output, is(false));
  }

  @Test
  public void testIsSupplementalDataChanged_ReturnsTrueForAddNonNullSupplementalData() {
    DefDescPair supplementalData1 =
        DefDescPair.builder()
            .definition("THIS_DEFINITION")
            .description("Just a dumb definition")
            .build();
    final Measure changed = Measure.builder().supplementalData(List.of(supplementalData1)).build();
    boolean output = measureUtil.isSupplementalDataChanged(new Measure(), changed);
    assertThat(output, is(true));
  }

  @Test
  public void testIsSupplementalDataChanged_ReturnsTrueForSupplementalDataToNull() {
    DefDescPair supplementalData1 =
        DefDescPair.builder()
            .definition("THIS_DEFINITION")
            .description("Just a dumb definition")
            .build();
    final Measure original = Measure.builder().supplementalData(List.of(supplementalData1)).build();
    boolean output = measureUtil.isSupplementalDataChanged(original, new Measure());
    assertThat(output, is(true));
  }

  @Test
  public void testIsSupplementalDataChanged_ReturnsFalseForEmptySupplementalData() {
    final Measure original = Measure.builder().supplementalData(Collections.emptyList()).build();
    final Measure changed = original.toBuilder().supplementalData(Collections.emptyList()).build();
    boolean output = measureUtil.isSupplementalDataChanged(changed, original);
    assertThat(output, is(false));
  }

  @Test
  public void testIsSupplementalDataChanged_ReturnsTrueForRemovedSupplementalData() {
    DefDescPair supplementalData1 =
        DefDescPair.builder()
            .definition("THIS_DEFINITION")
            .description("Just a dumb definition")
            .build();
    final Measure original = Measure.builder().supplementalData(List.of(supplementalData1)).build();
    final Measure changed = Measure.builder().supplementalData(Collections.emptyList()).build();
    boolean output = measureUtil.isSupplementalDataChanged(changed, original);
    assertThat(output, is(true));
  }

  @Test
  public void testIsSupplementalDataChanged_ReturnsTrueForNewNonNullSupplementalData() {
    final Measure original = Measure.builder().supplementalData(Collections.emptyList()).build();
    DefDescPair supplementalData1 =
        DefDescPair.builder()
            .definition("THIS_DEFINITION")
            .description("Just a dumb definition")
            .build();
    final Measure changed =
        original.toBuilder().supplementalData(List.of(supplementalData1)).build();
    boolean output = measureUtil.isSupplementalDataChanged(changed, original);
    assertThat(output, is(true));
  }

  @Test
  public void testIsSupplementalDataChanged_ReturnsFalseForNotChanged() {
    final Measure original = Measure.builder().cqlLibraryName("ORIGINAL").build();
    final Measure changed = original.toBuilder().cqlLibraryName("CHANGED").build();
    boolean output = measureUtil.isSupplementalDataChanged(changed, original);
    assertThat(output, is(false));
  }

  @Test
  public void testIsSupplementalDataChanged_ReturnsTrueForChanged() {

    DefDescPair supplementalData1 =
        DefDescPair.builder()
            .definition("THIS_DEFINITION")
            .description("Just a dumb definition")
            .build();
    List<DefDescPair> sde1 =
        new ArrayList<>() {
          {
            add(supplementalData1);
          }
        };

    DefDescPair supplementalData2 =
        DefDescPair.builder()
            .definition("THAT_DEFINITION")
            .description("Just aother dumb definition")
            .build();

    List<DefDescPair> sde2 =
        new ArrayList<>() {
          {
            add(supplementalData2);
          }
        };

    Measure original = Measure.builder().elmJson("{}").supplementalData(sde1).build();
    Measure changed = original.toBuilder().build();
    changed.setSupplementalData(sde2);

    boolean output = measureUtil.isSupplementalDataChanged(changed, original);
    assertThat(output, is(true));
  }

  @Test
  public void testIsSupplementalDataChanged_ReturnsFalseForUnchanged() {

    DefDescPair supplementalData1 =
        DefDescPair.builder()
            .definition("THIS_DEFINITION")
            .description("Just a dumb definition")
            .build();
    List<DefDescPair> sde1 =
        new ArrayList<>() {
          {
            add(supplementalData1);
          }
        };

    Measure original = Measure.builder().elmJson("{}").supplementalData(sde1).build();
    Measure changed = original.toBuilder().build();
    changed.setSupplementalData(sde1);

    boolean output = measureUtil.isSupplementalDataChanged(changed, original);
    assertThat(output, is(false));
  }

  @Test
  public void testIsMeasurementPeriodChangedReturnsFalseForBothNull() {
    final Measure original = Measure.builder().build();
    final Measure notChanged = original.toBuilder().build();
    boolean output = measureUtil.isMeasurementPeriodChanged(notChanged, original);
    assertThat(output, is(false));
  }

  @Test
  public void testIsMeasurementPeriodChangedReturnsFalseForNoChange() {
    final Measure original =
        Measure.builder()
            .measurementPeriodStart(new Date())
            .measurementPeriodEnd(new Date())
            .build();
    final Measure changed = original.toBuilder().build();
    boolean output = measureUtil.isMeasurementPeriodChanged(changed, original);
    assertThat(output, is(false));
  }

  @Test
  public void testIsMeasurementPeriodChangedReturnsTrueForStartChange() {
    final Measure original =
        Measure.builder()
            .measurementPeriodStart(new Date())
            .measurementPeriodEnd(new Date())
            .build();
    final Measure changed =
        original.toBuilder()
            .measurementPeriodStart(Date.from(Instant.now().minus(5, ChronoUnit.DAYS)))
            .build();
    boolean output = measureUtil.isMeasurementPeriodChanged(changed, original);
    assertThat(output, is(true));
  }

  @Test
  public void testIsMeasurementPeriodChangedReturnsTrueForEndChange() {
    final Measure original =
        Measure.builder()
            .measurementPeriodStart(new Date())
            .measurementPeriodEnd(new Date())
            .build();
    final Measure changed =
        original.toBuilder()
            .measurementPeriodEnd(Date.from(Instant.now().plus(5, ChronoUnit.DAYS)))
            .build();
    boolean output = measureUtil.isMeasurementPeriodChanged(changed, original);
    assertThat(output, is(true));
  }

  @Test
  public void testValidateAllMeasureGroupReturnTypesNoGroupForQdm() throws Exception {
    QdmMeasure measure =
        QdmMeasure.builder()
            .elmJson("{}")
            .model(ModelType.QDM_5_6.getValue())
            .error(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES)
            .cqlLibraryName("NotNull")
            .build();
    when(validLibraryNameValidator.isValid(any(Measure.class), isNull())).thenReturn(true);
    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(output.getErrors().isEmpty(), is(true));
  }

  @Test
  public void testValidateAllMeasureGroupReturnTypesReturnsMeasureWithErrorsRemovedForQdm()
      throws Exception {
    QdmMeasure measure =
        QdmMeasure.builder()
            .elmJson("{}")
            .model(ModelType.QDM_5_6.getValue())
            .cqlLibraryName("NotNull")
            .error(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES)
            .groups(
                List.of(
                    Group.builder().id("Group1").populations(null).build(),
                    Group.builder()
                        .id("Group2")
                        .populations(
                            List.of(
                                Population.builder().definition("").build(),
                                Population.builder().definition("GOOD DEFINE HERE").build()))
                        .build()))
            .build();
    when(validLibraryNameValidator.isValid(any(Measure.class), isNull())).thenReturn(true);
    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(output.getErrors().isEmpty(), is(true));
  }

  @Test
  public void testQdmMismatchCqlPopulationReturnTypesAdded() throws Exception {
    Group group =
        Group.builder()
            .id("Group1")
            .populations(
                List.of(
                    Population.builder().definition("").build(),
                    Population.builder().definition("GOOD DEFINE HERE").build()))
            .build();
    QdmMeasure measure =
        QdmMeasure.builder()
            .elmJson("{}")
            .model(ModelType.QDM_5_6.getValue())
            .error(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES)
            .groups(List.of(group))
            .build();

    doThrow(new IllegalArgumentException("No definitions found."))
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypesForQdm(group, "{}", true);

    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(output.getErrors().isEmpty(), is(false));
  }

  @Test
  public void testIsQDMGroupReturnTypesValidReturnsFalseWithNoDefinitions() throws Exception {

    Group group = Group.builder().build();

    doThrow(new IllegalArgumentException("No definitions found."))
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypesForQdm(group, "{}", true);

    boolean output = measureUtil.isQDMGroupReturnTypesValid(group, "{}", true);
    assertThat(output, is(false));
  }

  @Test
  public void testIsQDMGroupReturnTypesValidReturnsFalseWithNoObservationDefinition()
      throws Exception {
    Group group = Group.builder().build();
    doThrow(
            new InvalidMeasureObservationException(
                "Measure CQL does not have observation definition"))
        .when(cqlObservationFunctionService)
        .validateObservationFunctionsForQdm(group, "{}", true, "");

    boolean output = measureUtil.isQDMGroupReturnTypesValid(group, "{}", true);
    assertThat(output, is(false));
  }

  @Test
  public void testIsSupplementalDataChanged() throws Exception {
    DefDescPair supplementalData1 =
        DefDescPair.builder().definition("test 1").description("desc 1").build();
    List<DefDescPair> sdes =
        new ArrayList<>() {
          {
            add(supplementalData1);
          }
        };
    Measure original = Measure.builder().build();
    Measure changed = Measure.builder().build();

    boolean result = measureUtil.isSupplementalDataChanged(changed, original);
    assertFalse(result);

    original.setSupplementalData(sdes);
    result = measureUtil.isSupplementalDataChanged(changed, original);
    assertTrue(result);

    DefDescPair supplementalData2 =
        DefDescPair.builder().definition("test 2").description("desc 2").build();
    List<DefDescPair> sdes2 =
        new ArrayList<>() {
          {
            add(supplementalData1);
          }
        };
    original.setSupplementalData(sdes2);

    result = measureUtil.isSupplementalDataChanged(changed, original);
    assertTrue(result);
  }

  @Test
  public void testIsRiskAdjustmentChanged() throws Exception {
    DefDescPair riskAdjustment1 =
        DefDescPair.builder().definition("test 1").description("desc 1").build();
    List<DefDescPair> ravs =
        new ArrayList<>() {
          {
            add(riskAdjustment1);
          }
        };
    Measure original = Measure.builder().build();
    Measure changed = Measure.builder().build();

    boolean result = measureUtil.isRiskAdjustmentChanged(changed, original);
    assertFalse(result);

    original.setRiskAdjustments(ravs);
    result = measureUtil.isRiskAdjustmentChanged(changed, original);
    assertTrue(result);

    DefDescPair riskAdjustment2 =
        DefDescPair.builder().definition("test 2").description("desc 2").build();
    List<DefDescPair> ravs2 =
        new ArrayList<>() {
          {
            add(riskAdjustment2);
          }
        };
    changed.setRiskAdjustments(ravs2);

    result = measureUtil.isRiskAdjustmentChanged(changed, original);
    assertTrue(result);
  }

  @Test
  public void isTestCaseConfigurationChangedReturnsFalseForNullObjects() {
    Measure updatingMeasure = Measure.builder().testCaseConfiguration(null).build();
    Measure existingMeasure = Measure.builder().testCaseConfiguration(null).build();
    boolean result = measureUtil.isTestCaseConfigurationChanged(updatingMeasure, existingMeasure);
    assertFalse(result);
  }

  @Test
  public void isTestCaseConfigurationChangedReturnsTrueWhenNewConfigurationIsAdded() {
    TestCaseConfiguration testCaseConfiguration =
        TestCaseConfiguration.builder()
            .id("test-case-config")
            .sdeIncluded(false)
            .manifestExpansion(
                ManifestExpansion.builder().id("manifest-123").fullUrl("manifest-123-url").build())
            .build();
    Measure updatingMeasure =
        Measure.builder().testCaseConfiguration(testCaseConfiguration).build();
    Measure existingMeasure = Measure.builder().testCaseConfiguration(null).build();
    boolean result = measureUtil.isTestCaseConfigurationChanged(updatingMeasure, existingMeasure);
    assertTrue(result);
  }

  @Test
  public void isTestCaseConfigurationChangedReturnsTrueWhenSdeIsUpdated() {
    TestCaseConfiguration existingTestCaseConfiguration =
        TestCaseConfiguration.builder()
            .id("test-case-config")
            .sdeIncluded(false)
            .manifestExpansion(
                ManifestExpansion.builder().id("manifest-123").fullUrl("manifest-123-url").build())
            .build();
    TestCaseConfiguration newTestCaseConfiguration =
        TestCaseConfiguration.builder()
            .id("test-case-config")
            .sdeIncluded(true)
            .manifestExpansion(
                ManifestExpansion.builder().id("manifest-123").fullUrl("manifest-123-url").build())
            .build();
    Measure updatingMeasure =
        Measure.builder().testCaseConfiguration(newTestCaseConfiguration).build();
    Measure existingMeasure =
        Measure.builder().testCaseConfiguration(existingTestCaseConfiguration).build();
    boolean result = measureUtil.isTestCaseConfigurationChanged(updatingMeasure, existingMeasure);
    assertTrue(result);
  }

  @Test
  public void isTestCaseConfigurationChangedReturnsTrueWhenManifestExpansionIsUpdated() {
    TestCaseConfiguration existingTestCaseConfiguration =
        TestCaseConfiguration.builder()
            .id("test-case-config")
            .sdeIncluded(true)
            .manifestExpansion(
                ManifestExpansion.builder().id("manifest-123").fullUrl("manifest-123-url").build())
            .build();
    TestCaseConfiguration newTestCaseConfiguration =
        TestCaseConfiguration.builder()
            .id("test-case-config")
            .sdeIncluded(true)
            .manifestExpansion(
                ManifestExpansion.builder().id("manifest-456").fullUrl("manifest-456-url").build())
            .build();
    Measure updatingMeasure =
        Measure.builder().testCaseConfiguration(newTestCaseConfiguration).build();
    Measure existingMeasure =
        Measure.builder().testCaseConfiguration(existingTestCaseConfiguration).build();
    boolean result = measureUtil.isTestCaseConfigurationChanged(updatingMeasure, existingMeasure);
    assertTrue(result);
  }

  @Test
  public void testCqlLibraryNameEmptyGetRejected() {
    Measure measure = Measure.builder().elmJson("{}").groups(List.of()).build();
    when(validLibraryNameValidator.isValid(any(Measure.class), isNull())).thenReturn(true);
    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(output.getErrors().contains(MeasureErrorType.INVALID_LIBRARY_NAME), is(true));
  }

  @Test
  public void testCqlLibraryNameLong64GetRejected() {
    Measure measure =
        Measure.builder()
            .cqlLibraryName("1234567812345678123456781234567812345678123456781234567812345678a")
            .elmJson("{}")
            .groups(List.of())
            .build();
    when(validLibraryNameValidator.isValid(any(Measure.class), isNull())).thenReturn(true);
    Measure output = measureUtil.validateAllMeasureDependencies(measure);
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(output.getErrors().isEmpty(), is(false));
    assertThat(output.getErrors().contains(MeasureErrorType.INVALID_LIBRARY_NAME), is(true));
  }

  @Test
  public void testGetIncludedLibrariesBlankCql() {
    List<IncludedLibrary> result = MeasureUtil.getIncludedLibraries("");
    assertThat(result.size(), is(0));
  }

  @Test
  public void testIsMeasureGroupChangedReturnsFalseForNullObjects() {
    Measure updatingMeasure = Measure.builder().groups(null).build();
    Measure existingMeasure = Measure.builder().groups(null).build();
    assertFalse(MeasureUtil.isMeasureGroupsChanged(updatingMeasure, existingMeasure));
  }

  @Test
  public void testIsMeasureGroupChangedReturnsTrueWhenNewGroupIsAdded() {
    Group group =
        Group.builder()
            .id("Group1")
            .scoring(MeasureScoring.PROPORTION.toString())
            .populations(
                List.of(
                    Population.builder().definition("IPP").build(),
                    Population.builder().definition("DENOM").build(),
                    Population.builder().definition("NUMER").build()))
            .build();
    Measure newGroup = Measure.builder().groups(List.of(group)).build();
    Measure nullGroup = Measure.builder().groups(null).build();
    boolean existingIsNull = MeasureUtil.isMeasureGroupsChanged(newGroup, nullGroup);
    assertTrue(existingIsNull);

    boolean modifiedIsNull = MeasureUtil.isMeasureGroupsChanged(nullGroup, newGroup);
    assertTrue(modifiedIsNull);
  }

  @Test
  public void testIsMeasureGroupChangedReturnsTrueWhenGroupModified() {
    Group group =
        Group.builder()
            .id("Group1")
            .scoring(MeasureScoring.PROPORTION.toString())
            .populations(
                List.of(
                    Population.builder().definition("IPP").build(),
                    Population.builder().definition("DENOM").build(),
                    Population.builder().definition("NUMER").build()))
            .build();
    Group modifiedGroup =
        Group.builder()
            .id("Group1")
            .scoring(MeasureScoring.COHORT.toString())
            .populations(List.of(Population.builder().definition("IPP").build()))
            .build();
    Measure updatingMeasure = Measure.builder().groups(List.of(modifiedGroup)).build();
    Measure existingMeasure = Measure.builder().groups(List.of(group)).build();
    boolean result = MeasureUtil.isMeasureGroupsChanged(updatingMeasure, existingMeasure);
    assertTrue(result);
  }
}
