package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.HapiOperationOutcome;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCaseValidationStatus;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class TestCaseRepositoryImpl implements TestCaseRepository {
  private final MongoOperations mongoOperations;

  public TestCaseRepositoryImpl(MongoOperations mongoOperations) {
    this.mongoOperations = mongoOperations;
  }

  @Override
  public Measure setValidationStatusToPending(String testCaseId, String measureId) {
    Query query = new Query();
    query.addCriteria(
        Criteria.where("_id")
            .is(measureId)
            .and("testCases._id")
            .is(testCaseId)
            .and("testCases.testCaseValidationStatus")
            .ne(TestCaseValidationStatus.PENDING.toString()));

    Update update = new Update();
    update.set("testCases.$.testCaseValidationStatus", TestCaseValidationStatus.PENDING.toString());

    return mongoOperations.findAndModify(
        query, update, FindAndModifyOptions.options().returnNew(true), Measure.class);
  }

  @Override
  public Measure setValidationStatusToValidating(String testCaseId, String measureId, UUID taskId) {
    Query query = new Query();
    query.addCriteria(Criteria.where("_id").is(measureId).and("testCases._id").is(testCaseId));
    query.addCriteria(
        Criteria.where("testCases.testCaseValidationStatus")
            .is(TestCaseValidationStatus.PENDING.toString()));

    Update update = new Update();
    update.set(
        "testCases.$.testCaseValidationStatus", TestCaseValidationStatus.VALIDATING.toString());
    // Save taskId to identify most recent validation request.
    update.set("testCases.$.taskId", taskId.toString());

    return mongoOperations.findAndModify(
        query, update, FindAndModifyOptions.options().returnNew(true), Measure.class);
  }

  @Override
  public void setValidationStatusToNotComplete(
      String testCaseId, String measureId, TestCaseValidationStatus status) {
    Query query = new Query();
    query.addCriteria(Criteria.where("_id").is(measureId).and("testCases._id").is(testCaseId));

    Update update = new Update();
    update.set("testCases.$.testCaseValidationStatus", status.toString());

    mongoOperations.findAndModify(
      query, update, FindAndModifyOptions.options().returnNew(true), Measure.class);
  }

  @Override
  public Measure findAndUpdateValidationResults(
      String testCaseId, String measureId, UUID taskId, HapiOperationOutcome validationResults) {
    Query query = new Query();
    query.addCriteria(
        Criteria.where("_id")
            .is(measureId)
            .and("testCases._id")
            .is(testCaseId)
            .and("testCases.testCaseValidationStatus")
            .is(TestCaseValidationStatus.VALIDATING.toString())
            .and("testCases.taskId")
            .is(taskId.toString()));

    Update update = new Update();
    update.set(
        "testCases.$.testCaseValidationStatus",
        validationResults.isSuccessful()
            ? TestCaseValidationStatus.VALID.toString()
            : TestCaseValidationStatus.INVALID.toString());
    update.set("testCases.$.hapiOperationOutcome", validationResults);

    return mongoOperations.findAndModify(
        query, update, FindAndModifyOptions.options().returnNew(true), Measure.class);
  }
}
