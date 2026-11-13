package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureReviewRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.Comment;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureReview;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeasureReviewService {

  // prefix on the logged message so a comment event can be traced to its comment
  private static final String RELATED_COMMENT_MESSAGE_PREFIX = "relatedCommentId: ";
  // second entry on a reply's logged message, naming the comment it replies to
  private static final String PARENT_COMMENT_MESSAGE_PREFIX = "parentCommentId: ";

  private final MeasureReviewRepository measureReviewRepository;
  private final ActionLogService actionLogService;
  private final MeasureService measureService;
  private final UserNameService userNameService;

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
    review.setComments(assignIdsToNewComments(review.getComments()));
    boolean isReadyForReviewOnCreate = review.getStatus() == ReviewStatus.READY_FOR_REVIEW;
    review.setMarkedForReviewBy(isReadyForReviewOnCreate ? username : null);
    review.setMarkedForReviewAt(isReadyForReviewOnCreate ? Instant.now() : null);
    MeasureReview saved = measureReviewRepository.save(review);
    log.info("Created review [{}] for Measure [{}]", saved.getId(), measureId);
    logReviewAction(measureId, saved.getStatus(), username);
    List<CommentChange> addedCommentChanges =
        ListUtils.emptyIfNull(saved.getComments()).stream()
            .map(comment -> new CommentChange(ActionType.COMMENT_ADDED, comment, null))
            .toList();
    logCommentActions(measureId, addedCommentChanges, username);
    return withMarkedForReviewByDisplayName(saved);
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

    List<Comment> comments =
        review.getComments() != null
            ? mergeUserChangesIntoStoredComments(
                existing.getComments(), review.getComments(), username)
            : existing.getComments();
    List<CommentChange> commentChanges =
        findChangedCommentsAndReplies(existing.getComments(), comments);
    existing.setComments(assignIdsToNewComments(comments));
    existing.setReviewers(review.getReviewers());
    applyMarkedForReviewStamp(existing, previousStatus, newStatus, username);

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
    logCommentActions(measureId, commentChanges, username);
    return withMarkedForReviewByDisplayName(saved);
  }

  /**
   * Retrieves the single review associated with the given measureId.
   *
   * @param measureId the measure id
   * @return the review for the measure
   */
  public MeasureReview getReviewByMeasureId(String measureId) {
    MeasureReview review =
        measureReviewRepository
            .findByMeasureId(measureId)
            .orElseThrow(() -> new ResourceNotFoundException("Measure Review", measureId));
    return withMarkedForReviewByDisplayName(review);
  }

  private MeasureReview withMarkedForReviewByDisplayName(MeasureReview review) {
    review.setMarkedForReviewBy(userNameService.getUserDisplayName(review.getMarkedForReviewBy()));
    return review;
  }

  /**
   * Retrieves all reviews for measures belonging to the given measureSetId. measureSetIds are not
   * unique across measures, so this may return multiple reviews.
   *
   * @param measureSetId the measure set id
   * @return the list of reviews under the measure set
   */
  public List<MeasureReview> getReviewsByMeasureSetId(String measureSetId) {
    List<MeasureReview> reviews = measureReviewRepository.findAllByMeasureSetId(measureSetId);
    Map<String, String> displayNames =
        userNameService.getUsersDisplayNames(
            reviews.stream().map(MeasureReview::getMarkedForReviewBy).toList());
    reviews.stream()
        .filter(review -> StringUtils.isNotBlank(review.getMarkedForReviewBy()))
        .forEach(
            review ->
                review.setMarkedForReviewBy(
                    displayNames.getOrDefault(
                        review.getMarkedForReviewBy(), review.getMarkedForReviewBy())));
    return reviews;
  }

  /**
   * Retrieves the measures that are currently under review, one entry per measure. The ownership
   * types decide which reviews are listed: every review (the "All Reviews" tab), or only the
   * unfinished ones assigned to the requesting reviewer (the "My Reviews" tab).
   *
   * @param searchCriteria the search criteria, may be null
   * @param ownershipTypes OWNED for the reviews assigned to the reviewer, ALL for every review
   * @param pageReq pagination and sort parameters
   * @param username the HARP id of the requesting user
   * @return a page of measures under review
   */
  public Page<MeasureListDTO> getMeasuresInReview(
      MeasureSearchCriteria searchCriteria,
      List<OwnershipType> ownershipTypes,
      Pageable pageReq,
      String username) {
    return measureService.getMeasuresInReview(searchCriteria, ownershipTypes, pageReq, username);
  }

  private static List<Comment> assignIdsToNewComments(List<Comment> comments) {
    if (comments != null) {
      comments.stream()
          .filter(comment -> StringUtils.isBlank(comment.getId()))
          .forEach(comment -> comment.setId(ObjectId.get().toString()));
      comments.stream()
          .map(MeasureReviewService::getRepliesOrEmpty)
          .flatMap(List::stream)
          .filter(reply -> StringUtils.isBlank(reply.getId()))
          .forEach(reply -> reply.setId(ObjectId.get().toString()));
    }

    return comments;
  }

  /**
   * Builds the comment list to save from the stored comments and the user's update. The update can
   * come from a page that was loaded before other users made changes, so it is only trusted for the
   * comments and replies the user wrote; everyone else's stay exactly as stored.
   *
   * <ul>
   *   <li>A stored entry that is missing from the update is deleted only if the user wrote it.
   *   <li>A stored entry whose text changed in the update takes the new text only if the user wrote
   *       it.
   *   <li>An entry in the update whose id is no longer stored (deleted meanwhile) is dropped.
   *   <li>An entry in the update without an id is new and is stamped with the user's HARP id.
   * </ul>
   *
   * @param storedEntries the comments (or one comment's replies) currently saved
   * @param incomingEntries the comments (or replies) sent in the update
   * @param username the HARP id of the user saving the update
   * @return the entries to save
   */
  private static List<Comment> mergeUserChangesIntoStoredComments(
      List<Comment> storedEntries, List<Comment> incomingEntries, String username) {
    if (storedEntries == null && incomingEntries == null) {
      return null;
    }
    Map<String, Comment> incomingEntriesById =
        mapCommentsById(ListUtils.emptyIfNull(incomingEntries));
    List<Comment> mergedEntries = new ArrayList<>();

    for (Comment storedEntry : ListUtils.emptyIfNull(storedEntries)) {
      Comment incomingEntry = incomingEntriesById.get(storedEntry.getId());
      boolean writtenByUser = isWrittenByUser(storedEntry, username);
      if (incomingEntry == null) {
        if (!writtenByUser) {
          mergedEntries.add(storedEntry);
        }
        continue;
      }
      Comment versionToKeep = writtenByUser ? incomingEntry : storedEntry;
      mergedEntries.add(
          versionToKeep.toBuilder()
              .harpId(storedEntry.getHarpId())
              .replies(
                  mergeUserChangesIntoStoredComments(
                      storedEntry.getReplies(), incomingEntry.getReplies(), username))
              .build());
    }

    ListUtils.emptyIfNull(incomingEntries).stream()
        .filter(entry -> StringUtils.isBlank(entry.getId()))
        .map(newEntry -> newEntry.toBuilder().harpId(username).build())
        .forEach(mergedEntries::add);
    return mergedEntries;
  }

  private static boolean isWrittenByUser(Comment entry, String username) {
    String author =
        StringUtils.isNotBlank(entry.getHarpId()) ? entry.getHarpId() : entry.getAuthorName();
    return StringUtils.equalsIgnoreCase(author, username);
  }

  /**
   * Finds every comment and reply that was added, edited or deleted between the stored review and
   * the incoming update. Replies are only compared for comments that still exist; when a comment is
   * deleted its replies go with it and only the comment deletion is reported.
   */
  private static List<CommentChange> findChangedCommentsAndReplies(
      List<Comment> storedComments, List<Comment> incomingComments) {
    List<Comment> storedCommentsOrEmpty = ListUtils.emptyIfNull(storedComments);
    List<Comment> incomingCommentsOrEmpty = ListUtils.emptyIfNull(incomingComments);
    Map<String, Comment> storedCommentsById = mapCommentsById(storedCommentsOrEmpty);

    List<CommentChange> collectedChanges = new ArrayList<>();
    collectAddedEditedAndDeletedEntries(
        storedCommentsOrEmpty, incomingCommentsOrEmpty, null, collectedChanges);
    incomingCommentsOrEmpty.forEach(
        incomingComment -> {
          Comment storedVersionOfComment =
              StringUtils.isBlank(incomingComment.getId())
                  ? null
                  : storedCommentsById.get(incomingComment.getId());
          collectAddedEditedAndDeletedEntries(
              getRepliesOrEmpty(storedVersionOfComment),
              getRepliesOrEmpty(incomingComment),
              incomingComment,
              collectedChanges);
        });
    return collectedChanges;
  }

  private static List<Comment> getRepliesOrEmpty(Comment comment) {
    return comment == null ? List.of() : ListUtils.emptyIfNull(comment.getReplies());
  }

  private static Map<String, Comment> mapCommentsById(List<Comment> comments) {
    return comments.stream()
        .filter(comment -> comment.getId() != null)
        .collect(
            Collectors.toMap(Comment::getId, Function.identity(), (first, duplicate) -> first));
  }


  /**
   * Compares one level of entries, either the top-level comments or the replies of one comment, and
   * adds an added / edited / deleted change for each entry that differs.
   *
   * @param storedEntries the entries currently saved
   * @param incomingEntries the entries sent in the update
   * @param parentComment the comment the entries reply to, or null for top-level comments
   * @param collectedChanges the list the changes are added to
   */
  private static void collectAddedEditedAndDeletedEntries(
      List<Comment> storedEntries,
      List<Comment> incomingEntries,
      Comment parentComment,
      List<CommentChange> collectedChanges) {
    Map<String, Comment> storedEntriesById = mapCommentsById(storedEntries);
    Set<String> incomingEntryIds =
        incomingEntries.stream()
            .map(Comment::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    List<Comment> addedEntries =
        incomingEntries.stream().filter(entry -> StringUtils.isBlank(entry.getId())).toList();
    List<Comment> editedEntries =
        incomingEntries.stream()
            .filter(entry -> entry.getId() != null && storedEntriesById.containsKey(entry.getId()))
            .filter(
                entry ->
                    !Objects.equals(
                        entry.getContent(), storedEntriesById.get(entry.getId()).getContent()))
            .toList();
    List<Comment> deletedEntries =
        storedEntries.stream()
            .filter(entry -> entry.getId() != null && !incomingEntryIds.contains(entry.getId()))
            .toList();

    addedEntries.forEach(
        entry ->
            collectedChanges.add(
                new CommentChange(ActionType.COMMENT_ADDED, entry, parentComment)));
    editedEntries.forEach(
        entry ->
            collectedChanges.add(
                new CommentChange(ActionType.COMMENT_EDITED, entry, parentComment)));
    deletedEntries.forEach(
        entry ->
            collectedChanges.add(
                new CommentChange(ActionType.COMMENT_DELETED, entry, parentComment)));
  }

  private void logCommentActions(
      String measureId, List<CommentChange> commentChanges, String username) {
    commentChanges.forEach(
        commentChange ->
            actionLogService.logAction(
                measureId,
                MeasureReview.class,
                commentChange.actionType(),
                username,
                buildLogMessages(commentChange)));
  }

  private static String[] buildLogMessages(CommentChange commentChange) {
    String changedCommentMessage = relatedCommentLogMessage(commentChange.changedComment());
    return commentChange.parentComment() == null
        ? new String[] {changedCommentMessage}
        : new String[] {
          changedCommentMessage,
          PARENT_COMMENT_MESSAGE_PREFIX + commentChange.parentComment().getId()
        };
  }

  static String relatedCommentLogMessage(Comment comment) {
    return RELATED_COMMENT_MESSAGE_PREFIX + comment.getId();
  }

  private void applyMarkedForReviewStamp(
      MeasureReview review, ReviewStatus previousStatus, ReviewStatus newStatus, String username) {
    if (newStatus == null || newStatus == previousStatus) {
      return;
    }
    if (newStatus == ReviewStatus.NOT_READY_FOR_REVIEW) {
      review.setMarkedForReviewBy(null);
      review.setMarkedForReviewAt(null);
    } else if (newStatus == ReviewStatus.READY_FOR_REVIEW
        && review.getMarkedForReviewAt() == null) {
      review.setMarkedForReviewBy(username);
      review.setMarkedForReviewAt(Instant.now());
    }
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

  // one comment or reply to log; parentComment is set only when the changed entry is a reply
  private record CommentChange(
      ActionType actionType, Comment changedComment, Comment parentComment) {}
}
