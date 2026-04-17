package cms.gov.madie.measure.utils;

import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;

public class SearchAggregationUtils {
  // Add string field called cmsIdDisplay. If model is QI-Core, append "FHIR" to measureSet
  // .cmsId, else only convert measureSet.cmsId to a string
  public static AggregationOperation addCmsIdDisplayField() {
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
