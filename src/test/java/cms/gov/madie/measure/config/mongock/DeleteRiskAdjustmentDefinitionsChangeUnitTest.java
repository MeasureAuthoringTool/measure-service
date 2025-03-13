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
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
public class DeleteRiskAdjustmentDefinitionsChangeUnitTest {
  @Mock private MeasureRepository measureRepository;
  @Mock private MongoOperations mongoOperations;

  @Test
  public void testDeleteRiskAdjustmentsDescriptions() {
    Query query = new Query(Criteria.where("riskAdjustments").exists(true));
    Update update = new Update().unset("riskAdjustments.$[].description");
    BulkOperations bulkOperations = mock(BulkOperations.class);

    when(mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED, Measure.class))
        .thenReturn(bulkOperations);

    new DeleteRiskAdjustmentsDescriptionsChangeUnit().deleteAdjustmentDescriptions(mongoOperations);

    verify(bulkOperations, new Times(1)).updateMulti(query, update);
    verify(bulkOperations, new Times(1)).execute();
  }

  @Test
  void testRollback() {
    new DeleteRiskAdjustmentsDescriptionsChangeUnit().rollbackExecution();
    verifyNoInteractions(measureRepository);
  }
}
