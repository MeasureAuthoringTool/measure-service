package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.*;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
public class MeasureSearchServiceImpl implements MeasureSearchService {
  private final MongoTemplate mongoTemplate;

  public MeasureSearchServiceImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  private LookupOperation getLookupOperation() {
    return LookupOperation.newLookup()
        .from("measureSet")
        .localField("measureSetId")
        .foreignField("measureSetId")
        .as("measureSet");
  }

  @Override
  public Page<MeasureListDTO> searchMeasuresByCriteriaWhenFeatureFlagIsOff(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria measureSearchCriteria,
      List<OwnershipType> ownershipTypes) {
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

    Aggregation pipeline = newAggregation(lookupOperation, unwindOperation, matchOperation, facets);
    List<FacetDTO> results =
        mongoTemplate.aggregate(pipeline, Measure.class, FacetDTO.class).getMappedResults();
    return new PageImpl<>(
        results.get(0).getQueryResults(), pageable, results.get(0).getCount().size());
  }

  @Override
  public Page<MeasureListDTO> searchMeasuresByCriteria(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria measureSearchCriteria,
      List<OwnershipType> ownershipTypes) {
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
      // if feature flag is ON, then we need to search for searchField only in the provided
      // filters
      if (StringUtils.isNotBlank(measureSearchCriteria.getSearchField())) {
        if (CollectionUtils.isEmpty(measureSearchCriteria.getOptionalSearchProperties())
            || measureSearchCriteria.getOptionalSearchProperties().contains("cmsId")) {
          aggregationOperations.add(SearchUtils.addCmsIdDisplayField());
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
    List<AggregationOperation> initialPipeline = new ArrayList<>(aggregationOperations);
    initialPipeline.add(
        group("measureSetId").count().as("matchCount").first("_id").as("matchedMeasureId"));
    // Find all the measures that matches the given Criteria and fetch unique measureSetIds
    List<MeasureSetMatchCountDTO> matchedMeasureSetCounts =
        mongoTemplate
            .aggregate(
                newAggregation(initialPipeline), Measure.class, MeasureSetMatchCountDTO.class)
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

    List<Criteria> ownershipCriteria = new ArrayList<>();

    if (ownershipTypes.contains(OwnershipType.OWNED)) {
      ownershipCriteria.add(
          Criteria.where("measureSet.owner").regex("^\\Q" + userId + "\\E$", "i"));
    }

    if (ownershipTypes.contains(OwnershipType.SHARED)) {
      ownershipCriteria.add(
          Criteria.where("measureSet.acls.userId")
              .regex("^\\Q" + userId + "\\E$", "i")
              .and("measureSet.acls.roles")
              .in(RoleEnum.SHARED_WITH));
    }

    return ownershipCriteria.isEmpty()
        ? null
        : new Criteria().orOperator(ownershipCriteria.toArray(Criteria[]::new));
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
  public Page<MeasureListDTO> updatedSearchMeasuresByCriteria(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria criteria,
      List<OwnershipType> ownershipTypes) {

    // 1. Ownership + optional cmsId filter (measureSet collection)
    Criteria ownership = buildMeasureSetCriteria(userId, ownershipTypes);
    Criteria root = new Criteria();
    List<Criteria> ands = new ArrayList<>();
    if (ownership != null) {
      ands.add(ownership);
    }
    boolean cmsIdSearchRequested =
        criteria != null
            && criteria.getOptionalSearchProperties() != null
            && criteria.getOptionalSearchProperties().stream().anyMatch("cmsId"::equalsIgnoreCase)
            && StringUtils.isNotBlank(criteria.getSearchField());
    if (cmsIdSearchRequested) {
      ands.add(
          Criteria.where("cmsId")
              .regex(".*" + Pattern.quote(criteria.getSearchField()) + ".*", "i"));
    }
    if (!ands.isEmpty()) {
      root.andOperator(ands.toArray(new Criteria[0]));
    }
    MatchOperation matchMeasureSet = match(root);

    // 2. Lookup active measures and add them to pipeline
    LookupOperation lookupMeasures = SearchUtils.buildLookupActiveMeasures();

    // 3. Sort measures array (draft true first, then version desc)
    AggregationOperation sortMeasures =
        ctx ->
            new Document(
                "$addFields",
                new Document(
                    "measures",
                    new Document(
                        "$sortArray",
                        new Document("input", "$measures")
                            .append(
                                "sortBy",
                                new Document("version.major", -1)
                                    .append("version.minor", -1)
                                    .append("version.revisionNumber", -1)
                                    .append("draft", -1)))));

    // 4. latestMeasure
    AddFieldsOperation latestMeasure =
        addFields().addFieldWithValue("latestMeasure", new Document("$first", "$measures")).build();

    // 5. Dynamic condition for matched measures (skip if only cmsId search)
    Document measureMatchExpr = buildMeasureLevelFilter(criteria);

    // 6. matchedMeasuresRaw (all matches incl latest if matched)
    AggregationOperation addMatchedMeasures =
        ctx -> {
          Document filterDoc =
              new Document(
                  "$filter",
                  new Document("input", "$measures")
                      .append("as", "m")
                      .append(
                          "cond",
                          measureMatchExpr == null
                              ? new Document("$literal", true)
                              : substituteVar(measureMatchExpr, "m")));
          return new Document("$addFields", new Document("matchedMeasuresRaw", filterDoc));
        };

    // 7. Keep only measureSets with at least one match
    MatchOperation atLeastOne = match(Criteria.where("matchedMeasuresRaw.0").exists(true));

    // 8. matchedMeasures excluding duplicate latest
    AggregationOperation finalizeMatched =
        ctx ->
            new Document(
                "$addFields",
                new Document(
                    "matchedMeasures",
                    new Document(
                        "$filter",
                        new Document("input", "$matchedMeasuresRaw")
                            .append("as", "mm")
                            .append(
                                "cond",
                                new Document("$ne", List.of("$$mm._id", "$latestMeasure._id"))))));

    // 9. Optional trimming (only keep arrays we need)
    ProjectionOperation project =
        project("measureSetId", "owner", "acls", "latestMeasure", "matchedMeasures");

    // 10. Sorting + paging via facet (page over measureSets)
    Sort sort =
        pageable.getSort().isUnsorted()
            ? Sort.by(Sort.Direction.DESC, "measureSetId")
            : pageable.getSort();
    SortOperation rootSort = sort(sort);
    FacetOperation facet =
        facet(skip((int) pageable.getOffset()), limit(pageable.getPageSize()))
            .as("data")
            .and(count().as("total"))
            .as("meta");

    // 11. Unwind meta
    AggregationOperation unwrap =
        ctx ->
            new Document(
                "$project",
                new Document("data", 1)
                    .append(
                        "total",
                        new Document(
                            "$ifNull", List.of(new Document("$first", "$meta.total"), 0))));

    Aggregation agg =
        newAggregation(
            matchMeasureSet,
            lookupMeasures,
            sortMeasures,
            latestMeasure,
            // measureMatchExpr may be null if only cmsId search: still add to keep consistent
            addMatchedMeasures,
            atLeastOne,
            finalizeMatched,
            project,
            rootSort,
            facet,
            unwrap);
    List<Document> allDocs = mongoTemplate.findAll(Document.class, "measureSet");
    System.out.println("All docs: " + allDocs);
    AggregationResults<Document> results =
        mongoTemplate.aggregate(agg, "measureSet", Document.class);

    if (!results.iterator().hasNext()) {
      return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }
    Document rootDoc = results.getUniqueMappedResult();
    @SuppressWarnings("unchecked")
    List<Document> rows = (List<Document>) rootDoc.get("data");
    int total = ((Number) rootDoc.get("total")).intValue();

    List<MeasureListDTO> content =
        rows.stream()
            .map(
                d -> {
                  MeasureListDTO dto = new MeasureListDTO();
                  dto.setMeasureSetId(d.getString("measureSetId"));
                  dto.setLatestMeasure(
                      mongoTemplate
                          .getConverter()
                          .read(Measure.class, (Document) d.get("latestMeasure")));
                  @SuppressWarnings("unchecked")
                  List<Document> mm = (List<Document>) d.get("matchedMeasures");
                  if (mm != null) {
                    dto.setMatchedMeasures(
                        mm.stream()
                            .map(doc -> mongoTemplate.getConverter().read(Measure.class, doc))
                            .collect(Collectors.toList()));
                  }
                  return dto;
                })
            .collect(Collectors.toList());

    return new PageImpl<>(content, pageable, total);
  }

  /**
   * Builds a measure-level filter expression (as Document) based on MeasureSearchCriteria. Returns
   * null if no measure-level constraints (i.e. only cmsId search or criteria null).
   */
  private Document buildMeasureLevelFilter(MeasureSearchCriteria measureSearchCriteria) {
    if (measureSearchCriteria == null) {
      return null;
    }
    boolean onlyCmsId =
        measureSearchCriteria.getOptionalSearchProperties() != null
            && !measureSearchCriteria.getOptionalSearchProperties().isEmpty()
            && measureSearchCriteria.getOptionalSearchProperties().stream()
                .allMatch(p -> p.equalsIgnoreCase("cmsId"));
    if (onlyCmsId) {
      return null;
    }

    List<Document> andList = new ArrayList<>();

    // Model filter
    if (StringUtils.isNotBlank(measureSearchCriteria.getModel())) {
      andList.add(new Document("$eq", List.of("$model", measureSearchCriteria.getModel())));
    }

    // Draft filter
    if (measureSearchCriteria.getDraft() != null) {
      // draft projected as top-level field in lookup pipeline
      andList.add(new Document("$eq", List.of("$draft", measureSearchCriteria.getDraft())));
    }

    // Exclude by measure IDs
    if (CollectionUtils.isNotEmpty(measureSearchCriteria.getExcludeByMeasureIds())) {
      andList.add(
          new Document(
              "$not",
              new Document(
                  "$in", List.of("$_id", measureSearchCriteria.getExcludeByMeasureIds()))));
    }

    // Search field logic
    String searchField = measureSearchCriteria.getSearchField();
    List<String> props =
        (measureSearchCriteria.getOptionalSearchProperties() == null
                || measureSearchCriteria.getOptionalSearchProperties().isEmpty())
            ? List.of("measure", "model", "version")
            : measureSearchCriteria.getOptionalSearchProperties();

    if (StringUtils.isNotBlank(searchField)) {
      List<Document> orList = new ArrayList<>();
      for (String p : props) {
        switch (p) {
          case "measure":
            orList.add(regexExpr("$measureName", searchField));
            break;
          case "model":
            orList.add(regexExpr("$model", searchField));
            break;
          case "version":
            orList.add(buildVersionSwitch(searchField));
            break;
          default:
            // ignore unsupported
        }
      }
      if (!orList.isEmpty()) {
        andList.add(new Document("$or", orList));
      }
    }

    if (andList.isEmpty()) {
      return null;
    }
    return new Document("$and", andList);
  }

  private Document regexExpr(String fieldRef, String val) {
    return new Document(
        "$regexMatch",
        new Document("input", fieldRef).append("regex", Pattern.quote(val)).append("options", "i"));
  }

  // Version switch similar to service-side logic
  private Document buildVersionSwitch(String search) {
    String[] parts = search.split("\\.");
    List<Document> branches = new ArrayList<>();
    if (parts.length == 3 && partsAreNumeric(parts)) {
      branches.add(
          new Document(
                  "case",
                  new Document(
                      "$and",
                      List.of(
                          new Document(
                              "$eq", List.of("$.version.major", Integer.parseInt(parts[0]))),
                          new Document(
                              "$eq", List.of("$.version.minor", Integer.parseInt(parts[1]))),
                          new Document(
                              "$eq",
                              List.of("$.version.revisionNumber", Integer.parseInt(parts[2]))))))
              .append("then", true));
    } else if (parts.length == 2 && partsAreNumeric(parts)) {
      branches.add(
          new Document(
                  "case",
                  new Document(
                      "$and",
                      List.of(
                          new Document(
                              "$eq", List.of("$.version.major", Integer.parseInt(parts[0]))),
                          new Document(
                              "$eq", List.of("$.version.minor", Integer.parseInt(parts[1]))))))
              .append("then", true));
      branches.add(
          new Document(
                  "case",
                  new Document(
                      "$and",
                      List.of(
                          new Document(
                              "$eq", List.of("$.version.minor", Integer.parseInt(parts[0]))),
                          new Document(
                              "$eq",
                              List.of("$.version.revisionNumber", Integer.parseInt(parts[1]))))))
              .append("then", true));
    } else if (parts.length == 1 && partsAreNumeric(parts)) {
      int n = Integer.parseInt(parts[0]);
      branches.add(
          new Document("case", new Document("$eq", List.of("$.version.major", n)))
              .append("then", true));
      branches.add(
          new Document("case", new Document("$eq", List.of("$.version.minor", n)))
              .append("then", true));
      branches.add(
          new Document("case", new Document("$eq", List.of("$.version.revisionNumber", n)))
              .append("then", true));
    } else {
      return new Document("$literal", false);
    }
    return new Document("$switch", new Document("branches", branches).append("default", false));
  }

  private boolean partsAreNumeric(String[] parts) {
    for (String p : parts) {
      if (!StringUtils.isNumeric(p)) {
        return false;
      }
    }
    return true;
  }

  private Document substituteVar(Document expr, String alias) {
    return (Document) deepReplaceValue(expr, alias);
  }

  private Object deepReplaceValue(Object node, String alias) {
    if (node instanceof String s) {
      if (s.startsWith("$") && !s.startsWith("$$")) {
        String path = s.substring(1);
        if (!path.startsWith(".")) {
          path = "." + path; // ensure dot separator
        }
        return "$$" + alias + path;
      }
      return s;
    } else if (node instanceof Document doc) {
      Document out = new Document();
      for (Map.Entry<String, Object> e : doc.entrySet()) {
        out.append(e.getKey(), deepReplaceValue(e.getValue(), alias));
      }
      return out;
    } else if (node instanceof List<?> list) {
      List<Object> out = new ArrayList<>();
      for (Object v : list) {
        out.add(deepReplaceValue(v, alias));
      }
      return out;
    }
    return node;
  }
}
