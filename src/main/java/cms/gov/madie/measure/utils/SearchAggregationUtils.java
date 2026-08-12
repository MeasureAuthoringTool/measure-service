package cms.gov.madie.measure.utils;

import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import gov.cms.madie.models.common.ReviewStatus;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.ConditionalOperators.*;

public class SearchAggregationUtils {

  public static List<AggregationOperation> getReviewStages(boolean isReview) {

    List<AggregationOperation> reviewStages =
        new ArrayList<>(
            Arrays.asList(
                addFields()
                    .addField("measureIdString")
                    .withValue(ConvertOperators.ToString.toString("$_id"))
                    .build(),
                lookup("measureReview", "measureIdString", "measureId", "review"),
                addFields()
                    .addField("reviewStatus")
                    .withValue(
                        switchCases(
                                Switch.CaseOperator.when(
                                        ComparisonOperators.Eq.valueOf(
                                                ArrayOperators.ArrayElemAt.arrayOf("$review.status")
                                                    .elementAt(0))
                                            .equalToValue(ReviewStatus.READY_FOR_REVIEW.name()))
                                    .then("Ready"),
                                Switch.CaseOperator.when(
                                        ComparisonOperators.Eq.valueOf(
                                                ArrayOperators.ArrayElemAt.arrayOf("$review.status")
                                                    .elementAt(0))
                                            .equalToValue(ReviewStatus.IN_PROGRESS.name()))
                                    .then("In Progress"),
                                Switch.CaseOperator.when(
                                        ComparisonOperators.Eq.valueOf(
                                                ArrayOperators.ArrayElemAt.arrayOf("$review.status")
                                                    .elementAt(0))
                                            .equalToValue(ReviewStatus.COMPLETE.name()))
                                    .then("Complete"))
                            .defaultTo(""))
                    .build()));
    if (isReview) {

      reviewStages.add(
          match(
              new Criteria()
                  .orOperator(
                      Criteria.where("reviewStatus").is("Ready"),
                      Criteria.where("reviewStatus").is("In Progress"),
                      Criteria.where("reviewStatus").is("Complete"))));
    }
    return reviewStages;
  }

  public static boolean isReviewSearch(MeasureSearchCriteria measureSearchCriteria) {
    return measureSearchCriteria != null
        && CollectionUtils.isNotEmpty(measureSearchCriteria.getOptionalSearchProperties())
        && measureSearchCriteria.getOptionalSearchProperties().contains("review");
  }

  // Add string field called cmsIdDisplay. The CMS ID is zero-padded to 4 digits
  // for display & search consistency. Values wider than 4 digits
  // are left as-is. For QI-Core measures the "FHIR" suffix is appended.
  // When cmsId is null/missing, cmsIdDisplay is null (so search doesn't match).
  public static AggregationOperation addCmsIdDisplayField() {
    Document safeCmsIdString =
        new Document("$ifNull", Arrays.asList(new Document("$toString", "$measureSet.cmsId"), ""));
    Document cmsIdStrLen = new Document("$strLenCP", safeCmsIdString);
    Document padCount =
        new Document(
            "$max", Arrays.asList(0, new Document("$subtract", Arrays.asList(4, cmsIdStrLen))));
    Document paddedCmsId =
        new Document(
            "$concat",
            Arrays.asList(
                new Document("$substrCP", Arrays.asList("0000", 0, padCount)), safeCmsIdString));

    Document paddedWithOptionalFhirSuffix =
        new Document(
            "$cond",
            List.of(
                new Document(
                    "$regexMatch", new Document("input", "$model").append("regex", "QI-Core")),
                new Document("$concat", Arrays.asList(paddedCmsId, "FHIR")),
                paddedCmsId));

    Document cmsIdDisplayExpr =
        new Document(
            "$cond",
            Arrays.asList(
                new Document("$gt", Arrays.asList("$measureSet.cmsId", null)),
                paddedWithOptionalFhirSuffix,
                null));

    return context -> new Document("$addFields", new Document("cmsIdDisplay", cmsIdDisplayExpr));
  }

