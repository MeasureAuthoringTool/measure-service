package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.dto.*;
import cms.gov.madie.measure.services.AppConfigService;
import cms.gov.madie.measure.utils.SearchAggregationUtils;
import cms.gov.madie.measure.utils.SearchUtils;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.measure.Measure;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cms.gov.madie.measure.utils.SearchAggregationUtils.createScoringTypeFilter;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
public class MeasureSearchServiceImpl implements MeasureSearchService {
  private final MongoTemplate mongoTemplate;
  private final AppConfigService appConfigService;
  private final UserServiceClient userServiceClient;

  public MeasureSearchServiceImpl(
      MongoTemplate mongoTemplate,
      AppConfigService appConfigService,
      UserServiceClient userServiceClient) {
    this.mongoTemplate = mongoTemplate;
    this.appConfigService = appConfigService;
    this.userServiceClient = userServiceClient;
  }

  private LookupOperation getLookupOperation() {
    return LookupOperation.newLookup()
        .from("measureSet")
        .localField("measureSetId")
        .foreignField("measureSetId")
        .as("measureSet");
  }

  /**
   * Generates aggregation stages to lookup measure and test case locks, filtering out locks held by
   * the specified user.
   *
   * @param userId ID of the user to exclude locks for.
   * @return List of aggregation operations.
   */
  private List<AggregationOperation> getLockStages(String userId) {
    if (StringUtils.isBlank(userId)) {
      return Collections.emptyList();
    }
    return Arrays.asList(
        // Stage 1: Add 'measureId' field as string version of measure._id because measureLock uses
        // measureId as String
        addFields()
            .addField("measureId")
            .withValue(ConvertOperators.ToString.toString("$_id"))
            .build(),

        // Stage 2: Lookup measureLock
        lookup("measureLock", "measureId", "measureId", "measureLock"),

        // Stage 3: Filter out measureLocks where lockedBy equals current userId
        addFields()
            .addField("measureLock")
            .withValue(
                ArrayOperators.Filter.filter("measureLock")
                    .as("lock")
                    .by(ComparisonOperators.Ne.valueOf("$$lock.lockedBy").notEqualToValue(userId)))
            .build(),

        // Stage 4: Set measureLock to first element of filtered array
        addFields()
            .addField("measureLock")
            .withValue(ArrayOperators.ArrayElemAt.arrayOf("measureLock").elementAt(0))
            .build(),

        // Stage 5: Lookup testCaseLock
        lookup("testCaseLock", "measureId", "measureId", "testCaseLock"),

        // Stage 6: Filter out testCaseLocks where lockedBy equals current userId
        addFields()
            .addField("testCaseLock")
            .withValue(
                ArrayOperators.Filter.filter("testCaseLock")
                    .as("lock")
                    .by(ComparisonOperators.Ne.valueOf("$$lock.lockedBy").notEqualToValue(userId)))
            .build(),

        // Stage 7: Set flag to indicate if any test case is locked by other users
        addFields()
            .addField("hasLockedTestCases")
            .withValue(
                ComparisonOperators.Gt.valueOf(ArrayOperators.Size.lengthOfArray("testCaseLock"))
                    .greaterThanValue(0))
            .build());
  }

