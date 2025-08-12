package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import gov.cms.madie.models.measure.Measure;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
class HtmlifyTextDataTest {

  @Mock MeasureRepository measureRepository;
  @Spy HtmlifyTextData htmlifyTextData;

  private Measure measure;

  @BeforeEach
  void setup() throws IOException {
    ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    Path mock = Path.of("src/test/resources/measure_mock_165FHIR.json");
    measure = objectMapper.readValue(Files.readAllBytes(mock), Measure.class);
  }

  @Test
  void htmlifyTextTest() {
    when(measureRepository.findAllMeasureIdsByActiveAndMeasureMetaDataDraft(true))
        .thenReturn(List.of(measure));

    htmlifyTextData.htmlfiyText(measureRepository);

    ArgumentCaptor<Measure> captor = ArgumentCaptor.forClass(Measure.class);
    verify(measureRepository, times(1)).findAndModify(captor.capture());

    Measure capturedMeasure = captor.getValue();
    assertThat(capturedMeasure).isNotNull();

    assertThat(capturedMeasure.getId()).isEqualTo(measure.getId());
    assertThat(capturedMeasure.getMeasureMetaData().getDescription())
      .isEqualTo("<p>"+measure.getMeasureMetaData().getDescription()+"</p>");
  }

  @Test
  @Disabled
  void rollbackExecutionTest() {}
}
