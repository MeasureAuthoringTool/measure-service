package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.dto.*;
import cms.gov.madie.measure.locks.MeasureLock;
import cms.gov.madie.measure.services.AppConfigService;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@EnableMongoRepositories(basePackages = "com.gov.madie.measure.repository")
public class MeasureSearchServiceImplTest {

  @Mock MongoTemplate mongoTemplate;
  @Mock AppConfigService appConfigService;
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
    assertEquals(page.getTotalElements(), 3);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 3);
    List<MeasureListDTO> page1Measures = page.getContent();
    assertEquals(page1Measures.get(0).getId(), measure1.getId());
    assertEquals(page1Measures.get(1).getId(), measure2.getId());
    assertEquals(page1Measures.get(2).getId(), measure3.getId());
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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(true);

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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.DISPLAY_OWNER)).thenReturn(true);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.DISPLAY_OWNER)).thenReturn(true);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.DISPLAY_OWNER)).thenReturn(true);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

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
  public void testPopulateOwnerDisplayNamesWithEmptyNames() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.DISPLAY_OWNER)).thenReturn(true);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

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
    assertEquals("-", page.getContent().get(0).getOwnerDisplayName());
  }

  @Test
  public void testPopulateOwnerDisplayNamesWithUserNotFound() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.DISPLAY_OWNER)).thenReturn(true);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.DISPLAY_OWNER)).thenReturn(true);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.DISPLAY_OWNER)).thenReturn(true);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.DISPLAY_OWNER)).thenReturn(true);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.DISPLAY_OWNER)).thenReturn(true);
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measureWithAllowedScoring =
            MeasureListDTO.builder()
                    .id("1")
                    .measureName("measure1")
                    .measureSetId("set1")
                    .build();

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
                    .isFromCompositeMeasureComponents(true)
                    .allowedScoringTypes(Arrays.asList("Cohort", "Continuous Variable"))
                    .build();

    Page<MeasureListDTO> page =
            measureAclRepository.searchMeasuresByCriteria(
                    "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("measure1", page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testSearchMeasuresWithSingleAllowedScoringType() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measureWithCohort =
            MeasureListDTO.builder()
                    .id("1")
                    .measureName("cohort-measure")
                    .measureSetId("set1")
                    .build();

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
                    .isFromCompositeMeasureComponents(true)
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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
            MeasureListDTO.builder()
                    .id("1")
                    .measureName("measure1")
                    .measureSetId("set1")
                    .build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure1)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
            MeasureSearchCriteria.builder()
                    .isFromCompositeMeasureComponents(true)
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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
            MeasureListDTO.builder()
                    .id("1")
                    .measureName("measure1")
                    .measureSetId("set1")
                    .build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure1)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
            MeasureSearchCriteria.builder()
                    .isFromCompositeMeasureComponents(true)
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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
            MeasureListDTO.builder()
                    .id("1")
                    .measureName("measure1")
                    .measureSetId("set1")
                    .build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure1)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
            MeasureSearchCriteria.builder()
                    .isFromCompositeMeasureComponents(false)
                    .allowedScoringTypes(Arrays.asList("Proportion", "Ratio"))
                    .build();

    Page<MeasureListDTO> page =
            measureAclRepository.searchMeasuresByCriteria(
                    "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("measure1", page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testSearchMeasuresWithIsFromCompositeMeasureComponentsNull() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
            MeasureListDTO.builder()
                    .id("1")
                    .measureName("measure1")
                    .measureSetId("set1")
                    .build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure1)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
            MeasureSearchCriteria.builder()
                    .isFromCompositeMeasureComponents(null)
                    .allowedScoringTypes(Arrays.asList("Cohort"))
                    .build();

    Page<MeasureListDTO> page =
            measureAclRepository.searchMeasuresByCriteria(
                    "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));
    assertEquals(1, page.getContent().size());
    assertEquals("measure1", page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testSearchMeasuresWithMultipleAllowedScoringTypes() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
            MeasureListDTO.builder()
                    .id("1")
                    .measureName("measure1")
                    .measureSetId("set1")
                    .build();

    MeasureListDTO measure2 =
            MeasureListDTO.builder()
                    .id("2")
                    .measureName("measure2")
                    .measureSetId("set2")
                    .build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    MeasureSetMatchCountDTO dto2 = MeasureSetMatchCountDTO.builder().measureSetId("set2").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(dto1, dto2), new Document()));

    FacetDTO facetDTO =
            FacetDTO.builder()
                    .queryResults(List.of(measure1, measure2))
                    .count(List.of(1, 2))
                    .build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
            MeasureSearchCriteria.builder()
                    .isFromCompositeMeasureComponents(true)
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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
            MeasureListDTO.builder()
                    .id("1")
                    .measureName("measure1")
                    .measureSetId("set1")
                    .build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure1)).count(List.of(1)).build();
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
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(true);

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
            MeasureListDTO.builder()
                    .id("1")
                    .measureName("measure1")
                    .measureSetId("set1")
                    .build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure1)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
            MeasureSearchCriteria.builder()
                    .isFromCompositeMeasureComponents(true)
                    .allowedScoringTypes(Arrays.asList("Cohort", "Continuous Variable"))
                    .build();

    Page<MeasureListDTO> page =
            measureAclRepository.searchMeasuresByCriteria(
                    "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals("measure1", page.getContent().get(0).getMeasureName());
  }

  @Test
  public void testSearchMeasuresVerifiesCorrectSortingWithScoringFilter() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)).thenReturn(false);

    PageRequest pageRequest = PageRequest.of(0, 10);

    MeasureListDTO measure1 =
            MeasureListDTO.builder()
                    .id("1")
                    .measureName("measure1")
                    .measureSetId("set1")
                    .version(Version.builder().major(2).minor(0).revisionNumber(0).build())
                    .build();

    MeasureSetMatchCountDTO dto1 = MeasureSetMatchCountDTO.builder().measureSetId("set1").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(MeasureSetMatchCountDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(dto1), new Document()));

    FacetDTO facetDTO = FacetDTO.builder().queryResults(List.of(measure1)).count(List.of(1)).build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class),
            ArgumentMatchers.eq(Measure.class),
            ArgumentMatchers.eq(FacetDTO.class)))
            .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    MeasureSearchCriteria measureSearchCriteria =
            MeasureSearchCriteria.builder()
                    .isFromCompositeMeasureComponents(true)
                    .allowedScoringTypes(Arrays.asList("Cohort"))
                    .build();

    Page<MeasureListDTO> page =
            measureAclRepository.searchMeasuresByCriteria(
                    "userId", pageRequest, measureSearchCriteria, List.of(OwnershipType.OWNED));

    assertEquals(1, page.getContent().size());
    assertEquals(Version.builder().major(2).minor(0).revisionNumber(0).build(), page.getContent().get(0).getVersion());
  }
}
