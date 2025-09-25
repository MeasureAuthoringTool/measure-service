package cms.gov.madie.measure.config.mongock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateMeasureOrganizationRecordNameChangeUnitTest {

  @Mock private MongoTemplate mongoTemplate;

  @Test
  void updatesOrganizationNameWhenMatchingRecordExists() {
    Query query = new Query(Criteria.where("name").is("The Joint Commission"));
    Update update = new Update().set("name", "Joint Commission");

    when(mongoTemplate.updateFirst(query, update, "organization")).thenReturn(null);

    new UpdateMeasureOrganizationRecordNameChangeUnit()
        .updateMeasureOrganizationRecordName(mongoTemplate);

    verify(mongoTemplate, times(1)).updateFirst(query, update, "organization");
  }

  @Test
  void doesNotUpdateOrganizationNameWhenNoMatchingRecordExists() {
    Query query = new Query(Criteria.where("name").is("The Joint Commission"));
    Update update = new Update().set("name", "Joint Commission");

    when(mongoTemplate.updateFirst(query, update, "organization")).thenReturn(null);

    new UpdateMeasureOrganizationRecordNameChangeUnit()
        .updateMeasureOrganizationRecordName(mongoTemplate);

    verify(mongoTemplate, times(1)).updateFirst(query, update, "organization");
  }

  @Test
  void handlesEmptyCollectionGracefully() {
    Query query = new Query(Criteria.where("name").is("The Joint Commission"));
    Update update = new Update().set("name", "Joint Commission");

    when(mongoTemplate.updateFirst(query, update, "organization")).thenReturn(null);

    new UpdateMeasureOrganizationRecordNameChangeUnit()
        .updateMeasureOrganizationRecordName(mongoTemplate);

    verify(mongoTemplate, times(1)).updateFirst(query, update, "organization");
  }
}
