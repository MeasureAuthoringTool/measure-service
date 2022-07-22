package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.ActionLogService;
import cms.gov.madie.measure.services.MeasureService;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureGroupTypes;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class MeasureTransferControllerTest {

  private static final String LAMBDA_TEST_API_KEY = "TOUCH-DOWN";

  private Measure measure;

  @Mock private MeasureService measureService;
  @Mock private MeasureRepository repository;
  @Mock private ActionLogService actionLogService;

  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;
  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;
  @Captor private ArgumentCaptor<String> performedByArgumentCaptor;

  @InjectMocks private MeasureTransferController controller;

  MockHttpServletRequest request;

  @BeforeEach
  public void setUp() {
    request = new MockHttpServletRequest();
    MeasureMetaData measureMetaData = new MeasureMetaData();
    List<Group> groups =
        List.of(
            new Group(
                "id-abc",
                "Cohort",
                List.of(
                    new Population(
                        "id-1", PopulationType.INITIAL_POPULATION, "Initial Population")),
                "Description",
                "improvmentNotation",
                "rateAggragation",
                List.of(MeasureGroupTypes.PROCESS)));

    measureMetaData.setSteward("SB");
    measureMetaData.setCopyright("Copyright@SB");

    measure =
        Measure.builder()
            .id("testId")
            .createdBy("testCreatedBy")
            .measureSetId("abc-pqr-xyz")
            .version("0.000")
            .measureName("MedicationDispenseTest")
            .cqlLibraryName("MedicationDispenseTest")
            .model("QI-Core")
            .measureMetaData(measureMetaData)
            .groups(groups)
            .cql("library MedicationDispenseTest version '0.0.001' using FHIR version '4.0.1'")
            .build();
  }

  @Test
  public void createMeasureSuccessTest() {
    ArgumentCaptor<Measure> persistedMeasureArgCaptor = ArgumentCaptor.forClass(Measure.class);
    doNothing().when(measureService).checkDuplicateCqlLibraryName(any(String.class));
    doReturn(measure).when(repository).save(any(Measure.class));

    ResponseEntity<Measure> response =
        controller.createMeasure(request, measure, LAMBDA_TEST_API_KEY);

    verify(repository, times(1)).save(persistedMeasureArgCaptor.capture());
    Measure persistedMeasure = response.getBody();
    assertNotNull(persistedMeasure);

    assertEquals(measure.getMeasureSetId(), persistedMeasure.getMeasureSetId());
    assertEquals(measure.getMeasureName(), persistedMeasure.getMeasureName());
    assertEquals(measure.getCqlLibraryName(), persistedMeasure.getCqlLibraryName());
    assertEquals(measure.getCql(), persistedMeasure.getCql());
    assertEquals(measure.getGroups().size(), persistedMeasure.getGroups().size());

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            performedByArgumentCaptor.capture());
    assertNotNull(targetIdArgumentCaptor.getValue());
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.IMPORTED)));
    assertThat(performedByArgumentCaptor.getValue(), is(equalTo("testCreatedBy")));
  }

  @Test
  public void createMeasureDuplicateCqlLibraryTest() {
    doThrow(new DuplicateKeyException("cqlLibraryName", "CQL library already exists."))
        .when(measureService)
        .checkDuplicateCqlLibraryName(any(String.class));

    assertThrows(
        DuplicateKeyException.class,
        () -> controller.createMeasure(request, measure, LAMBDA_TEST_API_KEY));
  }
}
