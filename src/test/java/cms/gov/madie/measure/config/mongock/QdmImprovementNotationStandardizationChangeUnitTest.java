package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.QdmMeasure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
class QdmImprovementNotationStandardizationChangeUnitTest {

  @Mock private MeasureRepository measureRepository;

  @InjectMocks private QdmImprovementNotationStandardizationChangeUnit changeUnit;

  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor;

  private QdmMeasure qdmDraftMeasureWithNoImprovementNotation;
  private QdmMeasure qdmDraftMeasureWithBlankImprovementNotation;
  private QdmMeasure qdmDraftMeasureWithInvalidImprovementNotation;
  private QdmMeasure qdmDraftMeasureWithInvalidImprovementNotationAndDescription;
  private QdmMeasure qdmDraftMeasureWithOtherImprovementNotation;
  private QdmMeasure qdmDraftMeasureWithValidIncreaseImprovementNotation;
  private QdmMeasure qdmDraftMeasureWithValidDecreaseImprovementNotation;

  private QdmMeasure qdmVersionedMeasureWithInvalidImprovementNotation;

  private QdmMeasure qdmInactiveDraftMeasureWithInvalidImprovementNotation;

  @BeforeEach
  void setup() {
    qdmDraftMeasureWithNoImprovementNotation =
        QdmMeasure.builder()
            .model(ModelType.QDM_5_6.getValue())
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .improvementNotation(null)
            .build();
    qdmDraftMeasureWithBlankImprovementNotation =
        QdmMeasure.builder()
            .model(ModelType.QDM_5_6.getValue())
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .improvementNotation("")
            .build();
    qdmDraftMeasureWithInvalidImprovementNotation =
        QdmMeasure.builder()
            .model(ModelType.QDM_5_6.getValue())
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .improvementNotation("This is definitely an invalid improvement notation!")
            .build();
    qdmDraftMeasureWithInvalidImprovementNotationAndDescription =
        QdmMeasure.builder()
            .model(ModelType.QDM_5_6.getValue())
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .improvementNotation("Invalid ImprovementNotation")
            .improvementNotationDescription("With a description")
            .build();
    qdmDraftMeasureWithOtherImprovementNotation =
        QdmMeasure.builder()
            .model(ModelType.QDM_5_6.getValue())
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .improvementNotation("Other")
            .improvementNotationDescription("There's some other content here")
            .build();
    qdmDraftMeasureWithValidIncreaseImprovementNotation =
        QdmMeasure.builder()
            .model(ModelType.QDM_5_6.getValue())
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .improvementNotation("Increased score indicates improvement")
            .build();
    qdmDraftMeasureWithValidDecreaseImprovementNotation =
        QdmMeasure.builder()
            .model(ModelType.QDM_5_6.getValue())
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .improvementNotation("Decreased score indicates improvement")
            .build();
    qdmVersionedMeasureWithInvalidImprovementNotation =
        QdmMeasure.builder()
            .model(ModelType.QDM_5_6.getValue())
            .active(true)
            .version(Version.parse("1.0.000"))
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .improvementNotation("This is definitely an invalid improvement notation!")
            .build();
    qdmInactiveDraftMeasureWithInvalidImprovementNotation =
        QdmMeasure.builder()
            .model(ModelType.QDM_5_6.getValue())
            .active(false)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .improvementNotation("This is definitely an invalid improvement notation!")
            .build();
  }

  @Test
  void standardizeQdmImprovementNotationFieldExitsForNoMeasures() throws Exception {
    // given
    when(measureRepository.findAllByModel(anyString())).thenReturn(List.of());

    // when
    changeUnit.standardizeQdmImprovementNotationField(measureRepository);

    // then
    verify(measureRepository, never()).save(any());
  }

  @Test
  void standardizeQdmImprovementNotationFieldDoesNothingForNoMatchingMeasures() throws Exception {
    // given
    when(measureRepository.findAllByModel(anyString()))
        .thenReturn(
            List.of(
                qdmDraftMeasureWithNoImprovementNotation,
                qdmDraftMeasureWithBlankImprovementNotation,
                qdmDraftMeasureWithOtherImprovementNotation,
                qdmDraftMeasureWithValidIncreaseImprovementNotation,
                qdmDraftMeasureWithValidDecreaseImprovementNotation,
                qdmVersionedMeasureWithInvalidImprovementNotation,
                qdmInactiveDraftMeasureWithInvalidImprovementNotation));

    // when
    changeUnit.standardizeQdmImprovementNotationField(measureRepository);

    // then
    verify(measureRepository, never()).save(any());
  }

  @Test
  void standardizeQdmImprovementNotationField() {
    // given
    when(measureRepository.findAllByModel(anyString()))
        .thenReturn(
            List.of(
                qdmDraftMeasureWithNoImprovementNotation,
                qdmDraftMeasureWithBlankImprovementNotation,
                qdmDraftMeasureWithOtherImprovementNotation,
                qdmDraftMeasureWithInvalidImprovementNotation,
                qdmDraftMeasureWithInvalidImprovementNotationAndDescription,
                qdmDraftMeasureWithValidIncreaseImprovementNotation,
                qdmDraftMeasureWithValidDecreaseImprovementNotation,
                qdmVersionedMeasureWithInvalidImprovementNotation,
                qdmInactiveDraftMeasureWithInvalidImprovementNotation));

    // when
    changeUnit.standardizeQdmImprovementNotationField(measureRepository);

    // then
    verify(measureRepository, times(2)).save(measureArgumentCaptor.capture());
    List<Measure> allSavedMeasures = measureArgumentCaptor.getAllValues();

    Measure updatedMeasureNoDesc = allSavedMeasures.get(0);
    assertThat(updatedMeasureNoDesc, is(notNullValue()));
    assertThat(updatedMeasureNoDesc.getModel(), is(equalTo(ModelType.QDM_5_6.getValue())));
    QdmMeasure qdmMeasureNoDesc = (QdmMeasure) updatedMeasureNoDesc;
    assertThat(qdmMeasureNoDesc.getImprovementNotation(), is(equalTo("Other")));
    assertThat(
        qdmMeasureNoDesc.getImprovementNotationDescription(),
        is(equalTo("This is definitely an invalid improvement notation!")));

    Measure updatedMeasureWithDesc = allSavedMeasures.get(1);
    assertThat(updatedMeasureWithDesc, is(notNullValue()));
    assertThat(updatedMeasureWithDesc.getModel(), is(equalTo(ModelType.QDM_5_6.getValue())));
    QdmMeasure qdmMeasureWithDesc = (QdmMeasure) updatedMeasureWithDesc;
    assertThat(qdmMeasureWithDesc.getImprovementNotation(), is(equalTo("Other")));
    assertThat(
        qdmMeasureWithDesc.getImprovementNotationDescription(),
        is(equalTo("Invalid ImprovementNotation - With a description")));
  }
}
