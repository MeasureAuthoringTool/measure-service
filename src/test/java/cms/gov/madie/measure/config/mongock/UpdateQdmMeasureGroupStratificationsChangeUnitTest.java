package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.Stratification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateQdmMeasureGroupStratificationsChangeUnitTest {

  @Mock private MeasureRepository measureRepository;

  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor;
  @InjectMocks UpdateQdmMeasureGroupStratificationsChangeUnit changeUnit;

  @Test
  void testStratsAreUpdatedAndRollbackHoldsMeasures() throws Exception {
    List<Measure> qdmMeasures =
        List.of(
            Measure.builder().measureName("M0_DEAD").active(false).build(),
            Measure.builder().measureName("M0_NOGROUPS").active(true).groups(List.of()).build(),
            Measure.builder()
                .measureName("M1_NeedsIds")
                .active(true)
                .model(ModelType.QDM_5_6.getValue())
                .groups(
                    List.of(
                        Group.builder()
                            .stratifications(
                                List.of(
                                    Stratification.builder().cqlDefinition("ipp").build(),
                                    Stratification.builder().cqlDefinition("denom").build()))
                            .build()))
                .build(),
            Measure.builder()
                .measureName("M2_Good")
                .active(true)
                .model(ModelType.QDM_5_6.getValue())
                .groups(
                    List.of(
                        Group.builder()
                            .stratifications(
                                List.of(
                                    Stratification.builder()
                                        .id("ExistingID1")
                                        .cqlDefinition("ipp2")
                                        .build(),
                                    Stratification.builder()
                                        .id("ExistingID2")
                                        .cqlDefinition("denom2")
                                        .build()))
                            .build()))
                .build(),
            Measure.builder()
                .measureName("M3_Needs1ID")
                .active(true)
                .model(ModelType.QDM_5_6.getValue())
                .groups(
                    List.of(
                        Group.builder()
                            .stratifications(
                                List.of(
                                    Stratification.builder()
                                        .id("ExistingID1")
                                        .cqlDefinition("ipp2")
                                        .build(),
                                    Stratification.builder().cqlDefinition("denom2").build()))
                            .build()))
                .build(),
            Measure.builder()
                .measureName("M4_NoStrats")
                .active(true)
                .model(ModelType.QDM_5_6.getValue())
                .groups(List.of(Group.builder().stratifications(List.of()).build()))
                .build());

    when(measureRepository.findAllByModel(anyString())).thenReturn(qdmMeasures);

    changeUnit.updateQdmMeasureGroupStratifications(measureRepository);
    List<Measure> preUpdateMeasures = changeUnit.getTempMeasures();
    assertThat(preUpdateMeasures, is(notNullValue()));
    assertThat(preUpdateMeasures.size(), is(equalTo(2)));
    verify(measureRepository, times(2)).save(measureArgumentCaptor.capture());
    List<Measure> allUpdatedMeasures = measureArgumentCaptor.getAllValues();
    assertThat(allUpdatedMeasures, is(notNullValue()));
    assertThat(allUpdatedMeasures.size(), is(equalTo(2)));
    assertThat(allUpdatedMeasures.get(0).getMeasureName(), is(equalTo("M1_NeedsIds")));
    assertThat(allUpdatedMeasures.get(1).getMeasureName(), is(equalTo("M3_Needs1ID")));
  }

  @Test
  void testRollbackDoesNothingForNullTempMeasures() throws Exception {
    changeUnit.setTempMeasures(null);
    changeUnit.rollbackExecution(measureRepository);
    verifyNoInteractions(measureRepository);
  }

  @Test
  void testRollbackDoesNothingForEmptyTempMeasures() throws Exception {
    changeUnit.setTempMeasures(List.of());
    changeUnit.rollbackExecution(measureRepository);
    verifyNoInteractions(measureRepository);
  }

  @Test
  void testRollbackHandlesRollback() throws Exception {
    changeUnit.setTempMeasures(List.of(Measure.builder().build(), Measure.builder().build()));
    changeUnit.rollbackExecution(measureRepository);
    verify(measureRepository, times(2)).save(measureArgumentCaptor.capture());
  }
}
