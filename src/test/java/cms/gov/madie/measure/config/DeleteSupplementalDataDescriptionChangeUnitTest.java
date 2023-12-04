package cms.gov.madie.measure.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.query.Update;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Measure;

@ExtendWith(MockitoExtension.class)
public class DeleteSupplementalDataDescriptionChangeUnitTest {

  @Mock private MongoOperations mongoOperations;
  @Mock private MeasureRepository measureRepository;

  @Test
  public void testDeleteSdeDescriptionFromDefDescPair() {
    Query query = new Query();
    Update update = new Update().unset("supplementalData.$[].description");
    BulkOperations bulkOperations = mock(BulkOperations.class);

    when(mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED, Measure.class))
        .thenReturn(bulkOperations);

    new DeleteSupplementalDataDescriptionChangeUnit()
        .deleteSdeDescriptionFromDefDescPair(mongoOperations);

    verify(bulkOperations, new Times(1)).updateMulti(query, update);
    verify(bulkOperations, new Times(1)).execute();
  }

  @Test
  void testRollback() {
    new DeleteSupplementalDataDescriptionChangeUnit().rollbackExecution();
    verifyNoInteractions(measureRepository);
  }
}
