package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeleteSDEAndRAVFromMeasureMetaDataChangeUnitTest {
  @Mock private MeasureRepository measureRepository;
  @Mock private MongoOperations mongoOperations;

  @Test
  public void testDeleteSDEAndRAVFromMeasureMetaData() {
    Query query = new Query(Criteria.where("model").is(ModelType.QI_CORE.getValue()));
    Update update =
        new Update()
            .unset("measureMetaData.riskAdjustment")
            .unset("measureMetaData.supplementalDataElements");
    BulkOperations bulkOperations = mock(BulkOperations.class);

    when(mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED, Measure.class))
        .thenReturn(bulkOperations);

    new DeleteSDEAndRAVFromMeasureMetaDataChangeUnit()
        .deleteSDEAndRAVFromMeasureMetaData(mongoOperations);

    verify(bulkOperations, new Times(1)).updateMulti(query, update);
    verify(bulkOperations, new Times(1)).execute();
  }

  @Test
  void testRollback() {
    new DeleteSDEAndRAVFromMeasureMetaDataChangeUnit().rollbackExecution();
    verifyNoInteractions(measureRepository);
  }
}
