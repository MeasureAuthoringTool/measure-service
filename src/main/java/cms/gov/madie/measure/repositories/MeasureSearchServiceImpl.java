package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.*;
import cms.gov.madie.measure.services.AppConfigService;
import cms.gov.madie.measure.utils.SearchUtils;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.measure.Measure;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
public class MeasureSearchServiceImpl implements MeasureSearchService {
  private final MongoTemplate mongoTemplate;
  private AppConfigService appConfigService;

  public MeasureSearchServiceImpl(MongoTemplate mongoTemplate, AppConfigService appConfigService) {
    this.mongoTemplate = mongoTemplate;
    this.appConfigService = appConfigService;
  }

  private LookupOperation getLookupOperation() {
    return LookupOperation.newLookup()
        .from("measureSet")
        .localField("measureSetId")
        .foreignField("measureSetId")
        .as("measureSet");
  }

  @Override
  public Page<MeasureListDTO> searchMeasuresByCriteria(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria measureSearchCriteria,
      // TODO Remove parameter when either measureSearch or EditTestsOnVersionedMeasure is removed.
      List<OwnershipType> ownershipTypes,
      String invocationSource) {
    List<AggregationOperation> aggregationOperations = new ArrayList<>();

    // join measure and measure_set to lookup owner and ACL info
    LookupOperation lookupOperation = getLookupOperation();
    UnwindOperation unwindOperation = unwind("measureSet");

    Criteria measureCriteria = Criteria.where("active").is(true);

    boolean nestedFlag =
        invocationSource.equals("testCase")
            ? appConfigService.isFlagEnabled(MadieFeatureFlag.EDIT_TESTS_ON_VERSIONED_MEASURES)
            : appConfigService.isFlagEnabled(MadieFeatureFlag.MEASURE_SEARCH);

    aggregationOperations.add(lookupOperation);
    aggregationOperations.add(unwindOperation);

    if (measureSearchCriteria != null) {
      if (!nestedFlag) {
        // If feature flag is OFF, then we need to search for the searchField in
        // measureName or ecqmTitle or CMDId
        if (StringUtils.isNotBlank(measureSearchCriteria.getSearchField())) {
          String[] searchWords = measureSearchCriteria.getSearchField().split("\\s+");
          List<Criteria> wordCriteria = new ArrayList<>();

          for (String word : searchWords) {
            word = word.replaceAll("[^a-zA-Z0-9]", ""); // Remove special characters
            if (StringUtils.isNotBlank(word)) {
              wordCriteria.add(
                  new Criteria()
                      .orOperator(
                          Criteria.where("measureName").regex(".*" + word + ".*", "i"),
                          Criteria.where("ecqmTitle").regex(".*" + word + ".*", "i")));
            }
          }

          if (!wordCriteria.isEmpty()) {
            measureCriteria = measureCriteria.andOperator(wordCriteria.toArray(new Criteria[0]));
          } else {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
          }
        }
      } else {
        // if feature flag is ON, then we need to search for searchField only in the provided
        // filters
        if (StringUtils.isNotBlank(measureSearchCriteria.getSearchField())) {
          SearchUtils.appendAdditionalSearchCriteria(measureCriteria, measureSearchCriteria);
        }
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

    if (nestedFlag) {
      // Find all the measures that matches the given Criteria and fetch unique measureSetIds
      List<MeasureSetMatchCountDTO> matchedMeasureSetCounts =
          mongoTemplate
              .aggregate(
                  newAggregation(
                      lookupOperation,
                      unwindOperation,
                      matchOperation,
                      group("measureSetId")
                          .count()
                          .as("matchCount")
                          .first("_id")
                          .as("matchedMeasureId")),
                  Measure.class,
                  MeasureSetMatchCountDTO.class)
              .getMappedResults();

      Map<String, MeasureSetMatchCountDTO> matchInfoMap =
          matchedMeasureSetCounts.stream()
              .collect(
                  Collectors.toMap(MeasureSetMatchCountDTO::getMeasureSetId, Function.identity()));

      List<String> matchedMeasureSetIds = new ArrayList<>(matchInfoMap.keySet());

      if (matchedMeasureSetIds.isEmpty()) {
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
      }

      // Fetch all measures associated to each MeasureSetId
      MatchOperation matchMeasureSetIds =
          match(Criteria.where("measureSetId").in(matchedMeasureSetIds));

      // Sort those measures based on active status, version and draft status
      // Active measures should come first, then draft measures, then by version
      SortOperation sortByVersionAndDraft =
          sort(Sort.by(Sort.Direction.DESC, "active", "measureMetaData.draft", "version"));

      // Group all measures that has same measureSetId and get the count and also first document
      // which will be the latest measure in the MeasureSet
      GroupOperation groupByMeasureSet = group("measureSetId").first("$$ROOT").as("selectedDoc");

      ReplaceRootOperation replaceRoot = replaceRoot("selectedDoc");

      // We are missing to search from CMS ID when the field is not provided altogether
      if (measureSearchCriteria != null
          && StringUtils.isNotBlank(measureSearchCriteria.getSearchField())
          && measureSearchCriteria.getOptionalSearchProperties().contains("cmsId")) {
        aggregationOperations.add(addCmsIdDisplayField());
        aggregationOperations.add(matchCmsIdDisplay(measureSearchCriteria.getSearchField()));
      }

      aggregationOperations.add(matchMeasureSetIds);
      aggregationOperations.add(sortByVersionAndDraft);
      aggregationOperations.add(groupByMeasureSet);
      aggregationOperations.add(replaceRoot);
      aggregationOperations.add(facets);

      Aggregation pipeline = newAggregation(aggregationOperations);
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
      return new PageImpl<>(results.get(0).getQueryResults(), pageable, totalSize);

    } else {
      Aggregation pipeline =
          newAggregation(lookupOperation, unwindOperation, matchOperation, facets);
      List<FacetDTO> results =
          mongoTemplate.aggregate(pipeline, Measure.class, FacetDTO.class).getMappedResults();
      return new PageImpl<>(
          results.get(0).getQueryResults(), pageable, results.get(0).getCount().size());
    }
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

  // Add string field called cmsIdDisplay. If model is QI-Core, append "FHIR" to measureSet
  // .cmsId, else only convert measureSet.cmsId to a string
  private AggregationOperation addCmsIdDisplayField() {
    return context ->
        new Document(
            "$addFields",
            new Document(
                "cmsIdDisplay",
                new Document(
                    "$cond",
                    List.of(
                        new Document(
                            "$regexMatch",
                            new Document("input", "$model").append("regex", "QI-Core")),
                        new Document(
                            "$concat",
                            List.of(new Document("$toString", "$measureSet.cmsId"), "FHIR")),
                        new Document("$toString", "$measureSet.cmsId")))));
  }

  // Case-insensitive contains search
  private AggregationOperation matchCmsIdDisplay(String input) {
    return context ->
        new Document(
            "$match",
            new Document("cmsIdDisplay", new Document("$regex", input).append("$options", "i")));
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
