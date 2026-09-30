package cms.gov.madie.measure.services;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.UserMeasuresDTO;
import cms.gov.madie.measure.utils.UserDisplayNameUtils;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

/**
 * Assembles owned and shared measures for many users in a single pass, for the bulk Full User
 * Export.
 *
 * <p>Instead of the export making two search calls per user (OWNED + SHARED) - i.e. hundreds of
 * HTTP round-trips, each with its own aggregation and its own user-service lookup - this service
 * runs <b>one</b> aggregation over the measure collection to get the latest measure per family,
 * groups them into per-user owned/shared buckets in memory, and resolves every owner display name
 * with a <b>single</b> user-service call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMeasureExportService {

  private final MongoTemplate mongoTemplate;
  private final UserServiceClient userServiceClient;

  /**
   * Returns, for each requested HARP id, the latest measure per family the user owns or is shared
   * on.
   *
   * @param harpIds users to include; when null/empty, every user that owns/shares a measure is
   *     returned
   * @return map of lower-cased HARP id -&gt; owned/shared measure lists
   */
  public Map<String, UserMeasuresDTO> getMeasuresForUsers(List<String> harpIds) {
    Set<String> targets =
        CollectionUtils.isEmpty(harpIds)
            ? null
            : harpIds.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

    List<MeasureListDTO> latestPerFamily = findLatestMeasurePerFamily();

    // Resolve every owner's display name in one user-service call.
    List<String> ownerIds =
        latestPerFamily.stream()
            .map(
                measure ->
                    measure.getMeasureSet() == null ? null : measure.getMeasureSet().getOwner())
            .filter(StringUtils::isNotBlank)
            .map(String::toLowerCase)
            .distinct()
            .collect(Collectors.toList());
    Map<String, UserDetailsDto> ownerDetails =
        ownerIds.isEmpty() ? Map.of() : userServiceClient.getBulkUserDetails(ownerIds);

    Map<String, UserMeasuresDTO> byUser = new HashMap<>();
    for (MeasureListDTO measure : latestPerFamily) {
      MeasureSet measureSet = measure.getMeasureSet();
      if (measureSet == null) {
        continue;
      }
      // Owner/acls live on the measureSet (family level), so the latest measure carries them.
      String owner =
          StringUtils.isBlank(measureSet.getOwner()) ? null : measureSet.getOwner().toLowerCase();
      if (owner != null) {
        measure.setOwnerDisplayName(resolveDisplayName(ownerDetails, owner));
        if (targets == null || targets.contains(owner)) {
          bucketFor(byUser, owner).getOwnedMeasures().add(measure);
        }
      }
      if (CollectionUtils.isNotEmpty(measureSet.getAcls())) {
        for (AclSpecification acl : measureSet.getAcls()) {
          if (acl.getRoles() != null
              && acl.getRoles().contains(RoleEnum.SHARED_WITH)
              && StringUtils.isNotBlank(acl.getUserId())) {
            String sharedUser = acl.getUserId().toLowerCase();
            if (targets == null || targets.contains(sharedUser)) {
              bucketFor(byUser, sharedUser).getSharedMeasures().add(measure);
            }
          }
        }
      }
    }
    log.info(
        "Bulk export assembled measures for {} user(s) from {} measure families",
        byUser.size(),
        latestPerFamily.size());
    return byUser;
  }

  private UserMeasuresDTO bucketFor(Map<String, UserMeasuresDTO> byUser, String harpId) {
    return byUser.computeIfAbsent(harpId, key -> new UserMeasuresDTO());
  }

  private String resolveDisplayName(Map<String, UserDetailsDto> details, String harpId) {
    UserDetailsDto userDetails = details.get(harpId);
    if (userDetails != null) {
      String fullName = UserDisplayNameUtils.getFullName(userDetails);
      if (StringUtils.isNotBlank(fullName)) {
        return fullName;
      }
    }
    return StringUtils.isNotBlank(harpId) ? harpId : "-";
  }

  /**
   * One aggregation: keep active measures, drop the heavy fields before the join, look up the
   * measureSet, then pick the latest measure per family (active &gt; draft &gt; version, DESC) -
   * matching the selection the UI/search uses.
   */
  private List<MeasureListDTO> findLatestMeasurePerFamily() {
    LookupOperation lookup =
        LookupOperation.newLookup()
            .from("measureSet")
            .localField("measureSetId")
            .foreignField("measureSetId")
            .as("measureSet");
    Aggregation aggregation =
        newAggregation(
            match(Criteria.where("active").is(true)),
            project().andExclude("cql", "elmJson", "testCases"),
            lookup,
            unwind("measureSet"),
            sort(Sort.by(Sort.Direction.DESC, "active", "measureMetaData.draft", "version")),
            group("measureSetId").first("$$ROOT").as("selectedDoc"),
            replaceRoot("selectedDoc"));
    return mongoTemplate
        .aggregate(aggregation, Measure.class, MeasureListDTO.class)
        .getMappedResults();
  }
}
