package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureReviewRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureReview;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeasureReviewServiceTest {

  private static final String USERNAME = "test.user";

  @Mock private MeasureReviewRepository measureReviewRepository;

  @Mock private ActionLogService actionLogService;

  @InjectMocks private MeasureReviewService measureReviewService;

  @Captor private ArgumentCaptor<MeasureReview> reviewCaptor;

  private MeasureReview review;

  @BeforeEach
  void setUp() {
    review =
        MeasureReview.builder()
            .id("review-1")
            .measureId("m1")
            .measureSetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("Looks good")
            .build();
  }

  @Test
  void createReviewSucceedsWhenNoneExists() {
    MeasureReview persisted =
        MeasureReview.builder()
            .id("review-1")
            .measureId("m1")
            .measureSetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("Looks good")
            .build();
    when(measureReviewRepository.existsByMeasureId("m1")).thenReturn(false);
    when(measureReviewRepository.save(any(MeasureReview.class))).thenReturn(persisted);

    MeasureReview result = measureReviewService.createReview(review, USERNAME);

    assertEquals("review-1", result.getId());
    verify(measureReviewRepository).save(reviewCaptor.capture());
    assertNull(reviewCaptor.getValue().getId(), "id should be cleared before save");
    verify(actionLogService, times(1))
        .logAction("m1", Measure.class, ActionType.READY_FOR_REVIEW, USERNAME);
  }

  @Test
  void createReviewThrowsWhenReviewAlreadyExists() {
    when(measureReviewRepository.existsByMeasureId("m1")).thenReturn(true);

    assertThrows(
        InvalidResourceStateException.class,
        () -> measureReviewService.createReview(review, USERNAME));

    verify(measureReviewRepository, never()).save(any(MeasureReview.class));
    verify(actionLogService, never())
        .logAction(anyString(), any(), any(ActionType.class), anyString());
  }

  @Test
  void updateReviewLogsWhenStatusChanges() {
    MeasureReview existing =
        MeasureReview.builder()
            .id("review-1")
            .measureId("m1")
            .measureSetId("set-1")
            .status(ReviewStatus.NOT_READY_FOR_REVIEW)
            .comment("old")
            .build();

    MeasureReview update =
        MeasureReview.builder()
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("updated")
            .measureSetId("set-2")
            .build();

    when(measureReviewRepository.findByMeasureId("m1")).thenReturn(Optional.of(existing));
    when(measureReviewRepository.save(any(MeasureReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MeasureReview result = measureReviewService.updateReview("m1", update, USERNAME);

    assertEquals("review-1", result.getId());
    assertEquals(ReviewStatus.READY_FOR_REVIEW, result.getStatus());
    assertEquals("updated", result.getComment());
    assertEquals("set-2", result.getMeasureSetId());
    verify(actionLogService, times(1))
        .logAction("m1", Measure.class, ActionType.READY_FOR_REVIEW, USERNAME);
  }

  @Test
  void updateReviewLogsNotReadyForReviewWhenToggledOff() {
    MeasureReview existing =
        MeasureReview.builder()
            .id("review-1")
            .measureId("m1")
            .measureSetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("old")
            .build();

    MeasureReview update =
        MeasureReview.builder()
            .status(ReviewStatus.NOT_READY_FOR_REVIEW)
            .comment("needs work")
            .build();

    when(measureReviewRepository.findByMeasureId("m1")).thenReturn(Optional.of(existing));
    when(measureReviewRepository.save(any(MeasureReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    measureReviewService.updateReview("m1", update, USERNAME);

    verify(actionLogService, times(1))
        .logAction("m1", Measure.class, ActionType.NOT_READY_FOR_REVIEW, USERNAME);
  }

  @Test
  void updateReviewKeepsExistingMeasureSetIdWhenNotProvided() {
    MeasureReview existing =
        MeasureReview.builder()
            .id("review-1")
            .measureId("m1")
            .measureSetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("old")
            .build();

    MeasureReview update =
        MeasureReview.builder().status(ReviewStatus.READY_FOR_REVIEW).comment("updated").build();

    when(measureReviewRepository.findByMeasureId("m1")).thenReturn(Optional.of(existing));
    when(measureReviewRepository.save(any(MeasureReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MeasureReview result = measureReviewService.updateReview("m1", update, USERNAME);

    assertEquals("set-1", result.getMeasureSetId());
  }

  @Test
  void updateReviewDoesNotLogWhenStatusUnchanged() {
    MeasureReview existing =
        MeasureReview.builder()
            .id("review-1")
            .measureId("m1")
            .measureSetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("original comment")
            .build();

    // Same status, only the comment changes.
    MeasureReview update =
        MeasureReview.builder()
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("updated comment")
            .build();

    when(measureReviewRepository.findByMeasureId("m1")).thenReturn(Optional.of(existing));
    when(measureReviewRepository.save(any(MeasureReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MeasureReview result = measureReviewService.updateReview("m1", update, USERNAME);

    assertEquals("updated comment", result.getComment());
    verify(actionLogService, never())
        .logAction(anyString(), any(), any(ActionType.class), anyString());
  }

  @Test
  void updateReviewThrowsWhenReviewNotFound() {
    when(measureReviewRepository.findByMeasureId("m1")).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> measureReviewService.updateReview("m1", review, USERNAME));

    verify(measureReviewRepository, never()).save(any(MeasureReview.class));
    verify(actionLogService, never())
        .logAction(anyString(), any(), any(ActionType.class), anyString());
  }

  @Test
  void getReviewByMeasureIdSucceeds() {
    when(measureReviewRepository.findByMeasureId("m1")).thenReturn(Optional.of(review));

    MeasureReview result = measureReviewService.getReviewByMeasureId("m1");

    assertEquals("review-1", result.getId());
  }

  @Test
  void getReviewByMeasureIdThrowsWhenNotFound() {
    when(measureReviewRepository.findByMeasureId(anyString())).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class, () -> measureReviewService.getReviewByMeasureId("m1"));
  }

  @Test
  void getReviewsByMeasureSetIdReturnsAllReviews() {
    MeasureReview other =
        MeasureReview.builder().id("review-2").measureId("m2").measureSetId("set-1").build();
    when(measureReviewRepository.findAllByMeasureSetId("set-1")).thenReturn(List.of(review, other));

    List<MeasureReview> results = measureReviewService.getReviewsByMeasureSetId("set-1");

    assertEquals(2, results.size());
    verify(measureReviewRepository, times(1)).findAllByMeasureSetId("set-1");
    verify(actionLogService, never())
        .logAction(anyString(), any(), any(ActionType.class), eq(USERNAME));
  }
}
