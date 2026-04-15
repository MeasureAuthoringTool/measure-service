package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.TestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AddTestCaseSetIdsChangeUnitTest {

  @Mock private MongoTemplate mongoTemplate;
  @Mock private MeasureRepository measureRepository;

  @InjectMocks private AddTestCaseSetIdsChangeUnit changeUnit;

  @SuppressWarnings("unchecked")
  private AggregationResults<Measure> mockAggregationResults(List<Measure> measures) {
    AggregationResults<Measure> results = mock(AggregationResults.class);
    lenient().when(results.getMappedResults()).thenReturn(measures);
    return results;
  }

  @Test
  void testAddTestCaseSetIds_happyPath_draftMeasure() {
    // A draft QICore measure with test cases that have no testCaseSetId
    String measureSetId = "setId-1";
    TestCase tc1 = TestCase.builder().id("tc1").build();
    TestCase tc2 = TestCase.builder().id("tc2").build();
    Measure draftMeasure =
        Measure.builder()
            .id("m1")
            .measureSetId(measureSetId)
            .model("QICore v4.1.1")
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .version(Version.builder().major(0).minor(0).revisionNumber(0).build())
            .testCases(new ArrayList<>(List.of(tc1, tc2)))
            .build();

    // Pre-create mock results to avoid nested stubbing
    AggregationResults<Measure> pageOne = mockAggregationResults(List.of(draftMeasure));
    AggregationResults<Measure> emptyPage = mockAggregationResults(List.of());

    when(mongoTemplate.aggregate(any(TypedAggregation.class), eq(Measure.class)))
        .thenReturn(pageOne)
        .thenReturn(emptyPage);

    // testCaseSetIdExistsInSet returns false
    when(measureRepository.testCaseSetIdExistsInSet(anyString())).thenReturn(false);

    when(mongoTemplate.save(any(Measure.class))).thenReturn(draftMeasure);

    changeUnit.addTestCaseSetIds(mongoTemplate, measureRepository);

    // Verify test cases got unique UUIDs assigned
    assertNotNull(tc1.getTestCaseSetId());
    assertNotNull(tc2.getTestCaseSetId());
    assertNotEquals(tc1.getTestCaseSetId(), tc2.getTestCaseSetId());

    // Verify save was called
    verify(mongoTemplate, times(1)).save(any(Measure.class));
  }

  @Test
  void testAddTestCaseSetIds_happyPath_latestVersionedMeasure() {
    // No draft exists; should pick the latest versioned measure
    String measureSetId = "setId-2";
    TestCase tc1 = TestCase.builder().id("tc1").build();
    Measure versionedMeasure =
        Measure.builder()
            .id("m2")
            .measureSetId(measureSetId)
            .model("QICore v6.0.0")
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .version(Version.builder().major(2).minor(1).revisionNumber(0).build())
            .testCases(new ArrayList<>(List.of(tc1)))
            .build();

    AggregationResults<Measure> pageOne = mockAggregationResults(List.of(versionedMeasure));
    AggregationResults<Measure> emptyPage = mockAggregationResults(List.of());

    when(mongoTemplate.aggregate(any(TypedAggregation.class), eq(Measure.class)))
        .thenReturn(pageOne)
        .thenReturn(emptyPage);
    when(measureRepository.testCaseSetIdExistsInSet(anyString())).thenReturn(false);
    when(mongoTemplate.save(any(Measure.class))).thenReturn(versionedMeasure);

    changeUnit.addTestCaseSetIds(mongoTemplate, measureRepository);

    assertNotNull(tc1.getTestCaseSetId());
    verify(mongoTemplate, times(1)).save(any(Measure.class));
  }

  @Test
  void testAddTestCaseSetIds_multipleMeasureSetIds() {
    TestCase tc1 = TestCase.builder().id("tc1").build();
    TestCase tc2 = TestCase.builder().id("tc2").build();

    Measure measure1 =
        Measure.builder()
            .id("m1")
            .measureSetId("setId-1")
            .model("QICore v4.1.1")
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .version(Version.builder().major(0).minor(0).revisionNumber(0).build())
            .testCases(new ArrayList<>(List.of(tc1)))
            .build();
    Measure measure2 =
        Measure.builder()
            .id("m2")
            .measureSetId("setId-2")
            .model("QICore v6.0.0")
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .testCases(new ArrayList<>(List.of(tc2)))
            .build();

    AggregationResults<Measure> pageOne = mockAggregationResults(List.of(measure1, measure2));
    AggregationResults<Measure> emptyPage = mockAggregationResults(List.of());

    when(mongoTemplate.aggregate(any(TypedAggregation.class), eq(Measure.class)))
        .thenReturn(pageOne)
        .thenReturn(emptyPage);
    when(measureRepository.testCaseSetIdExistsInSet(anyString())).thenReturn(false);
    when(mongoTemplate.save(any(Measure.class))).thenReturn(measure1).thenReturn(measure2);

    changeUnit.addTestCaseSetIds(mongoTemplate, measureRepository);

    assertNotNull(tc1.getTestCaseSetId());
    assertNotNull(tc2.getTestCaseSetId());
    verify(mongoTemplate, times(2)).save(any(Measure.class));
  }

  @Test
  void testAddTestCaseSetIds_noQualifyingMeasures() {
    AggregationResults<Measure> emptyPage = mockAggregationResults(List.of());

    when(mongoTemplate.aggregate(any(TypedAggregation.class), eq(Measure.class)))
        .thenReturn(emptyPage);

    changeUnit.addTestCaseSetIds(mongoTemplate, measureRepository);

    verify(mongoTemplate, never()).exists(any(Query.class), eq(Measure.class));
    verify(mongoTemplate, never()).save(any(Measure.class));
  }

  @Test
  void testAddTestCaseSetIds_skipsWhenTestCaseSetIdAlreadyExists() {
    Measure measure =
        Measure.builder()
            .id("m1")
            .measureSetId("setId-existing")
            .model("QICore v4.1.1")
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .version(Version.builder().major(0).minor(0).revisionNumber(0).build())
            .testCases(new ArrayList<>(List.of(TestCase.builder().id("tc1").build())))
            .build();

    AggregationResults<Measure> pageOne = mockAggregationResults(List.of(measure));
    AggregationResults<Measure> emptyPage = mockAggregationResults(List.of());

    when(mongoTemplate.aggregate(any(TypedAggregation.class), eq(Measure.class)))
        .thenReturn(pageOne)
        .thenReturn(emptyPage);
    // testCaseSetIdExistsInSet returns true -> should skip
    when(measureRepository.testCaseSetIdExistsInSet(anyString())).thenReturn(true);

    changeUnit.addTestCaseSetIds(mongoTemplate, measureRepository);

    verify(mongoTemplate, never()).save(any(Measure.class));
  }

  @Test
  void testAddTestCaseSetIds_skipsWhenMeasureHasEmptyTestCases() {
    Measure emptyTcMeasure =
        Measure.builder()
            .id("m-empty")
            .measureSetId("setId-empty-tc")
            .model("QICore v4.1.1")
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .version(Version.builder().major(0).minor(0).revisionNumber(0).build())
            .testCases(new ArrayList<>())
            .build();

    AggregationResults<Measure> pageOne = mockAggregationResults(List.of(emptyTcMeasure));
    AggregationResults<Measure> emptyPage = mockAggregationResults(List.of());

    when(mongoTemplate.aggregate(any(TypedAggregation.class), eq(Measure.class)))
        .thenReturn(pageOne)
        .thenReturn(emptyPage);
    when(measureRepository.testCaseSetIdExistsInSet(anyString())).thenReturn(false);

    changeUnit.addTestCaseSetIds(mongoTemplate, measureRepository);

    verify(mongoTemplate, never()).save(any(Measure.class));
  }

  @Test
  void testAddTestCaseSetIds_doesNotOverwriteExistingTestCaseSetIds() {
    UUID existingId = UUID.randomUUID();
    TestCase tc1 = TestCase.builder().id("tc1").testCaseSetId(existingId).build();
    Measure measure =
        Measure.builder()
            .id("m-with-ids")
            .measureSetId("setId-with-ids")
            .model("QICore v4.1.1")
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .version(Version.builder().major(0).minor(0).revisionNumber(0).build())
            .testCases(new ArrayList<>(List.of(tc1)))
            .build();

    AggregationResults<Measure> pageOne = mockAggregationResults(List.of(measure));
    AggregationResults<Measure> emptyPage = mockAggregationResults(List.of());

    when(mongoTemplate.aggregate(any(TypedAggregation.class), eq(Measure.class)))
        .thenReturn(pageOne)
        .thenReturn(emptyPage);
    // testCaseSetIdExistsInSet returns true (set already has ids)
    when(measureRepository.testCaseSetIdExistsInSet(anyString())).thenReturn(true);

    changeUnit.addTestCaseSetIds(mongoTemplate, measureRepository);

    // Existing id was not touched
    assertEquals(existingId, tc1.getTestCaseSetId());
    verify(mongoTemplate, never()).save(any(Measure.class));
  }

  @Test
  void testAddTestCaseSetIds_pagination() {
    // First page returns exactly PAGE_SIZE items, second page returns fewer
    List<Measure> firstPageList = new ArrayList<>();
    for (int i = 0; i < AddTestCaseSetIdsChangeUnit.PAGE_SIZE; i++) {
      firstPageList.add(
          Measure.builder()
              .id("m-" + i)
              .measureSetId("setId-" + i)
              .model("QICore v4.1.1")
              .active(true)
              .measureMetaData(MeasureMetaData.builder().draft(true).build())
              .version(Version.builder().major(0).minor(0).revisionNumber(0).build())
              .testCases(new ArrayList<>(List.of(TestCase.builder().id("tc-" + i).build())))
              .build());
    }
    Measure lastMeasure =
        Measure.builder()
            .id("m-last")
            .measureSetId("setId-last")
            .model("QICore v4.1.1")
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .version(Version.builder().major(0).minor(0).revisionNumber(0).build())
            .testCases(new ArrayList<>(List.of(TestCase.builder().id("tc-last").build())))
            .build();

    AggregationResults<Measure> firstPage = mockAggregationResults(firstPageList);
    AggregationResults<Measure> secondPage = mockAggregationResults(List.of(lastMeasure));

    when(mongoTemplate.aggregate(any(TypedAggregation.class), eq(Measure.class)))
        .thenReturn(firstPage)
        .thenReturn(secondPage);
    // All sets already have testCaseSetIds -> skip all (simplifies pagination test)
    when(measureRepository.testCaseSetIdExistsInSet(anyString())).thenReturn(true);

    changeUnit.addTestCaseSetIds(mongoTemplate, measureRepository);

    // aggregate should be called twice (first page full -> continues, second page partial -> stops)
    verify(mongoTemplate, times(2)).aggregate(any(TypedAggregation.class), eq(Measure.class));
    verify(mongoTemplate, never()).save(any(Measure.class));
  }

  @Test
  void testRollbackExecution_doesNotThrow() {
    assertDoesNotThrow(() -> changeUnit.rollbackExecution());
  }
}
