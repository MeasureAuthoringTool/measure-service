package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Measure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.internal.verification.Times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
public class DeleteAclsFromMeasureChangeUnitTest {
  @Mock private MeasureRepository measureRepository;
  @Mock private MongoOperations mongoOperations;

  @Test
  public void testDeleteAclFieldFromMeasure() {
    Query query = new Query();
    Update update = new Update().unset("acls");
    BulkOperations bulkOperations = mock(BulkOperations.class);

    when(mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED, Measure.class))
        .thenReturn(bulkOperations);

    new DeleteMeasureAclsFromMeasureChangeUnit().deleteAclsFromMeasure(mongoOperations);

    verify(bulkOperations, new Times(1)).updateMulti(query, update);
    verify(bulkOperations, new Times(1)).execute();
  }

  @Test
  void testRollback() {
    new DeleteMeasureAclsFromMeasureChangeUnit().rollbackExecution();
    verifyNoInteractions(measureRepository);
  }
}
