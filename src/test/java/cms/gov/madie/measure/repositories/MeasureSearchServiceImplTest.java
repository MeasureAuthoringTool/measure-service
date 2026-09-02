package cms.gov.madie.measure.repositories;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.dto.*;
import cms.gov.madie.measure.locks.MeasureLock;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@ExtendWith(MockitoExtension.class)
@EnableMongoRepositories(basePackages = "com.gov.madie.measure.repository")
public class MeasureSearchServiceImplTest {

  @Mock MongoTemplate mongoTemplate;
  @Mock UserServiceClient userServiceClient;
  @InjectMocks MeasureSearchServiceImpl measureAclRepository;

  private MeasureListDTO measure1;
  private MeasureListDTO measure2;
  private MeasureListDTO measure3;
  private MeasureListDTO measure4;
  private MeasureListDTO measure5;

  @BeforeEach
  void setup() {
    measure1 =
        MeasureListDTO.builder()
            .id("1")
            .measureName("test-measure-name")
            .ecqmTitle("test measure 1")
            .measureSetId("1-1")
            .build();
    measure2 =
        MeasureListDTO.builder().id("2").ecqmTitle("test measure 2").measureSetId("2-2").build();
    measure3 =
        MeasureListDTO.builder()
            .id("3")
            .model(ModelType.QDM_5_6.getValue())
            .measureSetId("3-3")
            .build();
    measure4 = MeasureListDTO.builder().id("4").measureSetId("1-1").build();
    measure5 = MeasureListDTO.builder().id("5").measureSetId("1-1").build();
  }

