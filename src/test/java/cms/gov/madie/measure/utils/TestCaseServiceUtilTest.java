package cms.gov.madie.measure.utils;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import cms.gov.madie.measure.exceptions.InvalidIdException;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.MeasureObservation;
import gov.cms.madie.models.measure.MeasureScoring;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.Stratification;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.TestCaseGroupPopulation;
import gov.cms.madie.models.measure.TestCasePopulationValue;
import gov.cms.madie.models.measure.TestCaseStratificationValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TestCaseServiceUtilTest {
  private Population population1;
  private Population population2;
  private Population population3;
  private Population population4;
  private Population population5;
  private Population measurePopulation;
  private Group group;

  List<TestCasePopulationValue> populationValues = new ArrayList<>();
  TestCasePopulationValue testCasePopulationValue1 =
      TestCasePopulationValue.builder()
          .name(PopulationType.INITIAL_POPULATION)
          .expected("1")
          .build();
  TestCasePopulationValue testCasePopulationValue2 =
      TestCasePopulationValue.builder().name(PopulationType.DENOMINATOR).expected("2").build();
  TestCasePopulationValue testCasePopulationValue3 =
      TestCasePopulationValue.builder()
          .name(PopulationType.DENOMINATOR_EXCLUSION)
          .expected("3")
          .build();
  TestCasePopulationValue testCasePopulationValue4 =
      TestCasePopulationValue.builder().name(PopulationType.NUMERATOR).expected("4").build();
  TestCasePopulationValue testCasePopulationValue5 =
      TestCasePopulationValue.builder()
          .name(PopulationType.NUMERATOR_EXCLUSION)
          .expected("5")
          .build();

  private List<TestCaseGroupPopulation> testCaseGroupPopulations = new ArrayList<>();
  TestCaseGroupPopulation testCaseGroupPopulation1 = null;
  TestCaseGroupPopulation testCaseGroupPopulation2 = null;
  private MeasureObservation measureObservation1 = null;
  private MeasureObservation measureObservation2 = null;

  private TestCase testCase;

  @BeforeEach
  public void setup() {
    populationValues.add(testCasePopulationValue1);
    populationValues.add(testCasePopulationValue2);
    populationValues.add(testCasePopulationValue3);
    populationValues.add(testCasePopulationValue4);
    populationValues.add(testCasePopulationValue5);

    testCaseGroupPopulation1 =
        TestCaseGroupPopulation.builder()
            .populationBasis("Encounter")
            .populationValues(populationValues)
            .build();
    testCaseGroupPopulation2 =
        TestCaseGroupPopulation.builder()
            .populationBasis("Encounter")
            .populationValues(populationValues)
            .build();
    testCaseGroupPopulations.add(testCaseGroupPopulation1);

    population1 =
        Population.builder()
            .id("Population1Id")
            .name(PopulationType.INITIAL_POPULATION)
            .definition("Initial Population")
            .build();
    population2 =
        Population.builder()
            .id("Population2Id")
            .name(PopulationType.DENOMINATOR)
            .definition("Denominator")
            .build();
    population3 =
        Population.builder()
            .id("Population3Id")
            .name(PopulationType.DENOMINATOR_EXCLUSION)
            .definition("Denominator Exclusion")
            .build();
    population4 =
        Population.builder()
            .id("Population4Id")
            .name(PopulationType.NUMERATOR)
            .definition("Numerator")
            .build();
    population5 =
        Population.builder()
            .id("Population5Id")
            .name(PopulationType.NUMERATOR_EXCLUSION)
            .definition("Numerator Exclusion")
            .build();
    measurePopulation =
        Population.builder()
            .id("testCriteriaReference")
            .name(PopulationType.MEASURE_POPULATION)
            .definition("Measure Population")
            .build();
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.COHORT.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population3, population4, population5))
            .build();

    testCase =
        TestCase.builder()
            .id("TESTID")
            .title("IPPPass")
            .series("BloodPressure>124")
            .json("{\"resourceType\":\"Patient\"}")
            .patientId(UUID.randomUUID())
            .createdBeforeVersioning(true)
            .build();

    measureObservation1 =
        MeasureObservation.builder()
            .id("measureObservationId1")
            .definition("Measure Observation")
            .criteriaReference("criteriaReference1")
            .aggregateMethod("Count")
            .build();
    measureObservation2 =
        MeasureObservation.builder()
            .id("measureObservationId2")
            .definition("Measure Observation")
            .criteriaReference("criteriaReference2")
            .aggregateMethod("Average")
            .build();
  }

  @Test
  public void testGetGroupsWithValidPopulationsReturnsNullWithNullInput() {
    List<Group> changedGroups = TestCaseServiceUtil.getGroupsWithValidPopulations(null);
    assertThat(changedGroups, is(nullValue()));
  }

  @Test
  public void testGetGroupsWithValidPopulationsReturnsNullPopulations() {
    group = Group.builder().id("testGroupId").build();
    List<Group> changedGroups = TestCaseServiceUtil.getGroupsWithValidPopulations(List.of(group));
    assertNotNull(changedGroups);
    assertThat(changedGroups.size(), is(1));
    assertNull(changedGroups.get(0).getPopulations());
  }

  @Test
  public void testGetGroupsWithValidPopulationsRemovePopulationsWithoutDefinition() {
    population1 =
        Population.builder()
            .name(PopulationType.INITIAL_POPULATION)
            .definition("Initial Population")
            .build();
    population2 = Population.builder().name(PopulationType.DENOMINATOR).build();
    population3 = Population.builder().name(PopulationType.DENOMINATOR_EXCLUSION).build();
    population4 = Population.builder().name(PopulationType.NUMERATOR).build();
    population5 = Population.builder().name(PopulationType.NUMERATOR_EXCLUSION).build();
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.COHORT.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population3, population4, population5))
            .build();
    List<Group> changedGroups = TestCaseServiceUtil.getGroupsWithValidPopulations(List.of(group));
    assertNotNull(changedGroups);
    assertThat(changedGroups.size(), is(1));
    assertNotNull(changedGroups.get(0));
    assertNotNull(changedGroups.get(0).getPopulations());
    assertThat(changedGroups.get(0).getPopulations().size(), is(1));
  }

  @Test
  public void testGetGroupsWithValidPopulationsNoRemoval() {
    List<Group> changedGroups = TestCaseServiceUtil.getGroupsWithValidPopulations(List.of(group));
    assertNotNull(changedGroups);
    assertThat(changedGroups.size(), is(1));
    assertNotNull(changedGroups.get(0));
    assertNotNull(changedGroups.get(0).getPopulations());
    assertThat(changedGroups.get(0).getPopulations().size(), is(5));
  }

  @Test
  public void testMatchCriteriaGroupsSuccessAllMatch() {
    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(testCaseGroupPopulations, List.of(group), testCase);
    assertTrue(result);
    assertThat(testCase, is(notNullValue()));
    assertThat(testCase.getGroupPopulations().size(), is(equalTo(1)));
    assertThat(testCase.getGroupPopulations().get(0).getGroupId(), is(equalTo("testGroupId")));
    assertThat(testCase.getGroupPopulations().get(0).getPopulationValues(), is(notNullValue()));
    assertThat(testCase.getGroupPopulations().get(0).getPopulationValues().size(), is(equalTo(5)));
    assertThat(
        testCase.getGroupPopulations().get(0).getPopulationValues().get(0).getId(),
        is(equalTo("Population1Id")));
    assertThat(
        testCase.getGroupPopulations().get(0).getPopulationValues().get(1).getId(),
        is(equalTo("Population2Id")));
    assertThat(
        testCase.getGroupPopulations().get(0).getPopulationValues().get(2).getId(),
        is(equalTo("Population3Id")));
    assertThat(
        testCase.getGroupPopulations().get(0).getPopulationValues().get(3).getId(),
        is(equalTo("Population4Id")));
    assertThat(
        testCase.getGroupPopulations().get(0).getPopulationValues().get(4).getId(),
        is(equalTo("Population5Id")));
  }

  @Test
  public void testMatchCriteriaGroupsNullGroup() {
    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(testCaseGroupPopulations, null, testCase);
    assertFalse(result);
  }

  @Test
  public void testMatchCriteriaGroupsNullTestCaseGroupPopulations() {
    testCaseGroupPopulations.clear();
    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(testCaseGroupPopulations, List.of(group), testCase);
    assertFalse(result);
  }

  @Test
  public void testMatchCriteriaGroupsGroupSizeNotMatch() {
    List<TestCaseGroupPopulation> testCaseGroupPopulations = new ArrayList<>();
    populationValues = new ArrayList<>();
    populationValues.add(testCasePopulationValue1);
    testCaseGroupPopulation1 =
        TestCaseGroupPopulation.builder()
            .populationBasis("Boolean")
            .populationValues(populationValues)
            .build();
    testCaseGroupPopulations.add(testCaseGroupPopulation1);
    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(testCaseGroupPopulations, List.of(group), testCase);
    assertFalse(result);
  }

  @Test
  public void testMatchCriteriaGroupsNoGroupPopulation() {
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.COHORT.name())
            .populationBasis("Boolean")
            .build();
    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(testCaseGroupPopulations, List.of(group), testCase);
    assertFalse(result);
  }

  @Test
  public void testMatchCriteriaGroupsNoTestCaseGroupPopulationValues() {
    List<TestCaseGroupPopulation> testCaseGroupPopulations = new ArrayList<>();
    testCaseGroupPopulation1 =
        TestCaseGroupPopulation.builder().populationBasis("Encounter").build();
    testCaseGroupPopulations.add(testCaseGroupPopulation1);
    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(testCaseGroupPopulations, List.of(group), testCase);
    assertFalse(result);
  }

  @Test
  public void testMatchCriteriaGroupsPopulationSizeNotMatch() {
    List<TestCaseGroupPopulation> testCaseGroupPopulations = new ArrayList<>();
    List<TestCasePopulationValue> populationValues = new ArrayList<>();
    populationValues.add(testCasePopulationValue1);
    testCaseGroupPopulation1 =
        TestCaseGroupPopulation.builder()
            .populationBasis("Encounter")
            .populationValues(populationValues)
            .build();
    testCaseGroupPopulations.add(testCaseGroupPopulation1);
    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(testCaseGroupPopulations, List.of(group), testCase);
    assertFalse(result);
  }

  @Test
  public void testMatchCriteriaGroupsNotAllMatched() {
    List<TestCaseGroupPopulation> testCaseGroupPopulations = new ArrayList<>();
    List<TestCasePopulationValue> populationValues = new ArrayList<>();
    populationValues.add(testCasePopulationValue1);
    populationValues.add(testCasePopulationValue2);
    populationValues.add(testCasePopulationValue3);
    populationValues.add(testCasePopulationValue4);
    populationValues.add(testCasePopulationValue4);
    testCaseGroupPopulation1 =
        TestCaseGroupPopulation.builder()
            .populationBasis("Encounter")
            .populationValues(populationValues)
            .build();
    testCaseGroupPopulations.add(testCaseGroupPopulation1);
    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(testCaseGroupPopulations, List.of(group), testCase);
    assertFalse(result);
  }

  @Test
  public void testMatchCriteriaGroupsNoPopulationBasis() {
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.COHORT.name())
            .populations(List.of(population1, population2, population3, population4, population5))
            .build();
    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(testCaseGroupPopulations, List.of(group), testCase);
    assertTrue(result);
  }

  @Test
  public void testMatchCriteriaGroupsPopulationBasisNotBoolean() {
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.COHORT.name())
            .populationBasis("Encounter")
            .populations(List.of(population1, population2, population3, population4, population5))
            .build();
    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(testCaseGroupPopulations, List.of(group), testCase);
    assertTrue(result);
  }

  @Test
  public void testMatchCVCriteriaGroupsWithObservations() {
    List<TestCasePopulationValue> populationValues = getCVTestCasePopulationValues();

    TestCaseGroupPopulation groupPopulation = TestCaseGroupPopulation.builder().build();
    groupPopulation.setPopulationValues(populationValues);

    MeasureObservation measureObservation =
        MeasureObservation.builder()
            .definition(PopulationType.MEASURE_OBSERVATION.name())
            .criteriaReference("testCriteriaReference")
            .build();
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.name())
            .populationBasis("Encounter")
            .populations(List.of(population1, measurePopulation))
            .measureObservations(List.of(measureObservation))
            .build();

    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(List.of(groupPopulation), List.of(group), testCase);
    assertTrue(result);
  }

  @Test
  public void testMatchRatioCriteriaGroupsWithObservations() {
    List<TestCasePopulationValue> populationValues = new ArrayList<>();
    TestCasePopulationValue initialPopulationValue =
        TestCasePopulationValue.builder()
            .name(PopulationType.INITIAL_POPULATION)
            .expected(4)
            .build();
    populationValues.add(initialPopulationValue);
    TestCasePopulationValue denomPopulationValue =
        TestCasePopulationValue.builder().name(PopulationType.DENOMINATOR).expected(1).build();
    populationValues.add(denomPopulationValue);
    TestCasePopulationValue denomObservationPopulationValue =
        TestCasePopulationValue.builder()
            .name(PopulationType.DENOMINATOR_OBSERVATION)
            .expected(1)
            .build();
    populationValues.add(denomObservationPopulationValue);
    TestCasePopulationValue numeratorValue =
        TestCasePopulationValue.builder().name(PopulationType.NUMERATOR).expected(1).build();
    populationValues.add(numeratorValue);
    TestCasePopulationValue numerObservationValue =
        TestCasePopulationValue.builder()
            .name(PopulationType.NUMERATOR_OBSERVATION)
            .expected(1)
            .build();
    populationValues.add(numerObservationValue);

    TestCaseGroupPopulation groupPopulation = TestCaseGroupPopulation.builder().build();
    groupPopulation.setPopulationValues(populationValues);

    MeasureObservation denomObservation =
        MeasureObservation.builder()
            .definition(PopulationType.DENOMINATOR_OBSERVATION.name())
            .criteriaReference("Population2Id")
            .build();
    MeasureObservation numerObservation =
        MeasureObservation.builder()
            .definition(PopulationType.NUMERATOR_OBSERVATION.name())
            .criteriaReference("Population4Id")
            .build();
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.name())
            .populationBasis("Encounter")
            .populations(List.of(population1, population2, population4))
            .measureObservations(List.of(denomObservation, numerObservation))
            .build();

    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(List.of(groupPopulation), List.of(group), testCase);
    assertTrue(result);
  }

  @Test
  public void testMatchCVCriteriaGroupsWithObservationsAndStratifications() {
    List<TestCasePopulationValue> populationValues = getCVTestCasePopulationValues();

    TestCaseStratificationValue stratificationValue =
        TestCaseStratificationValue.builder().name("Strata-1").expected("1").build();
    stratificationValue.setPopulationValues(populationValues);

    TestCaseGroupPopulation groupPopulation = TestCaseGroupPopulation.builder().build();
    groupPopulation.setPopulationValues(populationValues);
    groupPopulation.setStratificationValues(List.of(stratificationValue));

    MeasureObservation measureObservation =
        MeasureObservation.builder()
            .definition(PopulationType.MEASURE_OBSERVATION.name())
            .criteriaReference("testCriteriaReference")
            .build();
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.name())
            .populationBasis("Encounter")
            .populations(List.of(population1, measurePopulation))
            .measureObservations(List.of(measureObservation))
            .build();

    boolean result =
        TestCaseServiceUtil.matchCriteriaGroups(List.of(groupPopulation), List.of(group), testCase);
    assertTrue(result);
  }

  private List<TestCasePopulationValue> getCVTestCasePopulationValues() {
    List<TestCasePopulationValue> populationValues = new ArrayList<>();
    TestCasePopulationValue initialPopulationValue =
        TestCasePopulationValue.builder()
            .name(PopulationType.INITIAL_POPULATION)
            .expected(4)
            .build();
    populationValues.add(initialPopulationValue);
    TestCasePopulationValue measurePopulationValue =
        TestCasePopulationValue.builder()
            .name(PopulationType.MEASURE_POPULATION)
            .expected(1)
            .build();
    populationValues.add(measurePopulationValue);
    TestCasePopulationValue measurePopulationObservationValue =
        TestCasePopulationValue.builder()
            .name(PopulationType.MEASURE_POPULATION_OBSERVATION)
            .expected(60)
            .build();
    populationValues.add(measurePopulationObservationValue);

    return populationValues;
  }

  @Test
  public void testAssignStratificationValuesQdmMismatch() {
    // Imported Populations
    List<TestCasePopulationValue> populationValues = getCVTestCasePopulationValues();

    // Imported Strat Population
    TestCaseStratificationValue stratValue =
        TestCaseStratificationValue.builder().name("Strata-1").expected(1).build();
    stratValue.setPopulationValues(populationValues);

    List<TestCaseGroupPopulation> importedGroups = new ArrayList<>();
    importedGroups.add(
        TestCaseGroupPopulation.builder()
            .populationValues(populationValues)
            .stratificationValues(List.of(stratValue))
            .build());

    MeasureObservation measureObservation =
        MeasureObservation.builder()
            .definition(PopulationType.MEASURE_OBSERVATION.name())
            .criteriaReference("testCriteriaReference")
            .build();
    group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.name())
            .populationBasis("Encounter")
            .populations(List.of(population1, measurePopulation))
            .measureObservations(List.of(measureObservation))
            .build();
    List<Group> measureGroups = new ArrayList<>();
    measureGroups.add(group);

    List<TestCaseGroupPopulation> testCaseGroups =
        TestCaseServiceUtil.assignStratificationValuesQdm(importedGroups, measureGroups);

    assertNull(testCaseGroups);
  }

  @Test
  public void testAssignStratificationValuesQdm() {
    // Measure Group
    Group group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population3, population4))
            .stratifications(List.of(Stratification.builder().cqlDefinition("def1").build()))
            .build();
    List<Group> measureGroups = new ArrayList<>();
    measureGroups.add(group);

    TestCasePopulationValue populationValue = TestCasePopulationValue.builder().expected(1).build();

    // Imported Strat Population
    TestCaseStratificationValue stratValue =
        TestCaseStratificationValue.builder().name("Strata-1").expected(1).build();
    stratValue.setPopulationValues(List.of(populationValue));

    TestCaseGroupPopulation stratPopultion =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue)).build();

    // Imported Populations
    List<TestCaseGroupPopulation> importedGroups = new ArrayList<>();
    importedGroups.add(
        TestCaseGroupPopulation.builder().populationValues(List.of(populationValue)).build());
    importedGroups.add(stratPopultion);

    List<TestCaseGroupPopulation> testCaseGroups =
        TestCaseServiceUtil.assignStratificationValuesQdm(importedGroups, measureGroups);

    assertEquals(1, testCaseGroups.size());
    assertThat(testCaseGroups.get(0).getStratificationValues(), not(empty()));

    List<TestCaseStratificationValue> finalStratValues =
        testCaseGroups.get(0).getStratificationValues();
    assertThat(finalStratValues.size(), is(1));
    assertThat(finalStratValues.get(0).getExpected(), is(1));
  }

  @Test
  public void testAssignStratificationValuesMultiPopulationCriteriaQdm() {
    // Measure Groups
    Group group1 =
        Group.builder()
            .id("testGroupId1")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population3, population4))
            .stratifications(
                List.of(
                    Stratification.builder().cqlDefinition("def1").build(),
                    Stratification.builder().cqlDefinition("def2").build()))
            .build();

    Group group2 =
        Group.builder()
            .id("testGroupId2")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population4))
            .stratifications(
                List.of(
                    Stratification.builder().cqlDefinition("def1").build(),
                    Stratification.builder().cqlDefinition("def2").build()))
            .build();
    List<Group> measureGroups = new ArrayList<>();
    measureGroups.add(group1);
    measureGroups.add(group2);

    // Imported Strat Populations
    TestCaseStratificationValue stratValue1 =
        TestCaseStratificationValue.builder().name("Strata-1").expected(1).build();
    stratValue1.setPopulationValues(
        List.of(
            TestCasePopulationValue.builder().expected(1).build(),
            TestCasePopulationValue.builder().expected(1).build(),
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(1).build()));

    TestCaseStratificationValue stratValue2 =
        TestCaseStratificationValue.builder().name("Strata-2").expected(0).build();
    stratValue2.setPopulationValues(
        List.of(
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(0).build()));

    TestCaseGroupPopulation stratPopCriteria1 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue1)).build();
    TestCaseGroupPopulation stratPopCriteria2 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue2)).build();
    TestCaseGroupPopulation stratPopCriteria3 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue1)).build();
    TestCaseGroupPopulation stratPopCriteria4 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue2)).build();

    // Imported Populations
    List<TestCaseGroupPopulation> importedGroups = new ArrayList<>();
    importedGroups.add(
        TestCaseGroupPopulation.builder()
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(0).build(),
                    TestCasePopulationValue.builder().expected(1).build()))
            .build());

    importedGroups.add(
        TestCaseGroupPopulation.builder()
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(0).build()))
            .build());

    importedGroups.add(stratPopCriteria1);
    importedGroups.add(stratPopCriteria2);
    importedGroups.add(stratPopCriteria3);
    importedGroups.add(stratPopCriteria4);

    List<TestCaseGroupPopulation> testCaseGroups =
        TestCaseServiceUtil.assignStratificationValuesQdm(importedGroups, measureGroups);

    assertEquals(2, testCaseGroups.size());
    assertThat(testCaseGroups.get(0).getStratificationValues(), not(empty()));
    assertThat(testCaseGroups.get(1).getStratificationValues(), not(empty()));

    List<TestCaseStratificationValue> finalStratValues1 =
        testCaseGroups.get(0).getStratificationValues();
    assertThat(finalStratValues1.size(), is(2));
    assertThat(finalStratValues1.get(0).getExpected(), is(1));
    assertThat(finalStratValues1.get(1).getExpected(), is(0));

    List<TestCaseStratificationValue> finalStratValues2 =
        testCaseGroups.get(1).getStratificationValues();
    assertThat(finalStratValues2.size(), is(2));
    assertThat(finalStratValues2.get(0).getExpected(), is(1));
    assertThat(finalStratValues2.get(1).getExpected(), is(0));
  }

  @Test
  public void testAssignStratificationValuesUnevenPopulationCriteriaQdm() {
    // Measure Groups
    Group group1 =
        Group.builder()
            .id("testGroupId1")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population3, population4))
            .stratifications(List.of(Stratification.builder().cqlDefinition("def1").build()))
            .build();

    Group group2 =
        Group.builder()
            .id("testGroupId2")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population4))
            .stratifications(
                List.of(
                    Stratification.builder().cqlDefinition("def1").build(),
                    Stratification.builder().cqlDefinition("def2").build()))
            .build();
    List<Group> measureGroups = new ArrayList<>();
    measureGroups.add(group1);
    measureGroups.add(group2);

    // Imported Strat Populations
    TestCaseStratificationValue stratValue1 =
        TestCaseStratificationValue.builder().name("Strata-1").expected(1).build();
    stratValue1.setPopulationValues(
        List.of(
            TestCasePopulationValue.builder().expected(1).build(),
            TestCasePopulationValue.builder().expected(1).build(),
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(1).build()));

    TestCaseStratificationValue stratValue2 =
        TestCaseStratificationValue.builder().name("Strata-2").expected(0).build();
    stratValue2.setPopulationValues(
        List.of(
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(0).build()));

    TestCaseGroupPopulation stratPopCriteria1 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue1)).build();
    TestCaseGroupPopulation stratPopCriteria3 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue1)).build();
    TestCaseGroupPopulation stratPopCriteria4 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue2)).build();

    // Imported Populations
    List<TestCaseGroupPopulation> importedGroups = new ArrayList<>();
    importedGroups.add(
        TestCaseGroupPopulation.builder()
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(0).build(),
                    TestCasePopulationValue.builder().expected(1).build()))
            .build());

    importedGroups.add(
        TestCaseGroupPopulation.builder()
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(0).build()))
            .build());

    importedGroups.add(stratPopCriteria1);
    importedGroups.add(stratPopCriteria3);
    importedGroups.add(stratPopCriteria4);

    List<TestCaseGroupPopulation> testCaseGroups =
        TestCaseServiceUtil.assignStratificationValuesQdm(importedGroups, measureGroups);

    assertEquals(2, testCaseGroups.size());
    assertThat(testCaseGroups.get(0).getStratificationValues(), not(empty()));
    assertThat(testCaseGroups.get(1).getStratificationValues(), not(empty()));

    List<TestCaseStratificationValue> finalStratValues1 =
        testCaseGroups.get(0).getStratificationValues();
    assertThat(finalStratValues1.size(), is(1));
    assertThat(finalStratValues1.get(0).getExpected(), is(1));

    List<TestCaseStratificationValue> finalStratValues2 =
        testCaseGroups.get(1).getStratificationValues();
    assertThat(finalStratValues2.size(), is(2));
    assertThat(finalStratValues2.get(0).getExpected(), is(1));
    assertThat(finalStratValues2.get(1).getExpected(), is(0));
  }

  @Test
  public void testAssignStratificationValuesQdmMoreImportStratsThanMeasureStrats() {
    // Measure Groups
    Group group1 =
        Group.builder()
            .id("testGroupId1")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population3, population4))
            .stratifications(List.of(Stratification.builder().cqlDefinition("def1").build()))
            .build();

    Group group2 =
        Group.builder()
            .id("testGroupId2")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population4))
            .stratifications(
                List.of(
                    Stratification.builder().cqlDefinition("def1").build(),
                    Stratification.builder().cqlDefinition("def2").build()))
            .build();
    List<Group> measureGroups = new ArrayList<>();
    measureGroups.add(group1);
    measureGroups.add(group2);

    // Imported Strat Populations
    TestCaseStratificationValue stratValue1 =
        TestCaseStratificationValue.builder().name("Strata-1").expected(1).build();
    stratValue1.setPopulationValues(
        List.of(
            TestCasePopulationValue.builder().expected(1).build(),
            TestCasePopulationValue.builder().expected(1).build(),
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(1).build()));

    TestCaseStratificationValue stratValue2 =
        TestCaseStratificationValue.builder().name("Strata-2").expected(0).build();
    stratValue2.setPopulationValues(
        List.of(
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(0).build()));

    TestCaseGroupPopulation stratPopCriteria1 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue1)).build();
    TestCaseGroupPopulation stratPopCriteria2 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue2)).build();
    TestCaseGroupPopulation stratPopCriteria3 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue1)).build();
    TestCaseGroupPopulation stratPopCriteria4 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue2)).build();

    // Imported Populations
    List<TestCaseGroupPopulation> importedGroups = new ArrayList<>();
    importedGroups.add(
        TestCaseGroupPopulation.builder()
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(0).build(),
                    TestCasePopulationValue.builder().expected(1).build()))
            .build());

    importedGroups.add(
        TestCaseGroupPopulation.builder()
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(0).build()))
            .build());

    importedGroups.add(stratPopCriteria1);
    importedGroups.add(stratPopCriteria2);
    importedGroups.add(stratPopCriteria3);
    importedGroups.add(stratPopCriteria4);

    List<TestCaseGroupPopulation> testCaseGroups =
        TestCaseServiceUtil.assignStratificationValuesQdm(importedGroups, measureGroups);

    assertNull(testCaseGroups);
  }

  @Test
  public void testAssignStratificationValuesQdmMoreMeasureStratsThanImportStrats() {
    // Measure Groups
    Group group1 =
        Group.builder()
            .id("testGroupId1")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population3, population4))
            .stratifications(
                List.of(
                    Stratification.builder().cqlDefinition("def1").build(),
                    Stratification.builder().cqlDefinition("def2").build()))
            .build();

    Group group2 =
        Group.builder()
            .id("testGroupId2")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population4))
            .stratifications(
                List.of(
                    Stratification.builder().cqlDefinition("def1").build(),
                    Stratification.builder().cqlDefinition("def2").build()))
            .build();
    List<Group> measureGroups = new ArrayList<>();
    measureGroups.add(group1);
    measureGroups.add(group2);

    // Imported Strat Populations
    TestCaseStratificationValue stratValue1 =
        TestCaseStratificationValue.builder().name("Strata-1").expected(1).build();
    stratValue1.setPopulationValues(
        List.of(
            TestCasePopulationValue.builder().expected(1).build(),
            TestCasePopulationValue.builder().expected(1).build(),
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(1).build()));

    TestCaseStratificationValue stratValue2 =
        TestCaseStratificationValue.builder().name("Strata-2").expected(0).build();
    stratValue2.setPopulationValues(
        List.of(
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(0).build(),
            TestCasePopulationValue.builder().expected(0).build()));

    TestCaseGroupPopulation stratPopCriteria1 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue1)).build();
    TestCaseGroupPopulation stratPopCriteria3 =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue1)).build();

    // Imported Populations
    List<TestCaseGroupPopulation> importedGroups = new ArrayList<>();
    importedGroups.add(
        TestCaseGroupPopulation.builder()
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(0).build(),
                    TestCasePopulationValue.builder().expected(1).build()))
            .build());

    importedGroups.add(
        TestCaseGroupPopulation.builder()
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(1).build(),
                    TestCasePopulationValue.builder().expected(0).build()))
            .build());

    importedGroups.add(stratPopCriteria1);
    importedGroups.add(stratPopCriteria3);

    List<TestCaseGroupPopulation> testCaseGroups =
        TestCaseServiceUtil.assignStratificationValuesQdm(importedGroups, measureGroups);

    assertNull(testCaseGroups);
  }

  @Test
  public void testAssignStratificationValuesQdmEmpty() {
    // Measure Group
    Group group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population3, population4))
            .stratifications(List.of(Stratification.builder().cqlDefinition("def1").build()))
            .build();
    List<Group> measureGroups = new ArrayList<>();
    measureGroups.add(group);

    // Imported Populations
    List<TestCaseGroupPopulation> importedGroups = new ArrayList<>();
    importedGroups.add(
        TestCaseGroupPopulation.builder()
            .populationValues(List.of(TestCasePopulationValue.builder().expected(1).build()))
            .build());

    List<TestCaseGroupPopulation> testCaseGroups =
        TestCaseServiceUtil.assignStratificationValuesQdm(importedGroups, measureGroups);
    assertNull(testCaseGroups);
  }

  @Test
  public void testAssignStratificationValuesQdmBoolean() {
    // Measure Group
    Group group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population3, population4))
            .stratifications(List.of(Stratification.builder().cqlDefinition("def1").build()))
            .build();
    List<Group> measureGroups = new ArrayList<>();
    measureGroups.add(group);

    // Imported Populations
    TestCaseStratificationValue stratValue =
        TestCaseStratificationValue.builder().name("Strata-1").expected(true).build();
    stratValue.setPopulationValues(List.of(TestCasePopulationValue.builder().expected(1).build()));

    TestCaseGroupPopulation importedPopulationGroup =
        TestCaseGroupPopulation.builder()
            .populationValues(List.of(TestCasePopulationValue.builder().expected(true).build()))
            .build();
    TestCaseGroupPopulation importedStratGroup =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue)).build();
    List<TestCaseGroupPopulation> importedGroups = new ArrayList<>();
    importedGroups.add(importedPopulationGroup);
    importedGroups.add(importedStratGroup);

    List<TestCaseGroupPopulation> testCaseGroups =
        TestCaseServiceUtil.assignStratificationValuesQdm(importedGroups, measureGroups);

    assertEquals(1, testCaseGroups.get(0).getStratificationValues().size());
    assertEquals(
        Boolean.TRUE, testCaseGroups.get(0).getStratificationValues().get(0).getExpected());
  }

  @Test
  public void testAssignStratificationValuesQdmBooleanFalse() {
    // Measure Group
    Group group =
        Group.builder()
            .id("testGroupId")
            .scoring(MeasureScoring.PROPORTION.name())
            .populationBasis("Boolean")
            .populations(List.of(population1, population2, population3, population4))
            .stratifications(List.of(Stratification.builder().cqlDefinition("def1").build()))
            .build();
    List<Group> measureGroups = new ArrayList<>();
    measureGroups.add(group);

    // Imported Populations
    TestCaseStratificationValue stratValue =
        TestCaseStratificationValue.builder().name("Strata-1").expected(false).build();
    stratValue.setPopulationValues(List.of(TestCasePopulationValue.builder().expected(1).build()));

    TestCaseGroupPopulation importedPopulationGroup =
        TestCaseGroupPopulation.builder()
            .populationValues(List.of(TestCasePopulationValue.builder().expected(true).build()))
            .build();
    TestCaseGroupPopulation importedStratGroup =
        TestCaseGroupPopulation.builder().stratificationValues(List.of(stratValue)).build();
    List<TestCaseGroupPopulation> importedGroups = new ArrayList<>();
    importedGroups.add(importedPopulationGroup);
    importedGroups.add(importedStratGroup);

    List<TestCaseGroupPopulation> testCaseGroups =
        TestCaseServiceUtil.assignStratificationValuesQdm(importedGroups, measureGroups);

    assertEquals(1, testCaseGroups.get(0).getStratificationValues().size());
    assertEquals(
        Boolean.FALSE, testCaseGroups.get(0).getStratificationValues().get(0).getExpected());
  }

  @Test
  public void testAssignObservationValuesBoolean() {
    TestCaseGroupPopulation groupPopulationFromTestCase =
        TestCaseGroupPopulation.builder().populationValues(populationValues).build();
    TestCase newTestCase =
        TestCase.builder().groupPopulations(List.of(groupPopulationFromTestCase)).build();

    TestCasePopulationValue populationValue1 =
        TestCasePopulationValue.builder()
            .name(PopulationType.DENOMINATOR_OBSERVATION)
            .expected(1)
            .build();
    TestCaseGroupPopulation groupPopulation =
        TestCaseGroupPopulation.builder().populationValues(List.of(populationValue1)).build();

    TestCaseServiceUtil.assignObservationValues(newTestCase, List.of(groupPopulation), "Boolean");

    assertEquals(newTestCase.getGroupPopulations().get(0).getPopulationValues().size(), 6);
    assertEquals(
        newTestCase.getGroupPopulations().get(0).getPopulationValues().get(5).getExpected(),
        Boolean.TRUE);
  }

  @Test
  public void testAssignObservationValuesBooleanFalse() {
    TestCaseGroupPopulation groupPopulationFromTestCase =
        TestCaseGroupPopulation.builder().populationValues(populationValues).build();
    TestCase newTestCase =
        TestCase.builder().groupPopulations(List.of(groupPopulationFromTestCase)).build();

    TestCasePopulationValue populationValue1 =
        TestCasePopulationValue.builder()
            .name(PopulationType.DENOMINATOR_OBSERVATION)
            .expected(0)
            .build();
    TestCaseGroupPopulation groupPopulation =
        TestCaseGroupPopulation.builder().populationValues(List.of(populationValue1)).build();

    TestCaseServiceUtil.assignObservationValues(newTestCase, List.of(groupPopulation), "Boolean");

    assertEquals(newTestCase.getGroupPopulations().get(0).getPopulationValues().size(), 6);
    assertEquals(
        newTestCase.getGroupPopulations().get(0).getPopulationValues().get(5).getExpected(),
        Boolean.FALSE);
  }

  @Test
  public void testAssignObservationValuesNonBoolean() {
    TestCaseGroupPopulation groupPopulationFromTestCase =
        TestCaseGroupPopulation.builder().populationValues(populationValues).build();
    TestCase newTestCase =
        TestCase.builder().groupPopulations(List.of(groupPopulationFromTestCase)).build();

    TestCasePopulationValue populationValue1 =
        TestCasePopulationValue.builder()
            .name(PopulationType.DENOMINATOR_OBSERVATION)
            .expected(1)
            .build();
    TestCaseGroupPopulation groupPopulation =
        TestCaseGroupPopulation.builder().populationValues(List.of(populationValue1)).build();

    TestCaseServiceUtil.assignObservationValues(newTestCase, List.of(groupPopulation), "Encounter");

    assertEquals(newTestCase.getGroupPopulations().get(0).getPopulationValues().size(), 6);
    assertEquals(
        newTestCase.getGroupPopulations().get(0).getPopulationValues().get(5).getExpected(), 1);
  }

  @Test
  public void testGetObservationPopulationsReturnsNull() {
    List<TestCasePopulationValue> populationValues =
        TestCaseServiceUtil.getObservationPopulations(null);
    assertNull(populationValues);
  }

  @Test
  public void testGetRevisedGroupPopulationReturnsEmptyListForNull() {
    List<TestCaseGroupPopulation> output =
        TestCaseServiceUtil.getNonObservationGroupPopulations(null);
    assertThat(output, is(notNullValue()));
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  public void testGetRevisedGroupPopulationReturnsEmptyListForEmptyInput() {
    final List<TestCaseGroupPopulation> originalTestCaseGroupPopulations = List.of();
    List<TestCaseGroupPopulation> output =
        TestCaseServiceUtil.getNonObservationGroupPopulations(originalTestCaseGroupPopulations);
    assertThat(output, is(notNullValue()));
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  public void testGetRevisedGroupPopulationReturnsListWithEmptyPopValuesLists() {
    final List<TestCaseGroupPopulation> originalTestCaseGroupPopulations =
        List.of(
            TestCaseGroupPopulation.builder()
                .groupId("Group1Id")
                .populationBasis("boolean")
                .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
                .build(),
            TestCaseGroupPopulation.builder()
                .groupId("Group2Id")
                .populationBasis("boolean")
                .scoring(MeasureScoring.PROPORTION.toString())
                .build());
    List<TestCaseGroupPopulation> output =
        TestCaseServiceUtil.getNonObservationGroupPopulations(originalTestCaseGroupPopulations);
    assertThat(output, is(notNullValue()));
    assertThat(output.size(), is(equalTo(2)));
    assertThat(output.get(0), is(notNullValue()));
    assertThat(output.get(0).getPopulationValues(), is(nullValue()));
  }

  @Test
  public void testGetRevisedGroupPopulationReturnsListWithoutObservations() {
    final List<TestCaseGroupPopulation> originalTestCaseGroupPopulations =
        List.of(
            TestCaseGroupPopulation.builder()
                .groupId("Group1Id")
                .populationBasis("boolean")
                .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
                .populationValues(
                    List.of(
                        TestCasePopulationValue.builder()
                            .name(PopulationType.INITIAL_POPULATION)
                            .id("G1P1")
                            .expected("true")
                            .build(),
                        TestCasePopulationValue.builder()
                            .name(PopulationType.MEASURE_POPULATION)
                            .id("G1P2")
                            .expected("true")
                            .build(),
                        TestCasePopulationValue.builder()
                            .name(PopulationType.MEASURE_POPULATION_OBSERVATION)
                            .id("G1P3")
                            .expected("2")
                            .build()))
                .build(),
            TestCaseGroupPopulation.builder()
                .groupId("Group2Id")
                .populationBasis("Encounter")
                .scoring(MeasureScoring.RATIO.toString())
                .populationValues(
                    List.of(
                        TestCasePopulationValue.builder()
                            .name(PopulationType.INITIAL_POPULATION)
                            .id("G2P1")
                            .expected("2")
                            .build(),
                        TestCasePopulationValue.builder()
                            .name(PopulationType.DENOMINATOR)
                            .id("G2P2")
                            .expected("2")
                            .build(),
                        TestCasePopulationValue.builder()
                            .name(PopulationType.DENOMINATOR_OBSERVATION)
                            .id("G2P3")
                            .expected("15")
                            .build(),
                        TestCasePopulationValue.builder()
                            .name(PopulationType.DENOMINATOR_OBSERVATION)
                            .id("G2P4")
                            .expected("120")
                            .build(),
                        TestCasePopulationValue.builder()
                            .name(PopulationType.NUMERATOR)
                            .id("G2P5")
                            .expected("1")
                            .build(),
                        TestCasePopulationValue.builder()
                            .name(PopulationType.NUMERATOR_OBSERVATION)
                            .id("G2P6")
                            .expected("2")
                            .build()))
                .build());
    List<TestCaseGroupPopulation> output =
        TestCaseServiceUtil.getNonObservationGroupPopulations(originalTestCaseGroupPopulations);
    assertThat(output, is(notNullValue()));
    assertThat(output.size(), is(equalTo(2)));
    assertThat(output.get(0), is(notNullValue()));
    assertThat(output.get(0).getPopulationValues(), is(notNullValue()));
    assertThat(output.get(0).getPopulationValues().size(), is(equalTo(2)));
    assertThat(output.get(1), is(notNullValue()));
    assertThat(output.get(1).getPopulationValues(), is(notNullValue()));
    assertThat(output.get(1).getPopulationValues().size(), is(equalTo(3)));
  }

  @Test
  void testAssignObservationIdAndCriteriaReferenceCV() {
    group =
        Group.builder()
            .id("group1")
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
            .measureObservations(List.of(measureObservation1))
            .build();

    TestCasePopulationValue measurePopulationValue =
        TestCasePopulationValue.builder()
            .name(PopulationType.MEASURE_POPULATION)
            .expected("2")
            .build();
    TestCasePopulationValue measureObservationValue =
        TestCasePopulationValue.builder()
            .name(PopulationType.MEASURE_POPULATION_OBSERVATION)
            .expected("3")
            .build();
    TestCaseGroupPopulation groupPopulation =
        TestCaseGroupPopulation.builder()
            .populationValues(
                List.of(testCasePopulationValue1, measurePopulationValue, measureObservationValue))
            .build();

    List<TestCaseGroupPopulation> results =
        TestCaseServiceUtil.assignObservationIdAndCriteriaReferenceCVAndRatio(
            List.of(groupPopulation), List.of(group));
    assertThat(results.size(), is(equalTo(1)));
    assertThat(results.get(0).getPopulationValues().size(), is(equalTo(3)));
    assertThat(
        results.get(0).getPopulationValues().get(2).getId(),
        is(equalTo("measurePopulationObservation0")));
    assertThat(
        results.get(0).getPopulationValues().get(2).getCriteriaReference(),
        is(equalTo("criteriaReference1")));
  }

  @Test
  void testAssignObservationIdAndCriteriaReferenceRatio() {
    group =
        Group.builder()
            .id("group1")
            .scoring(MeasureScoring.RATIO.toString())
            .populations(List.of(population1, population2, population3, population4, population5))
            .measureObservations(List.of(measureObservation1, measureObservation2))
            .build();

    TestCasePopulationValue valueDenomObserv =
        TestCasePopulationValue.builder()
            .name(PopulationType.DENOMINATOR_OBSERVATION)
            .expected("2")
            .build();
    TestCasePopulationValue valueNumerObserv =
        TestCasePopulationValue.builder()
            .name(PopulationType.NUMERATOR_OBSERVATION)
            .expected("3")
            .build();
    TestCaseGroupPopulation groupPopulation =
        TestCaseGroupPopulation.builder()
            .populationValues(
                List.of(
                    testCasePopulationValue1,
                    testCasePopulationValue2,
                    valueDenomObserv,
                    testCasePopulationValue4,
                    valueNumerObserv))
            .build();

    List<TestCaseGroupPopulation> results =
        TestCaseServiceUtil.assignObservationIdAndCriteriaReferenceCVAndRatio(
            List.of(groupPopulation), List.of(group));
    assertThat(results.size(), is(equalTo(1)));
    assertThat(results.get(0).getPopulationValues().size(), is(equalTo(5)));
    assertThat(
        results.get(0).getPopulationValues().get(2).getId(),
        is(equalTo("denominatorObservation0")));
    assertThat(
        results.get(0).getPopulationValues().get(2).getCriteriaReference(),
        is(equalTo("Population2Id")));
    assertThat(
        results.get(0).getPopulationValues().get(4).getId(), is(equalTo("numeratorObservation1")));
    assertThat(
        results.get(0).getPopulationValues().get(4).getCriteriaReference(),
        is(equalTo("Population4Id")));
  }

  @Test
  void testAssignObservationIdAndCriteriaReferenceCVAndRatioGroupsNull() {
    List<TestCaseGroupPopulation> results =
        TestCaseServiceUtil.assignObservationIdAndCriteriaReferenceCVAndRatio(
            testCaseGroupPopulations, Collections.emptyList());
    assertThat(results.size(), is(equalTo(1)));
    assertThat(results.get(0).getPopulationValues().size(), is(equalTo(5)));
  }

  @Test
  void testAssignObservationIdAndCriteriaReferenceCVAndRatioPopulationValuesNull() {
    List<TestCaseGroupPopulation> results =
        TestCaseServiceUtil.assignObservationIdAndCriteriaReferenceCVAndRatio(
            Collections.emptyList(), List.of(group));
    assertThat(results.size(), is(equalTo(0)));
  }

  @Test
  void testAssignObservationIdAndCriteriaReferenceCVAndRatioGroupAndPopulationDifferent() {
    List<TestCaseGroupPopulation> results =
        TestCaseServiceUtil.assignObservationIdAndCriteriaReferenceCVAndRatio(
            testCaseGroupPopulations, List.of(Group.builder().build(), Group.builder().build()));
    assertThat(results.size(), is(equalTo(1)));
    assertThat(results.get(0).getPopulationValues().size(), is(equalTo(5)));
    assertNull(results.get(0).getPopulationValues().get(0).getCriteriaReference());
    assertNull(results.get(0).getPopulationValues().get(1).getCriteriaReference());
    assertNull(results.get(0).getPopulationValues().get(2).getCriteriaReference());
    assertNull(results.get(0).getPopulationValues().get(3).getCriteriaReference());
    assertNull(results.get(0).getPopulationValues().get(4).getCriteriaReference());
  }

  @Test
  void testAssignObservationIdAndCriteriaReferenceCVAndRatioNoPopulationValues() {
    group =
        Group.builder()
            .id("group1")
            .scoring(MeasureScoring.RATIO.toString())
            .measureObservations(List.of(measureObservation1))
            .build();
    TestCaseGroupPopulation groupPopulation = TestCaseGroupPopulation.builder().build();
    List<TestCaseGroupPopulation> results =
        TestCaseServiceUtil.assignObservationIdAndCriteriaReferenceCVAndRatio(
            List.of(groupPopulation), List.of(group));
    assertThat(results.size(), is(equalTo(1)));
    assertNull(results.get(0).getPopulationValues());
  }

  @Test
  void testAssignObservationIdAndCriteriaReferenceCVMeasureObservationsNull() {
    group =
        Group.builder()
            .id("group1")
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
            .measureObservations(List.of(measureObservation1))
            .build();

    TestCasePopulationValue measurePopulationValue =
        TestCasePopulationValue.builder()
            .name(PopulationType.MEASURE_POPULATION)
            .expected("2")
            .build();
    TestCaseGroupPopulation groupPopulation =
        TestCaseGroupPopulation.builder()
            .populationValues(List.of(testCasePopulationValue1, measurePopulationValue))
            .build();

    List<TestCaseGroupPopulation> results =
        TestCaseServiceUtil.assignObservationIdAndCriteriaReferenceCVAndRatio(
            List.of(groupPopulation), List.of(group));
    assertThat(results.size(), is(equalTo(1)));
    assertThat(results.get(0).getPopulationValues().size(), is(equalTo(2)));
  }

  @Test
  void testAssignObservationIdAndCriteriaReferenceNonCVRatio() {
    List<TestCaseGroupPopulation> results =
        TestCaseServiceUtil.assignObservationIdAndCriteriaReferenceCVAndRatio(
            testCaseGroupPopulations, List.of(group));
    assertThat(results.size(), is(equalTo(1)));
    assertThat(results.get(0).getPopulationValues().size(), is(equalTo(5)));
    assertNull(results.get(0).getPopulationValues().get(0).getCriteriaReference());
    assertNull(results.get(0).getPopulationValues().get(1).getCriteriaReference());
    assertNull(results.get(0).getPopulationValues().get(2).getCriteriaReference());
    assertNull(results.get(0).getPopulationValues().get(3).getCriteriaReference());
    assertNull(results.get(0).getPopulationValues().get(4).getCriteriaReference());
  }

  @Test
  void testAssignObservationIdAndCriteriaReferenceRatioObservationsNull() {
    group =
        Group.builder()
            .id("group1")
            .scoring(MeasureScoring.RATIO.toString())
            .measureObservations(List.of(measureObservation1, measureObservation2))
            .build();
    TestCaseGroupPopulation groupPopulation =
        TestCaseGroupPopulation.builder()
            .populationValues(
                List.of(
                    testCasePopulationValue1, testCasePopulationValue2, testCasePopulationValue4))
            .build();

    List<TestCaseGroupPopulation> results =
        TestCaseServiceUtil.assignObservationIdAndCriteriaReferenceCVAndRatio(
            List.of(groupPopulation), List.of(group));
    assertThat(results.size(), is(equalTo(1)));
    assertThat(results.get(0).getPopulationValues().size(), is(equalTo(3)));
    assertNull(results.get(0).getPopulationValues().get(0).getCriteriaReference());
    assertNull(results.get(0).getPopulationValues().get(1).getCriteriaReference());
    assertNull(results.get(0).getPopulationValues().get(2).getCriteriaReference());
  }

  @Test
  void testIfDeletableIfMeasureIsDraft() {
    assertDoesNotThrow(() -> TestCaseServiceUtil.checkIfDeletable(List.of(), List.of(), true));
  }

  @Test
  void testIfDeletableIfMeasureIsNotDraftAndTestCaseIsCreatedBeforeVersioning() {
    assertThrows(
        InvalidIdException.class,
        () -> TestCaseServiceUtil.checkIfDeletable(List.of(testCase), List.of("TESTID"), false));
  }

  @Test
  void testIfDeletableIfMeasureIsNotDraftAndTestCaseIsCreatedAfterVersioning() {
    testCase.setCreatedBeforeVersioning(false);
    assertDoesNotThrow(
        () -> TestCaseServiceUtil.checkIfDeletable(List.of(testCase), List.of("TESTID"), false));
  }
}
