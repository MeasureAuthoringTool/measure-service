package cms.gov.madie.measure.config.mongock;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.query.Criteria;
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
  @Mock private BulkOperations bulkOperations;

  private Update update = new Update().unset("supplementalData.$[].description");
  private DeleteSupplementalDataDescriptionChangeUnit changeUnit =
      new DeleteSupplementalDataDescriptionChangeUnit();

  @Test
  public void testDeleteSdeDescriptionFromDefDescPair() {
    Query query = new Query(Criteria.where("supplementalData").exists(true));

    when(mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED, Measure.class))
        .thenReturn(bulkOperations);

    changeUnit.deleteSdeDescriptionFromDefDescPair(mongoOperations);

    verify(bulkOperations, new Times(1)).updateMulti(query, update);
    verify(bulkOperations, new Times(1)).execute();
  }

  @Test
  public void testDeleteSdeDescriptionNoExecution() {
    Query query = new Query(Criteria.where("supplementalData").exists(false));

    when(mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED, Measure.class))
        .thenReturn(bulkOperations);

    changeUnit.deleteSdeDescriptionFromDefDescPair(mongoOperations);

    verify(bulkOperations, new Times(0)).updateMulti(query, update);
  }

  @Test
  void testRollback() {
    changeUnit.rollbackExecution();
    verifyNoInteractions(measureRepository);
  }
}
