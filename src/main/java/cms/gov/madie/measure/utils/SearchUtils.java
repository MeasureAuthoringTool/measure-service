package cms.gov.madie.measure.utils;

import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import gov.cms.madie.models.common.Version;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNumeric;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;

public class SearchUtils {
  public static void appendAdditionalSearchCriteria(
      Criteria measureCriteria, MeasureSearchCriteria measureSearchCriteria) {
    // Build the orOperator for the remaining properties
    String searchField = measureSearchCriteria.getSearchField();
    List<String> optionalProperties = measureSearchCriteria.getOptionalSearchProperties();
    List<Criteria> orConditions = new ArrayList<>();

    // If optionalProperties is empty or null, search for the field in all four: measureName,
    // model, version, CMSID
    List<String> effectiveProperties =
        optionalProperties == null || optionalProperties.isEmpty()
            ? Arrays.asList("measure", "model", "version", "cmsId")
            : optionalProperties;

    for (String property : effectiveProperties) {
      // this needs to run whenever we have multiple, however we need to force a search even if
      // the searchField split is less than 3 if the version is the only category that is applied
      switch (property) {
        case "version":
          String[] versionParts = searchField.split("\\.");
          if (versionParts.length == 3
              && isNumeric(versionParts[0])
              && isNumeric(versionParts[1])
              && isNumeric(versionParts[2])) {
            Criteria otherCriteria = Criteria.where("version").is(Version.parse(searchField));
            orConditions.add(otherCriteria);
          } else if (versionParts.length == 2
              && isNumeric(versionParts[0])
              && isNumeric(versionParts[1])) {
            int major = Integer.parseInt(versionParts[0]);
            int minor = Integer.parseInt(versionParts[1]);
            Criteria otherCriteria =
                Criteria.where("version.major").is(major).and("version.minor").is(minor);
            Criteria additionalCriteria =
                Criteria.where("version.minor").is(major).and("version.revisionNumber").is(minor);
            orConditions.add(otherCriteria);
            orConditions.add(additionalCriteria);
          } else if (versionParts.length == 1 && isNumeric(versionParts[0])) {
            int anyMatch = Integer.parseInt(versionParts[0]);
            Criteria majorMatch = Criteria.where("version.major").is(anyMatch);
            Criteria minorMatch = Criteria.where("version.minor").is(anyMatch);
            Criteria patchMatch = Criteria.where("version.revisionNumber").is(anyMatch);
            orConditions.add(majorMatch);
            orConditions.add(minorMatch);
            orConditions.add(patchMatch);
          } else {
            orConditions.add(Criteria.where("version.major").is("__NO_MATCH__"));
          }
          break;
        case "cmsId":
          orConditions.add(
              Criteria.where("cmsIdDisplay").regex(".*" + Pattern.quote(searchField) + ".*", "i"));
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
            orConditions.add(Criteria.where(property).regex(searchField, "i"));
          }
      }
    }
    if (!orConditions.isEmpty()) {
      measureCriteria.andOperator(new Criteria().orOperator(orConditions));
    }
  }

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
