package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCaseConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.ExecutableUpdateOperation;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@ExtendWith(MockitoExtension.class)
class TestCasePatchRepositoryImplTest {

  @Mock private MongoOperations mongoOperations;

  @Mock private ExecutableUpdateOperation.ExecutableUpdate<Measure> executableUpdateMock;
  @Mock private ExecutableUpdateOperation.UpdateWithUpdate<Measure> executableUpdateWithUpdateMock;

  @Mock
  private ExecutableUpdateOperation.TerminatingUpdate<Measure> executableUpdateWithTerminatingMock;

  @Mock
  private ExecutableUpdateOperation.TerminatingFindAndModify<Measure>
      executableUpdateWithTerminatingFindAndModifyMock;

  @InjectMocks private TestCasePatchRepositoryImpl repository;

  @Test
  void testFindAndModifyTestCaseConfigShouldReturnUpdatedMeasure() {

    String measureId = "123";
    TestCaseConfiguration testCaseConfiguration =
        TestCaseConfiguration.builder().sdeIncluded(true).build();
    Measure expectedMeasure = new Measure();

    when(mongoOperations.update(Measure.class)).thenReturn(executableUpdateMock);
    when(executableUpdateMock.matching(query(where("_id").is(measureId))))
        .thenReturn(executableUpdateWithUpdateMock);
    when(executableUpdateWithUpdateMock.apply(any(Update.class)))
        .thenReturn(executableUpdateWithTerminatingMock);
    when(executableUpdateWithTerminatingMock.withOptions(any(FindAndModifyOptions.class)))
        .thenReturn(executableUpdateWithTerminatingFindAndModifyMock);
    when(executableUpdateWithTerminatingFindAndModifyMock.findAndModifyValue())
        .thenReturn(expectedMeasure);

    Measure result = repository.findAndModifyTestCaseConfig(testCaseConfiguration, measureId);

    assertNotNull(result);
    assertEquals(expectedMeasure, result);
  }

  @Test
  void testFindAndModifyTestCaseConfig_ShouldReturnNullWhenNoDocumentFound() {
    String measureId = "123";
    TestCaseConfiguration testCaseConfiguration =
        TestCaseConfiguration.builder().sdeIncluded(true).build();

    when(mongoOperations.update(Measure.class)).thenReturn(executableUpdateMock);
    when(executableUpdateMock.matching(query(where("_id").is(measureId))))
        .thenReturn(executableUpdateWithUpdateMock);
    when(executableUpdateWithUpdateMock.apply(any(Update.class)))
        .thenReturn(executableUpdateWithTerminatingMock);
    when(executableUpdateWithTerminatingMock.withOptions(any(FindAndModifyOptions.class)))
        .thenReturn(executableUpdateWithTerminatingFindAndModifyMock);
    when(executableUpdateWithTerminatingFindAndModifyMock.findAndModifyValue()).thenReturn(null);

    Measure result = repository.findAndModifyTestCaseConfig(testCaseConfiguration, measureId);

    assertNull(result);
  }
}
