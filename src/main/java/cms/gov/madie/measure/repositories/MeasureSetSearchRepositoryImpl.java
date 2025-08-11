package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.List;

import static cms.gov.madie.measure.utils.SearchUtils.appendAdditionalSearchCriteria;
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
    LookupOperation lookupOperation =
        LookupOperation.newLookup()
            .from("measureSet")
            .localField("measureSetId")
            .foreignField("measureSetId")
            .as("measureSet");
    UnwindOperation unwindOperation = unwind("measureSet");

    Criteria measureCriteria =
        Criteria.where("active").is(true).and("measureSetId").is(measureSetId);

    if (measureSearchCriteria != null
        && StringUtils.isNotBlank(measureSearchCriteria.getSearchField())) {
      appendAdditionalSearchCriteria(measureCriteria, measureSearchCriteria);
    }

    MatchOperation matchOperation = match(measureCriteria);
    Aggregation aggregation;
    if (sortByLatestVersion) {
      SortOperation sortOperation =
          sort(
              Sort.by(
                  Sort.Direction.DESC, "version.major", "version.minor", "version.revisionNumber"));
      aggregation = newAggregation(lookupOperation, unwindOperation, matchOperation, sortOperation);
    } else {
      aggregation = newAggregation(lookupOperation, unwindOperation, matchOperation);
    }
    return mongoTemplate.aggregate(aggregation, "measure", MeasureListDTO.class).getMappedResults();
  }
}
