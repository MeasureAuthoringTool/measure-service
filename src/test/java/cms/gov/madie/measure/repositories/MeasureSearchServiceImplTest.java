package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.FacetDTO;
import cms.gov.madie.measure.dto.MadieFeatureFlag;
import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.services.AppConfigService;
import gov.cms.madie.models.dto.LibraryUsage;
import org.bson.Document;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  @Mock AppConfigService appConfigService;
  @InjectMocks MeasureSearchServiceImpl measureAclRepository;

  private MeasureListDTO measure1;
  private MeasureListDTO measure2;
  private MeasureListDTO measure3;
  private MeasureListDTO measure4;
  private MeasureListDTO measure5;

  @BeforeEach
  void setup() {
    measure1 =
        MeasureListDTO.builder().id("1").ecqmTitle("test measure 1").measureSetId("1-1").build();
    measure2 =
        MeasureListDTO.builder().id("2").ecqmTitle("test measure 2").measureSetId("2-2").build();
    measure3 = MeasureListDTO.builder().id("3").measureSetId("3-3").build();
    measure4 = MeasureListDTO.builder().id("4").measureSetId("4-4").build();
    measure5 = MeasureListDTO.builder().id("5").measureSetId("5-5").build();
  }

  @Test
  public void testFindMyActiveMeasures() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.MEASURE_SEARCH)).thenReturn(false);
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
        measureAclRepository.searchMeasuresByCriteria("john", pageRequest, null, true);
    assertEquals(page.getTotalElements(), 5);
    assertEquals(page.getTotalPages(), 2);
    assertEquals(page.getContent().size(), 3);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getId(), measure1.getId());
    assertEquals(page1Measures.get(1).getId(), measure2.getId());
    assertEquals(page1Measures.get(2).getId(), measure3.getId());
  }

  @Test
  public void testFindMyActiveMeasuresWithSearchTerm() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.MEASURE_SEARCH)).thenReturn(false);
    PageRequest pageRequest = PageRequest.of(0, 3);

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(measure1, measure2)).count(List.of(1, 2)).build();
    AggregationResults pagedResults = new AggregationResults<>(List.of(facetDTO), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(pagedResults);

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder().searchField("test measure").build();
    Page<MeasureListDTO> page =
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, true);
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindMyActiveMeasuresWithSearchTermAndOneOptional() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.MEASURE_SEARCH)).thenReturn(false);
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
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, true);
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindMyActiveMeasuresWithVersionparts1() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.MEASURE_SEARCH)).thenReturn(false);
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
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, true);
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindMyActiveMeasuresWithVersionparts2() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.MEASURE_SEARCH)).thenReturn(false);
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
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, true);
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindMyActiveMeasuresWithVersionparts3() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.MEASURE_SEARCH)).thenReturn(false);
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
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, true);
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindMyActiveMeasuresWithSearchTermAndMultipleOptional() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.MEASURE_SEARCH)).thenReturn(false);
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
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, true);
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
  }

  @Test
  public void testFindMyActiveMeasuresWithSearchTermAndOnlyCmsId() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.MEASURE_SEARCH)).thenReturn(false);
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
        measureAclRepository.searchMeasuresByCriteria(
            "john", pageRequest, measureSearchCriteria, true);
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getEcqmTitle(), measure1.getEcqmTitle());
    assertEquals(page1Measures.get(1).getEcqmTitle(), measure2.getEcqmTitle());
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
  void testCountAllMyMeasures() {
    String owner = "john";
    Map<String, String> resultMap = new HashMap<>();
    resultMap.put("count", "5");
    AggregationResults result = new AggregationResults<>(List.of(resultMap), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(result);
    int count = measureAclRepository.countAllMyMeasures(true, owner);
    assertEquals(count, 5);
  }

  @Test
  void testCountAllMyMeasuresReturnZero() {
    String owner = "john";
    AggregationResults result = new AggregationResults<>(new ArrayList<>(), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
            .thenReturn(result);
    int count = measureAclRepository.countAllMyMeasures(true, owner);
    assertEquals(count, 0);
  }

  void testCountAllMeasures() {
    Map<String, String> resultMap = new HashMap<>();
    resultMap.put("count", "5");
    AggregationResults result = new AggregationResults<>(List.of(resultMap), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
            .thenReturn(result);
    int count = measureAclRepository.countAllMeasures(true);
    assertEquals(count, 5);
  }

  @Test
  void testCountAllMeasuresReturnZero() {
    AggregationResults result = new AggregationResults<>(new ArrayList<>(), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
            .thenReturn(result);
    int count = measureAclRepository.countAllMeasures(true);
    assertEquals(count, 0);
  }
}
