package cms.gov.madie.measure.utils;

import cms.gov.madie.measure.dto.NotificationDTO;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.MeasureSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * Utility class for building notification objects when a measure is updated or when
 * comments/replies are added. Constructs human-readable notification messages and determines
 * recipient lists.
 */
@Slf4j
public final class MeasureChangeNotificationUtil {

  private MeasureChangeNotificationUtil() {
    // utility class – prevent instantiation
  }

  // ======================== Measure-change notifications ========================

  /**
   * Detects which supported field changed between {@code existingMeasure} and {@code
   * updatingMeasure}, builds a notification message, and returns a single {@link NotificationDTO}
   * with the list of users who should be notified (owner + shared users, excluding the actor).
   *
   * @param existingMeasure the measure as it exists in the database before the update
   * @param updatingMeasure the incoming measure with new values
   * @param username the HARP ID of the user who triggered the update
   * @return a NotificationDTO with all recipient userIds, or {@code null} if no supported field was
   *     changed or there are no recipients
   */
  public static NotificationDTO buildNotification(
      Measure existingMeasure, Measure updatingMeasure, String username) {

    if (existingMeasure == null || updatingMeasure == null || StringUtils.isBlank(username)) {
      return null;
    }

    // 1. Figure out what changed
    ChangedField changedField = detectChange(existingMeasure, updatingMeasure);
    if (changedField == null) {
      log.debug("No supported field change detected for measure [{}]", existingMeasure.getId());
      return null;
    }

    // 2. Build the message
    String measureIdentifier = buildMeasureIdentifier(existingMeasure);
    String message =
        String.format(
            "%s has updated the %s for measure %s.",
            username, changedField.displayName, measureIdentifier);

    // 3. Build the additionalLink
    String additionalLink =
        String.format("/measures/%s/edit/%s", existingMeasure.getId(), changedField.route);

    // 4. Determine recipients (owner + shared users, excluding the actor)
    Set<String> recipients = collectRecipients(existingMeasure, username);
    if (recipients.isEmpty()) {
      log.debug("No recipients to notify for measure [{}]", existingMeasure.getId());
      return null;
    }

    // 5. Create a single NotificationDTO with all recipient userIds
    return NotificationDTO.builder()
        .userIds(new ArrayList<>(recipients))
        .message(message)
        .additionalLink(additionalLink)
        .build();
  }

  // ======================== Comment notifications ========================

  /**
   * Builds a notification for a newly created comment on a measure. Recipients are all measure
   * users (owner + shared) excluding the comment author.
   *
   * <p>If the author is an external user (not the owner and not in the ACLs), all measure users are
   * notified. If the author is a measure user, all <i>other</i> measure users are notified.
   *
   * @param measure the measure on which the comment was added (must have measureSet populated)
   * @param commentAuthor the HARP ID of the user who created the comment
   * @return a NotificationDTO, or {@code null} if there are no recipients
   */
  public static NotificationDTO buildCommentNotification(Measure measure, String commentAuthor) {
    if (measure == null || StringUtils.isBlank(commentAuthor)) {
      return null;
    }

    Set<String> recipients = collectRecipients(measure, commentAuthor);
    if (recipients.isEmpty()) {
      log.debug("No recipients to notify for comment on measure [{}]", measure.getId());
      return null;
    }

    String measureIdentifier = buildMeasureIdentifier(measure);
    String message =
        String.format("%s added a comment on measure %s.", commentAuthor, measureIdentifier);

    String additionalLink = String.format("/measures/%s/edit/cql-editor", measure.getId());

    return NotificationDTO.builder()
        .userIds(new ArrayList<>(recipients))
        .message(message)
        .additionalLink(additionalLink)
        .build();
  }

  /**
   * Builds a notification for a reply added to a comment. Recipients are all measure users (owner +
   * shared) <b>plus</b> the original comment author (who may be external to the measure), excluding
   * the reply author.
   *
   * @param measure the measure on which the reply was added (must have measureSet populated)
   * @param commentAuthor the HARP ID of the original comment author
   * @param replyAuthor the HARP ID of the user who posted the reply
   * @return a NotificationDTO, or {@code null} if there are no recipients
   */
  public static NotificationDTO buildReplyNotification(
      Measure measure, String commentAuthor, String replyAuthor) {
    if (measure == null || StringUtils.isBlank(replyAuthor)) {
      return null;
    }

    // Start with all measure users, excluding the reply author
    Set<String> recipients = collectRecipients(measure, replyAuthor);

    // Also include the original comment author (might be external to the measure)
    if (StringUtils.isNotBlank(commentAuthor) && !commentAuthor.equalsIgnoreCase(replyAuthor)) {
      recipients.add(commentAuthor.toLowerCase());
    }

    if (recipients.isEmpty()) {
      log.debug("No recipients to notify for reply on measure [{}]", measure.getId());
      return null;
    }

    String measureIdentifier = buildMeasureIdentifier(measure);
    String message =
        String.format("%s replied to a comment on measure %s.", replyAuthor, measureIdentifier);

    String additionalLink = String.format("/measures/%s/edit/comments", measure.getId());

    return NotificationDTO.builder()
        .userIds(new ArrayList<>(recipients))
        .message(message)
        .additionalLink(additionalLink)
        .build();
  }

