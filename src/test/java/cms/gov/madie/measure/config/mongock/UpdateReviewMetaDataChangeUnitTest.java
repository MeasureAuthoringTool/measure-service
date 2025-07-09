package cms.gov.madie.measure.config.mongock;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.ReviewMetaData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

@Slf4j
class UpdateReviewMetaDataChangeUnitTest {

  @Test
  void updatesReviewMetaDataWhenDatesAreInvalid() {
    MeasureRepository measureRepository = mock(MeasureRepository.class);
    Measure measure = new Measure();
    measure.setId("1");
    measure.setCreatedAt(Instant.parse("2023-01-01T00:00:00Z"));
    ReviewMetaData reviewMetaData = new ReviewMetaData();
    reviewMetaData.setApprovalDate(Instant.parse("2022-12-31T00:00:00Z"));
    reviewMetaData.setLastReviewDate(Instant.parse("2022-12-30T00:00:00Z"));
    measure.setReviewMetaData(reviewMetaData);

    when(measureRepository.findAll()).thenReturn(List.of(measure));

    new UpdateReviewMetaDataChangeUnit().updateReviewMetaData(measureRepository);

    ArgumentCaptor<Measure> captor = ArgumentCaptor.forClass(Measure.class);
    verify(measureRepository, times(1)).save(captor.capture());
    Measure updatedMeasure = captor.getValue();

    assertNull(updatedMeasure.getReviewMetaData().getApprovalDate());
    assertNull(updatedMeasure.getReviewMetaData().getLastReviewDate());
  }

  @Test
  void doesNotUpdateReviewMetaDataWhenDatesAreValid() {
    MeasureRepository measureRepository = mock(MeasureRepository.class);
    Measure measure = new Measure();
    measure.setId("2");
    measure.setCreatedAt(Instant.parse("2023-01-01T00:00:00Z"));
    ReviewMetaData reviewMetaData = new ReviewMetaData();
    reviewMetaData.setApprovalDate(Instant.parse("2023-01-02T00:00:00Z"));
    reviewMetaData.setLastReviewDate(Instant.parse("2023-01-03T00:00:00Z"));
    measure.setReviewMetaData(reviewMetaData);

    when(measureRepository.findAll()).thenReturn(List.of(measure));

    new UpdateReviewMetaDataChangeUnit().updateReviewMetaData(measureRepository);
    verify(measureRepository, never()).save(any());
  }

  @Test
  void skipsMeasuresWithoutReviewMetaData() {
    MeasureRepository measureRepository = mock(MeasureRepository.class);
    Measure measure = new Measure();
    measure.setId("3");
    measure.setCreatedAt(Instant.parse("2023-01-01T00:00:00Z"));
    measure.setReviewMetaData(new ReviewMetaData());

    when(measureRepository.findAll()).thenReturn(List.of(measure));

    new UpdateReviewMetaDataChangeUnit().updateReviewMetaData(measureRepository);
    verify(measureRepository, never()).save(any());
  }

  @Test
  void skipsMeasuresWithNullDatesInReviewMetaData() {
    MeasureRepository measureRepository = mock(MeasureRepository.class);
    Measure measure = new Measure();
    measure.setId("4");
    measure.setCreatedAt(Instant.parse("2023-01-01T00:00:00Z"));
    ReviewMetaData reviewMetaData = new ReviewMetaData();
    reviewMetaData.setApprovalDate(null);
    reviewMetaData.setLastReviewDate(null);
    measure.setReviewMetaData(reviewMetaData);

    when(measureRepository.findAll()).thenReturn(List.of(measure));

    new UpdateReviewMetaDataChangeUnit().updateReviewMetaData(measureRepository);
    verify(measureRepository, never()).save(any());
  }

  @Test
  void rollbackExecutionLogsDebugMessage() throws Exception {
    MeasureRepository measureRepository = mock(MeasureRepository.class);
    UpdateReviewMetaDataChangeUnit changeUnit = new UpdateReviewMetaDataChangeUnit();

    changeUnit.rollbackExecution();

    verifyNoInteractions(measureRepository);
  }
}
