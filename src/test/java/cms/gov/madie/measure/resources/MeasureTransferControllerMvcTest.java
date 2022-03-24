package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.models.Group;
import cms.gov.madie.measure.models.Measure;
import cms.gov.madie.measure.models.MeasureMetaData;
import cms.gov.madie.measure.models.MeasurePopulation;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.MeasureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({MeasureTransferController.class})
public class MeasureTransferControllerMvcTest {
  private static final String HARP_ID_HEADER_KEY = "harp-id";
  private static final String HARP_ID_HEADER_VALUE = "XxYyZz";
  private static final String LAMBDA_TEST_API_KEY_HEADER = "api-key";
  private static final String LAMBDA_TEST_API_KEY_HEADER_VALUE = "9202c9fa";

  @MockBean private MeasureRepository measureRepository;
  @MockBean private MeasureService measureService;

  @Autowired private MockMvc mockMvc;

  Measure measure;

  @BeforeEach
  public void setUp() {
    MeasureMetaData measureMetaData = new MeasureMetaData();
    List<Group> groups =
        List.of(
            new Group(
                "id-abc",
                "Cohort",
                Map.of(MeasurePopulation.INITIAL_POPULATION, "Initial Population")));

    measureMetaData.setSteward("SB");
    measureMetaData.setCopyright("Copyright@SB");

    measure =
        Measure.builder()
            .measureSetId("abc-pqr-xyz")
            .version("0.000")
            .measureName("MedicationDispenseTest")
            .cqlLibraryName("MedicationDispenseTest")
            .measureScoring("Cohort")
            .model("QI-Core")
            .measureMetaData(measureMetaData)
            .groups(groups)
            .cql("library MedicationDispenseTest version '0.0.001' using FHIR version '4.0.1'")
            .build();
  }

  @Test
  public void testCreateMeasureSuccess() throws Exception {
    String measureJson = new ObjectMapper().writeValueAsString(measure);

    ArgumentCaptor<Measure> persistedMeasureArgCaptor = ArgumentCaptor.forClass(Measure.class);

    doNothing().when(measureService).checkDuplicateCqlLibraryName(any(String.class));
    doReturn(measure).when(measureRepository).save(any(Measure.class));

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/measure-transfer/mat-measures")
                .content(measureJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .header(LAMBDA_TEST_API_KEY_HEADER, LAMBDA_TEST_API_KEY_HEADER_VALUE)
                .header(HARP_ID_HEADER_KEY, HARP_ID_HEADER_VALUE))
        .andExpect(status().isCreated());

    verify(measureRepository, times(1)).save(persistedMeasureArgCaptor.capture());
    Measure persistedMeasure = persistedMeasureArgCaptor.getValue();
    assertNotNull(persistedMeasure);

    assertEquals(measure.getMeasureSetId(), persistedMeasure.getMeasureSetId());
    assertEquals(measure.getMeasureName(), persistedMeasure.getMeasureName());
    assertEquals(measure.getCqlLibraryName(), persistedMeasure.getCqlLibraryName());
    assertEquals(measure.getMeasureScoring(), persistedMeasure.getMeasureScoring());
    assertEquals(measure.getCql(), persistedMeasure.getCql());
    assertEquals(measure.getGroups().size(), persistedMeasure.getGroups().size());
  }

  @Test
  public void testCreateMeasureFailureWhenDuplicateLibraryName() throws Exception {
    String measureJson = new ObjectMapper().writeValueAsString(measure);

    doThrow(new DuplicateKeyException("cqlLibraryName", "CQL library already exists."))
        .when(measureService)
        .checkDuplicateCqlLibraryName(any(String.class));

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/measure-transfer/mat-measures")
                .content(measureJson)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .header(LAMBDA_TEST_API_KEY_HEADER, LAMBDA_TEST_API_KEY_HEADER_VALUE)
                .header(HARP_ID_HEADER_KEY, HARP_ID_HEADER_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName").value("CQL library already exists."));

    verify(measureService, times(1)).checkDuplicateCqlLibraryName(eq(measure.getCqlLibraryName()));
  }

  @Test
  public void testCreateMeasureFailureWhenInvalidApiKey() throws Exception {
    String measureJson = new ObjectMapper().writeValueAsString(measure);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/measure-transfer/mat-measures")
                    .content(measureJson)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .header(LAMBDA_TEST_API_KEY_HEADER, "invalid-api-key")
                  .header(HARP_ID_HEADER_KEY, HARP_ID_HEADER_VALUE))
            .andExpect(status().isUnauthorized())
            .andReturn();

    assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus());
  }
}
