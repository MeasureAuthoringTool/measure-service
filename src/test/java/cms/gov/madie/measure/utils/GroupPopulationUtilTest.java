package cms.gov.madie.measure.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureGroupTypes;
import gov.cms.madie.models.measure.MeasureObservation;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.Stratification;

@ExtendWith(MockitoExtension.class)
public class GroupPopulationUtilTest {

  private Population population11;
  private Population population2;
  private Population population3;

  private Measure measure;
  private Group group2IPs;
  private Group group1IP;
  private Population population12;

  private MeasureObservation observation1;
  private MeasureObservation observation2;
  private Stratification stratification1;
  private Stratification stratification2;

  List<Group> groups;

  @BeforeEach
  public void setUp() {
    population11 =
        Population.builder()
            .id("id-1")
            .name(PopulationType.INITIAL_POPULATION)
            .definition(PopulationType.INITIAL_POPULATION.getDisplay())
            .displayId("InitialPopulation_1_1")
            .build();

    population12 =
        Population.builder()
            .id("id-4")
            .name(PopulationType.INITIAL_POPULATION)
            .definition(PopulationType.INITIAL_POPULATION.getDisplay())
            .displayId("InitialPopulation_1_2")
            .build();

    population2 =
        Population.builder()
            .id("id-2")
            .name(PopulationType.DENOMINATOR)
            .definition(PopulationType.DENOMINATOR.getDisplay())
            .displayId("Denominator_1")
            .build();

    population3 =
        Population.builder()
            .id("id-3")
            .name(PopulationType.NUMERATOR)
            .definition(PopulationType.NUMERATOR.getDisplay())
            .displayId("Numerator_1")
            .build();

    observation1 =
        MeasureObservation.builder().id("obser1").definition("isFinishedEncounter").build();
    observation2 =
        MeasureObservation.builder().id("obser2").definition("isFinishedEncounter").build();
    stratification1 = Stratification.builder().cqlDefinition("Stratification 1").build();
    stratification2 = Stratification.builder().cqlDefinition("Stratification 2").build();

    group2IPs =
        Group.builder()
            .id("testGroupId1")
            .displayId("Group_1")
            .scoring("Ratio")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .populations(List.of(population11, population12, population2, population3))
            .build();

    group1IP =
        Group.builder()
            .id("testGroupId1")
            .displayId("Group_1")
            .scoring("Proportion")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .populations(List.of(population11, population2, population3))
            .build();

    measure = Measure.builder().id("testMeasureId").groups(List.of(group2IPs)).build();
  }

  @Test
  public void testGetGroupNumber() {
    int result =
        GroupPopulationUtil.getGroupNumber(
            group2IPs, List.of(Group.builder().id("testGroup1").build(), group2IPs));
    assertEquals(result, 2);
  }

  @Test
  public void testSetGroupAndPopulationsDisplayIds() {
    population11.setDisplayId(null);
    population12.setDisplayId(null);
    population2.setDisplayId(null);
    group2IPs.setDisplayId(null);
    group2IPs.setPopulations(List.of(population11, population12, population2, population3));
    measure.setGroups(List.of(group2IPs));

    GroupPopulationUtil.setGroupAndPopulationsDisplayIds(measure, group2IPs);

    assertEquals("Group_1", measure.getGroups().get(0).getDisplayId());
    assertEquals(
        "InitialPopulation_1_1", measure.getGroups().get(0).getPopulations().get(0).getDisplayId());
    assertEquals(
        "InitialPopulation_1_2", measure.getGroups().get(0).getPopulations().get(1).getDisplayId());
    assertEquals(
        "Denominator_1", measure.getGroups().get(0).getPopulations().get(2).getDisplayId());
    assertEquals("Numerator_1", measure.getGroups().get(0).getPopulations().get(3).getDisplayId());
  }

  @Test
  public void testSetGroupAndPopulationsDisplayIdsNoPopulations() {
    Group group = Group.builder().id("testGroupId1").populations(Collections.emptyList()).build();
    measure.setGroups(List.of(group));

    GroupPopulationUtil.setGroupAndPopulationsDisplayIds(measure, group);

    assertEquals("Group_1", measure.getGroups().get(0).getDisplayId());
    assertEquals(0, measure.getGroups().get(0).getPopulations().size());
  }

