package cms.gov.madie.measure.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@ExtendWith(MockitoExtension.class)
class MeasureSetSearchRepositoryImplTest {

  @InjectMocks private MeasureSetSearchRepositoryImpl repository;

  @Mock private MongoTemplate mongoTemplate;

  private static final String MEASURE_SET_ID = "set-123";

  @Test
  void shouldReturnMeasuresWithoutSortAndWithoutSearchCriteria() {
    List<MeasureListDTO> mockResults = List.of(createDTO("Measure 1"), createDTO("Measure 2"));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("measure"), eq(MeasureListDTO.class)))
        .thenReturn(new AggregationResults<>(mockResults, new Document()));

    List<MeasureListDTO> result =
        repository.findMeasuresByMeasureSetId(MEASURE_SET_ID, false, null);

    assertEquals(2, result.size());
    assertEquals("Measure 1", result.get(0).getMeasureName());

    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate).aggregate(captor.capture(), eq("measure"), eq(MeasureListDTO.class));

    Aggregation aggregation = captor.getValue();
    assertEquals(3, aggregation.getPipeline().getOperations().size());
  }

  @Test
  void shouldReturnMeasuresWithSortByLatestVersion() {
    List<MeasureListDTO> mockResults = List.of(createDTO("Latest Measure"));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("measure"), eq(MeasureListDTO.class)))
        .thenReturn(new AggregationResults<>(mockResults, new Document()));

    List<MeasureListDTO> result = repository.findMeasuresByMeasureSetId(MEASURE_SET_ID, true, null);

    assertEquals("Latest Measure", result.get(0).getMeasureName());

    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate).aggregate(captor.capture(), eq("measure"), eq(MeasureListDTO.class));

    Aggregation aggregation = captor.getValue();
    assertEquals(4, aggregation.getPipeline().getOperations().size());
  }

  @Test
  void shouldApplySearchCriteriaWhenPresent() {
    MeasureSearchCriteria criteria = new MeasureSearchCriteria();
    criteria.setSearchField("1.2.3");
    criteria.setOptionalSearchProperties(List.of("version"));

    List<MeasureListDTO> mockResults = List.of(createDTO("CMS123 Measure"));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("measure"), eq(MeasureListDTO.class)))
        .thenReturn(new AggregationResults<>(mockResults, new Document()));

    List<MeasureListDTO> result =
        repository.findMeasuresByMeasureSetId(MEASURE_SET_ID, false, criteria);

    assertEquals("CMS123 Measure", result.get(0).getMeasureName());

    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate).aggregate(captor.capture(), eq("measure"), eq(MeasureListDTO.class));

    Aggregation aggregation = captor.getValue();
    assertEquals(3, aggregation.getPipeline().getOperations().size());
  }

  @Test
  void shouldApplyCmsIdSearchCriteriaAndReturnMatchingMeasures() {
    MeasureSearchCriteria criteria = new MeasureSearchCriteria();
    criteria.setSearchField("117");
    criteria.setOptionalSearchProperties(List.of("cmsId"));

    List<MeasureListDTO> mockResults =
        List.of(createDTO("Measure with CMS 117"), createDTO("Measure with CMS 117FHIR"));

    when(mongoTemplate.aggregate(any(Aggregation.class), eq("measure"), eq(MeasureListDTO.class)))
        .thenReturn(new AggregationResults<>(mockResults, new Document()));

    List<MeasureListDTO> result =
        repository.findMeasuresByMeasureSetId(MEASURE_SET_ID, false, criteria);

    assertEquals(2, result.size());
    assertEquals("Measure with CMS 117", result.get(0).getMeasureName());
    assertEquals("Measure with CMS 117FHIR", result.get(1).getMeasureName());

    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate).aggregate(captor.capture(), eq("measure"), eq(MeasureListDTO.class));

    Aggregation aggregation = captor.getValue();

    assertEquals(4, aggregation.getPipeline().getOperations().size());
    String pipelineString = aggregation.toString();

    org.assertj.core.api.Assertions.assertThat(pipelineString).contains("cmsIdDisplay");
  }

  private MeasureListDTO createDTO(String name) {
    MeasureListDTO dto = new MeasureListDTO();
    dto.setMeasureName(name);
    dto.setMeasureSetId(MEASURE_SET_ID);
    dto.setActive(true);
    return dto;
  }
}
