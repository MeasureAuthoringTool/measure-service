package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cms.gov.madie.measure.exceptions.CqlElmTranslationServiceException;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import cms.gov.madie.measure.utils.ResourceUtil;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureGroupTypes;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;

@ExtendWith(MockitoExtension.class)
class ElmToJsonServiceTest implements ResourceUtil {

  @Mock private ElmTranslatorClient elmTranslatorClient;

  @InjectMocks private ElmToJsonService elmToJsonService;

  private Measure measure;

  @BeforeEach
  public void setUp() {
    Group group =
        Group.builder()
            .id("xyz-p12r-12ert")
            .populationBasis("Encounter")
            .measureGroupTypes(List.of(MeasureGroupTypes.PROCESS))
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "FactorialOfFive",
                        null,
                        null,
                        null)))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    List<Group> groups = new ArrayList<>();
    groups.add(group);
    String elmJson = getData("/test_elm.json");
    MeasureMetaData metaData = MeasureMetaData.builder().draft(true).build();
    measure =
        Measure.builder()
            .active(true)
            .id("xyz-p13r-13ert")
            .cql("test cql")
            .model(ModelType.QDM_5_6.getValue())
            .cqlErrors(false)
            .elmJson(elmJson)
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .groups(groups)
            .measureMetaData(metaData)
            .createdAt(Instant.now())
            .createdBy("test user")
            .lastModifiedAt(Instant.now())
            .lastModifiedBy("test user")
            .build();
  }

  @Test
  void testBundleMeasureThrowsCqlElmTranslationServiceException() {

    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new CqlElmTranslationServiceException(
                "There was an error calling CQL-ELM translation service", new Exception()));
    assertThrows(
        CqlElmTranslationServiceException.class,
        () -> elmToJsonService.retrieveElmJson(measure, "Info", "accessToken"));
  }

  @Test
  void testBundleMeasureWhenThereAreCqlErrors() {
    measure.setCqlErrors(true);
    assertThrows(
        InvalidResourceStateException.class,
        () -> elmToJsonService.retrieveElmJson(measure, "Info", "accessToken"));
  }

  @Test
  void testBundleMeasureWhenThereIsNoCql() {
    measure.setCql(null);
    assertThrows(
        InvalidResourceStateException.class,
        () -> elmToJsonService.retrieveElmJson(measure, "Info", "accessToken"));
  }
}