  @Test
  public void testSetDisplayIdsNoIP() {
    Group group =
        Group.builder()
            .id("testGroupId1")
            .displayId("Group_1")
            .scoring("Proportion")
            .populations(List.of(population2, population3))
            .build();
    measure.setGroups(List.of(group));

    GroupPopulationUtil.setGroupAndPopulationsDisplayIds(measure, group);

    assertEquals("Group_1", measure.getGroups().get(0).getDisplayId());
    assertEquals(
        "Denominator_1", measure.getGroups().get(0).getPopulations().get(0).getDisplayId());
    assertEquals("Numerator_1", measure.getGroups().get(0).getPopulations().get(1).getDisplayId());
  }

  @Test
  public void testSetDisplayIdsOneIP() {
    population11.setDisplayId(null);
    population2.setDisplayId(null);
    group1IP.setDisplayId(null);
    group1IP.setPopulations(List.of(population11, population2, population3));
    measure.setGroups(List.of(group1IP));

    GroupPopulationUtil.setGroupAndPopulationsDisplayIds(measure, group1IP);

    assertEquals("Group_1", measure.getGroups().get(0).getDisplayId());
    assertEquals(
        "InitialPopulation_1", measure.getGroups().get(0).getPopulations().get(0).getDisplayId());
    assertEquals(
        "Denominator_1", measure.getGroups().get(0).getPopulations().get(1).getDisplayId());
    assertEquals("Numerator_1", measure.getGroups().get(0).getPopulations().get(2).getDisplayId());
  }

  @Test
  public void testSetGroupPopulationsObservationsStratificationsDisplayIds() {
    population2 =
        Population.builder()
            .id("id-2")
            .name(PopulationType.MEASURE_POPULATION)
            .definition(PopulationType.MEASURE_POPULATION.getDisplay())
            .build();
    group1IP.setScoring("Continuous Variable");
    group1IP.setDisplayId(null);
    group1IP.setPopulations(List.of(population11, population2));
    group1IP.setMeasureObservations(List.of(observation1, observation2));
    group1IP.setStratifications(List.of(stratification1, stratification2));
    measure.setGroups(List.of(group1IP));

    GroupPopulationUtil.setGroupAndPopulationsDisplayIds(measure, group1IP);

    assertEquals("Group_1", measure.getGroups().get(0).getDisplayId());
    assertEquals(
        "InitialPopulation_1", measure.getGroups().get(0).getPopulations().get(0).getDisplayId());
    assertEquals(
        "MeasurePopulation_1", measure.getGroups().get(0).getPopulations().get(1).getDisplayId());

    assertEquals(2, measure.getGroups().get(0).getMeasureObservations().size());
    assertEquals(
        "MeasureObservation_1_1",
        measure.getGroups().get(0).getMeasureObservations().get(0).getDisplayId());
    assertEquals(
        "MeasureObservation_1_2",
        measure.getGroups().get(0).getMeasureObservations().get(1).getDisplayId());

    assertEquals(2, measure.getGroups().get(0).getStratifications().size());
    assertEquals(
        "Stratification_1_1",
        measure.getGroups().get(0).getStratifications().get(0).getDisplayId());
    assertEquals(
        "Stratification_1_2",
        measure.getGroups().get(0).getStratifications().get(1).getDisplayId());
  }

  @Test
  public void testSetDisplayIdsForSingleObservationAndStratification() {
    population2 =
        Population.builder()
            .id("id-2")
            .name(PopulationType.MEASURE_POPULATION)
            .definition(PopulationType.MEASURE_POPULATION.getDisplay())
            .build();
    group1IP.setScoring("Continuous Variable");
    group1IP.setDisplayId(null);
    group1IP.setPopulations(List.of(population11, population2));
    group1IP.setMeasureObservations(List.of(observation1));
    group1IP.setStratifications(List.of(stratification1));
    measure.setGroups(List.of(group1IP));

    GroupPopulationUtil.setGroupAndPopulationsDisplayIds(measure, group1IP);

    assertEquals("Group_1", measure.getGroups().get(0).getDisplayId());
    assertEquals(
        "InitialPopulation_1", measure.getGroups().get(0).getPopulations().get(0).getDisplayId());
    assertEquals(
        "MeasurePopulation_1", measure.getGroups().get(0).getPopulations().get(1).getDisplayId());

    assertEquals(1, measure.getGroups().get(0).getMeasureObservations().size());
    assertEquals(
        "MeasureObservation_1",
        measure.getGroups().get(0).getMeasureObservations().get(0).getDisplayId());

    assertEquals(1, measure.getGroups().get(0).getStratifications().size());
    assertEquals(
        "Stratification_1", measure.getGroups().get(0).getStratifications().get(0).getDisplayId());
  }
}