  // Add boolean field component: true if compositeMeasureIds is not null and not empty
  public static AggregationOperation addIsComponentField() {
    return context ->
        new Document(
            "$addFields",
            new Document(
                "component",
                new Document(
                    "$gt",
                    Arrays.asList(
                        new Document(
                            "$size",
                            new Document(
                                "$ifNull",
                                Arrays.asList("$compositeMeasureIds", Collections.emptyList()))),
                        0))));
  }

  /**
   * Adds a numeric field {@code draftSortOrder} (1–5) used for custom draft-based sorting:
   *
   * <ol>
   *   <li>Non-composite versioned (draft=false, composite=false, compositeMeasureIds empty/null)
   *   <li>Composite versioned (draft=false, composite=true)
   *   <li>In-composite versioned component (draft=false, compositeMeasureIds non-empty)
   *   <li>Draft (draft=true, composite=false)
   *   <li>Composite draft (draft=true, composite=true)
   * </ol>
   *
   * Sort ASC on this field to get the order 1→5 (versioned first); DESC to get 5→1 (composite draft
   * first).
   */
  public static AggregationOperation addDraftSortOrderField() {
    // Helper expression: compositeMeasureIds array size > 0
    Document hasCompositeMeasureIds =
        new Document(
            "$gt",
            Arrays.asList(
                new Document(
                    "$size",
                    new Document(
                        "$ifNull", Arrays.asList("$compositeMeasureIds", Collections.emptyList()))),
                0));

    Document switchExpr =
        new Document(
            "$switch",
            new Document(
                    "branches",
                    Arrays.asList(
                        // 1: versioned, not composite, not a component
                        new Document(
                                "case",
                                new Document(
                                    "$and",
                                    Arrays.asList(
                                        new Document(
                                            "$eq", Arrays.asList("$measureMetaData.draft", false)),
                                        new Document(
                                            "$ne",
                                            Arrays.asList("$measureMetaData.composite", true)),
                                        new Document(
                                            "$not", Arrays.asList(hasCompositeMeasureIds)))))
                            .append("then", 1),
                        // 2: composite versioned
                        new Document(
                                "case",
                                new Document(
                                    "$and",
                                    Arrays.asList(
                                        new Document(
                                            "$eq",
                                            Arrays.asList("$measureMetaData.composite", true)),
                                        new Document(
                                            "$eq",
                                            Arrays.asList("$measureMetaData.draft", false)))))
                            .append("then", 2),
                        // 3: versioned component (in composite)
                        new Document(
                                "case",
                                new Document(
                                    "$and",
                                    Arrays.asList(
                                        new Document(
                                            "$eq", Arrays.asList("$measureMetaData.draft", false)),
                                        hasCompositeMeasureIds)))
                            .append("then", 3),
                        // 4: draft, not composite
                        new Document(
                                "case",
                                new Document(
                                    "$and",
                                    Arrays.asList(
                                        new Document(
                                            "$eq", Arrays.asList("$measureMetaData.draft", true)),
                                        new Document(
                                            "$ne",
                                            Arrays.asList("$measureMetaData.composite", true)))))
                            .append("then", 4),
                        // 5: composite draft
                        new Document(
                                "case",
                                new Document(
                                    "$and",
                                    Arrays.asList(
                                        new Document(
                                            "$eq",
                                            Arrays.asList("$measureMetaData.composite", true)),
                                        new Document(
                                            "$eq", Arrays.asList("$measureMetaData.draft", true)))))
                            .append("then", 5)))
                .append("default", 4));

    return context -> new Document("$addFields", new Document("draftSortOrder", switchExpr));
  }

  // aggregation operation to filter measures based on allowed scoring types
  public static AggregationOperation createScoringTypeFilter(List<String> allowedScoringTypes) {
    AggregationExpression expr =
        context ->
            new Document(
                "$allElementsTrue",
                Collections.singletonList(
                    new Document(
                        "$map",
                        new Document(
                                "input",
                                new Document(
                                    "$ifNull", Arrays.asList("$groups", Collections.emptyList())))
                            .append("as", "g")
                            .append(
                                "in",
                                new Document(
                                    "$in", Arrays.asList("$$g.scoring", allowedScoringTypes))))));
    return match(Criteria.where("$expr").is(expr));
  }
}
