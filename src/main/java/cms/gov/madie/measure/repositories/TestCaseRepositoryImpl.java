package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.HapiOperationOutcome;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCaseValidationStatus;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class TestCaseRepositoryImpl implements TestCaseRepository {
  private final MongoOperations mongoOperations;

  public TestCaseRepositoryImpl(MongoOperations mongoOperations) {
    this.mongoOperations = mongoOperations;
  }

  @Override
  public Measure findAndUpdateValidationStatus(
      String testCaseId, String measureId, TestCaseValidationStatus status) {
    Query query = new Query();
    query.addCriteria(Criteria.where("_id").is(measureId).and("testCases._id").is(testCaseId));

    Update update = new Update();
    update.set("testCases.$.testCaseValidationStatus", status);

    return mongoOperations.findAndModify(query, update, Measure.class);
  }

  @Override
  public Measure findAndUpdateValidationResults(
      String testCaseId, String measureId, HapiOperationOutcome validationResults) {
    Query query = new Query();
    query.addCriteria(Criteria.where("_id").is(measureId).and("testCases._id").is(testCaseId));

    Update update = new Update();
    update.set(
        "testCases.$.testCaseValidationStatus",
        validationResults.isSuccessful()
            ? TestCaseValidationStatus.VALID
            : TestCaseValidationStatus.INVALID);
    update.set("testCases.$.hapiOperationOutcome", validationResults);

    return mongoOperations.findAndModify(query, update, Measure.class);
  }
}
