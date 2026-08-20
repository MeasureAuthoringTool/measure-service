package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureReviewRepository;
import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureReview;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeasureReviewService {

  private final MeasureReviewRepository measureReviewRepository;
  private final ActionLogService actionLogService;
  private final MeasureService measureService;

  /**
   * Creates a new review document for the given measure. Enforces the one-review-per-measure
   * invariant by rejecting the request if a review already exists for the measure. Logs a
   * READY_FOR_REVIEW / NOT_READY_FOR_REVIEW event against the measure instance history.
   *
   * @param review the review to create; measureId must be populated on the review
   * @param username the HARP id / name of the user performing the action
   * @return the persisted review
   */
  public MeasureReview createReview(MeasureReview review, String username) {
    final String measureId = review.getMeasureId();
    if (measureReviewRepository.existsByMeasureId(measureId)) {
      throw new InvalidResourceStateException(
          "A review already exists for Measure with id: " + measureId);
    }
    // Ensure a new document is created rather than overwriting an existing one.
    review.setId(null);
    MeasureReview saved = measureReviewRepository.save(review);
    log.info("Created review [{}] for Measure [{}]", saved.getId(), measureId);
    logReviewAction(measureId, saved.getStatus(), username);
    return saved;
  }

  /**
   * Updates the existing review for the given measure. The review is looked up by measureId, so the
   * caller does not need to know the review document id. A review status event is logged against
   * the measure instance history only when the status actually changes (e.g. a reviewer-only or
   * comment-only update is not logged).
   *
   * @param measureId the measure whose review should be updated
   * @param review the review payload containing the new status/reviewers/comment
   * @param username the HARP id / name of the user performing the action
   * @return the updated review
   */
  public MeasureReview updateReview(String measureId, MeasureReview review, String username) {
    MeasureReview existing =
        measureReviewRepository
            .findByMeasureId(measureId)
            .orElseThrow(() -> new ResourceNotFoundException("Measure Review", measureId));

    final ReviewStatus previousStatus = existing.getStatus();
    final ReviewStatus newStatus = review.getStatus();

    if (newStatus != null) {
      existing.setStatus(newStatus);
    }
    existing.setComment(review.getComment());
    existing.setReviewers(review.getReviewers());

    MeasureReview saved = measureReviewRepository.save(existing);
    log.info("Updated review [{}] for Measure [{}]", saved.getId(), measureId);

    // Only log a history event when the review status actually changed. A no-op status (e.g. a
    // comment-only update) should not add READY_FOR_REVIEW / NOT_READY_FOR_REVIEW history records.
    if (newStatus != null && newStatus != previousStatus) {
      logReviewAction(measureId, newStatus, username);
    } else {
      log.info(
          "Review status unchanged [{}] for Measure [{}]; skipping history log",
          newStatus,
          measureId);
    }
    return saved;
  }

  /**
   * Retrieves the single review associated with the given measureId.
   *
   * @param measureId the measure id
   * @return the review for the measure
   */
  public MeasureReview getReviewByMeasureId(String measureId) {
    return measureReviewRepository
        .findByMeasureId(measureId)
        .orElseThrow(() -> new ResourceNotFoundException("Measure Review", measureId));
  }

  /**
   * Retrieves all reviews for measures belonging to the given measureSetId. measureSetIds are not
   * unique across measures, so this may return multiple reviews.
   *
   * @param measureSetId the measure set id
   * @return the list of reviews under the measure set
   */
  public List<MeasureReview> getReviewsByMeasureSetId(String measureSetId) {
    return measureReviewRepository.findAllByMeasureSetId(measureSetId);
  }

  /**
   * Retrieves the measures that are currently under review (review status of Ready, In Progress or
   * Complete)
   *
   * @param searchCriteria the search criteria, may be null
   * @param pageReq pagination and sort parameters
   * @param username the HARP id of the requesting user
   * @return a page of measures under review
   */
  public Page<MeasureListDTO> getMeasuresInReview(
      MeasureSearchCriteria searchCriteria, Pageable pageReq, String username) {
    return measureService.getMeasuresInReview(searchCriteria, pageReq, username);
  }

  /**
   * Logs a review status change against the measure instance (not the measure set) history. Each
   * status logs its own event: READY_FOR_REVIEW, REVIEW_IN_PROGRESS, REVIEW_COMPLETE, or
   * NOT_READY_FOR_REVIEW. No additional info is recorded.
   *
   * @param measureId the measure instance id the event is logged against
   * @param status the review status driving the event type
   * @param username the HARP id / name of the user performing the action
   */
  private void logReviewAction(String measureId, ReviewStatus status, String username) {
    if (status == null) {
      return;
    }
    actionLogService.logAction(measureId, Measure.class, status.toActionType(), username);
  }
}
