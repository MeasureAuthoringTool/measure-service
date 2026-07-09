package cms.gov.madie.measure.validations;

import cms.gov.madie.measure.exceptions.InvalidMeasureObservationException;
import cms.gov.madie.measure.exceptions.InvalidReturnTypeException;
import cms.gov.madie.measure.utils.ResourceUtil;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.MeasureObservation;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CqlObservationFunctionServiceTest implements ResourceUtil {

  @InjectMocks private CqlObservationFunctionService cqlObservationFunctionService;

  @Test
  public void testValidateObservationFunctionsThrowsInvalidMeasureObservationException() {
    String elmJson = getData("/test_elm_no_observation.json");
    MeasureObservation observation =
        MeasureObservation.builder().definition("boolFunc").aggregateMethod("Count").build();
    Group group =
        Group.builder()
            .scoring("Continuous Variable")
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
            .measureObservations(List.of(observation))
            .build();

    assertThrows(
        InvalidMeasureObservationException.class,
        () -> cqlObservationFunctionService.validateObservationFunctions(group, elmJson),
        "Measure CQL does not have observation definition");
  }

  @Test
  public void testValidateObservationFunctionsSuccess() {
    String elmJson = getData("/test_elm_with_observation.json");
    MeasureObservation observation = MeasureObservation.builder().aggregateMethod("Count").build();
    Group group =
        Group.builder()
            .scoring("Continuous Variable")
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
            .measureObservations(List.of(observation))
            .build();

    assertDoesNotThrow(
        () -> cqlObservationFunctionService.validateObservationFunctions(group, elmJson));
  }

  @Test
  public void testValidateObservationFunctionsSuccessWithNoCQLNoObservation() {
    Group group =
        Group.builder()
            .scoring("Continuous Variable")
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
            .measureObservations(null)
            .build();
    assertDoesNotThrow(() -> cqlObservationFunctionService.validateObservationFunctions(group, ""));
  }

  @Test
  public void
      testValidateObservationFunctionsThrowsInvalidReturnTypeExceptionWithBooleanGroupPopulationBasis() {
    String elmJson = getData("/test_elm.json");
    MeasureObservation observation =
        MeasureObservation.builder().definition("boolFunc").aggregateMethod("Count").build();
    Group group =
        Group.builder()
            .scoring("Continuous Variable")
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
            .measureObservations(List.of(observation))
            .build();

    assertThrows(
        InvalidReturnTypeException.class,
        () -> cqlObservationFunctionService.validateObservationFunctions(group, elmJson),
        "Selected observation function boolFunc can not have parameters");
  }

  @Test
  public void testValidateObservationFunctionsThrowsInvalidReturnTypeException() {
    String elmJson = getData("/test_elm.json");
    MeasureObservation observation =
        MeasureObservation.builder().definition("boolFunc").aggregateMethod("Count").build();
    Group group =
        Group.builder()
            .scoring("Continuous Variable")
            .populationBasis("non-boolean")
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
            .measureObservations(List.of(observation))
            .build();

    assertThrows(
        InvalidReturnTypeException.class,
        () -> cqlObservationFunctionService.validateObservationFunctions(group, elmJson),
        "Selected observation function boolFunc can not have parameters");
  }

  @Test
  public void testValidateObservationFunctionsThrowsInvalidMeasureObservationExceptionForQdm() {
    String elmJson = getData("/test_elm_no_observation.json");
    MeasureObservation observation =
        MeasureObservation.builder().definition("boolFunc").aggregateMethod("Count").build();
    Group group =
        Group.builder()
            .scoring("Continuous Variable")
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
            .measureObservations(List.of(observation))
            .build();

    assertThrows(
        InvalidMeasureObservationException.class,
        () ->
            cqlObservationFunctionService.validateObservationFunctionsForQdm(
                group, elmJson, true, ""),
        "Measure CQL does not have observation definition");
  }

  @Test
  public void testValidateObservationFunctionsSuccessWithNoCQLNoObservationForQdm() {
    Group group =
        Group.builder()
            .scoring("Continuous Variable")
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
            .measureObservations(null)
            .build();
    assertDoesNotThrow(
        () ->
            cqlObservationFunctionService.validateObservationFunctionsForQdm(group, "", true, ""));
  }

  @Test
  public void testValidateObservationFunctionsSuccessForQdm() {
    String elmJson = getData("/test_elm_with_observation.json");
    MeasureObservation observation =
        MeasureObservation.builder().aggregateMethod("Count").definition("boolFunc").build();
    Group group =
        Group.builder()
            .scoring("Continuous Variable")
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
            .measureObservations(List.of(observation))
            .build();

    assertDoesNotThrow(
        () ->
            cqlObservationFunctionService.validateObservationFunctionsForQdm(
                group, elmJson, true, ""));
  }

  @Test
  public void
      testValidateObservationFunctionsThrowsInvalidReturnTypeExceptionWithBooleanGroupPopulationBasisForQdm() {
    String elmJson = getData("/test_elm.json");
    MeasureObservation observation =
        MeasureObservation.builder().definition("boolFunc").aggregateMethod("Count").build();
    Group group =
        Group.builder()
            .scoring("Continuous Variable")
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
            .measureObservations(List.of(observation))
            .build();

    assertThrows(
        InvalidReturnTypeException.class,
        () ->
            cqlObservationFunctionService.validateObservationFunctionsForQdm(
                group, elmJson, true, ""),
        "Selected observation function boolFunc can not have parameters");
  }

  @Test
  public void testValidateObservationFunctionsThrowsInvalidReturnTypeExceptionForQdm() {
    String elmJson = getData("/test_elm.json");
    MeasureObservation observation =
        MeasureObservation.builder().definition("boolFunc").aggregateMethod("Count").build();
    Group group =
        Group.builder()
            .scoring("Continuous Variable")
            .populationBasis("non-boolean")
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
            .measureObservations(List.of(observation))
            .build();

    assertThrows(
        InvalidReturnTypeException.class,
        () ->
            cqlObservationFunctionService.validateObservationFunctionsForQdm(
                group, elmJson, false, "test"),
        "Selected observation function boolFunc can not have parameters");
  }

  @Test
  public void
      testValidateObservationFunctionsThrowsInvalidReturnTypeExceptionForQdmNonPatientBased() {
    String elmJson = getData("/test_elm.json");
    MeasureObservation observation =
        MeasureObservation.builder().definition("boolFunc").aggregateMethod("Count").build();
    Group group =
        Group.builder()
            .scoring("Continuous Variable")
            .populationBasis("non-boolean")
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
            .measureObservations(List.of(observation))
            .build();

    assertThrows(
        InvalidReturnTypeException.class,
        () ->
            cqlObservationFunctionService.validateObservationFunctionsForQdm(
                group, elmJson, false, "boolFunc"),
        "Selected observation function must have exactly one parameter of type 'false'");
  }
}
