package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.*;
import cms.gov.madie.measure.services.AppConfigService;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.Version;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNumeric;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.ConditionalOperators.Cond.when;

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

  private void appendAdditionalSearchCriteria(
      Criteria measureCriteria, MeasureSearchCriteria measureSearchCriteria) {
    // Build the orOperator for the remaining properties
    String searchField = measureSearchCriteria.getSearchField();
    List<Criteria> orConditions = new ArrayList<>();

    for (String property : measureSearchCriteria.getOptionalSearchProperties()) {
      // this needs to run whenever we have multiple, however we need to force a search even if
      // the searchField split is less than 3 if the version is the only category that is applied
      switch (property) {
        case "version":
          String[] versionParts = searchField.split("\\.");
          if (versionParts.length == 3) {
            if (isNumeric(versionParts[0])
                && isNumeric(versionParts[1])
                && isNumeric(versionParts[2])) {
              Criteria otherCriteria = Criteria.where("version").is(Version.parse(searchField));
              orConditions.add(otherCriteria);
            } else {
              if (measureSearchCriteria.getOptionalSearchProperties().size() == 1) {
                Criteria noVersionMatch = Criteria.where("version.major").is(versionParts[0]);
                orConditions.add(noVersionMatch);
              }
            }
          }
          if (versionParts.length == 2) {
            if (isNumeric(versionParts[0]) && isNumeric(versionParts[1])) {
              int major = Integer.parseInt(versionParts[0]);
              int minor = Integer.parseInt(versionParts[1]);
              Criteria otherCriteria =
                  Criteria.where("version.major").is(major).and("version.minor").is(minor);
              Criteria additionalCriteria =
                  Criteria.where("version.minor").is(major).and("version.revisionNumber").is(minor);
              orConditions.add(otherCriteria);
              orConditions.add(additionalCriteria);
            } else {
              if (measureSearchCriteria.getOptionalSearchProperties().size() == 1) {
                Criteria noVersionMatch = Criteria.where("version.major").is(versionParts[0]);
                orConditions.add(noVersionMatch);
              }
            }
          }
          if (versionParts.length == 1) {
            if (isNumeric(versionParts[0])) {
              int anyMatch = Integer.parseInt(versionParts[0]);
              Criteria majorMatch = Criteria.where("version.major").is(anyMatch);
              Criteria minorMatch = Criteria.where("version.minor").is(anyMatch);
              Criteria patchMatch = Criteria.where("version.revisionNumber").is(anyMatch);
              orConditions.add(majorMatch);
              orConditions.add(minorMatch);
              orConditions.add(patchMatch);
            } else {
              if (measureSearchCriteria.getOptionalSearchProperties().size() == 1) {
                Criteria noVersionMatch = Criteria.where("version.major").is(versionParts[0]);
                orConditions.add(noVersionMatch);
              }
            }
          }
          //  if its a bad version that's a random string, and there are no other optional params
          // provided, we need to force this criteria search
          break;
        case "cmsId":
          break;
        case "measure":
          orConditions.add(
              Criteria.where("measureName").regex(".*" + Pattern.quote(searchField) + ".*", "i"));
          break;
        case "model":
          orConditions.add(
              Criteria.where("model").regex(".*" + Pattern.quote(searchField) + ".*", "i"));
          break;
        default:
          if (!StringUtils.isBlank(property)) {
            orConditions.add(
                Criteria.where(property).regex(measureSearchCriteria.getSearchField(), "i"));
          }
      }
    }
    Criteria allOrConditions = new Criteria();
    if (!orConditions.isEmpty()) {
      allOrConditions.orOperator(orConditions);
    }
    measureCriteria.andOperator(allOrConditions);
  }

  @Override
  public Page<MeasureListDTO> searchMeasuresByCriteria(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria measureSearchCriteria,
      boolean filterByCurrentUser,
      // TODO Remove parameter when either measureSearch or EditTestsOnVersionedMeasure is removed.
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
      // If searchField is given and no filter is applied, then search for the searchField in
      // measureName and ecqmTitle
      if (StringUtils.isNotBlank(measureSearchCriteria.getSearchField())
          && CollectionUtils.isEmpty(measureSearchCriteria.getOptionalSearchProperties())) {
        aggregationOperations.add(addCmsIdDisplayField());

        String[] searchWords = measureSearchCriteria.getSearchField().split("\\s+");
        List<Criteria> wordCriteria = new ArrayList<>();

        for (String word : searchWords) {
          word = word.replaceAll("[^a-zA-Z0-9]", ""); // Remove special characters
          if (StringUtils.isNotBlank(word)) {
            wordCriteria.add(
                new Criteria()
                    .orOperator(
                        Criteria.where("measureName").regex(".*" + word + ".*", "i"),
                        Criteria.where("ecqmTitle").regex(".*" + word + ".*", "i"),
                        Criteria.where("cmsIdDisplay").regex(".*" + word + ".*", "i")));
          }
        }

        if (!wordCriteria.isEmpty()) {
          aggregationOperations.add(match(new Criteria().andOperator(wordCriteria)));
        } else {
          // If search string is only special characters return no results
          return new PageImpl<>(new ArrayList<MeasureListDTO>(), pageable, 0);
        }
      }

      // if searchField and optional filters are provided, then search for searchField only in the
      // provided filters
      if (StringUtils.isNotBlank(measureSearchCriteria.getSearchField())
          && CollectionUtils.isNotEmpty(measureSearchCriteria.getOptionalSearchProperties())) {
        appendAdditionalSearchCriteria(measureCriteria, measureSearchCriteria);
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
    Criteria measureSetCriteria = new Criteria();
    if (filterByCurrentUser) {
      measureSetCriteria =
          new Criteria()
              .orOperator(
                  Criteria.where("measureSet.owner").regex("^\\Q" + userId + "\\E$", "i"),
                  Criteria.where("measureSet.acls.userId")
                      .regex("^\\Q" + userId + "\\E$", "i")
                      .and("measureSet.acls.roles")
                      .in(RoleEnum.SHARED_WITH));
    }

    MatchOperation matchOperation =
        match(new Criteria().andOperator(measureCriteria, measureSetCriteria));

    FacetOperation facets =
        facet(sortByCount("id"))
            .as("count")
            .and(
                sort(pageable.getSort()),
                skip(pageable.getOffset()),
                limit(pageable.getPageSize()),
                project(MeasureListDTO.class))
            .as("queryResults");

    Aggregation pipeline;
    if (nestedFlag) {
      // Find all the measures that matches the given Criteria and fetch unique measureSetIds
      List<String> matchedMeasureSetIds =
          mongoTemplate
              .aggregate(
                  newAggregation(
                      lookupOperation, unwindOperation, matchOperation, group("measureSetId")),
                  Measure.class,
                  MeasureSetIdDTO.class)
              .getMappedResults()
              .stream()
              .map(MeasureSetIdDTO::getId)
              .collect(Collectors.toList());

      // Fetch all measures associated to each measureSetId
      MatchOperation matchMeasureSetIds =
          match(Criteria.where("measureSetId").in(matchedMeasureSetIds));

      // Sort those measures based on version and draft status
      SortOperation sortByVersionAndDraft =
          sort(Sort.by(Sort.Direction.DESC, "measureMetaData.draft", "version"));

      // Group all measures that has same measureSetId and get the count and also first document
      // which will be the latest measure in the MeasureSet
      GroupOperation groupByMeasureSet =
          group("measureSetId").count().as("count").first("$$ROOT").as("selectedDoc");

      // Set hasAssociatedMeasures = true if more than one measure found in the MeasureSet
      AddFieldsOperation addHasAssociated =
          addFields()
              .addField("selectedDoc.hasAssociatedMeasures")
              .withValueOf(
                  when(ComparisonOperators.Gt.valueOf("count").greaterThanValue(1))
                      .then(true)
                      .otherwise(false))
              .build();
      ReplaceRootOperation replaceRootOperation = replaceRoot("selectedDoc");

      if (StringUtils.isNotBlank(measureSearchCriteria.getSearchField())
          && measureSearchCriteria.getOptionalSearchProperties().contains("cmsId")) {
        aggregationOperations.add(addCmsIdDisplayField());
        aggregationOperations.add(matchCmsIdDisplay(measureSearchCriteria.getSearchField()));
      }

      aggregationOperations.add(matchMeasureSetIds);
      aggregationOperations.add(sortByVersionAndDraft);
      aggregationOperations.add(groupByMeasureSet);
      aggregationOperations.add(addHasAssociated);
      aggregationOperations.add(replaceRootOperation);
      aggregationOperations.add(facets);

      pipeline = newAggregation(aggregationOperations);

    } else {
      pipeline = newAggregation(lookupOperation, unwindOperation, matchOperation, facets);
    }

    List<FacetDTO> results =
        mongoTemplate.aggregate(pipeline, Measure.class, FacetDTO.class).getMappedResults();
    if (nestedFlag) {
      long totalSize = 0;
      if (results != null && !results.isEmpty()) {
        List<?> countList = results.get(0).getCount();
        if (countList != null && !countList.isEmpty()) {
          Object totalCount = countList.get(0);
          if (totalCount instanceof Map<?, ?>) {
            Object count = ((Map<?, ?>) totalCount).get("count");
            if (count instanceof Number) {
              totalSize = ((Number) count).longValue();
            }
          }
        }
      }
      return new PageImpl<>(results.get(0).getQueryResults(), pageable, totalSize);
    }

    return new PageImpl<>(
        results.get(0).getQueryResults(), pageable, results.get(0).getCount().size());
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
  public int countAllMyMeasures(boolean isActive, String userId) {
    // join measure and measure_set to lookup owner and ACL info
    LookupOperation lookupOperation = getLookupOperation();
    Criteria measureCriteria = Criteria.where("active").is(isActive);

    Criteria measureSetCriteria =
        new Criteria()
            .orOperator(
                Criteria.where("measureSet.owner").regex("^\\Q" + userId + "\\E$", "i"),
                Criteria.where("measureSet.acls.userId")
                    .regex("^\\Q" + userId + "\\E$", "i")
                    .and("measureSet.acls.roles")
                    .in(RoleEnum.SHARED_WITH));

    MatchOperation matchOperation =
        match(new Criteria().andOperator(measureCriteria, measureSetCriteria));

    GroupOperation groupOperation = group("measureSetId");

    Aggregation aggregation =
        newAggregation(
            lookupOperation, matchOperation, groupOperation, group().count().as("count"));

    List<Map> results =
        mongoTemplate.aggregate(aggregation, Measure.class, Map.class).getMappedResults();
    if (!results.isEmpty()) {
      return Integer.parseInt(results.get(0).get("count").toString());
    } else {
      return 0;
    }
  }

  @Override
  public int countAllMeasures(boolean isActive) {
    // join measure and measure_set to lookup owner and ACL info
    LookupOperation lookupOperation = getLookupOperation();
    Criteria measureCriteria = Criteria.where("active").is(isActive);

    MatchOperation matchOperation = match(measureCriteria);

    GroupOperation groupOperation = group("measureSetId");

    Aggregation aggregation =
        newAggregation(
            lookupOperation, matchOperation, groupOperation, group().count().as("count"));

    List<Map> results =
        mongoTemplate.aggregate(aggregation, Measure.class, Map.class).getMappedResults();
    if (!results.isEmpty()) {
      return Integer.parseInt(results.get(0).get("count").toString());
    } else {
      return 0;
    }
  }
}
