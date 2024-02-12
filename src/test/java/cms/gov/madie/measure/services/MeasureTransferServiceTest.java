package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureGroupTypes;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.MeasureObservation;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.Stratification;
import gov.cms.madie.models.measure.TestCase;

@ExtendWith(MockitoExtension.class)
public class MeasureTransferServiceTest {
  @Mock private MeasureRepository measureRepository;

  @InjectMocks private MeasureTransferService measureTransferService;

  private Measure measure1;
  private Measure measure2;
  private Group group1;
  private Group group2;
  private Population population1;
  private Population population2;
  private Population population3;
  private Population population4;
  private Population population5;
  private Population population6;
  private MeasureObservation observation1;
  private MeasureObservation observation2;
  private Stratification stratification1;
  private Stratification stratification2;
  private TestCase testcase;

  @BeforeEach
  public void setUp() {
    measure1 =
        Measure.builder()
            .id("testMeasureId1")
            .measureSetId("testMeasureSetId1")
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .lastModifiedAt(Instant.now())
            .build();

    measure2 =
        Measure.builder()
            .id("testMeasureId2")
            .measureSetId("testMeasureSetId1")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .lastModifiedAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .build();

    population1 =
        Population.builder()
            .name(PopulationType.INITIAL_POPULATION)
            .definition("Initial Population")
            .build();
    population2 =
        Population.builder().name(PopulationType.DENOMINATOR).definition("Denominator").build();
    population3 =
        Population.builder()
            .name(PopulationType.DENOMINATOR_EXCLUSION)
            .definition("Denominator Exclusion")
            .build();
    population4 =
        Population.builder().name(PopulationType.NUMERATOR).definition("Numerator").build();
    population5 =
        Population.builder()
            .name(PopulationType.NUMERATOR_EXCLUSION)
            .definition("Numerator Exclusion")
            .build();
    population6 =
        Population.builder()
            .name(PopulationType.DENOMINATOR_EXCEPTION)
            .definition("Denominator Exception")
            .build();

    observation1 = MeasureObservation.builder().definition("Denominator Observation").build();
    observation2 = MeasureObservation.builder().definition("Numerator Observation").build();

    stratification1 = Stratification.builder().cqlDefinition("Initial Population").build();
    stratification2 = Stratification.builder().cqlDefinition("Denominator").build();

    group1 =
        Group.builder()
            .id("testGroupId1")
            .scoring("Proportion")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .populations(List.of(population1, population2, population3, population4))
            .measureObservations(List.of(observation1))
            .stratifications(List.of(stratification1))
            .build();
    group2 =
        Group.builder()
            .id("testGroupId1")
            .scoring("Proportion")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .populations(List.of(population1, population2, population5, population6))
            .measureObservations(List.of(observation2))
            .stratifications(List.of(stratification2))
            .build();

    testcase = TestCase.builder().id("testCaseId").build();
  }

  @Test
  public void testFindByMeasureSetId() {
    when(measureRepository.findAllByMeasureSetId(anyString()))
        .thenReturn(List.of(measure1, measure2));

    List<Measure> results = measureTransferService.findByMeasureSetId("testMeasureSetId1");

    assertTrue(results.size() == 2);
  }

  @Test
  public void testDeleteVersionedMeasuresDeleteVersionedMeasures() {
    ArgumentCaptor<List<Measure>> repositoryArgCaptor = ArgumentCaptor.forClass(List.class);
    measureTransferService.deleteVersionedMeasures(List.of(measure1, measure2));
    verify(measureRepository, times(1)).deleteAll(repositoryArgCaptor.capture());

    List<Measure> deletedMeasures = repositoryArgCaptor.getValue();
    assertTrue(deletedMeasures.size() == 1);
    assertEquals("testMeasureSetId1", deletedMeasures.get(0).getMeasureSetId());
  }

  @Test
  public void testDeleteVersionedMeasuresNotDeletedMetaDataNull() {
    measure1.setMeasureMetaData(null);
    measure2.setMeasureMetaData(null);
    ArgumentCaptor<List<Measure>> repositoryArgCaptor = ArgumentCaptor.forClass(List.class);
    measureTransferService.deleteVersionedMeasures(List.of(measure1, measure2));
    verify(measureRepository, times(0)).deleteAll(repositoryArgCaptor.capture());
  }

  @Test
  public void testOverwriteExistingMeasure() {
    Measure transferredMeasure = Measure.builder().build();
    measure1.setMeasureMetaData(MeasureMetaData.builder().draft(true).build());

    Measure overwrittenMeasure =
        measureTransferService.overwriteExistingMeasure(
            List.of(measure1, measure2), transferredMeasure);
    assertEquals("testMeasureId1", overwrittenMeasure.getId());
  }

  @Test
  public void testOverwriteExistingMeasureNotOverwrittenNoMetaData() {
    Measure transferredMeasure = Measure.builder().build();
    measure1.setMeasureMetaData(null);
    measure2.setMeasureMetaData(null);

    Measure overwrittenMeasure =
        measureTransferService.overwriteExistingMeasure(
            List.of(measure1, measure2), transferredMeasure);
    assertNull(overwrittenMeasure.getId());
  }

  @Test
  public void testOverwriteExistingMeasureNotOverwrittenNoDraft() {
    Measure transferredMeasure = Measure.builder().build();
    measure2.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());

    Measure overwrittenMeasure =
        measureTransferService.overwriteExistingMeasure(
            List.of(measure1, measure2), transferredMeasure);
    assertNull(overwrittenMeasure.getId());
  }

  @Test
  public void testOverwriteExistingMeasureWithTestCases() {
    Measure transferredMeasure = Measure.builder().model("QDM").groups(List.of(group1)).build();
    measure1.setMeasureMetaData(MeasureMetaData.builder().draft(true).build());
    measure1.setGroups(List.of(group1));
    measure1.setTestCases(List.of(testcase));

    Measure overwrittenMeasure =
        measureTransferService.overwriteExistingMeasure(List.of(measure1), transferredMeasure);

    assertNotNull(overwrittenMeasure.getId());
    assertEquals(1, overwrittenMeasure.getTestCases().size());
  }
}