  // ---- internal helpers ----

  /**
   * Compares the existing and updating measures for the MVP-supported fields: CQL, description, and
   * references.
   *
   * @return a {@link ChangedField} if a change is detected, or {@code null} otherwise
   */
  static ChangedField detectChange(Measure existingMeasure, Measure updatingMeasure) {
    // 1. CQL
    if (!StringUtils.equals(existingMeasure.getCql(), updatingMeasure.getCql())) {
      return ChangedField.CQL;
    }

    MeasureMetaData existingMeta = existingMeasure.getMeasureMetaData();
    MeasureMetaData updatingMeta = updatingMeasure.getMeasureMetaData();

    if (existingMeta != null && updatingMeta != null) {
      // 2. Description
      if (!StringUtils.equals(existingMeta.getDescription(), updatingMeta.getDescription())) {
        return ChangedField.DESCRIPTION;
      }

      // 3. References (compare by size as a simple heuristic – a new reference added/removed)
      int existingRefSize =
          CollectionUtils.isEmpty(existingMeta.getReferences())
              ? 0
              : existingMeta.getReferences().size();
      int updatingRefSize =
          CollectionUtils.isEmpty(updatingMeta.getReferences())
              ? 0
              : updatingMeta.getReferences().size();
      if (existingRefSize != updatingRefSize) {
        return ChangedField.REFERENCES;
      }

      // Even if sizes match, content may have changed – do a deep equality check
      if (!Objects.equals(existingMeta.getReferences(), updatingMeta.getReferences())) {
        return ChangedField.REFERENCES;
      }
    } else if (existingMeta == null && updatingMeta != null) {
      if (StringUtils.isNotBlank(updatingMeta.getDescription())) {
        return ChangedField.DESCRIPTION;
      }
      if (CollectionUtils.isNotEmpty(updatingMeta.getReferences())) {
        return ChangedField.REFERENCES;
      }
    }

    return null; // no supported change detected
  }

  /**
   * Builds a human-readable measure identifier from the measure name and version. Example: "My
   * Measure v2.3.001"
   */
  static String buildMeasureIdentifier(Measure measure) {
    String name =
        StringUtils.isNotBlank(measure.getMeasureName())
            ? measure.getMeasureName()
            : "Unnamed Measure";
    Version version = measure.getVersion();
    if (version != null) {
      return String.format(
          "%s v%d.%d.%03d",
          name, version.getMajor(), version.getMinor(), version.getRevisionNumber());
    }
    return name;
  }

  /**
   * Collects all users who should receive a notification: the measure owner and all shared users,
   * minus the user who performed the update.
   */
  static Set<String> collectRecipients(Measure measure, String actorUsername) {
    Set<String> recipients = new LinkedHashSet<>();

    MeasureSet measureSet = measure.getMeasureSet();
    if (measureSet == null) {
      return recipients;
    }

    // Add the owner
    if (StringUtils.isNotBlank(measureSet.getOwner())) {
      recipients.add(measureSet.getOwner().toLowerCase());
    }

    // Add shared users
    if (CollectionUtils.isNotEmpty(measureSet.getAcls())) {
      for (AclSpecification acl : measureSet.getAcls()) {
        if (StringUtils.isNotBlank(acl.getUserId())) {
          recipients.add(acl.getUserId().toLowerCase());
        }
      }
    }

    // Remove the actor (the person who triggered the update shouldn't be notified)
    recipients.remove(actorUsername.toLowerCase());

    return recipients;
  }

  /**
   * Enum-like holder for each supported field change. Stores the human-readable display name and
   * the front-end route fragment.
   */
  enum ChangedField {
    CQL("CQL", "cql-editor"),
    DESCRIPTION("Measure Description", "details/measure-description"),
    REFERENCES("Measure References", "details/measure-references");

    final String displayName;
    final String route;

    ChangedField(String displayName, String route) {
      this.displayName = displayName;
      this.route = route;
    }
  }
}
