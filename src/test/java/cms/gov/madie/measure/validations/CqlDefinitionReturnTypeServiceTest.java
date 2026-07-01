package cms.gov.madie.measure.validations;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;


import cms.gov.madie.measure.exceptions.InvalidFhirGroupException;
import cms.gov.madie.measure.exceptions.InvalidGroupException;
import cms.gov.madie.measure.exceptions.InvalidReturnTypeException;
import cms.gov.madie.measure.exceptions.InvalidReturnTypeForQdmException;
import cms.gov.madie.measure.utils.ResourceUtil;
import gov.cms.madie.models.measure.DefDescPair;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.MeasureGroupTypes;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.Stratification;

@ExtendWith(MockitoExtension.class)
class CqlDefinitionReturnTypeServiceTest implements ResourceUtil {

  @InjectMocks private CqlDefinitionReturnTypeService qlDefinitionReturnTypeService;

  @Test
  void testValidateCqlDefinitionReturnTypesNullElm() {
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1"),
                    new Population(
                        "id-2",
                        PopulationType.INITIAL_POPULATION,
                        "Denominator",
                        null,
                        null,
                        "Denominator_1")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypes(group1, null),
        "No definitions found.");
  }

  @Test
  void testValidateCqlDefinitionReturnTypesNullPopulations() {

    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm.json");

    assertDoesNotThrow(
        () -> qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypes(group1, elmJson));
  }

  @Test
  void testValidateCqlDefinitionReturnTypesNoDefinition() {

    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "",
                        null,
                        null,
                        "IntialPopulation_1")))
            .build();

    String elmJson = getData("/test_elm.json");

    assertDoesNotThrow(
        () -> qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypes(group1, elmJson));
  }

  @Test
  void testValidateCqlDefinitionReturnTypesInvalidForStratification() {
    Stratification strat = new Stratification();
    strat.setId("id-2");
    strat.setDescription("test desc");
    strat.setCqlDefinition("ipp2");
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    Stratification strat2 = new Stratification();
    strat2.setId("id-3");
    strat2.setDescription("test desc");
    strat2.setCqlDefinition("ipp2");
    strat2.setAssociation(PopulationType.INITIAL_POPULATION);
    // new group, not in DB, so no ID
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .stratifications(Arrays.asList(strat, strat2))
            .build();

    String elmJson = getData("/test_elm.json");

    assertThrows(
        InvalidReturnTypeException.class,
        () -> qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypes(group1, elmJson),
        "Return type for the CQL definition selected for the Stratification(s) does not match with population basis.");
  }

  @Test
  void testValidateCqlDefinitionReturnTypesValidForStratification() {
    Stratification strat = new Stratification();
    strat.setId("id-2");
    strat.setDescription("test desc");
    strat.setCqlDefinition("Initial Population");
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    Stratification strat2 = new Stratification();
    strat2.setId("id-3");
    strat2.setDescription("test desc");
    strat2.setCqlDefinition("Initial Population");
    strat2.setAssociation(PopulationType.INITIAL_POPULATION);
    // new group, not in DB, so no ID
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .stratifications(Arrays.asList(strat, strat2))
            .build();

    String elmJson = getData("/test_elm.json");

    assertDoesNotThrow(
        () -> qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypes(group1, elmJson));
  }

  @Test
  void testValidateCqlDefinitionReturnTypesNoStratificationDefinition() {
    Stratification strat = new Stratification();
    strat.setId("id-2");
    strat.setDescription("test desc");
    strat.setAssociation(PopulationType.INITIAL_POPULATION);

    // new group, not in DB, so no ID
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .stratifications(Arrays.asList(strat))
            .build();

    String elmJson = getData("/test_elm.json");

    assertDoesNotThrow(
        () -> qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypes(group1, elmJson));
  }

  @Test
  void testValidateCqlDefinitionReturnTypesThrowsInvalidReturnTypeException() {
    // new group, not in DB, so no ID
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "SDE Race",
                        null,
                        null,
                        "IntialPopulation_1")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm.json");

    assertThrows(
        InvalidReturnTypeException.class,
        () -> qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypes(group1, elmJson),
        "Return type for the CQL definition selected for the Initial Population does not match with population basis.");
  }

  @Test
  void testValidateSdeDefinitions_Valid() {
    String elmJson = getData("/test_elm.json");

    DefDescPair sde =
        DefDescPair.builder().definition("fun23").description("Please Help Me").build();
    boolean isValid = qlDefinitionReturnTypeService.isDefineInElm(sde, elmJson);
    assertThat(isValid, is(true));
  }

  @Test
  void testValidateSdeDefinitions_Invalid() {
    String elmJson = getData("/test_elm.json");

    DefDescPair sde =
        DefDescPair.builder().definition("fun34").description("Please Help Me").build();
    boolean isValid = qlDefinitionReturnTypeService.isDefineInElm(sde, elmJson);
    assertThat(isValid, is(false));
  }

  @Test
  void testValidateSdeDefinitions_InvalidJson() {
    String elmJson = "{ curroped: json";

    DefDescPair sde =
        DefDescPair.builder().definition("fun34").description("Please Help Me").build();
    boolean isValid = qlDefinitionReturnTypeService.isDefineInElm(sde, elmJson);
    assertThat(isValid, is(false));
  }

  @Test
  void testValidateCqlDefinitionReturnTypesInvalidGroup() {
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populationBasis("boolean")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm.json");
    assertThrows(
        InvalidFhirGroupException.class,
        () -> qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypes(group1, elmJson),
        "Measure Group Types and Population Basis are required for FHIR Measure Group.");

    group1.setPopulationBasis(null);
    group1.setMeasureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME));
    assertThrows(
        InvalidFhirGroupException.class,
        () -> qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypes(group1, elmJson),
        "Measure Group Types and Population Basis are required for FHIR Measure Group.");
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmNullElm() {
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1"),
                    new Population(
                        "id-2",
                        PopulationType.INITIAL_POPULATION,
                        "Denominator",
                        null,
                        null,
                        "Denominator_1")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, null, true),
        "No definitions found.");
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmPatientBasedThrowsException() {
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm.json");

    assertThrows(
        InvalidReturnTypeForQdmException.class,
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, elmJson, true),
        "For Patient-based Measures, selected definitions must return a Boolean.");
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmNonPatientBasedThrowsException() {
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1_1"),
                    new Population(
                        "id-2",
                        PopulationType.INITIAL_POPULATION,
                        "SDE Payer",
                        null,
                        null,
                        "IntialPopulation_1_2")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm.json");

    assertThrows(
        InvalidReturnTypeForQdmException.class,
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, elmJson, false),
        "For Episode-based Measures, selected definitions must return a list of the same type (Non-Boolean).");
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmPatientBasedSuccess() {
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "boolIpp",
                        null,
                        null,
                        "IntialPopulation_1")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm_with_boolean.json");

    assertDoesNotThrow(
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, elmJson, true));
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmNonPatientBasedSuccess() {
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1_1"),
                    new Population(
                        "id-2",
                        PopulationType.INITIAL_POPULATION,
                        "Denominator",
                        null,
                        null,
                        "IntialPopulation_1_2")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm.json");

    assertDoesNotThrow(
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, elmJson, false));
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmFailNoPopulations() {
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm.json");

    assertThrows(
        InvalidGroupException.class,
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, elmJson, false));
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmSuccessNoDefinition() {
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        null,
                        null,
                        null,
                        "IntialPopulation_1")))
            .build();

    String elmJson = getData("/test_elm.json");

    assertDoesNotThrow(
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, elmJson, false));
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmNonPatientBasedWithBoolean() {
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "boolIpp",
                        null,
                        null,
                        "IntialPopulation_1")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm_with_boolean.json");

    assertThrows(
        InvalidReturnTypeForQdmException.class,
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, elmJson, false),
        "For Episode-based Measures, selected definitions must return a list of the same type (Non-Boolean).");
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmPatientBasedWithStrats() {
    Stratification strat = new Stratification();
    strat.setId("id-2");
    strat.setDescription("test desc");
    strat.setCqlDefinition("ipp2");
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "boolIpp",
                        null,
                        null,
                        "IntialPopulation_1_1"),
                    new Population(
                        "id-2",
                        PopulationType.INITIAL_POPULATION,
                        "boolIpp2",
                        null,
                        null,
                        "IntialPopulation_1_2")))
            .stratifications(List.of(strat))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm_with_boolean.json");

    assertThrows(
        InvalidReturnTypeForQdmException.class,
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, elmJson, true),
        "For Patient-based Measures, selected definitions must return a Boolean.");
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmNonPatientBasedWithStrats() {
    Stratification strat = new Stratification();
    strat.setId("id-2");
    strat.setDescription("test desc");
    strat.setCqlDefinition("boolIpp");
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "ipp",
                        null,
                        null,
                        "IntialPopulation_1_1"),
                    new Population(
                        "id-2",
                        PopulationType.INITIAL_POPULATION,
                        "denom",
                        null,
                        null,
                        "IntialPopulation_1_2")))
            .stratifications(List.of(strat))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm_with_boolean.json");

    assertThrows(
        InvalidReturnTypeForQdmException.class,
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, elmJson, false),
        "For Episode-based Measures, selected definitions must return a list of the same type (Non-Boolean).");
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmNonPatientBasedWithStratsSuccess() {
    Stratification strat = new Stratification();
    strat.setId("id-2");
    strat.setDescription("test desc");
    strat.setCqlDefinition("ipp");
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "ipp",
                        null,
                        null,
                        "IntialPopulation_1_1"),
                    new Population(
                        "id-2",
                        PopulationType.INITIAL_POPULATION,
                        "denom",
                        null,
                        null,
                        "IntialPopulation_1_2")))
            .stratifications(List.of(strat))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm_with_boolean.json");

    assertDoesNotThrow(
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, elmJson, false));
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmNonPatientBasedStratNoDefinition() {
    Stratification strat = new Stratification();
    strat.setId("id-2");
    strat.setDescription("test desc");
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "ipp",
                        null,
                        null,
                        "IntialPopulation_1_1"),
                    new Population(
                        "id-2",
                        PopulationType.INITIAL_POPULATION,
                        "denom",
                        null,
                        null,
                        "IntialPopulation_1_2")))
            .stratifications(List.of(strat))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm_with_boolean.json");

    assertDoesNotThrow(
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, elmJson, false));
  }

  @Test
  void testValidateCqlDefinitionReturnTypesForQdmPatientBasedStratDefinitionBoolean() {
    Stratification strat = new Stratification();
    strat.setId("id-2");
    strat.setDescription("test desc");
    strat.setCqlDefinition("boolIpp");
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    Group group1 =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "boolIpp",
                        null,
                        null,
                        "IntialPopulation_1")))
            .stratifications(List.of(strat))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    String elmJson = getData("/test_elm_with_boolean.json");

    assertDoesNotThrow(
        () ->
            qlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
                group1, elmJson, true));
  }
}
