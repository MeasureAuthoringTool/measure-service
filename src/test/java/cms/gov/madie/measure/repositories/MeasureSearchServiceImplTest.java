package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.*;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.measure.Measure;
import org.bson.Document;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@EnableMongoRepositories(basePackages = "com.gov.madie.measure.repository")
public class MeasureSearchServiceImplTest {

  @Mock MongoTemplate mongoTemplate;
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

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(pagedResults);

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteriaWhenFeatureFlagIsOff(
            "john", pageRequest, null, List.of(OwnershipType.OWNED));
    assertEquals(page.getTotalElements(), 5);
    assertEquals(page.getTotalPages(), 2);
    assertEquals(page.getContent().size(), 3);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getId(), measure1.getId());
    assertEquals(page1Measures.get(1).getId(), measure2.getId());
    assertEquals(page1Measures.get(2).getId(), measure3.getId());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchTerm() {
    PageRequest pageRequest = PageRequest.of(0, 3);

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1, measure2)).count(List.of(1, 2)).build();
    AggregationResults pagedResults = new AggregationResults<>(List.of(facetDTO), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(pagedResults);

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder().searchField("test measure").build();
    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteriaWhenFeatureFlagIsOff(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchTermAndOneOptional() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1, measure2)).count(List.of(1, 2)).build();
    AggregationResults pagedResults = new AggregationResults<>(List.of(facetDTO), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(pagedResults);
    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("test")
            .optionalSearchProperties(List.of("version"))
            .build();
    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteriaWhenFeatureFlagIsOff(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithVersionParts1() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1, measure2)).count(List.of(1, 2)).build();
    AggregationResults pagedResults = new AggregationResults<>(List.of(facetDTO), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(pagedResults);
    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("1")
            .optionalSearchProperties(List.of("version"))
            .build();
    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteriaWhenFeatureFlagIsOff(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithVersionParts2() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1, measure2)).count(List.of(1, 2)).build();
    AggregationResults pagedResults = new AggregationResults<>(List.of(facetDTO), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(pagedResults);
    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("1.0")
            .optionalSearchProperties(List.of("version"))
            .build();
    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteriaWhenFeatureFlagIsOff(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithVersionParts3() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1, measure2)).count(List.of(1, 2)).build();
    AggregationResults pagedResults = new AggregationResults<>(List.of(facetDTO), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(pagedResults);
    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("1.0.0")
            .optionalSearchProperties(List.of("version"))
            .build();
    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteriaWhenFeatureFlagIsOff(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchTermAndMultipleOptional() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1, measure2)).count(List.of(1, 2)).build();
    AggregationResults pagedResults = new AggregationResults<>(List.of(facetDTO), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(pagedResults);
    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("test")
            .optionalSearchProperties(Arrays.asList("measureName", "cmsId", "version"))
            .build();
    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteriaWhenFeatureFlagIsOff(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchTermAndOnlyCmsId() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1, measure2)).count(List.of(1, 2)).build();
    AggregationResults pagedResults = new AggregationResults<>(List.of(facetDTO), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(pagedResults);
    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("test")
            .optionalSearchProperties(List.of("cmsId"))
            .build();
    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteriaWhenFeatureFlagIsOff(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchFieldFilteredByMeasureNameAndFeatureFlagIsOn() {
    var measuresList = List.of(measure1);
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

    assertEquals(page.getTotalElements(), 1);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 1);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals("test-measure-name", page1Measures.get(0).getMeasureName());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchFieldFilteredByCmsIdAndFeatureFlagIsOn() {
    var measuresList = List.of(measure1);
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
            .searchField("28fhir")
            .optionalSearchProperties(List.of("cmsId"))
            .build();

    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(page.getTotalElements(), 1);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 1);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(measure1.getMeasureName(), page1Measures.get(0).getMeasureName());
  }

  @Test
  public void testFindOwnedActiveMeasuresWithSearchFieldFilteredByModel() {
    PageRequest pageRequest = PageRequest.of(0, 3);
    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure3)).count(List.of(1)).build();
    AggregationResults pagedResults = new AggregationResults<>(List.of(facetDTO), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(pagedResults);
    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder()
            .searchField("qdm")
            .optionalSearchProperties(List.of("model"))
            .build();
    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteriaWhenFeatureFlagIsOff(
            "john", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));
    assertEquals(page.getTotalElements(), 1);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 1);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(ModelType.QDM_5_6.getValue(), page1Measures.get(0).getModel());
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
}
