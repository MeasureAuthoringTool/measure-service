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
import gov.cms.madie.models.measure.MeasureSet;
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
import org.springframework.data.mongodb.core.query.Query;
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

    // Fast path: the All / My / Shared measure tabs load with no text search (and are not composite
    // or review searches). These can be served by ONE aggregation (latest-per-family) instead of
    // the two-query flow below.
    if (isNoSearchFastPath(measureSearchCriteria)) {
      return searchLatestPerFamily(userId, pageable, measureSearchCriteria, ownershipTypes);
    }

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
   * Returns true when the request is a plain list load (All / My / Shared tabs) with no text
   * search, no composite-component logic and no review search - the case that can be served by a
   * single aggregation via {@link #searchLatestPerFamily}.
   */
  private boolean isNoSearchFastPath(MeasureSearchCriteria measureSearchCriteria) {
    if (measureSearchCriteria == null) {
      return true;
    }
    return StringUtils.isBlank(measureSearchCriteria.getSearchField())
        && !measureSearchCriteria.isFromCompositeMeasureComponent()
        && !SearchAggregationUtils.isReviewSearch(measureSearchCriteria);
  }

  /**
   * Single-pass equivalent of {@link #searchMeasuresByCriteria} for the no-search list tabs.
   *
   * <p>Key differences from the two-query flow:
   *
   * <ul>
   *   <li>Ownership (My / Shared) is resolved from the small measureSet collection FIRST, and the
   *       resulting measureSetIds are pushed into the measure-level $match <b>before</b> the
   *       $lookup - so only the user's families are joined/unwound instead of every active measure.
   *   <li>The measure-level $match is the first stage, keeping it index-backed
   *       (active_1_measureMetaData.draft_1_measureSetId_1).
   *   <li>{@code hasAssociatedMeasures} and the total are derived inside the same aggregation
   *       (familySize &gt; 1, and the size of the count facet), removing the separate Query 1.
   *   <li>Lock/review/component stages run AFTER the latest-per-family selection, so they touch one
   *       document per family instead of every version.
   * </ul>
   */
  private Page<MeasureListDTO> searchLatestPerFamily(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria measureSearchCriteria,
      List<OwnershipType> ownershipTypes) {
    Criteria measureCriteria = Criteria.where("active").is(true);
    if (measureSearchCriteria != null) {
      if (StringUtils.isNotBlank(measureSearchCriteria.getModel())) {
        measureCriteria.and("model").is(measureSearchCriteria.getModel());
      }
      if (measureSearchCriteria.getDraft() != null) {
        measureCriteria.and("measureMetaData.draft").is(measureSearchCriteria.getDraft());
      }
      if (CollectionUtils.isNotEmpty(measureSearchCriteria.getExcludeByMeasureIds())) {
        measureCriteria.and("_id").nin(measureSearchCriteria.getExcludeByMeasureIds());
      }
      if (measureSearchCriteria.isExcludeCompositeMeasures()) {
        measureCriteria.and("measureMetaData.composite").ne(true);
      }
    }

    // Ownership-first: resolve the (few) owned/shared measureSetIds from the small measureSet
    // collection and push them into the measure-level $match BEFORE the $lookup, so we only join
    // the user's families rather than every active measure.
    List<String> ownershipMeasureSetIds = resolveOwnershipMeasureSetIds(userId, ownershipTypes);
    if (ownershipMeasureSetIds != null) {
      if (ownershipMeasureSetIds.isEmpty()) {
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
      }
      measureCriteria.and("measureSetId").in(ownershipMeasureSetIds);
    }

    List<AggregationOperation> pipeline = new ArrayList<>();
    // Index-backed filter FIRST, then drop the heavy fields before the join.
    pipeline.add(match(measureCriteria));
    pipeline.add(project().andExclude("cql", "elmJson", "testCases"));
    pipeline.add(getLookupOperation());
    pipeline.add(unwind("measureSet"));
    // Latest per family: active first, then drafts, then by version (matches the two-query flow).
    pipeline.add(sort(Sort.by(Sort.Direction.DESC, "active", "measureMetaData.draft", "version")));
    pipeline.add(group("measureSetId").first("$$ROOT").as("selectedDoc").count().as("familySize"));
    // hasAssociatedMeasures == the family has more than one measure.
    pipeline.add(
        addFields()
            .addField("selectedDoc.hasAssociatedMeasures")
            .withValue(ComparisonOperators.Gt.valueOf("familySize").greaterThanValue(1))
            .build());
    pipeline.add(replaceRoot("selectedDoc"));
    // Lock/review/component stages now run once per family (on the selected latest measure).
    pipeline.addAll(getLockStages(userId));
    pipeline.addAll(SearchAggregationUtils.getReviewStages());
    pipeline.add(SearchAggregationUtils.addIsComponentField());

    Sort effectiveSort = pageable.getSort();
    boolean hasDraftSort =
        effectiveSort.stream()
            .anyMatch(order -> "measureMetaData.draft".equals(order.getProperty()));
    if (hasDraftSort) {
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

    // Each document reaching this stage is exactly one family (we grouped by measureSetId), so
    // count them directly with $count. This mirrors the two-query flow's total (matchInfoMap.size()
    // = number of distinct measureSetIds) and, unlike sortByCount("id"), does not rely on Spring
    // Data still mapping "id" -> "_id" after the group/replaceRoot stages reshape the document.
    pipeline.add(
        facet(count().as("total"))
            .as("countFacet")
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
    List<MeasureListDTO> queryResults = facetResults.getQueryResults();
    populateOwnerDisplayNames(queryResults);
    long total =
        CollectionUtils.isEmpty(facetResults.getCountFacet())
            ? 0
            : facetResults.getCountFacet().get(0).getTotal();
    return new PageImpl<>(queryResults, pageable, total);
  }

  /**
   * Resolves the measureSetIds a user owns and/or is shared on by querying the (small) measureSet
   * collection directly. Returns {@code null} when no ownership restriction applies (no user, or
   * ALL requested), meaning "do not filter by ownership".
   */
  private List<String> resolveOwnershipMeasureSetIds(
      String userId, List<OwnershipType> ownershipTypes) {
    if (StringUtils.isBlank(userId)
        || ownershipTypes == null
        || ownershipTypes.isEmpty()
        || ownershipTypes.contains(OwnershipType.ALL)) {
      return null;
    }

    List<Criteria> ownershipCriterias = new ArrayList<>();
    if (ownershipTypes.contains(OwnershipType.OWNED)) {
      ownershipCriterias.add(Criteria.where("owner").regex("^\\Q" + userId + "\\E$", "i"));
    }
    if (ownershipTypes.contains(OwnershipType.SHARED)) {
      ownershipCriterias.add(
          Criteria.where("acls.userId")
              .regex("^\\Q" + userId + "\\E$", "i")
              .and("acls.roles")
              .in(RoleEnum.SHARED_WITH));
    }
    if (ownershipCriterias.isEmpty()) {
      return null;
    }

    Query query = new Query(new Criteria().orOperator(ownershipCriterias.toArray(new Criteria[0])));
    query.fields().include("measureSetId");
    return mongoTemplate.find(query, MeasureSet.class).stream()
        .map(MeasureSet::getMeasureSetId)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .collect(Collectors.toList());
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

    // Drop the heavy fields before the $lookup/$unwind that are never referenced in this pipeline
    aggregationOperations.add(project().andExclude("cql", "elmJson", "testCases"));
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

      if (StringUtils.isNotBlank(measureSearchCriteria.getModel())) {
        measureCriteria.and("model").is(measureSearchCriteria.getModel());
      }

      if (measureSearchCriteria.getDraft() != null) {
        measureCriteria.and("measureMetaData.draft").is(measureSearchCriteria.getDraft());
      }

      if (CollectionUtils.isNotEmpty(measureSearchCriteria.getExcludeByMeasureIds())) {
        measureCriteria.and("_id").nin(measureSearchCriteria.getExcludeByMeasureIds());
      }

      if (measureSearchCriteria.isExcludeCompositeMeasures()) {
        measureCriteria.and("measureMetaData.composite").ne(true);
      }

      if (measureSearchCriteria.isFromCompositeMeasureComponent()) {
        if (CollectionUtils.isNotEmpty(measureSearchCriteria.getAllowedScoringTypes())) {
          aggregationOperations.add(
              createScoringTypeFilter(measureSearchCriteria.getAllowedScoringTypes()));
        }
      }
    }

    Criteria measureSetCriteria = buildMeasureSetCriteria(userId, ownershipTypes);
    MatchOperation matchOperation =
        (measureSetCriteria != null)
            ? match(new Criteria().andOperator(measureCriteria, measureSetCriteria))
            : match(measureCriteria);

    aggregationOperations.add(matchOperation);
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
    boolean isCompositeComponentSearch =
        measureSearchCriteria != null && measureSearchCriteria.isFromCompositeMeasureComponent();
    Sort effectiveSort = pageable.getSort();
    Sort.Order translatorSort = effectiveSort.getOrderFor("translatorVersion");
    boolean needsElmJson = isCompositeComponentSearch && translatorSort != null;

    // Drop the heavy fields before the $lookup/$unwind that are never referenced in this pipeline.
    List<String> earlyExclude = new ArrayList<>(Arrays.asList("cql", "testCases"));
    if (!needsElmJson) {
      earlyExclude.add("elmJson");
    }

    List<AggregationOperation> postMatchPipeline = new ArrayList<>();
    postMatchPipeline.add(project().andExclude(earlyExclude.toArray(new String[0])));
    postMatchPipeline.add(lookupOperation);
    postMatchPipeline.add(unwindOperation);
    if (needsElmJson) {
      postMatchPipeline.add(SearchAggregationUtils.addTranslatorVersionSortField());
      effectiveSort = Sort.by(translatorSort.withProperty("translatorVersionSort"));
    }

    // Honor measureMeataData.draft searchCriteria
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

    postMatchPipeline.add(match(criteria));
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
    // Drop the heavy fields (cql/testCases/elmJson) as the VERY FIRST stage, before the
    // $lookup/$unwind,
    pipeline.add(project().andExclude("cql", "testCases", "elmJson"));
    pipeline.add(getLookupOperation());
    pipeline.add(unwind("measureSet"));

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
    Criteria measureCriteria = Criteria.where("active").is(isActive);

    // Ownership-first: resolve the (few) owned/shared measureSetIds from the small measureSet
    // collection and filter on them directly, so this count never joins measureSet from the
    // measure side. For ALL (no ownership filter) this is a plain distinct-family count.
    List<String> ownershipMeasureSetIds = resolveOwnershipMeasureSetIds(userId, ownershipTypes);
    if (ownershipMeasureSetIds != null) {
      if (ownershipMeasureSetIds.isEmpty()) {
        return 0;
      }
      measureCriteria.and("measureSetId").in(ownershipMeasureSetIds);
    }

    Aggregation aggregation =
        newAggregation(match(measureCriteria), group("measureSetId"), group().count().as("count"));

    List<Map> results =
        mongoTemplate.aggregate(aggregation, Measure.class, Map.class).getMappedResults();

    return results.isEmpty() ? 0 : Integer.parseInt(results.get(0).get("count").toString());
  }

  @Override
  public int countMeasuresByReview(
      boolean isActive, String userId, List<OwnershipType> ownershipTypes) {
    boolean assignedOnly = isAssignedToUser(ownershipTypes);

    List<AggregationOperation> pipeline = new ArrayList<>();
    // measureSet is never read by this count, so skip the previously-unused $lookup/$unwind.
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
