package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.Measure;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregationOptions;

@Repository
public class MeasureCmsIdRepositoryImpl implements MeasureCmsIdRepository {
  private final MongoTemplate mongoTemplate;

  public MeasureCmsIdRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public List<Measure> findAllByModelAndCmsId(String modelName, Integer qdmCmsId) {
    LookupOperation lookupOperation =
        LookupOperation.newLookup()
            .from("measureSet")
            .localField("measureSetId")
            .foreignField("measureSetId")
            .as("measureSet");

    // prepare measure search criteria
    Criteria measureCriteria = Criteria.where("model").is(modelName);

    // prepare measure set search criteria
    Criteria measureSetCriteria = Criteria.where("measureSet.cmsId").is(qdmCmsId);

    // combine measure and measure set criteria
    MatchOperation matchOperation =
        match(new Criteria().andOperator(measureCriteria, measureSetCriteria));
    Aggregation pipeline =
        newAggregation(lookupOperation, matchOperation)
            .withOptions(newAggregationOptions().allowDiskUse(true).build());
    ;
    return mongoTemplate.aggregate(pipeline, Measure.class, Measure.class).getMappedResults();
  }
}
