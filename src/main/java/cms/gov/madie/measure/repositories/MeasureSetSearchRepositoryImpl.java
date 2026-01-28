package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.utils.SearchUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

@Repository
public class MeasureSetSearchRepositoryImpl implements MeasureSetSearchRepository {
  @Autowired private MongoTemplate mongoTemplate;

  @Override
  public List<MeasureListDTO> findMeasuresByMeasureSetId(
      String measureSetId,
      boolean sortByLatestVersion,
      MeasureSearchCriteria measureSearchCriteria) {
    List<AggregationOperation> aggregationOperations = new ArrayList<>();
    LookupOperation lookupOperation =
        LookupOperation.newLookup()
            .from("measureSet")
            .localField("measureSetId")
            .foreignField("measureSetId")
            .as("measureSet");
    UnwindOperation unwindOperation = unwind("measureSet");

    aggregationOperations.add(lookupOperation);
    aggregationOperations.add(unwindOperation);

    Criteria measureCriteria =
        Criteria.where("active").is(true).and("measureSetId").is(measureSetId);

    if (measureSearchCriteria != null
        && StringUtils.isNotBlank(measureSearchCriteria.getSearchField())) {
      if (CollectionUtils.isEmpty(measureSearchCriteria.getOptionalSearchProperties())
          || measureSearchCriteria.getOptionalSearchProperties().contains("cmsId")) {
        aggregationOperations.add(SearchUtils.addCmsIdDisplayField());
      }
      SearchUtils.appendAdditionalSearchCriteria(measureCriteria, measureSearchCriteria);
    }

    // filter draft measures for composite measure components search
    if (measureSearchCriteria != null && measureSearchCriteria.isFromCompositeMeasureComponents()) {
      measureCriteria.and("measureMetaData.draft").is(false);
    }

    MatchOperation matchOperation = match(measureCriteria);
    aggregationOperations.add(matchOperation);

    // filter measures that contains only the allowed scoring types in all their groups for
    // composite measure components search
    if (measureSearchCriteria != null
        && measureSearchCriteria.isFromCompositeMeasureComponents()
        && CollectionUtils.isNotEmpty(measureSearchCriteria.getAllowedScoringTypes())) {
      aggregationOperations.add(
          SearchUtils.createScoringTypeFilter(measureSearchCriteria.getAllowedScoringTypes()));
    }

    Aggregation aggregation;
    if (sortByLatestVersion) {
      SortOperation sortOperation =
          sort(
              Sort.by(
                  Sort.Direction.DESC, "version.major", "version.minor", "version.revisionNumber"));
      aggregationOperations.add(sortOperation);
      aggregation = newAggregation(aggregationOperations);
    } else {
      aggregation = newAggregation(aggregationOperations);
    }
    return mongoTemplate.aggregate(aggregation, "measure", MeasureListDTO.class).getMappedResults();
  }
}
