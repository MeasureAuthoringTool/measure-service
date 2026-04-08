package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.Comment.CommentReply;
import cms.gov.madie.measure.dto.Comment.ReviewComment;
import cms.gov.madie.measure.repositories.ReviewCommentRepository;
import gov.cms.madie.models.measure.Measure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewCommentService {

  private final ReviewCommentRepository reviewCommentRepository;
  private final MeasureService measureService;
  private final NotificationService notificationService;

  public List<ReviewComment> getCommentsByMeasureId(String measureId) {
    log.info("Fetching comments for measureId: {}", measureId);
    return reviewCommentRepository.findAllByMeasureIdOrderByCreatedAtAsc(measureId);
  }

  public ReviewComment createComment(ReviewComment comment, String accessToken) {
    comment.setCreatedAt(Instant.now());
    comment.setResolved(false);
    if (comment.getReplies() == null) {
      comment.setReplies(new ArrayList<>());
    }
    log.info(
        "Creating comment for measureId: {} by author: {}",
        comment.getMeasureId(),
        comment.getAuthor());
    ReviewComment savedComment = reviewCommentRepository.save(comment);

    // Asynchronously notify measure users about the new comment
    try {
      Measure measure = measureService.findMeasureById(comment.getMeasureId());
      if (measure != null) {
        notificationService.sendCommentNotification(measure, comment.getAuthor(), accessToken);
      }
    } catch (Exception e) {
      log.warn(
          "Failed to trigger comment notification for measure [{}]: {}",
          comment.getMeasureId(),
          e.getMessage());
    }

    return savedComment;
  }

  public ReviewComment addReply(
      String id, CommentReply reply, String username, String accessToken) {
    ReviewComment comment =
        reviewCommentRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Comment not found with id: " + id));

    reply.setCreatedAt(Instant.now());
    reply.setAuthor(username);
    comment.getReplies().add(reply);

    log.info("Adding reply to comment [{}] by user: {}", id, username);
    ReviewComment savedComment = reviewCommentRepository.save(comment);

    // Asynchronously notify measure users + original comment author about the reply
    try {
      Measure measure = measureService.findMeasureById(comment.getMeasureId());
      if (measure != null) {
        notificationService.sendReplyNotification(
            measure, comment.getAuthor(), username, accessToken);
      }
    } catch (Exception e) {
      log.warn(
          "Failed to trigger reply notification for measure [{}]: {}",
          comment.getMeasureId(),
          e.getMessage());
    }

    return savedComment;
  }

  public void deleteComment(String id, String username) {
    ReviewComment comment =
        reviewCommentRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Comment not found with id: " + id));

    if (!comment.getAuthor().equalsIgnoreCase(username)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the author can delete this comment");
    }

    log.info("Deleting comment [{}] by author: {}", id, username);
    reviewCommentRepository.deleteById(id);
  }
}
