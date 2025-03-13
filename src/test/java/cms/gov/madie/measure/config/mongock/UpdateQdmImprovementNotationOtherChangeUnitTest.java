package cms.gov.madie.measure.config.mongock;

import com.mongodb.bulk.BulkWriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdateQdmImprovementNotationOtherChangeUnitTest {
  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks UpdateQdmImprovementNotationOtherChangeUnit changeUnit;

  private BulkOperations bulkOperations;

  @BeforeEach
  void setup() {
    bulkOperations = mock(BulkOperations.class);
    BulkWriteResult result = mock(BulkWriteResult.class);

    when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq("measure")))
        .thenReturn(bulkOperations);
    when(bulkOperations.execute()).thenReturn(result);
  }

  @Test
  void testUpdateQdmMeasureFields() {
    changeUnit.updateImprovementNotationOtherField(mongoTemplate);
    verify(bulkOperations, new Times(1)).updateMulti(any(Query.class), any(Update.class));
    verify(bulkOperations, new Times(1)).execute();
  }

  @Test
  void testRollbackExecution() {
    changeUnit.rollbackExecution(mongoTemplate);
    verify(bulkOperations, new Times(1)).updateMulti(any(Query.class), any(Update.class));
    verify(bulkOperations, new Times(1)).execute();
  }
}
