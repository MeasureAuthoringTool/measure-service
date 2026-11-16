package cms.gov.madie.measure.config.mongock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import gov.cms.madie.models.measure.Measure;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class AddSdeAndRavIdsChangeUnitTest {
  private static final String COLLECTION = "measure";

  @Mock private MongoTemplate mongoTemplate;
  @Captor private ArgumentCaptor<Query> queryCaptor;
  @Captor private ArgumentCaptor<Update> updateCaptor;

  private AddSdeAndRavIdsChangeUnit changeUnit;

  @BeforeEach
  void setUp() {
    changeUnit = new AddSdeAndRavIdsChangeUnit();
    when(mongoTemplate.getCollectionName(Measure.class)).thenReturn(COLLECTION);
  }

  @Test
  void testAddsIdsOnlyToSdesAndRavsWithoutIds() {
    ObjectId measureId = new ObjectId();
    ObjectId existingSdeId = new ObjectId();
    Document measure =
        new Document("_id", measureId)
            .append(
                "supplementalData",
                List.of(
                    new Document("definition", "SDE Ethnicity"),
                    new Document("_id", existingSdeId).append("definition", "SDE Sex")))
            .append("riskAdjustments", List.of(new Document("definition", "Risk Age")));
    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq(COLLECTION)))
        .thenReturn(Stream.of(measure));
    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION)))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));

    changeUnit.addSdeAndRavIds(mongoTemplate);

    verify(mongoTemplate)
        .updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(COLLECTION));
    Document set = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
    assertThat(set.keySet().size(), is(2));
    assertThat(set.get("supplementalData.0._id"), instanceOf(ObjectId.class));
    assertThat(set.get("riskAdjustments.0._id"), instanceOf(ObjectId.class));
    assertThat(set, not(hasKey("supplementalData.1._id")));

    Document query = queryCaptor.getValue().getQueryObject();
    assertThat(query.get("_id"), is(measureId));
    assertThat(query.get("supplementalData.0"), is(new Document("$exists", true)));
    assertThat(query.get("supplementalData.0._id"), is(nullValue()));
    assertThat(query, hasKey("supplementalData.0._id"));
    assertThat(query, not(hasKey("supplementalData.1._id")));
    assertThat(query.get("riskAdjustments.0"), is(new Document("$exists", true)));
    assertThat(query, hasKey("riskAdjustments.0._id"));

    assertThat(changeUnit.getRollbackUpdates().size(), is(1));
  }

  @Test
  void testSkipsInvalidEntriesAndMeasuresWithNothingToUpdate() {
    List<Object> sdes = new ArrayList<>(Arrays.asList((Object) null));
    Document measure =
        new Document("_id", new ObjectId())
            .append("supplementalData", sdes)
            .append(
                "riskAdjustments",
                List.of(new Document("_id", new ObjectId()).append("definition", "Risk Age")));
    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq(COLLECTION)))
        .thenReturn(Stream.of(measure));

    changeUnit.addSdeAndRavIds(mongoTemplate);

    verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), anyString());
    assertThat(changeUnit.getRollbackUpdates().isEmpty(), is(true));
  }

  @Test
  void testSkipsMeasureModifiedDuringMigration() {
    Document measure =
        new Document("_id", new ObjectId())
            .append("supplementalData", List.of(new Document("definition", "SDE Ethnicity")));
    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq(COLLECTION)))
        .thenReturn(Stream.of(measure));
    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION)))
        .thenReturn(UpdateResult.acknowledged(0, 0L, null));

    changeUnit.addSdeAndRavIds(mongoTemplate);

    verify(mongoTemplate, times(1))
        .updateFirst(any(Query.class), any(Update.class), eq(COLLECTION));
    assertThat(changeUnit.getRollbackUpdates().isEmpty(), is(true));
  }

  @Test
  void testContinuesWithOtherMeasuresWhenAnUpdateFails() {
    Document failing =
        new Document("_id", new ObjectId())
            .append("supplementalData", List.of(new Document("definition", "SDE Ethnicity")));
    Document succeeding =
        new Document("_id", new ObjectId())
            .append("riskAdjustments", List.of(new Document("definition", "Risk Age")));
    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq(COLLECTION)))
        .thenReturn(Stream.of(failing, succeeding));
    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION)))
        .thenThrow(new RuntimeException("write failed"))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));

    changeUnit.addSdeAndRavIds(mongoTemplate);

    verify(mongoTemplate, times(2))
        .updateFirst(any(Query.class), any(Update.class), eq(COLLECTION));
    assertThat(changeUnit.getRollbackUpdates().size(), is(1));
    assertThat(
        changeUnit.getRollbackUpdates().get(0).getFirst().getQueryObject().get("_id"),
        is(succeeding.get("_id")));
  }

  @Test
  void testNoMeasuresToUpdate() {
    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq(COLLECTION)))
        .thenReturn(Stream.empty());

    changeUnit.addSdeAndRavIds(mongoTemplate);

    verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), anyString());
  }

  @Test
  void testRollbackRemovesAddedIds() {
    Document measure =
        new Document("_id", new ObjectId())
            .append("supplementalData", List.of(new Document("definition", "SDE Ethnicity")));
    when(mongoTemplate.stream(any(Query.class), eq(Document.class), eq(COLLECTION)))
        .thenReturn(Stream.of(measure));
    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION)))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    changeUnit.addSdeAndRavIds(mongoTemplate);

    changeUnit.rollbackExecution(mongoTemplate);

    verify(mongoTemplate, times(2))
        .updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(COLLECTION));
    Update rollback = updateCaptor.getAllValues().get(1);
    Document unset = (Document) rollback.getUpdateObject().get("$unset");
    assertThat(unset, hasKey("supplementalData.0._id"));
    Document rollbackQuery = queryCaptor.getAllValues().get(1).getQueryObject();
    assertThat(rollbackQuery.get("supplementalData.0._id"), instanceOf(ObjectId.class));
  }
}
