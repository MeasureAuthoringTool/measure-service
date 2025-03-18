package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UpdateQICoreTestCaseStratificationsChangeUnitTest {

  @Mock private MeasureRepository measureRepository;

  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor;

  @InjectMocks
  UpdateQICoreTestCaseStratificationsChangeUnit updateQICoreTestCaseStratificationsChangeUnit;

  @Test
  void testStratsAreUpdatedInTestCasesAndRollbackHoldsMeasures() throws Exception {
    List<TestCasePopulationValue> testCaseStratificationPopulationValues =
        List.of(
            TestCasePopulationValue.builder()
                .name(PopulationType.INITIAL_POPULATION)
                .expected(true)
                .build(),
            TestCasePopulationValue.builder()
                .name(PopulationType.DENOMINATOR)
                .expected(true)
                .build(),
            TestCasePopulationValue.builder()
                .name(PopulationType.NUMERATOR)
                .expected(true)
                .build());

    final TestCaseGroupPopulation tcGroupPop =
        TestCaseGroupPopulation.builder()
            .groupId("group-1")
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
            .stratificationValues(
                List.of(
                    TestCaseStratificationValue.builder()
                        .id("strat-1")
                        .populationValues(testCaseStratificationPopulationValues)
                        .build()))
            .build();

    List<Measure> qiCoreMeasures =
        List.of(
            Measure.builder().measureName("M0_DEAD").active(false).build(),
            Measure.builder().measureName("M0_NOGROUPS").active(true).groups(List.of()).build(),
            Measure.builder()
                .measureName("M1")
                .active(true)
                .model(ModelType.QI_CORE.getValue())
                .groups(
                    List.of(
                        Group.builder()
                            .id("group-1")
                            .stratifications(
                                List.of(
                                    Stratification.builder()
                                        .id("strat-1")
                                        .associations(
                                            List.of(
                                                PopulationType.INITIAL_POPULATION,
                                                PopulationType.NUMERATOR))
                                        .build()))
                            .build()))
                .testCases(
                    List.of(TestCase.builder().groupPopulations(List.of(tcGroupPop)).build()))
                .build());

    when(measureRepository.findAllByModel(anyString())).thenReturn(qiCoreMeasures);
    updateQICoreTestCaseStratificationsChangeUnit.updateQICoreTestCaseStratifications(
        measureRepository);
    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    List<Measure> allUpdatedMeasures = measureArgumentCaptor.getAllValues();
    assertThat(allUpdatedMeasures, is(notNullValue()));
    assertThat(allUpdatedMeasures.size(), is(equalTo(1)));
  }

  @Test
  void testRollbackDoesNothingForNullTempMeasures() throws Exception {
    updateQICoreTestCaseStratificationsChangeUnit.setTempMeasures(null);
    updateQICoreTestCaseStratificationsChangeUnit.rollbackExecution(measureRepository);
    verifyNoInteractions(measureRepository);
  }

  @Test
  void testRollbackDoesNothingForEmptyTempMeasures() throws Exception {
    updateQICoreTestCaseStratificationsChangeUnit.setTempMeasures(List.of());
    updateQICoreTestCaseStratificationsChangeUnit.rollbackExecution(measureRepository);
    verifyNoInteractions(measureRepository);
  }

  @Test
  void testRollbackHandlesRollback() throws Exception {
    updateQICoreTestCaseStratificationsChangeUnit.setTempMeasures(
        List.of(Measure.builder().build(), Measure.builder().build()));
    updateQICoreTestCaseStratificationsChangeUnit.rollbackExecution(measureRepository);
    verify(measureRepository, times(2)).save(measureArgumentCaptor.capture());
  }
}
