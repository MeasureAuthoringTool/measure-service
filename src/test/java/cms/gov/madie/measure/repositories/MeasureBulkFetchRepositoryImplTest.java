package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.MeasureListDTO;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MeasureBulkFetchRepositoryImplTest {

  // Valid 24-char hex ObjectId strings
  private static final String ID1 = "507f1f77bcf86cd799439011";
  private static final String ID2 = "507f1f77bcf86cd799439012";
  private static final String ID3 = "507f1f77bcf86cd799439013";
  private static final String ID_NONEXISTENT_1 = "aaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String ID_NONEXISTENT_2 = "bbbbbbbbbbbbbbbbbbbbbbbb";

  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks private MeasureBulkFetchRepositoryImpl repository;

  private MeasureListDTO measure1;
  private MeasureListDTO measure2;
  private MeasureListDTO measure3;

  @BeforeEach
  void setup() {
    MeasureSet measureSet1 = MeasureSet.builder().id("set1").owner("user1").build();
    MeasureSet measureSet2 = MeasureSet.builder().id("set2").owner("user2").build();
    MeasureSet measureSet3 = MeasureSet.builder().id("set3").owner("user3").build();

    measure1 =
        MeasureListDTO.builder()
            .id(ID1)
            .measureName("Test Measure 1")
            .measureSetId("set1")
            .measureSet(measureSet1)
            .build();

    measure2 =
        MeasureListDTO.builder()
            .id(ID2)
            .measureName("Test Measure 2")
            .measureSetId("set2")
            .measureSet(measureSet2)
            .build();

    measure3 =
        MeasureListDTO.builder()
            .id(ID3)
            .measureName("Test Measure 3")
            .measureSetId("set3")
            .measureSet(measureSet3)
            .build();
  }

  @Test
  void findAllByIdInWithMeasureSetReturnsListWithMeasureSetsPopulated() {
    List<String> measureIds = List.of(ID1, ID2, ID3);

    AggregationResults<MeasureListDTO> aggregationResults =
        new AggregationResults<>(List.of(measure1, measure2, measure3), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(Measure.class), eq(MeasureListDTO.class)))
        .thenReturn(aggregationResults);

    List<MeasureListDTO> result = repository.findAllByIdInWithMeasureSet(measureIds);

    assertNotNull(result);
    assertEquals(3, result.size());

    // Verify measures are returned with IDs
    assertEquals(ID1, result.get(0).getId());
    assertEquals(ID2, result.get(1).getId());
    assertEquals(ID3, result.get(2).getId());

    // Verify measureSets are populated (not null)
    assertNotNull(result.get(0).getMeasureSet());
    assertNotNull(result.get(1).getMeasureSet());
    assertNotNull(result.get(2).getMeasureSet());

    // Verify measureSet details
    assertEquals("set1", result.get(0).getMeasureSet().getId());
    assertEquals("user1", result.get(0).getMeasureSet().getOwner());

    // Verify aggregation was called once
    verify(mongoTemplate, times(1))
        .aggregate(any(Aggregation.class), eq(Measure.class), eq(MeasureListDTO.class));
  }

  @Test
  void findAllByIdInWithMeasureSetWithEmptyListReturnsEmptyList() {
    List<MeasureListDTO> result = repository.findAllByIdInWithMeasureSet(Collections.emptyList());

    assertNotNull(result);
    assertTrue(result.isEmpty());

    // Verify no database call was made
    verifyNoInteractions(mongoTemplate);
  }

  @Test
  void findAllByIdInWithMeasureSetWithNullListReturnsEmptyList() {
    List<MeasureListDTO> result = repository.findAllByIdInWithMeasureSet(null);

    assertNotNull(result);
    assertTrue(result.isEmpty());

    // Verify no database call was made
    verifyNoInteractions(mongoTemplate);
  }

  @Test
  void findAllByIdInWithMeasureSetWithNoMatchingMeasuresReturnsEmptyList() {
    List<String> measureIds = List.of(ID_NONEXISTENT_1, ID_NONEXISTENT_2);

    AggregationResults<MeasureListDTO> aggregationResults =
        new AggregationResults<>(Collections.emptyList(), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(Measure.class), eq(MeasureListDTO.class)))
        .thenReturn(aggregationResults);

    List<MeasureListDTO> result = repository.findAllByIdInWithMeasureSet(measureIds);

    assertNotNull(result);
    assertTrue(result.isEmpty());

    verify(mongoTemplate, times(1))
        .aggregate(any(Aggregation.class), eq(Measure.class), eq(MeasureListDTO.class));
  }

  @Test
  void findAllByIdInWithMeasureSetWithOnlyNullIdsReturnsEmptyList() {
    // Collection is non-empty but all entries are null → uniqueIds will be empty after filtering
    List<String> measureIds = new ArrayList<>();
    measureIds.add(null);
    measureIds.add(null);

    List<MeasureListDTO> result = repository.findAllByIdInWithMeasureSet(measureIds);

    assertNotNull(result);
    assertTrue(result.isEmpty());

    // Verify no database call was made
    verifyNoInteractions(mongoTemplate);
  }

  @Test
  void findAllByIdInWithMeasureSetWithDuplicateIdsDeduplicatesBeforeQuery() {
    // Provide duplicate IDs; the aggregation should still be called once
    List<String> measureIds = List.of(ID1, ID1, ID2);

    AggregationResults<MeasureListDTO> aggregationResults =
        new AggregationResults<>(List.of(measure1, measure2), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(Measure.class), eq(MeasureListDTO.class)))
        .thenReturn(aggregationResults);

    List<MeasureListDTO> result = repository.findAllByIdInWithMeasureSet(measureIds);

    assertNotNull(result);
    assertEquals(2, result.size());

    verify(mongoTemplate, times(1))
        .aggregate(any(Aggregation.class), eq(Measure.class), eq(MeasureListDTO.class));
  }
}
