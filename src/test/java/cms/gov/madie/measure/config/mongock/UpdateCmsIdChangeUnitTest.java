package cms.gov.madie.measure.config.mongock;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdateCmsIdChangeUnitTest {
  @Mock private MongoTemplate mongoTemplate;

  @Test
  void testUpdateCmsIdChangeUnit() {
    var measureMeta1 =
        UpdateCmsIdChangeUnit.MeasureMeta.builder()
            .id("Measure1")
            .measureName("Measure 1")
            .measureSetId("124-set")
            .cmsId("124FHIR")
            .build();

    var measureMeta2 =
        UpdateCmsIdChangeUnit.MeasureMeta.builder()
            .id("Measure2")
            .measureName("Measure 2")
            .measureSetId("124-qdm-set")
            .cmsId("124")
            .build();

    var measureMeta3 =
        UpdateCmsIdChangeUnit.MeasureMeta.builder()
            .id("Measure3")
            .measureName("Measure 3")
            .measureSetId("125-set")
            .cmsId("125")
            .build();

    AggregationResults<UpdateCmsIdChangeUnit.MeasureMeta> results =
        new AggregationResults<>(List.of(measureMeta1, measureMeta2, measureMeta3), new Document());

    when(mongoTemplate.aggregate(
            (TypedAggregation<?>) any(Aggregation.class),
            (Class<UpdateCmsIdChangeUnit.MeasureMeta>) any()))
        .thenReturn(results);

    BulkOperations bulkOperations = mock(BulkOperations.class);
    when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq("measureSet")))
        .thenReturn(bulkOperations);
    new UpdateCmsIdChangeUnit().updateCmsId(mongoTemplate);
    verify(bulkOperations, new Times(1)).updateMulti(any());
    verify(bulkOperations, new Times(1)).execute();
  }

  @Test
  void testUpdateCmsIdChangeUnitWhenNoMeasureWithCmsIdFoundToUpdate() {
    AggregationResults<UpdateCmsIdChangeUnit.MeasureMeta> results =
        new AggregationResults<>(List.of(), new Document());
    when(mongoTemplate.aggregate(
            (TypedAggregation<?>) any(Aggregation.class),
            (Class<UpdateCmsIdChangeUnit.MeasureMeta>) any()))
        .thenReturn(results);
    BulkOperations bulkOperations = mock(BulkOperations.class);
    new UpdateCmsIdChangeUnit().updateCmsId(mongoTemplate);
    verifyNoInteractions(bulkOperations);
  }

  @Test
  void testRollbackUpdateCmsIdChangeUnit() {
    BulkOperations bulkOperations = mock(BulkOperations.class);
    when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq("measureSet")))
        .thenReturn(bulkOperations);
    new UpdateCmsIdChangeUnit().rollbackExecution(mongoTemplate);
    verify(bulkOperations, new Times(1)).updateMulti(any(), any());
    verify(bulkOperations, new Times(1)).execute();
  }
}