  @Test
  public void testFindOwnedActiveMeasures() {
    // page size 3 from 0-2
    PageRequest pageRequest = PageRequest.of(0, 3);
    List<MeasureListDTO> allMeasures = List.of(measure1, measure2, measure3, measure4, measure5);

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(List.of(measure1, measure2, measure3))
            .count(Arrays.asList(allMeasures.toArray()))
            .build();

    AggregationResults pagedResults = new AggregationResults<>(List.of(facetDTO), new Document());

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    AggregationResults<MeasureSetMatchCountDTO> measureSetResults =
        new AggregationResults<>(List.of(dto1, dto2), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(measureSetResults);

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(pagedResults);

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, null, List.of(OwnershipType.OWNED));
    assertEquals(5, page.getTotalElements());
    assertEquals(2, page.getTotalPages());
    assertEquals(3, page.getContent().size());
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getId(), measure1.getId());
    assertEquals(page1Measures.get(1).getId(), measure2.getId());
    assertEquals(page1Measures.get(2).getId(), measure3.getId());
  }

  private String reviewStatusMatchStage(Aggregation aggregation) {
    return aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT).stream()
        .map(Object::toString)
        .filter(stage -> stage.contains("$match") && stage.contains("reviewStatus"))
        .findFirst()
        .orElse("");
  }

  private ArgumentCaptor<Aggregation> stubAggregatesReturning(List<MeasureListDTO> queryResults) {
    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(queryResults)
            .count(Arrays.asList(queryResults.toArray()))
            .build();
    AggregationResults<FacetDTO> pagedResults =
        new AggregationResults<>(List.of(facetDTO), new Document());
    AggregationResults<MeasureSetMatchCountDTO> measureSetResults =
        new AggregationResults<>(
            List.of(MeasureSetMatchCountDTO.builder().measureSetId("1-1").build()), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(measureSetResults);
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(pagedResults);

    return ArgumentCaptor.forClass(Aggregation.class);
  }

  @Test
  public void testFacetQueryAlwaysJoinsReviewSoStatusIsDisplayed() {
    ArgumentCaptor<Aggregation> captor = stubAggregatesReturning(List.of(measure1));

    measureAclRepository.searchMeasuresByCriteria(
        "john", PageRequest.of(0, 10), null, List.of(OwnershipType.OWNED));

    verify(mongoTemplate)
        .aggregate(
            captor.capture(),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class));

    String pipeline = captor.getValue().toString();
    assertTrue(pipeline.contains("measureReview"), "facet query should join the review collection");
    assertTrue(pipeline.contains("reviewStatus"), "facet query should project reviewStatus");
    assertTrue(pipeline.contains("READY_FOR_REVIEW"));
  }

  @Test
  public void testMatchQueryJoinsReviewOnlyWhenSearchingByReview() {
    ArgumentCaptor<Aggregation> captor = stubAggregatesReturning(List.of(measure1));
    MeasureSearchCriteria criteria =
        MeasureSearchCriteria.builder()
            .searchField("Ready")
            .optionalSearchProperties(List.of("review"))
            .build();

    measureAclRepository.searchMeasuresByCriteria(
        "john", PageRequest.of(0, 10), criteria, List.of(OwnershipType.OWNED));

    verify(mongoTemplate)
        .aggregate(
            captor.capture(),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class));

    String pipeline = captor.getValue().toString();
    assertTrue(pipeline.contains("measureReview"), "match query should join review when filtering");
    assertTrue(pipeline.contains("reviewStatus"));
  }

  @Test
  public void testMatchQuerySkipsReviewJoinForNonReviewSearch() {
    ArgumentCaptor<Aggregation> captor = stubAggregatesReturning(List.of(measure1));
    MeasureSearchCriteria criteria =
        MeasureSearchCriteria.builder().searchField("test measure").build();

    measureAclRepository.searchMeasuresByCriteria(
        "john", PageRequest.of(0, 10), criteria, List.of(OwnershipType.OWNED));

    verify(mongoTemplate)
        .aggregate(
            captor.capture(),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class));

    assertFalse(captor.getValue().toString().contains("measureReview"));
  }

  @Test
  public void testReviewStatusIsCarriedThroughToResults() {
    MeasureListDTO reviewed =
        MeasureListDTO.builder().id("1").measureSetId("1-1").reviewStatus("Ready").build();
    stubAggregatesReturning(List.of(reviewed));

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", PageRequest.of(0, 10), null, List.of(OwnershipType.OWNED));

    assertEquals("Ready", page.getContent().get(0).getReviewStatus());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchTerm() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    List<MeasureListDTO> allMeasures = List.of(measure1, measure2);

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(allMeasures)
            .count(Arrays.asList(allMeasures.toArray()))
            .build();
    AggregationResults<FacetDTO> pagedResults =
        new AggregationResults<>(List.of(facetDTO), new Document());

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    AggregationResults<MeasureSetMatchCountDTO> measureSetResults =
        new AggregationResults<>(List.of(dto1, dto2), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(measureSetResults);

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(pagedResults);

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder().searchField("test measure").build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    assertEquals(page.getContent().get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page.getContent().get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchTermAndOneOptional() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    List<MeasureListDTO> allMeasures = List.of(measure1, measure2);

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(allMeasures)
            .count(Arrays.asList(allMeasures.toArray()))
            .build();
    AggregationResults<FacetDTO> pagedResults =
        new AggregationResults<>(List.of(facetDTO), new Document());

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    AggregationResults<MeasureSetMatchCountDTO> measureSetResults =
        new AggregationResults<>(List.of(dto1, dto2), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(measureSetResults);

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(pagedResults);

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("test")
            .optionalSearchProperties(List.of("version"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    assertEquals(page.getContent().get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page.getContent().get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithVersionParts1() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    List<MeasureListDTO> allMeasures = List.of(measure1, measure2);

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(allMeasures)
            .count(Arrays.asList(allMeasures.toArray()))
            .build();
    AggregationResults<FacetDTO> pagedResults =
        new AggregationResults<>(List.of(facetDTO), new Document());

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    AggregationResults<MeasureSetMatchCountDTO> measureSetResults =
        new AggregationResults<>(List.of(dto1, dto2), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(measureSetResults);

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(pagedResults);

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("1")
            .optionalSearchProperties(List.of("version"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    assertEquals(page.getContent().get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page.getContent().get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithVersionParts2() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    List<MeasureListDTO> allMeasures = List.of(measure1, measure2);

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(allMeasures)
            .count(Arrays.asList(allMeasures.toArray()))
            .build();
    AggregationResults<FacetDTO> pagedResults =
        new AggregationResults<>(List.of(facetDTO), new Document());

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    AggregationResults<MeasureSetMatchCountDTO> measureSetResults =
        new AggregationResults<>(List.of(dto1, dto2), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(measureSetResults);

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(pagedResults);

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("1.0")
            .optionalSearchProperties(List.of("version"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    assertEquals(page.getContent().get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page.getContent().get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithVersionParts3() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    List<MeasureListDTO> allMeasures = List.of(measure1, measure2);

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(allMeasures)
            .count(Arrays.asList(allMeasures.toArray()))
            .build();
    AggregationResults<FacetDTO> pagedResults =
        new AggregationResults<>(List.of(facetDTO), new Document());

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    AggregationResults<MeasureSetMatchCountDTO> measureSetResults =
        new AggregationResults<>(List.of(dto1, dto2), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(measureSetResults);

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(pagedResults);

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("1.0.0")
            .optionalSearchProperties(List.of("version"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    assertEquals(page.getContent().get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page.getContent().get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchTermAndMultipleOptional() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    List<MeasureListDTO> allMeasures = List.of(measure1, measure2);

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(allMeasures)
            .count(Arrays.asList(allMeasures.toArray()))
            .build();
    AggregationResults<FacetDTO> pagedResults =
        new AggregationResults<>(List.of(facetDTO), new Document());

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    AggregationResults<MeasureSetMatchCountDTO> measureSetResults =
        new AggregationResults<>(List.of(dto1, dto2), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(measureSetResults);

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(pagedResults);

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("test")
            .optionalSearchProperties(Arrays.asList("measureName", "cmsId", "version"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    assertEquals(page.getContent().get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page.getContent().get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchTermAndOnlyCmsId() {
    PageRequest pageRequest = PageRequest.of(0, 3);

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    AggregationResults<MeasureSetMatchCountDTO> measureSetResults =
        new AggregationResults<>(List.of(dto1, dto2), new Document());

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(List.of(measure1, measure2))
            .count(Arrays.asList(measure1, measure2))
            .build();
    AggregationResults<FacetDTO> pagedResults =
        new AggregationResults<>(List.of(facetDTO), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(measureSetResults);

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(pagedResults);

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("test")
            .optionalSearchProperties(List.of("cmsId"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(2, page.getTotalElements());
    assertEquals(1, page.getTotalPages());
    assertEquals(2, page.getContent().size());
    assertEquals(measure1.getEcqmTitle(), page.getContent().get(0).getEcqmTitle());
    assertEquals(measure2.getEcqmTitle(), page.getContent().get(1).getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchFieldFilteredByMeasureName() {
    var measuresList = List.of(measure1);
    FacetDTO mockFacetDto = FacetDTO.builder().queryResults(measuresList).build();
    PageRequest pageRequest = PageRequest.of(0, 3);

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1, dto2), new Document()));

    when(mongoTemplate.aggregate(
            any(), ArgumentMatchers.eq(Measure.class), ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(mockFacetDto), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("test-measure-name")
            .optionalSearchProperties(List.of("measure"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(page.getTotalElements(), 1);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 1);
    assertEquals("test-measure-name", page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchFieldFilteredByCmsId() {
    var measuresList = List.of(measure1);
    FacetDTO mockFacetDto = FacetDTO.builder().queryResults(measuresList).build();
    PageRequest pageRequest = PageRequest.of(0, 3);

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1, dto2), new Document()));

    when(mongoTemplate.aggregate(
            any(), ArgumentMatchers.eq(Measure.class), ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(mockFacetDto), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("28fhir")
            .optionalSearchProperties(List.of("cmsId"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(page.getTotalElements(), 1);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 1);
    assertEquals(measure1.getMeasureName(), page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchFieldFilteredByModel() {
    PageRequest pageRequest = PageRequest.of(0, 3);

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set3").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set4").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1, dto2), new Document()));

    FacetDTO mockFacetDto =
        FacetDTO.builder().queryResults(List.of(measure3)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(mockFacetDto), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("qdm")
            .optionalSearchProperties(List.of("model"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(page.getTotalElements(), 1);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 1);
    assertEquals(ModelType.QDM_5_6.getValue(), page.getContent().get(0).getModel());
  }

  @Test
  void testFindLibraryUsageByLibraryName() {
    String libraryName = "test";
    String owner = "john";
    LibraryUsage usage = LibraryUsage.builder().name(libraryName).owner(owner).build();
    AggregationResults result = new AggregationResults<>(List.of(usage), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    List<LibraryUsage> libraryUsages =
        measureAclRepository.findLibraryUsageByLibraryName(libraryName);
    assertEquals(libraryUsages.size(), 1);
    assertEquals(libraryUsages.get(0).getName(), libraryName);
    assertEquals(libraryUsages.get(0).getOwner(), owner);
  }

  @Test
  void testCountOwnedMeasures() {
    String owner = "john";
    Map<String, String> resultMap = new HashMap<>();
    resultMap.put("count", "5");
    AggregationResults result = new AggregationResults<>(List.of(resultMap), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    int count =
        measureAclRepository.countMeasuresByOwnership(true, owner, List.of(OwnershipType.OWNED));
    assertEquals(count, 5);
  }

  @Test
  void testCountOwnedMeasuresReturnsZero() {
    String owner = "john";
    AggregationResults result = new AggregationResults<>(new ArrayList<>(), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    int count =
        measureAclRepository.countMeasuresByOwnership(true, owner, List.of(OwnershipType.OWNED));
    assertEquals(count, 0);
  }

  @Test
  void testCountSharedMeasures() {
    String owner = "john";
    Map<String, String> resultMap = new HashMap<>();
    resultMap.put("count", "3");
    AggregationResults result = new AggregationResults<>(List.of(resultMap), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    int count =
        measureAclRepository.countMeasuresByOwnership(true, owner, List.of(OwnershipType.SHARED));
    assertEquals(count, 3);
  }

  @Test
  void testCountSharedMeasuresReturnsZero() {
    String owner = "john";
    AggregationResults result = new AggregationResults<>(new ArrayList<>(), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    int count =
        measureAclRepository.countMeasuresByOwnership(true, owner, List.of(OwnershipType.SHARED));
    assertEquals(count, 0);
  }

  @Test
  void testCountAllMeasures() {
    String owner = "john";
    Map<String, String> resultMap = new HashMap<>();
    resultMap.put("count", "10");
    AggregationResults result = new AggregationResults<>(List.of(resultMap), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    int count =
        measureAclRepository.countMeasuresByOwnership(true, owner, List.of(OwnershipType.ALL));
    assertEquals(count, 10);
  }

  @Test
  void testCountAllMeasuresReturnsZero() {
    String owner = "john";
    AggregationResults result = new AggregationResults<>(new ArrayList<>(), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    int count =
        measureAclRepository.countMeasuresByOwnership(true, owner, List.of(OwnershipType.ALL));
    assertEquals(count, 0);
  }

  @Test
  void testCountAllMeasuresWithEmptyOwnershipTypes() {
    String owner = "john";
    Map<String, String> resultMap = new HashMap<>();
    resultMap.put("count", "10");
    AggregationResults result = new AggregationResults<>(List.of(resultMap), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    int count = measureAclRepository.countMeasuresByOwnership(true, owner, List.of());
    assertEquals(count, 10);
  }

  @Test
  void testCountAllMeasuresWithNullOwnershipTypes() {
    String owner = "john";
    Map<String, String> resultMap = new HashMap<>();
    resultMap.put("count", "10");
    AggregationResults result = new AggregationResults<>(List.of(resultMap), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    int count = measureAclRepository.countMeasuresByOwnership(true, owner, null);
    assertEquals(count, 10);
  }

  @Test
  void testCountAllMeasuresWithNoUserId() {
    Map<String, String> resultMap = new HashMap<>();
    resultMap.put("count", "10");
    AggregationResults result = new AggregationResults<>(List.of(resultMap), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    int count = measureAclRepository.countMeasuresByOwnership(true, null, List.of());
    assertEquals(count, 10);
  }

  @Test
  void testFindOwnedActiveMeasuresWithLocking() {
    List<MeasureListDTO> measuresList =
        List.of(
            measure1.toBuilder()
                .measureLock(MeasureLock.builder().lockedBy("Jane").build())
                .hasLockedTestCases(true)
                .build());
    FacetDTO mockFacetDto = FacetDTO.builder().queryResults(measuresList).build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1, dto2), new Document()));
    PageRequest pageRequest = PageRequest.of(0, 3);

    when(mongoTemplate.aggregate(
            any(), ArgumentMatchers.eq(Measure.class), ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(mockFacetDto), new Document()));
    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("test-measure-name")
            .optionalSearchProperties(List.of("measure"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getTotalElements());
    assertEquals(1, page.getTotalPages());
    assertEquals(1, page.getContent().size());
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals("test-measure-name", page1Measures.get(0).getMeasureName());
    assertEquals("Jane", page1Measures.get(0).getMeasureLock().getLockedBy());
    assertTrue(page1Measures.get(0).isHasLockedTestCases());
  }

  @Test
  public void testSearchMeasuresPopulatesOwnerDisplayNames() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureSet measureSet1 = MeasureSet.builder().owner("harpId1").build();
    MeasureSet measureSet2 = MeasureSet.builder().owner("harpId2").build();

    MeasureListDTO measure1 =
        MeasureListDTO.builder()
            .id("1")
            .measureName("measure1")
            .measureSetId("set1")
            .measureSet(measureSet1)
            .build();

    MeasureListDTO measure2 =
        MeasureListDTO.builder()
            .id("2")
            .measureName("measure2")
            .measureSetId("set2")
            .measureSet(measureSet2)
            .build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1, dto2), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1, measure2)).count(List.of(1, 2)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Map<String, UserDetailsDto> userDetailsMap = new HashMap<>();
    userDetailsMap.put(
        "harpId1", UserDetailsDto.builder().firstName("John").lastName("Doe").build());
    userDetailsMap.put(
        "harpId2", UserDetailsDto.builder().firstName("Jane").lastName("Smith").build());

    when(userServiceClient.getBulkUserDetails(any())).thenReturn(userDetailsMap);

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.OWNED));

    assertEquals(2, page.getContent().size());
    assertEquals("John Doe", page.getContent().get(0).getOwnerDisplayName());
    assertEquals("Jane Smith", page.getContent().get(1).getOwnerDisplayName());
  }

  @Test
  public void testPopulateOwnerDisplayNamesWithFirstNameOnly() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureSet measureSet = MeasureSet.builder().owner("harpId1").build();
    MeasureListDTO measure =
        MeasureListDTO.builder()
            .id("1")
            .measureName("measure1")
            .measureSetId("set1")
            .measureSet(measureSet)
            .build();

    MeasureSetMatchCountDTO dto = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Map<String, UserDetailsDto> userDetailsMap = new HashMap<>();
    userDetailsMap.put("harpId1", UserDetailsDto.builder().firstName("John").build());
    when(userServiceClient.getBulkUserDetails(any())).thenReturn(userDetailsMap);

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("John", page.getContent().get(0).getOwnerDisplayName());
  }

  @Test
  public void testPopulateOwnerDisplayNamesWithLastNameOnly() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureSet measureSet = MeasureSet.builder().owner("harpId1").build();
    MeasureListDTO measure =
        MeasureListDTO.builder()
            .id("1")
            .measureName("measure1")
            .measureSetId("set1")
            .measureSet(measureSet)
            .build();

    MeasureSetMatchCountDTO dto = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Map<String, UserDetailsDto> userDetailsMap = new HashMap<>();
    userDetailsMap.put("harpId1", UserDetailsDto.builder().lastName("Doe").build());
    when(userServiceClient.getBulkUserDetails(any())).thenReturn(userDetailsMap);

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("Doe", page.getContent().get(0).getOwnerDisplayName());
  }

  @Test
  public void testPopulateOwnerDisplayNamesWithHarpIdWhenFirstAndLastNameIsNull() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureSet measureSet = MeasureSet.builder().owner("harpId1").build();
    MeasureListDTO measure =
        MeasureListDTO.builder()
            .id("1")
            .measureName("measure1")
            .measureSetId("set1")
            .measureSet(measureSet)
            .build();

    MeasureSetMatchCountDTO dto = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Map<String, UserDetailsDto> userDetailsMap = new HashMap<>();
    userDetailsMap.put("harpId1", UserDetailsDto.builder().firstName("").lastName("").build());
    when(userServiceClient.getBulkUserDetails(any())).thenReturn(userDetailsMap);

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("harpId1", page.getContent().get(0).getOwnerDisplayName());
  }

  @Test
  public void testPopulateOwnerDisplayNamesWithUserNotFound() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureSet measureSet = MeasureSet.builder().owner("harpId1").build();
    MeasureListDTO measure =
        MeasureListDTO.builder()
            .id("1")
            .measureName("measure1")
            .measureSetId("set1")
            .measureSet(measureSet)
            .build();

    MeasureSetMatchCountDTO dto = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    // Return empty map - user not found
    when(userServiceClient.getBulkUserDetails(any())).thenReturn(new HashMap<>());

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("-", page.getContent().get(0).getOwnerDisplayName());
  }

  @Test
  public void testPopulateOwnerDisplayNamesWithNullMeasureSet() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure =
        MeasureListDTO.builder()
            .id("1")
            .measureName("measure1")
            .measureSetId("set1")
            .measureSet(null)
            .build();

    MeasureSetMatchCountDTO dto = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    // Should not call userServiceClient when measureSet is null
  }

  @Test
  public void testPopulateOwnerDisplayNamesWithNullOwner() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureSet measureSet = MeasureSet.builder().owner(null).build();
    MeasureListDTO measure =
        MeasureListDTO.builder()
            .id("1")
            .measureName("measure1")
            .measureSetId("set1")
            .measureSet(measureSet)
            .build();

    MeasureSetMatchCountDTO dto = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    // Should not call userServiceClient when owner is null
  }

  @Test
  public void testPopulateOwnerDisplayNamesWithEmptyList() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureSetMatchCountDTO dto = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of()).count(List.of()).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.OWNED));

    assertEquals(0, page.getContent().size());
    // Should not call userServiceClient when list is empty
  }

  @Test
  public void testPopulateOwnerDisplayNamesDeduplicatesOwners() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureSet measureSet1 = MeasureSet.builder().owner("harpId1").build();
    MeasureSet measureSet2 = MeasureSet.builder().owner("harpId1").build(); // Same owner

    MeasureListDTO measure1 =
        MeasureListDTO.builder()
            .id("1")
            .measureName("measure1")
            .measureSetId("set1")
            .measureSet(measureSet1)
            .build();

    MeasureListDTO measure2 =
        MeasureListDTO.builder()
            .id("2")
            .measureName("measure2")
            .measureSetId("set2")
            .measureSet(measureSet2)
            .build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1, dto2), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1, measure2)).count(List.of(1, 2)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Map<String, UserDetailsDto> userDetailsMap = new HashMap<>();
    userDetailsMap.put(
        "harpId1", UserDetailsDto.builder().firstName("John").lastName("Doe").build());

    when(userServiceClient.getBulkUserDetails(ArgumentMatchers.argThat(list -> list.size() == 1)))
        .thenReturn(userDetailsMap);

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.OWNED));

    assertEquals(2, page.getContent().size());
    assertEquals("John Doe", page.getContent().get(0).getOwnerDisplayName());
    assertEquals("John Doe", page.getContent().get(1).getOwnerDisplayName());
  }

  @Test
  public void testSearchMeasuresWithAllowedScoringTypesFilter() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measureWithAllowedScoring =
        MeasureListDTO.builder().id("1").measureName("measure1").measureSetId("set1").build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(List.of(measureWithAllowedScoring))
            .count(List.of(1))
            .build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .fromCompositeMeasureComponent(true)
            .allowedScoringTypes(Arrays.asList("Cohort", "Continuous Variable"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("measure1", page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testSearchMeasuresFromCompositeComponentWithSearchField() {
    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO matchingMeasure =
        MeasureListDTO.builder()
            .id("1")
            .measureName("diabetes measure")
            .measureSetId("set1")
            .build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(matchingMeasure)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("diabetes")
            .optionalSearchProperties(List.of("measureName", "version", "cmsId"))
            .fromCompositeMeasureComponent(true)
            .allowedScoringTypes(Arrays.asList("Proportion", "Ratio"))
            .draft(false)
            .model("QI-Core v4.1.1")
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.ALL));

    assertEquals(1, page.getContent().size());
    assertEquals("diabetes measure", page.getContent().get(0).getMeasureName());

    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate)
        .aggregate(
            aggregationCaptor.capture(),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class));
    String pipeline = aggregationCaptor.getValue().toString();
    assertTrue(pipeline.contains("diabetes"), "search field should be in the pipeline");
    assertTrue(pipeline.contains("$nor"), "test case set id filter should be in the pipeline");
  }

  @Test
  public void testSearchMeasuresWithSingleAllowedScoringType() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measureWithCohort =
        MeasureListDTO.builder().id("1").measureName("cohort-measure").measureSetId("set1").build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measureWithCohort)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .fromCompositeMeasureComponent(true)
            .allowedScoringTypes(List.of("Cohort"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("cohort-measure", page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testSearchMeasuresWithEmptyAllowedScoringTypes() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
        MeasureListDTO.builder().id("1").measureName("measure1").measureSetId("set1").build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .fromCompositeMeasureComponent(true)
            .allowedScoringTypes(Collections.emptyList())
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("measure1", page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testSearchMeasuresWithNullAllowedScoringTypes() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
        MeasureListDTO.builder().id("1").measureName("measure1").measureSetId("set1").build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .fromCompositeMeasureComponent(true)
            .allowedScoringTypes(null)
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("measure1", page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testSearchMeasuresWithIsFromCompositeMeasureComponentsFalse() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
        MeasureListDTO.builder().id("1").measureName("measure1").measureSetId("set1").build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .fromCompositeMeasureComponent(false)
            .allowedScoringTypes(Arrays.asList("Proportion", "Ratio"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("measure1", page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testSearchMeasuresWithMultipleAllowedScoringTypes() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
        MeasureListDTO.builder().id("1").measureName("measure1").measureSetId("set1").build();

    MeasureListDTO measure2 =
        MeasureListDTO.builder().id("2").measureName("measure2").measureSetId("set2").build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1, dto2), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1, measure2)).count(List.of(1, 2)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .fromCompositeMeasureComponent(true)
            .allowedScoringTypes(Arrays.asList("Cohort", "Continuous Variable", "Ratio"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(2, page.getContent().size());
    assertEquals("measure1", page.getContent().get(0).getMeasureName());
    assertEquals("measure2", page.getContent().get(1).getMeasureName());
  }

  @Test
  public void testSearchMeasuresWithNullMeasureSearchCriteria() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
        MeasureListDTO.builder().id("1").measureName("measure1").measureSetId("set1").build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("measure1", page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testSearchMeasuresWithScoringFilterAndLocking() {
    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
        MeasureListDTO.builder().id("1").measureName("measure1").measureSetId("set1").build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .fromCompositeMeasureComponent(true)
            .allowedScoringTypes(Arrays.asList("Cohort", "Continuous Variable"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("measure1", page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testSearchMeasuresByCriteriaExecutesCreateScoringTypeFilterCompletely() {
    PageRequest pageRequest = PageRequest.of(0, 10);
    MeasureListDTO measureWithAllowedScoring =
        MeasureListDTO.builder()
            .id("measure-1")
            .measureName("Cohort Measure")
            .measureSetId("set-1")
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .measureSet(MeasureSet.builder().measureSetId("set-1").owner("testUser").build())
            .build();

    MeasureSetMatchCountDTO matchCountDTO =
        MeasureSetMatchCountDTO.builder()
            .measureSetId("set-1")
            .matchCount(1)
            .matchedMeasureId("measure-1")
            .build();

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(matchCountDTO), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(List.of(measureWithAllowedScoring))
            .count(List.of(1))
            .build();

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .fromCompositeMeasureComponent(true)
            .allowedScoringTypes(Arrays.asList("Cohort", "Continuous Variable", "Ratio"))
            .draft(true)
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "testUser", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertNotNull(page);
    assertEquals(1, page.getContent().size());
    assertEquals("Cohort Measure", page.getContent().get(0).getMeasureName());
    assertEquals("set-1", page.getContent().get(0).getMeasureSetId());

    verify(mongoTemplate, times(2))
        .aggregate(any(Aggregation.class), ArgumentMatchers.eq(Measure.class), any());
  }

  @Test
  public void testPopulateOwnerDisplayNamesWithHyphenWhenHarpIdFirstAndLastNameAreNull() {

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureSet measureSet = MeasureSet.builder().owner("harpId1").build();
    MeasureListDTO measure =
        MeasureListDTO.builder()
            .id("1")
            .measureName("measure1")
            .measureSetId("set1")
            .measureSet(measureSet)
            .build();

    MeasureSet measureSet1 = MeasureSet.builder().owner("").build();
    MeasureListDTO measure1 =
        MeasureListDTO.builder()
            .id("1")
            .measureName("measure1")
            .measureSetId("set1")
            .measureSet(measureSet1)
            .build();

    MeasureSetMatchCountDTO dto = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dto), new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure, measure1)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Map<String, UserDetailsDto> userDetailsMap = new HashMap<>();
    userDetailsMap.put("harpId1", UserDetailsDto.builder().firstName("").lastName("").build());
    userDetailsMap.put("", UserDetailsDto.builder().firstName("").lastName("").build());

    when(userServiceClient.getBulkUserDetails(any())).thenReturn(userDetailsMap);

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.OWNED));
    assertEquals(2, page.getContent().size());
    assertEquals("harpId1", page.getContent().get(0).getOwnerDisplayName());
    assertEquals("-", page.getContent().get(1).getOwnerDisplayName());
  }

  @Test
  public void testSearchMeasuresByCriteriaWithPriorityMeasureSets() {
    PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("measureSetId").ascending());

    // Create measures with different measureSetIds and last modified dates
    // Set A: measureSetId "setA", lastModifiedAt in 2023
    MeasureSet measureSetA = MeasureSet.builder().owner("user1").build();
    MeasureListDTO measureA1 =
        MeasureListDTO.builder()
            .id("measure-id-1")
            .measureName("Measure A1")
            .measureSetId("setA")
            .measureSet(measureSetA)
            .lastModifiedAt(Instant.ofEpochMilli(1672531200000L)) // Jan 1, 2023
            .build();

    // Set B: measureSetId "setB", lastModifiedAt in 2025
    MeasureSet measureSetB = MeasureSet.builder().owner("user2").build();
    MeasureListDTO measureB1 =
        MeasureListDTO.builder()
            .id("measure-id-3")
            .measureName("Measure B1")
            .measureSetId("setB")
            .measureSet(measureSetB)
            .lastModifiedAt(Instant.ofEpochMilli(1735689600000L)) // Jan 1, 2025
            .build();

    // Set C: measureSetId "setC", lastModifiedAt in 2026
    MeasureSet measureSetC = MeasureSet.builder().owner("user3").build();
    MeasureListDTO measureC1 =
        MeasureListDTO.builder()
            .id("measure-id-2")
            .measureName("Measure C1")
            .measureSetId("setC")
            .measureSet(measureSetC)
            .lastModifiedAt(Instant.ofEpochMilli(1767225600000L)) // Jan 1, 2026
            .build();

    // Set D: measureSetId "setD", lastModifiedAt in 2026
    MeasureSet measureSetD = MeasureSet.builder().owner("user4").build();
    MeasureListDTO measureD1 =
        MeasureListDTO.builder()
            .id("measure-id-8")
            .measureName("Measure D1")
            .measureSetId("setD")
            .measureSet(measureSetD)
            .lastModifiedAt(Instant.ofEpochMilli(1767312000000L)) // Jan 2, 2026 (later than setC)
            .build();

    // Mock the first aggregation for measure set match counts
    MeasureSetMatchCountDTO dtoA =
        MeasureSetMatchCountDTO.builder().measureSetId("setA").matchCount(1).build();
    MeasureSetMatchCountDTO dtoB =
        MeasureSetMatchCountDTO.builder().measureSetId("setB").matchCount(1).build();
    MeasureSetMatchCountDTO dtoC =
        MeasureSetMatchCountDTO.builder().measureSetId("setC").matchCount(1).build();
    MeasureSetMatchCountDTO dtoD =
        MeasureSetMatchCountDTO.builder().measureSetId("setD").matchCount(1).build();

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(dtoA, dtoB, dtoC, dtoD), new Document()));

    // Expected order: Set D (priority, most recent), Set B (priority, older),
    // Set C (non-priority, most recent), Set A (non-priority, oldest)
    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(List.of(measureD1, measureB1, measureC1, measureA1))
            .count(List.of(1, 2, 3, 4))
            .build();

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    // Mock user details
    Map<String, UserDetailsDto> userDetailsMap = new HashMap<>();
    userDetailsMap.put("user1", UserDetailsDto.builder().firstName("User").lastName("One").build());
    userDetailsMap.put("user2", UserDetailsDto.builder().firstName("User").lastName("Two").build());
    userDetailsMap.put(
        "user3", UserDetailsDto.builder().firstName("User").lastName("Three").build());
    userDetailsMap.put(
        "user4", UserDetailsDto.builder().firstName("User").lastName("Four").build());

    when(userServiceClient.getBulkUserDetails(any())).thenReturn(userDetailsMap);

    // Create search criteria with priorityMeasureSets
    MeasureSearchCriteria searchCriteria =
        MeasureSearchCriteria.builder()
            .fromCompositeMeasureComponent(true)
            .priorityMeasureSets(List.of("setB", "setD")) // Priority sets
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, searchCriteria, List.of(OwnershipType.OWNED));

    // Verify results
    assertEquals(4, page.getContent().size());

    // Verify the order: Set D first (priority + most recent), then Set B (priority),
    // then Set C (non-priority, most recent), then Set A (non-priority, oldest)
    assertEquals("setD", page.getContent().get(0).getMeasureSetId());
    assertEquals("measure-id-8", page.getContent().get(0).getId());

    assertEquals("setB", page.getContent().get(1).getMeasureSetId());
    assertEquals("measure-id-3", page.getContent().get(1).getId());

    assertEquals("setC", page.getContent().get(2).getMeasureSetId());
    assertEquals("measure-id-2", page.getContent().get(2).getId());

    assertEquals("setA", page.getContent().get(3).getMeasureSetId());
    assertEquals("measure-id-1", page.getContent().get(3).getId());
  }

  // -------------------------------------------------------------------------
  // 5-tier draft sort tests
  // -------------------------------------------------------------------------

  private void setupMongoMocksForDraftSort(
      List<MeasureSetMatchCountDTO> matchCounts, List<MeasureListDTO> queryResults) {
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(matchCounts, new Document()));

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(queryResults).count(List.of(queryResults.size())).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));
  }

  @Test
  public void testSearchMeasuresSortByDraftDesc() {
    // Sort by measureMetaData.draft DESC → draftSortOrder DESC (5 first = composite draft first,
    // 1 last = non-composite versioned last).  Direction is preserved, not flipped.
    PageRequest pageRequest =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "measureMetaData.draft"));

    MeasureListDTO versionedMeasure =
        MeasureListDTO.builder().id("v1").measureName("Versioned").measureSetId("set-v1").build();
    MeasureListDTO draftMeasure =
        MeasureListDTO.builder().id("d1").measureName("Draft").measureSetId("set-d1").build();

    MeasureSetMatchCountDTO dtoV =
        MeasureSetMatchCountDTO.builder()
            .measureSetId("set-v1")
            .matchCount(1)
            .matchedMeasureId("v1")
            .build();
    MeasureSetMatchCountDTO dtoD =
        MeasureSetMatchCountDTO.builder()
            .measureSetId("set-d1")
            .matchCount(1)
            .matchedMeasureId("d1")
            .build();

    // The service passes the sort to the aggregation pipeline;
    // the actual ordering here comes from the mocked Mongo response.
    setupMongoMocksForDraftSort(List.of(dtoV, dtoD), List.of(versionedMeasure, draftMeasure));

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.ALL));

    assertEquals(2, page.getContent().size());
    assertEquals("v1", page.getContent().get(0).getId());
    assertEquals("d1", page.getContent().get(1).getId());

    // Verify the aggregation pipeline was invoked twice (first pass + post-match)
    verify(mongoTemplate, times(2))
        .aggregate(any(Aggregation.class), ArgumentMatchers.eq(Measure.class), any());
  }

  @Test
  public void testSearchMeasuresSortByDraftAsc() {
    // Sort by measureMetaData.draft ASC → draftSortOrder ASC (1 first = non-composite versioned
    // first, 5 last = composite draft last).  Direction is preserved, not flipped.
    PageRequest pageRequest =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "measureMetaData.draft"));

    MeasureListDTO compositeDraft =
        MeasureListDTO.builder()
            .id("cd1")
            .measureName("Composite Draft")
            .measureSetId("set-cd1")
            .build();
    MeasureListDTO versionedMeasure =
        MeasureListDTO.builder().id("v1").measureName("Versioned").measureSetId("set-v1").build();

    MeasureSetMatchCountDTO dtoCD =
        MeasureSetMatchCountDTO.builder()
            .measureSetId("set-cd1")
            .matchCount(1)
            .matchedMeasureId("cd1")
            .build();
    MeasureSetMatchCountDTO dtoV =
        MeasureSetMatchCountDTO.builder()
            .measureSetId("set-v1")
            .matchCount(1)
            .matchedMeasureId("v1")
            .build();

    setupMongoMocksForDraftSort(List.of(dtoCD, dtoV), List.of(compositeDraft, versionedMeasure));

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "userId", pageRequest, null, List.of(OwnershipType.ALL));

    assertEquals(2, page.getContent().size());
    assertEquals("cd1", page.getContent().get(0).getId());
    assertEquals("v1", page.getContent().get(1).getId());

    verify(mongoTemplate, times(2))
        .aggregate(any(Aggregation.class), ArgumentMatchers.eq(Measure.class), any());
  }

  @Test
  public void testSearchMeasuresByCriteriaExcludesCompositeMeasures() {
    PageRequest pageRequest = PageRequest.of(0, 5);
    List<MeasureListDTO> nonCompositeMeasures = List.of(measure1, measure2);

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(nonCompositeMeasures)
            .count(Arrays.asList(nonCompositeMeasures.toArray()))
            .build();
    AggregationResults<FacetDTO> pagedResults =
        new AggregationResults<>(List.of(facetDTO), new Document());

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    AggregationResults<MeasureSetMatchCountDTO> measureSetResults =
        new AggregationResults<>(List.of(dto1, dto2), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(measureSetResults);

    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(pagedResults);

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder().excludeCompositeMeasures(true).build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(2, page.getTotalElements());
    assertEquals(2, page.getContent().size());
    assertEquals(measure1.getId(), page.getContent().get(0).getId());
    assertEquals(measure2.getId(), page.getContent().get(1).getId());

    verify(mongoTemplate, times(2))
        .aggregate(any(Aggregation.class), ArgumentMatchers.eq(Measure.class), any());
  }

  @Test
  public void countMeasuresByReviewReturnsCorrectCount() {
    String owner = "john";
    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    Map<String, String> resultMap = new HashMap<>();
    resultMap.put("count", "4");
    AggregationResults result = new AggregationResults<>(List.of(resultMap), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    int count = measureAclRepository.countMeasuresByReview(true, owner, List.of(OwnershipType.ALL));
    assertEquals(4, count);

    verify(mongoTemplate).aggregate(captor.capture(), (Class<?>) any(), any());
    assertFalse(
        captor.getValue().toString().contains("$measureSetId"),
        "reviews are counted per measure, matching the rows the reviews tab lists");
    assertFalse(
        captor.getValue().toString().contains("\"review.reviewers\""),
        "the All Reviews count is not narrowed to the requesting reviewer");
  }

  @Test
  public void countMeasuresByReviewReturnsZeroWhenNoResults() {
    AggregationResults result = new AggregationResults<>(new ArrayList<>(), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    int count =
        measureAclRepository.countMeasuresByReview(true, "john", List.of(OwnershipType.OWNED));
    assertEquals(0, count);
  }

  @Test
  public void testSearchMeasuresInReviewReturnsMeasuresWithOwnerDisplayName() {
    MeasureSet measureSet = MeasureSet.builder().measureSetId("1-1").owner("john").build();
    MeasureListDTO reviewed =
        MeasureListDTO.builder()
            .id("1")
            .measureSetId("1-1")
            .measureSet(measureSet)
            .reviewStatus("Ready")
            .build();
    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(reviewed)).count(List.of(new Object())).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));
    Map<String, UserDetailsDto> userDetailsMap = new HashMap<>();
    userDetailsMap.put("john", UserDetailsDto.builder().firstName("John").lastName("Doe").build());
    when(userServiceClient.getBulkUserDetails(any())).thenReturn(userDetailsMap);

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresInReview(
            "john", PageRequest.of(0, 10), null, List.of(OwnershipType.ALL));

    assertEquals(1, page.getTotalElements());
    assertEquals("Ready", page.getContent().get(0).getReviewStatus());
    assertEquals("John Doe", page.getContent().get(0).getOwnerDisplayName());
  }

  @Test
  public void testSearchMeasuresInReviewResolvesReviewersToFullNames() {
    MeasureSet measureSet = MeasureSet.builder().measureSetId("1-1").owner("john").build();
    MeasureListDTO reviewed =
        MeasureListDTO.builder()
            .id("1")
            .measureSetId("1-1")
            .measureSet(measureSet)
            .reviewStatus("Ready")
            .reviewers(List.of("ada", "grace", "unknown"))
            .build();
    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(reviewed)).count(List.of(new Object())).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));
    Map<String, UserDetailsDto> userDetailsMap = new HashMap<>();
    userDetailsMap.put("john", UserDetailsDto.builder().firstName("John").lastName("Doe").build());
    userDetailsMap.put(
        "ada", UserDetailsDto.builder().firstName("Ada").lastName("Lovelace").build());
    userDetailsMap.put(
        "grace", UserDetailsDto.builder().firstName("Grace").lastName("Hopper").build());
    ArgumentCaptor<List<String>> harpIdCaptor = ArgumentCaptor.forClass(List.class);
    when(userServiceClient.getBulkUserDetails(any())).thenReturn(userDetailsMap);

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresInReview(
            "john", PageRequest.of(0, 10), null, List.of(OwnershipType.ALL));

    verify(userServiceClient).getBulkUserDetails(harpIdCaptor.capture());
    assertEquals(List.of("john", "ada", "grace", "unknown"), harpIdCaptor.getValue());
    assertEquals(
        List.of("Ada Lovelace", "Grace Hopper", "unknown"),
        page.getContent().get(0).getReviewers());
  }

  @Test
  public void testSearchMeasuresInReviewLeavesReviewersAloneWhenThereAreNone() {
    MeasureSet measureSet = MeasureSet.builder().measureSetId("1-1").owner("john").build();
    MeasureListDTO reviewed =
        MeasureListDTO.builder()
            .id("1")
            .measureSetId("1-1")
            .measureSet(measureSet)
            .reviewStatus("")
            .build();
    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(reviewed)).count(List.of(new Object())).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));
    when(userServiceClient.getBulkUserDetails(any())).thenReturn(new HashMap<>());

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresInReview(
            "john", PageRequest.of(0, 10), null, List.of(OwnershipType.ALL));

    assertNull(page.getContent().get(0).getReviewers());
  }

  @Test
  public void testReviewStagesProjectReviewersOntoEachMeasure() {
    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of()).count(List.of()).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    measureAclRepository.searchMeasuresInReview(
        "john", PageRequest.of(0, 10), null, List.of(OwnershipType.ALL));

    verify(mongoTemplate)
        .aggregate(
            captor.capture(),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class));
    assertTrue(captor.getValue().toString().contains("$review.reviewers"));
  }

  @Test
  public void testSearchMeasuresInReviewFiltersToMeasuresUnderReview() {
    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of()).count(List.of()).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresInReview(
            "john", PageRequest.of(0, 10), null, List.of(OwnershipType.ALL));

    assertTrue(page.getContent().isEmpty());
    assertEquals(0, page.getTotalElements());
    verify(mongoTemplate)
        .aggregate(
            captor.capture(),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class));
    String pipeline = captor.getValue().toString();
    assertTrue(
        pipeline.contains("measureReview"), "review query should join the review collection");
    assertTrue(pipeline.contains("reviewStatus"));
    assertTrue(pipeline.contains("In Progress"));
    assertFalse(
        pipeline.contains("selectedDoc"),
        "measures under review should be listed individually, not rolled up per measure set");
    assertTrue(pipeline.contains("measureLock"), "review query should join locks");
  }

  @Test
  public void testSearchMeasuresInReviewAppliesSearchCriteria() {
    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of()).count(List.of()).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));
    MeasureSearchCriteria criteria =
        MeasureSearchCriteria.builder()
            .searchField("1234")
            .optionalSearchProperties(List.of("cmsId"))
            .build();

    measureAclRepository.searchMeasuresInReview(
        "john", PageRequest.of(0, 10), criteria, List.of(OwnershipType.ALL));

    verify(mongoTemplate)
        .aggregate(
            captor.capture(),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class));
    String pipeline = captor.getValue().toString();
    assertTrue(pipeline.contains("cmsIdDisplay"), "search by cms id should add cmsIdDisplay");
    assertTrue(pipeline.contains("1234"));
  }

  @Test
  public void testSearchMeasuresInReviewForTheAssignedScopeFiltersToOpenStatusesForTheReviewer() {
    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of()).count(List.of()).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    measureAclRepository.searchMeasuresInReview(
        "john", PageRequest.of(0, 10), null, List.of(OwnershipType.OWNED));

    verify(mongoTemplate)
        .aggregate(
            captor.capture(),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class));
    String statusMatch = reviewStatusMatchStage(captor.getValue());
    assertTrue(statusMatch.contains("Ready"));
    assertTrue(statusMatch.contains("In Progress"));
    assertFalse(
        statusMatch.contains("Complete"), "completed reviews do not belong on the My Reviews tab");
    String pipeline = captor.getValue().toString();
    assertTrue(pipeline.contains("\"review.reviewers\""));
    assertTrue(pipeline.contains("john"));
  }

  @Test
  public void testSearchMeasuresInReviewForTheAllScopeIsNotNarrowedToTheReviewer() {
    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of()).count(List.of()).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    measureAclRepository.searchMeasuresInReview(
        "john", PageRequest.of(0, 10), null, List.of(OwnershipType.ALL));

    verify(mongoTemplate)
        .aggregate(
            captor.capture(),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class));
    assertFalse(captor.getValue().toString().contains("\"review.reviewers\""));
  }

  @Test
  public void testCountMeasuresByReviewForTheAssignedScope() {
    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    Map<String, String> resultMap = new HashMap<>();
    resultMap.put("count", "2");
    AggregationResults result = new AggregationResults<>(List.of(resultMap), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);

    assertEquals(
        2, measureAclRepository.countMeasuresByReview(true, "john", List.of(OwnershipType.OWNED)));

    verify(mongoTemplate).aggregate(captor.capture(), (Class<?>) any(), any());
    assertTrue(captor.getValue().toString().contains("\"review.reviewers\""));
    String statusMatch = reviewStatusMatchStage(captor.getValue());
    assertTrue(statusMatch.contains("Ready"));
    assertTrue(statusMatch.contains("In Progress"));
    assertFalse(statusMatch.contains("Complete"));
  }

  @Test
  public void testCountMeasuresByReviewForTheAssignedScopeReturnsZeroWhenNoResults() {
    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

    assertEquals(
        0, measureAclRepository.countMeasuresByReview(true, "john", List.of(OwnershipType.OWNED)));
  }

  @Test
  public void testSearchMeasuresInReviewSubstitutesDraftSortOrder() {
    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of()).count(List.of()).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    measureAclRepository.searchMeasuresInReview(
        "john",
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "measureMetaData.draft")),
        null,
        List.of(OwnershipType.ALL));

    verify(mongoTemplate)
        .aggregate(
            captor.capture(),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class));
    assertTrue(captor.getValue().toString().contains("draftSortOrder"));
  }

  @Test
  public void testSearchMeasuresByCriteria_WithDraftCriteria() {
    PageRequest pageRequest = PageRequest.of(0, 5);

    List<MeasureListDTO> measures = List.of(measure1);

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(measures).count(Arrays.asList(measures.toArray())).build();

    AggregationResults<FacetDTO> facetResults =
        new AggregationResults<>(List.of(facetDTO), new Document());

    MeasureSetMatchCountDTO matchDto =
        MeasureSetMatchCountDTO.builder()
            .measureSetId(measure1.getMeasureSetId())
            .matchCount(1)
            .matchedMeasureId(measure1.getId())
            .build();

    AggregationResults<MeasureSetMatchCountDTO> measureSetResults =
        new AggregationResults<>(List.of(matchDto), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(Measure.class), eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(measureSetResults);

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(Measure.class), eq(FacetDTO.class)))
        .thenReturn(facetResults);

    MeasureSearchCriteria criteria = MeasureSearchCriteria.builder().draft(true).build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, criteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getTotalElements());

    verify(mongoTemplate).aggregate(any(Aggregation.class), eq(Measure.class), eq(FacetDTO.class));
  }

  @Test
  public void testSearchMeasuresByCriteria_WithoutDraftCriteria() {
    PageRequest pageRequest = PageRequest.of(0, 5);

    List<MeasureListDTO> measures = List.of(measure1);

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(measures).count(Arrays.asList(measures.toArray())).build();

    AggregationResults<FacetDTO> facetResults =
        new AggregationResults<>(List.of(facetDTO), new Document());

    MeasureSetMatchCountDTO matchDto =
        MeasureSetMatchCountDTO.builder()
            .measureSetId(measure1.getMeasureSetId())
            .matchCount(1)
            .matchedMeasureId(measure1.getId())
            .build();

    AggregationResults<MeasureSetMatchCountDTO> measureSetResults =
        new AggregationResults<>(List.of(matchDto), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(Measure.class), eq(MeasureSetMatchCountDTO.class)))
        .thenReturn(measureSetResults);

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(Measure.class), eq(FacetDTO.class)))
        .thenReturn(facetResults);

    MeasureSearchCriteria criteria =
        MeasureSearchCriteria.builder().build(); // null draft for else branch

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, criteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getTotalElements());

    verify(mongoTemplate).aggregate(any(Aggregation.class), eq(Measure.class), eq(FacetDTO.class));
  }
}
