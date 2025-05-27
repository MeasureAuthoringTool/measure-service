package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.FacetDTO;
import cms.gov.madie.measure.dto.MadieFeatureFlag;
import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.services.AppConfigService;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.dto.LibraryUsage;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

  private void appendAdditionalSearchCriteriaOmittingcmsId(
      Criteria measureCriteria, MeasureSearchCriteria measureSearchCriteria) {
    // Ensure optionalSearchProperties exists and isn’t empty
    if (CollectionUtils.isNotEmpty(measureSearchCriteria.getOptionalSearchProperties())) {
      // Build the orOperator for the remaining properties
      List<Criteria> orConditions = new ArrayList<>();
      for (String property : measureSearchCriteria.getOptionalSearchProperties()) {
        // this needs to run whenever we have multiple, however we need to force a search even if
        // the searchField split is less than 3 if the version is the only category that is applied
        if (property.equals("version")) {
          String[] versionParts = measureSearchCriteria.getSearchField().split("\\.");
          if (versionParts.length == 3) {
            if (isNumeric(versionParts[0])
                && isNumeric(versionParts[1])
                && isNumeric(versionParts[2])) {
              Criteria otherCriteria =
                  Criteria.where("version")
                      .is(Version.parse(measureSearchCriteria.getSearchField()));
              orConditions.add(otherCriteria);
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
        } else if (property.equals("cmsId")) {
          String searchField = measureSearchCriteria.getSearchField();
          if (isNumeric(searchField)) {
            int number = Integer.parseInt(searchField);
            orConditions.add(Criteria.where("measureSet.cmsId").is(number));
          }
        } else {
          orConditions.add(
              Criteria.where(property).regex(measureSearchCriteria.getSearchField(), "i"));
        }
      }
      Criteria allOrConditions = new Criteria();
      if (!orConditions.isEmpty()) {
        allOrConditions.orOperator(orConditions);
      }
      measureCriteria.andOperator(allOrConditions);
    }
  }

  @Override
  public Page<MeasureListDTO> searchMeasuresByCriteria(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria measureSearchCriteria,
      boolean filterByCurrentUser) {
    // join measure and measure_set to lookup owner and ACL info
    LookupOperation lookupOperation = getLookupOperation();

    UnwindOperation unwindOperation = unwind("measureSet");
    Criteria measureCriteria = Criteria.where("active").is(true);
    if (measureSearchCriteria != null) {
      // If query is given, search for the query string in measureName and ecqmTitle
      if (StringUtils.isNotBlank(measureSearchCriteria.getSearchField())
          && CollectionUtils.isEmpty(measureSearchCriteria.getOptionalSearchProperties())) {
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
          measureCriteria.andOperator(wordCriteria);
        }
      }
      // optional query provided
      if (StringUtils.isNotBlank(measureSearchCriteria.getSearchField())
          && CollectionUtils.isNotEmpty(measureSearchCriteria.getOptionalSearchProperties())) {
        appendAdditionalSearchCriteriaOmittingcmsId(measureCriteria, measureSearchCriteria);
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

    Aggregation pipeline = null;
    if (appConfigService.isFlagEnabled(MadieFeatureFlag.MEASURE_SEARCH)) {
      SortOperation sortOperation =
          sort(Sort.by(Sort.Direction.DESC, "measureMetaData.draft", "version"));

      GroupOperation groupOperation =
          group("measureSetId").count().as("count").first("$$ROOT").as("selectedDoc");

      AddFieldsOperation addFieldsOperation =
          addFields()
              .addField("selectedDoc.hasAssociatedMeasures")
              .withValueOf(
                  when(ComparisonOperators.Gt.valueOf("count").greaterThanValue(1))
                      .then(true)
                      .otherwise(false))
              .build();

      ReplaceRootOperation replaceRootOperation = replaceRoot("selectedDoc");

      pipeline =
          newAggregation(
              lookupOperation,
              unwindOperation,
              matchOperation,
              sortOperation,
              project(MeasureListDTO.class),
              groupOperation,
              addFieldsOperation,
              replaceRootOperation,
              sort(Sort.by(Sort.Direction.DESC, "lastModifiedAt")),
              facets);

    } else {
      pipeline = newAggregation(lookupOperation, unwindOperation, matchOperation, facets);
    }

    List<FacetDTO> results =
        mongoTemplate.aggregate(pipeline, Measure.class, FacetDTO.class).getMappedResults();

    if (appConfigService.isFlagEnabled(MadieFeatureFlag.MEASURE_SEARCH)) {
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

    List<Map> results = mongoTemplate.aggregate(aggregation, Measure.class, Map.class).getMappedResults();
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

    List<Map> results = mongoTemplate.aggregate(aggregation, Measure.class, Map.class).getMappedResults();
    if (!results.isEmpty()) {
      return Integer.parseInt(results.get(0).get("count").toString());
    } else {
      return 0;
    }
  }
}
