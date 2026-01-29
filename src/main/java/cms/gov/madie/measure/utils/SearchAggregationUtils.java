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
