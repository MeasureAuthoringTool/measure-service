package cms.gov.madie.measure.repositories;

import static cms.gov.madie.measure.utils.SearchAggregationUtils.createScoringTypeFilter;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.dto.*;
import cms.gov.madie.measure.utils.SearchAggregationUtils;
import cms.gov.madie.measure.utils.UserDisplayNameUtils;
import cms.gov.madie.measure.utils.SearchUtils;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.measure.Measure;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Repository
public class MeasureSearchServiceImpl implements MeasureSearchService {
  private final MongoTemplate mongoTemplate;
  private final UserServiceClient userServiceClient;

  public MeasureSearchServiceImpl(
      MongoTemplate mongoTemplate, UserServiceClient userServiceClient) {
    this.mongoTemplate = mongoTemplate;
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

    // Query 1: find all matching measureSetIds and their match counts
    Map<String, MeasureSetMatchCountDTO> matchInfoMap =
        findMatchedMeasureSets(userId, measureSearchCriteria, ownershipTypes);

    List<String> matchedMeasureSetIds = new ArrayList<>(matchInfoMap.keySet());
    if (matchedMeasureSetIds.isEmpty()) {
      return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    // Query 2: fetch paginated + sorted results for the matched measure sets
    List<FacetDTO> results =
        fetchFacetResults(userId, pageable, measureSearchCriteria, matchedMeasureSetIds);

    List<MeasureListDTO> queryResults = results.get(0).getQueryResults();

    // Annotate each result with whether associated measures exist
    for (MeasureListDTO dto : queryResults) {
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

    populateOwnerDisplayNames(queryResults);
    return new PageImpl<>(queryResults, pageable, matchInfoMap.size());
  }

  /**
   * Query 1: Aggregates all active measures matching the given criteria and ownership filters,
   * grouping by measureSetId to return a map of measureSetId → match-count info.
   *
   * @param userId ID of the requesting user.
   * @param measureSearchCriteria Search criteria (filters, model, draft, etc.).
   * @param ownershipTypes Ownership filter (OWNED, SHARED, ALL).
   * @return Map of measureSetId to {@link MeasureSetMatchCountDTO}.
   */
  private Map<String, MeasureSetMatchCountDTO> findMatchedMeasureSets(
      String userId,
      MeasureSearchCriteria measureSearchCriteria,
      List<OwnershipType> ownershipTypes) {
    List<AggregationOperation> aggregationOperations = new ArrayList<>();

    LookupOperation lookupOperation = getLookupOperation();
    UnwindOperation unwindOperation = unwind("measureSet");

    // testCases is only referenced after the $lookup by the composite-component testCaseSetId
    // filter (appendTestCaseSetIdCriteria). Every other search can drop it up front alongside
    // cql/elmJson so it is not dragged through the join.
    boolean needsTestCasesForFilter =
        measureSearchCriteria != null && measureSearchCriteria.isFromCompositeMeasureComponent();

    // Performance: filter on the measure-only predicates and drop the heaviest fields (cql,
    // elmJson,
    // and - unless the testCaseSetId filter still needs it - testCases) BEFORE the $lookup, so the
    // join against the measureSet collection processes far fewer, much lighter documents. The match
    // is a strict subset of the full $match applied after the lookup, so the result set is
    // identical
    // - only faster. Keeping it as the first stage also lets MongoDB use the {active, measureSetId}
    // index.
    aggregationOperations.add(match(buildPreLookupMeasureCriteria(measureSearchCriteria)));
    aggregationOperations.add(
        needsTestCasesForFilter
            ? project().andExclude("cql", "elmJson")
            : project().andExclude("cql", "elmJson", "testCases"));

    aggregationOperations.add(lookupOperation);
    aggregationOperations.add(unwindOperation);

    Criteria measureCriteria = Criteria.where("active").is(true);

    if (measureSearchCriteria != null) {
      if (StringUtils.isNotBlank(measureSearchCriteria.getSearchField())) {
        if (CollectionUtils.isEmpty(measureSearchCriteria.getOptionalSearchProperties())
            || measureSearchCriteria.getOptionalSearchProperties().contains("cmsId")) {
          aggregationOperations.add(SearchAggregationUtils.addCmsIdDisplayField());
        }
        SearchUtils.appendAdditionalSearchCriteria(measureCriteria, measureSearchCriteria);
      }

      if (SearchAggregationUtils.isReviewSearch(measureSearchCriteria)) {
        aggregationOperations.addAll(SearchAggregationUtils.getReviewStages());
      }

      // The measure-only predicates (model, measureMetaData.draft, excludeByMeasureIds,
      // excludeCompositeMeasures) are already enforced up front by buildPreLookupMeasureCriteria in
      // the first $match, before the $lookup. Re-applying them here would be redundant: none of the
      // intervening stages ($project, $lookup, $unwind, cmsIdDisplay/review add-fields) add
      // documents or mutate those fields, so the result set is identical. Only predicates that need
      // the joined measureSet / derived fields (search field, cmsIdDisplay) or the measure's
      // testCases array (testCaseSetId) are applied post-lookup below.

      if (measureSearchCriteria.isFromCompositeMeasureComponent()) {
        if (CollectionUtils.isNotEmpty(measureSearchCriteria.getAllowedScoringTypes())) {
          aggregationOperations.add(
              createScoringTypeFilter(measureSearchCriteria.getAllowedScoringTypes()));
        }
        // Allow measures with no test cases, or measures where all test cases have a non-blank
        // testCaseSetId.
        SearchUtils.appendTestCaseSetIdCriteria(measureCriteria);
      }
    }

    Criteria measureSetCriteria = buildMeasureSetCriteria(userId, ownershipTypes);
    MatchOperation matchOperation =
        (measureSetCriteria != null)
            ? match(new Criteria().andOperator(measureCriteria, measureSetCriteria))
            : match(measureCriteria);

    aggregationOperations.add(matchOperation);
    // When the composite-component testCaseSetId filter needed testCases, drop it now that the
    // filter has run. Otherwise it was already excluded before the $lookup (cql/elmJson always
    // are).
    if (needsTestCasesForFilter) {
      aggregationOperations.add(project().andExclude("testCases"));
    }
    aggregationOperations.add(
        group("measureSetId").count().as("matchCount").first("_id").as("matchedMeasureId"));

    List<MeasureSetMatchCountDTO> matchedMeasureSetCounts =
        mongoTemplate
            .aggregate(
                newAggregation(aggregationOperations), Measure.class, MeasureSetMatchCountDTO.class)
            .getMappedResults();

    return matchedMeasureSetCounts.stream()
        .collect(Collectors.toMap(MeasureSetMatchCountDTO::getMeasureSetId, Function.identity()));
  }

  /**
   * Query 2: Builds and executes the paginated facet aggregation for the given set of
   * measureSetIds, applying lock stages, grouping, sorting, and projection.
   *
   * @param userId ID of the requesting user (used for lock filtering).
   * @param pageable Pagination and sort parameters.
   * @param measureSearchCriteria Search criteria (used for composite/priority sort logic).
   * @param matchedMeasureSetIds The measureSetIds to include (output of Query 1).
   * @return Raw {@link FacetDTO} results from MongoDB.
   */
  private List<FacetDTO> fetchFacetResults(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria measureSearchCriteria,
      List<String> matchedMeasureSetIds) {
    LookupOperation lookupOperation = getLookupOperation();
    UnwindOperation unwindOperation = unwind("measureSet");
    ProjectionOperation initialProjection = project().andExclude("testCases", "elmJson");
    boolean isCompositeComponentSearch =
        measureSearchCriteria != null && measureSearchCriteria.isFromCompositeMeasureComponent();
    Sort effectiveSort = pageable.getSort();
    Sort.Order translatorSort = effectiveSort.getOrderFor("translatorVersion");

    List<AggregationOperation> postMatchPipeline = new ArrayList<>();

    // Honor measureMeataData.draft seachCriteria
    Criteria criteria;
    if (measureSearchCriteria != null && measureSearchCriteria.getDraft() != null) {
      criteria =
          new Criteria()
              .andOperator(
                  Criteria.where("measureSetId").in(matchedMeasureSetIds),
                  Criteria.where("measureMetaData.draft").is(measureSearchCriteria.getDraft()));
    } else {
      criteria = Criteria.where("measureSetId").in(matchedMeasureSetIds);
    }

    // Performance: restrict to the already-matched measure sets FIRST (index-eligible on
    // {measureSetId}) and drop the heavy cql field, so the measureSet $lookup only joins the
    // handful of relevant measures instead of the entire collection. elmJson is intentionally
    // retained here because the translator-version sort below still needs it.
    postMatchPipeline.add(match(criteria));
    postMatchPipeline.add(project().andExclude("cql"));
    postMatchPipeline.add(lookupOperation);
    postMatchPipeline.add(unwindOperation);
    if (isCompositeComponentSearch && translatorSort != null) {
      postMatchPipeline.add(SearchAggregationUtils.addTranslatorVersionSortField());
      effectiveSort = Sort.by(translatorSort.withProperty("translatorVersionSort"));
    }
    postMatchPipeline.add(initialProjection);

    postMatchPipeline.addAll(getLockStages(userId));
    postMatchPipeline.addAll(SearchAggregationUtils.getReviewStages());

    // Sort those measures based on active status, version and draft status
    // Active measures should come first, then draft measures, then by version
    SortOperation sortByVersionAndDraft =
        measureSearchCriteria != null && measureSearchCriteria.isFromCompositeMeasureComponent()
            ? sort(Sort.by(Sort.Direction.DESC, "active", "version"))
            : sort(Sort.by(Sort.Direction.DESC, "active", "measureMetaData.draft", "version"));
    postMatchPipeline.add(sortByVersionAndDraft);

    ReplaceRootOperation replaceRoot = replaceRoot("selectedDoc");

    FacetOperation facets;
    // priority-based sorting based on the presence of priorityMeasureSets for composite component
    // search
    boolean usePrioritySort =
        measureSearchCriteria != null
            && measureSearchCriteria.isFromCompositeMeasureComponent()
            && CollectionUtils.isNotEmpty(measureSearchCriteria.getPriorityMeasureSets());
    if (usePrioritySort) {
      // Group: preserve sortField so the subsequent priority sort can reference it
      Sort.Order sortOrder = effectiveSort.stream().iterator().next();
      String sortField = sortOrder.getProperty();
      String groupedSortField = "measureSet.cmsId".equals(sortField) ? "cmsIdSort" : sortField;
      GroupOperation groupByMeasureSet =
          group("measureSetId")
              .first("$$ROOT")
              .as("selectedDoc")
              .first(sortField)
              .as(groupedSortField)
              .max(
                  ConditionalOperators.Cond.when(
                          ArrayOperators.In.arrayOf(measureSearchCriteria.getPriorityMeasureSets())
                              .containsValue("$measureSetId"))
                      .then(1)
                      .otherwise(0))
              .as("isPrioritySet");
      postMatchPipeline.add(groupByMeasureSet);
      // Sort by priority first, then by the provided sort (which is preserved in the group stage)
      Sort groupedSort = Sort.by(sortOrder.withProperty(groupedSortField));
      postMatchPipeline.add(sort(Sort.by(Sort.Direction.DESC, "isPrioritySet").and(groupedSort)));
      postMatchPipeline.add(replaceRoot);
      postMatchPipeline.add(SearchAggregationUtils.addIsComponentField());
      // Facet: sort already applied above, so omit it here
      facets =
          facet(sortByCount("id"))
              .as("count")
              .and(
                  skip(pageable.getOffset()),
                  limit(pageable.getPageSize()),
                  project(MeasureListDTO.class))
              .as("queryResults");
    } else {
      postMatchPipeline.add(group("measureSetId").first("$$ROOT").as("selectedDoc"));
      postMatchPipeline.add(replaceRoot);
      postMatchPipeline.add(SearchAggregationUtils.addIsComponentField());
      // When sorting by measureMetaData.draft, substitute with the 5-tier draftSortOrder field
      // so that the ordering follows:
      //   DESC: 5→1 (composite draft first → non-composite versioned last)
      //   ASC:  1→5 (non-composite versioned first → composite draft last)
      // The original sort direction is preserved as-is.
      boolean hasDraftSort =
          effectiveSort.stream()
              .anyMatch(order -> "measureMetaData.draft".equals(order.getProperty()));
      if (hasDraftSort) {
        postMatchPipeline.add(SearchAggregationUtils.addDraftSortOrderField());
        effectiveSort =
            Sort.by(
                effectiveSort.stream()
                    .map(
                        order ->
                            "measureMetaData.draft".equals(order.getProperty())
                                ? new Sort.Order(order.getDirection(), "draftSortOrder")
                                : order)
                    .collect(Collectors.toList()));
      }
      facets =
          facet(sortByCount("id"))
              .as("count")
              .and(
                  sort(effectiveSort),
                  skip(pageable.getOffset()),
                  limit(pageable.getPageSize()),
                  project(MeasureListDTO.class))
              .as("queryResults");
    }

    postMatchPipeline.add(facets);
    return mongoTemplate
        .aggregate(newAggregation(postMatchPipeline), Measure.class, FacetDTO.class)
        .getMappedResults();
  }

  @Override
  public Page<MeasureListDTO> searchMeasuresInReview(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria measureSearchCriteria,
      List<OwnershipType> ownershipTypes) {
    boolean assignedOnly = isAssignedToUser(ownershipTypes);
    return searchReviews(
        userId,
        pageable,
        measureSearchCriteria,
        assignedOnly
            ? SearchAggregationUtils.OPEN_REVIEW_STATUSES
            : SearchAggregationUtils.IN_REVIEW_STATUSES,
        assignedOnly ? userId : null);
  }

  private boolean isAssignedToUser(List<OwnershipType> ownershipTypes) {
    return ownershipTypes != null && ownershipTypes.contains(OwnershipType.OWNED);
  }

  /**
   * @param userId ID of the requesting user (used for lock filtering).
   * @param pageable Pagination and sort parameters.
   * @param measureSearchCriteria Search criteria, may be null.
   * @param reviewStatuses The review statuses to include.
   * @param assignedTo When set, only the measures this reviewer is assigned to are returned.
   */
  private Page<MeasureListDTO> searchReviews(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria measureSearchCriteria,
      List<String> reviewStatuses,
      String assignedTo) {
    List<AggregationOperation> pipeline = new ArrayList<>();
    pipeline.add(getLookupOperation());
    pipeline.add(unwind("measureSet"));
    pipeline.add(project().andExclude("testCases", "elmJson"));

    pipeline.addAll(SearchAggregationUtils.getReviewStages());
    pipeline.add(SearchAggregationUtils.matchReviewStatusIn(reviewStatuses));
    if (StringUtils.isNotBlank(assignedTo)) {
      pipeline.add(SearchAggregationUtils.matchAssignedReviewer(assignedTo));
    }

    Criteria measureCriteria = Criteria.where("active").is(true);
    if (measureSearchCriteria != null
        && StringUtils.isNotBlank(measureSearchCriteria.getSearchField())) {
      if (CollectionUtils.isEmpty(measureSearchCriteria.getOptionalSearchProperties())
          || measureSearchCriteria.getOptionalSearchProperties().contains("cmsId")) {
        pipeline.add(SearchAggregationUtils.addCmsIdDisplayField());
      }
      SearchUtils.appendAdditionalSearchCriteria(measureCriteria, measureSearchCriteria);
    }
    pipeline.add(match(measureCriteria));

    pipeline.addAll(getLockStages(userId));
    pipeline.add(SearchAggregationUtils.addIsComponentField());

    Sort effectiveSort = pageable.getSort();
    if (effectiveSort.stream()
        .anyMatch(order -> "measureMetaData.draft".equals(order.getProperty()))) {
      pipeline.add(SearchAggregationUtils.addDraftSortOrderField());
      effectiveSort =
          Sort.by(
              effectiveSort.stream()
                  .map(
                      order ->
                          "measureMetaData.draft".equals(order.getProperty())
                              ? new Sort.Order(order.getDirection(), "draftSortOrder")
                              : order)
                  .collect(Collectors.toList()));
    }

    pipeline.add(
        facet(sortByCount("id"))
            .as("count")
            .and(
                sort(effectiveSort),
                skip(pageable.getOffset()),
                limit(pageable.getPageSize()),
                project(MeasureListDTO.class))
            .as("queryResults"));

    List<FacetDTO> results =
        mongoTemplate
            .aggregate(newAggregation(pipeline), Measure.class, FacetDTO.class)
            .getMappedResults();
    if (CollectionUtils.isEmpty(results)) {
      return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    FacetDTO facetResults = results.get(0);
    List<MeasureListDTO> measuresInReview = facetResults.getQueryResults();
    populateOwnerDisplayNames(measuresInReview);
    return new PageImpl<>(
        measuresInReview,
        pageable,
        facetResults.getCount() == null ? 0 : facetResults.getCount().size());
  }

  /**
   * Populates the ownerDisplayName and reviewers fields for each MeasureListDTO by fetching user
   * details from user-service.
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

    List<String> reviewerHarpIds =
        measureListDTOs.stream()
            .map(MeasureListDTO::getReviewers)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .collect(Collectors.toList());

    List<String> harpIds =
        Stream.concat(ownerHarpIds.stream(), reviewerHarpIds.stream())
            .distinct()
            .collect(Collectors.toList());

    if (harpIds.isEmpty()) {
      return;
    }

    // Fetch user details from user-service
    Map<String, UserDetailsDto> userDetailsMap = userServiceClient.getBulkUserDetails(harpIds);

    // Populate ownerDisplayName for each DTO
    measureListDTOs.forEach(
        dto -> {
          if (dto.getMeasureSet() != null && dto.getMeasureSet().getOwner() != null) {
            String ownerHarpId = dto.getMeasureSet().getOwner();
            UserDetailsDto userDetails = userDetailsMap.get(ownerHarpId);

            if (userDetails != null) {
              String displayName = UserDisplayNameUtils.getFullName(userDetails);

              dto.setOwnerDisplayName(
                  StringUtils.isNotBlank(displayName)
                      ? displayName
                      : StringUtils.isNotBlank(ownerHarpId) ? ownerHarpId : "-");
            } else {
              dto.setOwnerDisplayName("-");
            }
          }
          dto.setReviewers(
              UserDisplayNameUtils.toReviewerDisplayNames(dto.getReviewers(), userDetailsMap));
        });
  }

  /**
   * Builds the measure-only predicates that can be evaluated before the measureSet {@code $lookup}.
   *
   * <p>These are exactly the fields that live on the measure document itself (never on the joined
   * measureSet, the cmsIdDisplay derived field, or the review lookup), so applying them up front as
   * the first pipeline stage is index-eligible and strictly reduces the number of documents that
   * flow into the expensive join. Every predicate here is also re-applied in the post-lookup {@code
   * $match}, so this stage is a pure performance optimization and never changes the result set.
   *
   * @param measureSearchCriteria the caller's search criteria (may be null)
   * @return a Criteria matching active measures that satisfy the measure-only predicates
   */
  private Criteria buildPreLookupMeasureCriteria(MeasureSearchCriteria measureSearchCriteria) {
    Criteria criteria = Criteria.where("active").is(true);
    if (measureSearchCriteria != null) {
      if (StringUtils.isNotBlank(measureSearchCriteria.getModel())) {
        criteria.and("model").is(measureSearchCriteria.getModel());
      }
      if (measureSearchCriteria.getDraft() != null) {
        criteria.and("measureMetaData.draft").is(measureSearchCriteria.getDraft());
      }
      if (CollectionUtils.isNotEmpty(measureSearchCriteria.getExcludeByMeasureIds())) {
        criteria.and("_id").nin(measureSearchCriteria.getExcludeByMeasureIds());
      }
      if (measureSearchCriteria.isExcludeCompositeMeasures()) {
        criteria.and("measureMetaData.composite").ne(true);
      }
    }
    return criteria;
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

  @Override
  public int countMeasuresByReview(
      boolean isActive, String userId, List<OwnershipType> ownershipTypes) {
    boolean assignedOnly = isAssignedToUser(ownershipTypes);

    List<AggregationOperation> pipeline = new ArrayList<>();
    pipeline.add(getLookupOperation());
    pipeline.add(unwind("measureSet"));
    pipeline.addAll(SearchAggregationUtils.getReviewStages());
    pipeline.add(
        SearchAggregationUtils.matchReviewStatusIn(
            assignedOnly
                ? SearchAggregationUtils.OPEN_REVIEW_STATUSES
                : SearchAggregationUtils.IN_REVIEW_STATUSES));
    if (assignedOnly) {
      pipeline.add(SearchAggregationUtils.matchAssignedReviewer(userId));
    }

    pipeline.add(match(Criteria.where("active").is(isActive)));
    pipeline.add(group().count().as("count"));

    List<Map> results =
        mongoTemplate
            .aggregate(newAggregation(pipeline), Measure.class, Map.class)
            .getMappedResults();

    return results.isEmpty() ? 0 : Integer.parseInt(results.get(0).get("count").toString());
  }
}