  @Override
  public Page<MeasureListDTO> searchMeasuresByCriteria(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria measureSearchCriteria,
      List<OwnershipType> ownershipTypes) {

    AggregationOptions options = AggregationOptions.builder().allowDiskUse(true).build();

    List<AggregationOperation> aggregationOperations = new ArrayList<>();

    // join measure and measure_set to lookup owner and ACL info
    LookupOperation lookupOperation = getLookupOperation();
    UnwindOperation unwindOperation = unwind("measureSet");

    // Project only needed fields from Measure to improve performance
    ProjectionOperation initialProjection = project().andExclude("testCases", "elmJson");
    aggregationOperations.add(lookupOperation);
    aggregationOperations.add(unwindOperation);
    aggregationOperations.add(initialProjection);

    Criteria measureCriteria = Criteria.where("active").is(true);

    if (measureSearchCriteria != null) {
      // Search for searchField only in the provided filters
      if (StringUtils.isNotBlank(measureSearchCriteria.getSearchField())) {
        if (CollectionUtils.isEmpty(measureSearchCriteria.getOptionalSearchProperties())
            || measureSearchCriteria.getOptionalSearchProperties().contains("cmsId")) {
          aggregationOperations.add(SearchAggregationUtils.addCmsIdDisplayField());
        }
        SearchUtils.appendAdditionalSearchCriteria(measureCriteria, measureSearchCriteria);
      }

      // If model is provided, filter out those measures with that model
      if (StringUtils.isNotBlank(measureSearchCriteria.getModel())) {
        measureCriteria.and("model").is(measureSearchCriteria.getModel());
      }

      // If draft is provided, filter measures based on MeasureMetaData.draft
      if (measureSearchCriteria.getDraft() != null) {
        measureCriteria.and("measureMetaData.draft").is(measureSearchCriteria.getDraft());
      }

      // If excludeMeasures is not empty, exclude those measures by their IDs
      if (CollectionUtils.isNotEmpty(measureSearchCriteria.getExcludeByMeasureIds())) {
        measureCriteria.and("_id").nin(measureSearchCriteria.getExcludeByMeasureIds());
      }

      // filter measures that contains only the allowed scoring types in all their groups
      if (measureSearchCriteria.isFromCompositeMeasureComponent()
          && CollectionUtils.isNotEmpty(measureSearchCriteria.getAllowedScoringTypes())) {
        aggregationOperations.add(
            createScoringTypeFilter(measureSearchCriteria.getAllowedScoringTypes()));
      }
    }

    // prepare measure set search criteria(user is either owner or shared with)
    Criteria measureSetCriteria = buildMeasureSetCriteria(userId, ownershipTypes);

    MatchOperation matchOperation =
        (measureSetCriteria != null)
            ? match(new Criteria().andOperator(measureCriteria, measureSetCriteria))
            : match(measureCriteria);

    FacetOperation facets =
        facet(sortByCount("id"))
            .as("count")
            .and(
                sort(pageable.getSort()),
                skip(pageable.getOffset()),
                limit(pageable.getPageSize()),
                project(MeasureListDTO.class))
            .as("queryResults");

    aggregationOperations.add(matchOperation);

    aggregationOperations.add(
        group("measureSetId").count().as("matchCount").first("_id").as("matchedMeasureId"));
    // Find all the measures that matches the given Criteria and fetch unique measureSetIds
    List<MeasureSetMatchCountDTO> matchedMeasureSetCounts =
        mongoTemplate
            .aggregate(
                newAggregation(aggregationOperations), Measure.class, MeasureSetMatchCountDTO.class)
            .getMappedResults();

    Map<String, MeasureSetMatchCountDTO> matchInfoMap =
        matchedMeasureSetCounts.stream()
            .collect(
                Collectors.toMap(MeasureSetMatchCountDTO::getMeasureSetId, Function.identity()));

    List<String> matchedMeasureSetIds = new ArrayList<>(matchInfoMap.keySet());

    if (matchedMeasureSetIds.isEmpty()) {
      return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    List<AggregationOperation> postMatchPipeline = new ArrayList<>();
    postMatchPipeline.add(lookupOperation);
    postMatchPipeline.add(unwindOperation);
    postMatchPipeline.add(initialProjection);

    MatchOperation matchMeasureSetIds =
        match(Criteria.where("measureSetId").in(matchedMeasureSetIds));
    postMatchPipeline.add(matchMeasureSetIds);

    // lock stages
    postMatchPipeline.addAll(getLockStages(userId));

    // Sort those measures based on active status, version and draft status
    // Active measures should come first, then draft measures, then by version
    SortOperation sortByVersionAndDraft =
        measureSearchCriteria != null && measureSearchCriteria.isFromCompositeMeasureComponent()
            ? sort(Sort.by(Sort.Direction.DESC, "active", "version"))
            : sort(Sort.by(Sort.Direction.DESC, "active", "measureMetaData.draft", "version"));
    postMatchPipeline.add(sortByVersionAndDraft);

    // Group all measures that has same measureSetId and get the count and also first document
    // which will be the latest measure in the MeasureSet
    GroupOperation groupByMeasureSet = group("measureSetId").first("$$ROOT").as("selectedDoc");
    postMatchPipeline.add(groupByMeasureSet);

    ReplaceRootOperation replaceRoot = replaceRoot("selectedDoc");
    postMatchPipeline.add(replaceRoot);

    postMatchPipeline.add(facets);

    Aggregation pipeline = newAggregation(postMatchPipeline).withOptions(options);
    List<FacetDTO> results =
        mongoTemplate.aggregate(pipeline, Measure.class, FacetDTO.class).getMappedResults();
    for (MeasureListDTO dto : results.get(0).getQueryResults()) {
      MeasureSetMatchCountDTO matchInfo = matchInfoMap.get(dto.getMeasureSetId());

      if (matchInfo != null) {
        boolean hasAssociated;
        if (matchInfo.getMatchCount() > 1) {
          hasAssociated = true;
        } else {
          String selectedId = dto.getId();
          String matchedId = matchInfo.getMatchedMeasureId();
          hasAssociated = matchedId != null && !matchedId.equals(selectedId);
        }
        dto.setHasAssociatedMeasures(hasAssociated);
      } else {
        dto.setHasAssociatedMeasures(false);
      }
    }
    long totalSize = matchInfoMap.size();
    List<MeasureListDTO> queryResults = results.get(0).getQueryResults();
    populateOwnerDisplayNames(queryResults);
    return new PageImpl<>(queryResults, pageable, totalSize);
  }

  /**
   * Populates the ownerDisplayName field for each MeasureListDTO by fetching user details from
   * user-service.
   *
   * @param measureListDTOs List of MeasureListDTO objects to populate
   */
  private void populateOwnerDisplayNames(List<MeasureListDTO> measureListDTOs) {
    if (CollectionUtils.isEmpty(measureListDTOs)) {
      return;
    }

    // Collect unique owner harp IDs
    List<String> ownerHarpIds =
        measureListDTOs.stream()
            .map(dto -> dto.getMeasureSet() != null ? dto.getMeasureSet().getOwner() : null)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

    if (ownerHarpIds.isEmpty()) {
      return;
    }

    // Fetch user details from user-service
    Map<String, UserDetailsDto> userDetailsMap = userServiceClient.getBulkUserDetails(ownerHarpIds);

    // Populate ownerDisplayName for each DTO
    measureListDTOs.forEach(
        dto -> {
          if (dto.getMeasureSet() != null && dto.getMeasureSet().getOwner() != null) {
            String ownerHarpId = dto.getMeasureSet().getOwner();
            UserDetailsDto userDetails = userDetailsMap.get(ownerHarpId);

            if (userDetails != null) {
              String firstName = userDetails.getFirstName();
              String lastName = userDetails.getLastName();

              String displayName = "";
              if (StringUtils.isNotBlank(firstName) && StringUtils.isNotBlank(lastName)) {
                displayName = firstName + " " + lastName;
              } else if (StringUtils.isNotBlank(firstName)) {
                displayName = firstName;
              } else if (StringUtils.isNotBlank(lastName)) {
                displayName = lastName;
              }

              dto.setOwnerDisplayName(
                  StringUtils.isNotBlank(displayName)
                      ? displayName
                      : StringUtils.isNotBlank(ownerHarpId) ? ownerHarpId : "-");
            } else {
              dto.setOwnerDisplayName("-");
            }
          }
        });
  }

  private Criteria buildMeasureSetCriteria(String userId, List<OwnershipType> ownershipTypes) {
    // Can't filter without user ID
    if (StringUtils.isBlank(userId)) {
      return null;
    }

    // If null, empty, or ALL is included in the list, skip ownership filtering
    if (ownershipTypes == null
        || ownershipTypes.isEmpty()
        || ownershipTypes.contains(OwnershipType.ALL)) {
      return null;
    }

    List<Criteria> ownershipCriterias = new ArrayList<>();

    if (ownershipTypes.contains(OwnershipType.OWNED)) {
      ownershipCriterias.add(
          Criteria.where("measureSet.owner").regex("^\\Q" + userId + "\\E$", "i"));
    }

    if (ownershipTypes.contains(OwnershipType.SHARED)) {
      ownershipCriterias.add(
          Criteria.where("measureSet.acls.userId")
              .regex("^\\Q" + userId + "\\E$", "i")
              .and("measureSet.acls.roles")
              .in(RoleEnum.SHARED_WITH));
    }

    return ownershipCriterias.isEmpty()
        ? null
        : new Criteria().orOperator(ownershipCriterias.toArray(Criteria[]::new));
  }

  @Override
  public List<LibraryUsage> findLibraryUsageByLibraryName(String name) {
    LookupOperation lookupOperation = getLookupOperation();
    MatchOperation matchOperation =
        match(
            new Criteria()
                .andOperator(
                    Criteria.where("includedLibraries.name").is(name),
                    Criteria.where("active").is(true)));
    ProjectionOperation projectionOperation =
        project("version")
            .and("measureName")
            .as("name")
            .and("measureSet.owner")
            .as("owner")
            .andExclude("_id");
    UnwindOperation unwindOperation = unwind("owner");
    Aggregation aggregation =
        newAggregation(matchOperation, lookupOperation, projectionOperation, unwindOperation);
    return mongoTemplate
        .aggregate(aggregation, Measure.class, LibraryUsage.class)
        .getMappedResults();
  }

  @Override
  public int countMeasuresByOwnership(
      boolean isActive, String userId, List<OwnershipType> ownershipTypes) {
    LookupOperation lookupOperation = getLookupOperation();
    Criteria measureCriteria = Criteria.where("active").is(isActive);

    Criteria measureSetCriteria = buildMeasureSetCriteria(userId, ownershipTypes);

    MatchOperation matchOperation =
        (measureSetCriteria != null)
            ? match(new Criteria().andOperator(measureCriteria, measureSetCriteria))
            : match(measureCriteria);

    GroupOperation groupOperation = group("measureSetId");

    Aggregation aggregation =
        newAggregation(
            lookupOperation, matchOperation, groupOperation, group().count().as("count"));

    List<Map> results =
        mongoTemplate.aggregate(aggregation, Measure.class, Map.class).getMappedResults();

    return results.isEmpty() ? 0 : Integer.parseInt(results.get(0).get("count").toString());
  }
}
