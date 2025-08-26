package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.HapiOperationOutcome;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.TestCaseValidationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Slf4j
@Repository
public class TestCaseRepositoryImpl implements TestCaseRepository {
  private final MongoOperations mongoOperations;

  private static final FindAndModifyOptions RETURN_NEW_OPTIONS =
      FindAndModifyOptions.options().returnNew(true);

  public TestCaseRepositoryImpl(MongoOperations mongoOperations) {
    this.mongoOperations = mongoOperations;
  }

  /**
   * Adds a new TestCase to a Measure or updates an existing TestCase within a Measure. If the
   * TestCase with the given ID exists, it is replaced. If no matching TestCase exists, a new
   * TestCase is appended to the Measure's testCases array.
   *
   * @param measureId The ID of the Measure to update.
   * @param testCase The TestCase to add or update.
   * @return The updated Measure object after the operation.
   */
  @Override
  public Measure addOrUpdateTestCase(String measureId, TestCase testCase) {
    // Try updating existing test case
    Query updateQuery =
        new Query(Criteria.where("_id").is(measureId).and("testCases._id").is(testCase.getId()));

    Update update = new Update().set("testCases.$", testCase);

    Measure updatedMeasure =
        mongoOperations.findAndModify(updateQuery, update, RETURN_NEW_OPTIONS, Measure.class);
    if (updatedMeasure == null) {
      // Push as new test case
      Query pushQuery = new Query(Criteria.where("_id").is(measureId));
      Update pushUpdate = new Update().push("testCases", testCase);
      updatedMeasure =
          mongoOperations.findAndModify(pushQuery, pushUpdate, RETURN_NEW_OPTIONS, Measure.class);
    }
    return updatedMeasure;
  }

  /**
   * Sets the validation status of a specific TestCase within a Measure to PENDING. This only
   * applies if the current status is not already PENDING.
   *
   * @param testCaseId The ID of the TestCase to update.
   * @param measureId The ID of the Measure containing the TestCase.
   * @return The updated Measure object after the operation.
   */
  @Override
  public Measure setValidationStatusToPending(String testCaseId, String measureId) {
    Query query = new Query();
    query.addCriteria(
        Criteria.where("_id")
            .is(measureId)
            .and("testCases")
            .elemMatch(
                Criteria.where("_id")
                    .is(testCaseId)
                    .and("validationStatus")
                    .ne(TestCaseValidationStatus.PENDING.toString())));

    Update update = new Update();
    update.set("testCases.$.validationStatus", TestCaseValidationStatus.PENDING.toString());

    return mongoOperations.findAndModify(query, update, RETURN_NEW_OPTIONS, Measure.class);
  }

  /**
   * Sets the validation status of a specific TestCase within a Measure to VALIDATING and assigns a
   * validation task ID. This only applies if the current status is PENDING.
   *
   * @param testCaseId The ID of the TestCase to update.
   * @param measureId The ID of the Measure containing the TestCase.
   * @param taskId The UUID representing the validation task ID.
   * @return The updated Measure object after the operation.
   */
  @Override
  public Measure setValidationStatusToValidating(String testCaseId, String measureId, UUID taskId) {
    Query query = new Query();
    query.addCriteria(
        Criteria.where("_id")
            .is(measureId)
            .and("testCases")
            .elemMatch(
                Criteria.where("_id")
                    .is(testCaseId)
                    .and("validationStatus")
                    .is(TestCaseValidationStatus.PENDING.toString())));

    Update update = new Update();
    update.set("testCases.$.validationStatus", TestCaseValidationStatus.VALIDATING.toString());
    // Save taskId to identify most recent validation request.
    update.set("testCases.$.validationTaskId", taskId.toString());

    return mongoOperations.findAndModify(query, update, RETURN_NEW_OPTIONS, Measure.class);
  }

  /**
   * Updates the validation status of a specific TestCase within a Measure to the provided status.
   * This is typically used to mark incomplete or failed validations.
   *
   * @param testCaseId The ID of the TestCase to update.
   * @param measureId The ID of the Measure containing the TestCase.
   * @param status The new validation status to set.
   */
  @Override
  public void setValidationStatusToNotComplete(
      String testCaseId, String measureId, TestCaseValidationStatus status) {
    Query query = new Query();
    query.addCriteria(Criteria.where("_id").is(measureId).and("testCases._id").is(testCaseId));

    Update update = new Update();
    update.set("testCases.$.validationStatus", status.toString());

    mongoOperations.findAndModify(query, update, RETURN_NEW_OPTIONS, Measure.class);
  }

  /**
   * Updates the validation results of a specific TestCase within a Measure after HAPI FHIR
   * validation completes. This only applies if the current status is VALIDATING and the taskId
   * matches.
   *
   * @param testCaseId The ID of the TestCase to update.
   * @param measureId The ID of the Measure containing the TestCase.
   * @param taskId The UUID representing the validation task ID.
   * @param validationResults The validation results returned by HAPI FHIR.
   * @return The updated Measure object after the operation.
   */
  @Override
  public Measure findAndUpdateValidationResults(
      String testCaseId, String measureId, UUID taskId, HapiOperationOutcome validationResults) {
    Query query = new Query();
    query.addCriteria(
        Criteria.where("_id")
            .is(measureId)
            .and("testCases")
            .elemMatch(
                Criteria.where("_id")
                    .is(testCaseId)
                    .and("validationStatus")
                    .is(TestCaseValidationStatus.VALIDATING.toString())
                    .and("validationTaskId")
                    .is(taskId.toString())));

    Update update = new Update();
    update.set(
        "testCases.$.validationStatus",
        validationResults.isSuccessful()
            ? TestCaseValidationStatus.VALID.toString()
            : TestCaseValidationStatus.INVALID.toString());
    update.set("testCases.$.validResource", validationResults.isSuccessful());
    update.set("testCases.$.hapiOperationOutcome", validationResults);

    return mongoOperations.findAndModify(query, update, RETURN_NEW_OPTIONS, Measure.class);
  }
}
