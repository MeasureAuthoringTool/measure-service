package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.services.MeasureReviewService;
import gov.cms.madie.models.measure.MeasureReview;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MeasureReviewController {

  private final MeasureReviewService measureReviewService;

  @PostMapping("/measures/{measureId}/review")
  public ResponseEntity<MeasureReview> createReview(
      @PathVariable String measureId, @RequestBody MeasureReview review, Principal principal) {
    final String username = principal.getName().toLowerCase();
    log.info("User [{}] is creating a review for Measure [{}]", username, measureId);
    review.setMeasureId(measureId);
    MeasureReview created = measureReviewService.createReview(review, username);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/measures/{measureId}/review")
  public ResponseEntity<MeasureReview> updateReview(
      @PathVariable String measureId, @RequestBody MeasureReview review, Principal principal) {
    final String username = principal.getName().toLowerCase();
    log.info("User [{}] is updating the review for Measure [{}]", username, measureId);
    return ResponseEntity.ok(measureReviewService.updateReview(measureId, review, username));
  }

  @GetMapping("/measures/{measureId}/review")
  public ResponseEntity<MeasureReview> getReviewByMeasureId(@PathVariable String measureId) {
    return ResponseEntity.ok(measureReviewService.getReviewByMeasureId(measureId));
  }

  @GetMapping("/measures/measure-set/{measureSetId}/reviews")
  public ResponseEntity<List<MeasureReview>> getReviewsByMeasureSetId(
      @PathVariable String measureSetId) {
    return ResponseEntity.ok(measureReviewService.getReviewsByMeasureSetId(measureSetId));
  }
}
