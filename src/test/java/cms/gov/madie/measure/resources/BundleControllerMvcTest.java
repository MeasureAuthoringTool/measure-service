package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.config.security.SecurityConfigTest;
import cms.gov.madie.measure.exceptions.CqlElmTranslationServiceException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.BundleService;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureScoring;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({BundleController.class})
@ActiveProfiles("test")
@Import(SecurityConfigTest.class)
public class BundleControllerMvcTest {

  @MockitoBean private MeasureRepository measureRepository;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BundleService bundleService;

  private static final String TEST_USER_ID = "test-okta-user-id-123";

  @Test
  void getMeasureBundleReturnsNotFound() throws Exception {
    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());
    mockMvc
        .perform(
            get("/measures/1234/bundle")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
    verify(measureRepository, times(1)).findById(eq("1234"));
    verifyNoInteractions(bundleService);
  }

  @Test
  void testGetMeasureBundleReturnsServerExceptionForCqlElmTranslationFailure() throws Exception {
    final String elmJson = "{\"text\": \"ELM JSON\"}";
    Measure measure =
        Measure.builder()
            .measureName("EXM124")
            .createdBy(TEST_USER_ID)
            .cqlErrors(false)
            .groups(
                List.of(
                    Group.builder()
                        .groupDescription("Group1")
                        .scoring(MeasureScoring.RATIO.toString())
                        .build()))
            .elmJson(elmJson)
            .build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(bundleService.bundleMeasure(any(Measure.class), anyString(), anyString(), anyString()))
        .thenThrow(
            new CqlElmTranslationServiceException(
                measure.getMeasureName(), new RuntimeException("CAUSE")));
    mockMvc
        .perform(
            get("/measures/1234/bundle")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError());
    verify(measureRepository, times(1)).findById(anyString());
  }

  @Test
  void testGetMeasureBundleReturnsMeasureBundle() throws Exception {
    final String elmJson = "{\"text\": \"ELM JSON\"}";
    final String bundleString =
        "{\n"
            + "    \"resourceType\": \"Bundle\",\n"
            + "    \"type\": \"transaction\",\n"
            + "    \"entry\": [\n"
            + "        {\n"
            + "            \"resource\": {\n"
            + "                \"resourceType\": \"Measure\",\n"
            + "                \"meta\": {\n"
            + "                    \"profile\": [\n"
            + "                        \"http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/proportion-measure-cqfm\"\n"
            + "                    ]\n"
            + "                },\n"
            + "                \"url\": \"http://hl7.org/fhir/us/cqfmeasures/Measure/EXM124\",\n"
            + "                \"version\": \"9.0.000\",\n"
            + "                \"name\": \"EXM124\",\n"
            + "                \"title\": \"Cervical Cancer Screening\",\n"
            + "                \"experimental\": true,\n"
            + "                \"effectivePeriod\": {\n"
            + "                    \"start\": \"2022-05-09\",\n"
            + "                    \"end\": \"2022-05-09\"\n"
            + "                }\n"
            + "            }\n"
            + "        }\n"
            + "    ]\n"
            + "}";
    Measure measure =
        Measure.builder()
            .measureName("EXM124")
            .createdBy(TEST_USER_ID)
            .cqlErrors(false)
            .groups(
                List.of(
                    Group.builder()
                        .groupDescription("Group1")
                        .scoring(MeasureScoring.RATIO.toString())
                        .build()))
            .elmJson(elmJson)
            .build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(bundleService.bundleMeasure(any(Measure.class), anyString(), anyString(), anyString()))
        .thenReturn(bundleString);
    mockMvc
        .perform(
            get("/measures/1234/bundle")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resourceType").value("Bundle"))
        .andExpect(jsonPath("$.type").value("transaction"))
        .andExpect(jsonPath("$.entry[0].resource.resourceType").value("Measure"))
        .andExpect(jsonPath("$.entry[0].resource.name").value("EXM124"))
        .andExpect(jsonPath("$.entry[0].resource.version").value("9.0.000"));
    verify(measureRepository, times(1)).findById(eq("1234"));
  }
}
