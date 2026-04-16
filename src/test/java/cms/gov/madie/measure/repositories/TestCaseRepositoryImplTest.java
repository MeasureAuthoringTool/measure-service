package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.HapiOperationOutcome;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.TestCaseValidationStatus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestCaseRepositoryImplTest {
  @InjectMocks private TestCaseRepositoryImpl testCaseRepository;

  @Mock private MongoOperations mongoOperations;

  @Test
  void addOrUpdateTestCaseShouldUpdateExistingTestCase() {
    String measureId = "measure123";
    TestCase testCase = new TestCase();
    testCase.setId(new ObjectId().toString());

    Measure mockUpdatedMeasure = new Measure();
    when(mongoOperations.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class)))
        .thenReturn(mockUpdatedMeasure);

    Measure result = testCaseRepository.addOrUpdateTestCase(measureId, testCase);

    assertNotNull(result);
    verify(mongoOperations, times(1))
        .findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class));
  }

  @Test
  void addOrUpdateTestCaseShouldInsertNewTestCaseIfUpdateFails() {
    String measureId = "measure123";
    TestCase testCase = new TestCase();
    testCase.setId(new ObjectId().toString());

    when(mongoOperations.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class)))
        .thenReturn(null) // first update returns null
        .thenReturn(new Measure()); // second insert returns measure

    Measure result = testCaseRepository.addOrUpdateTestCase(measureId, testCase);

    assertNotNull(result);
    verify(mongoOperations, times(2))
        .findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class));
  }

  @Test
  void addOrUpdateTestCaseShouldReturnNullIfBothUpdateAndInsertFail() {
    String measureId = "measure123";
    TestCase testCase = new TestCase();
    testCase.setId(new ObjectId().toString());

    when(mongoOperations.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class)))
        .thenReturn(null); // both attempts fail

    Measure result = testCaseRepository.addOrUpdateTestCase(measureId, testCase);

    assertNull(result);
    verify(mongoOperations, times(2))
        .findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class));
  }

  @Test
  void setValidationStatusToPendingShouldUpdateStatus() {
    String testCaseId = new ObjectId().toString();
    String measureId = "measure123";
    Measure mockMeasure = new Measure();

    when(mongoOperations.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class)))
        .thenReturn(mockMeasure);

    Measure result = testCaseRepository.setValidationStatusToPending(testCaseId, measureId);

    assertNotNull(result);
    verify(mongoOperations, times(1))
        .findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class));
  }

  @Test
  void setValidationStatusToValidatingShouldUpdateStatusAndTaskId() {
    String testCaseId = new ObjectId().toString();
    String measureId = "measure123";
    UUID taskId = UUID.randomUUID();
    Measure mockMeasure = new Measure();

    when(mongoOperations.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class)))
        .thenReturn(mockMeasure);

    Measure result =
        testCaseRepository.setValidationStatusToValidating(testCaseId, measureId, taskId);

    assertNotNull(result);
    verify(mongoOperations, times(1))
        .findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class));
  }

  @Test
  void setValidationStatusToNotCompleteShouldUpdateStatus() {
    String testCaseId = new ObjectId().toString();
    String measureId = "measure123";

    when(mongoOperations.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class)))
        .thenReturn(new Measure());

    assertDoesNotThrow(
        () ->
            testCaseRepository.setValidationStatusToNotComplete(
                testCaseId, measureId, TestCaseValidationStatus.INVALID));

    verify(mongoOperations, times(1))
        .findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class));
  }

  @Test
  void testCaseSetIdExistsInSetShouldReturnTrueWhenExists() {
    String measureSetId = "measureSet123";

    when(mongoOperations.exists(any(Query.class), eq(Measure.class))).thenReturn(true);

    boolean result = testCaseRepository.testCaseSetIdExistsInSet(measureSetId);

    assertTrue(result);
    verify(mongoOperations, times(1)).exists(any(Query.class), eq(Measure.class));
  }

  @Test
  void testCaseSetIdExistsInSetShouldReturnFalseWhenNotExists() {
    String measureSetId = "measureSet123";

    when(mongoOperations.exists(any(Query.class), eq(Measure.class))).thenReturn(false);

    boolean result = testCaseRepository.testCaseSetIdExistsInSet(measureSetId);

    assertFalse(result);
    verify(mongoOperations, times(1)).exists(any(Query.class), eq(Measure.class));
  }

  @Test
  void findAndUpdateValidationResultsShouldUpdateValidationResult() {
    String testCaseId = new ObjectId().toString();
    String measureId = "measure123";
    UUID taskId = UUID.randomUUID();
    HapiOperationOutcome outcome = HapiOperationOutcome.builder().successful(true).build();

    when(mongoOperations.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class)))
        .thenReturn(new Measure());

    Measure result =
        testCaseRepository.findAndUpdateValidationResults(testCaseId, measureId, taskId, outcome);

    assertNotNull(result);
    verify(mongoOperations, times(1))
        .findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Measure.class));
  }
}
