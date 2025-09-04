package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCaseConfiguration;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Repository
public class TestCasePatchRepositoryImpl implements TestCasePatchRepository {

  private final MongoOperations mongoOperations;

  public TestCasePatchRepositoryImpl(MongoOperations mongoOperations) {
    this.mongoOperations = mongoOperations;
  }

  @Override
  public Measure findAndModifyTestCaseConfig(
      TestCaseConfiguration testCaseConfiguration, String measureId) {
    Update patchUpdate = new Update();
    patchUpdate.set("testCaseConfiguration", testCaseConfiguration);

    return mongoOperations
        .update(Measure.class)
        .matching(query(where("_id").is(measureId)))
        .apply(patchUpdate)
        .withOptions(FindAndModifyOptions.options().returnNew(true))
        .findAndModifyValue();
  }
}
