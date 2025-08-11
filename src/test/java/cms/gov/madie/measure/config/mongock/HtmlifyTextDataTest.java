package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
class HtmlifyTextDataTest {

  HtmlifyTextData htmlifyTextData = new HtmlifyTextData();

  @Mock MeasureRepository measureRepository;
  @Mock MongoOperations mongoOperations;
  @Captor ArgumentCaptor<Measure> measureCaptor;

  private Measure measure;

  @BeforeEach
  void setup() throws IOException {
    measure =
        new Measure()
            .toBuilder()
                .id("id")
                .active(true)
                .measureMetaData(
                    MeasureMetaData.builder()
                        .draft(true)
                        .description("measureDesc")
                        .rationale("rationale")
                        .purpose("purpose")
                        .guidance("guidance")
                        .clinicalRecommendation("clinicalRecommendation")
                        .references(
                            List.of(
                                Reference.builder()
                                    .referenceText("reference1")
                                    .referenceType("CITATION")
                                    .build()))
                        .measureDefinitions(
                            List.of(
                                MeasureDefinition.builder()
                                    .definition("definition1")
                                    .term("term")
                                    .build()))
                        .copyright("measure Copyright")
                        .disclaimer("disclaimer")
                        .build())
                .groups(
                    List.of(
                        Group.builder()
                            .groupDescription("group 1 Description")
                            .rateAggregation("rate agg")
                            .improvementNotationDescription("line go up")
                            .populations(
                                List.of(
                                    Population.builder()
                                        .description("population 1 Description")
                                        .build()))
                            .stratifications(
                                List.of(
                                    Stratification.builder()
                                        .description("stratification 1 Description")
                                        .build()))
                            .build()))
                .supplementalDataDescription("supplemental description")
                .supplementalData(
                    List.of(
                        DefDescPair.builder()
                            .description("sde desc")
                            .definition("sdeDefName")
                            .build()))
                .riskAdjustmentDescription("risk adjustment description")
                .riskAdjustments(
                    List.of(
                        DefDescPair.builder()
                            .description("rav desc")
                            .definition("ravDefName")
                            .build()))
                .build();
  }

  private Measure givenAnHtmlifiedMeasure() {
    when(mongoOperations.find(any(Query.class), any())).thenReturn(List.of(measure));

    htmlifyTextData.htmlfiyText(measureRepository, mongoOperations);
    verify(measureRepository).save(measureCaptor.capture());

    Measure capturedMeasure = measureCaptor.getValue();
    assertThat(capturedMeasure).isNotNull();
    return capturedMeasure;
  }

  @Test
  void testSimpleMetaDataFieldsHtmlify() {
    Measure htmlifiedMeasure = givenAnHtmlifiedMeasure();

    assertThat(htmlifiedMeasure.getId()).isEqualTo(measure.getId());
    assertThat(htmlifiedMeasure.getMeasureMetaData()).isNotNull();
    MeasureMetaData metaData = htmlifiedMeasure.getMeasureMetaData();
    assertThat(metaData.getDescription())
        .isEqualTo("<p>" + measure.getMeasureMetaData().getDescription() + "</p>");
    assertThat(metaData.getRationale())
        .isEqualTo("<p>" + measure.getMeasureMetaData().getRationale() + "</p>");
    assertThat(metaData.getPurpose())
        .isEqualTo("<p>" + measure.getMeasureMetaData().getPurpose() + "</p>");
    assertThat(metaData.getGuidance())
        .isEqualTo("<p>" + measure.getMeasureMetaData().getGuidance() + "</p>");
    assertThat(metaData.getClinicalRecommendation())
        .isEqualTo("<p>" + measure.getMeasureMetaData().getClinicalRecommendation() + "</p>");
    assertThat(metaData.getCopyright())
        .isEqualTo("<p>" + measure.getMeasureMetaData().getCopyright() + "</p>");
    assertThat(metaData.getDisclaimer())
        .isEqualTo("<p>" + measure.getMeasureMetaData().getDisclaimer() + "</p>");
  }

  @Test
  void testListMetaDataFieldsHtmlify() {
    Measure htmlifiedMeasure = givenAnHtmlifiedMeasure();

    assertThat(htmlifiedMeasure.getId()).isEqualTo(measure.getId());
    assertThat(htmlifiedMeasure.getMeasureMetaData()).isNotNull();

    MeasureMetaData metaData = htmlifiedMeasure.getMeasureMetaData();
    assertThat(metaData.getReferences()).hasSize(1);
    assertThat(metaData.getReferences().get(0).getReferenceText())
        .isEqualTo(
            "<p>"
                + measure.getMeasureMetaData().getReferences().get(0).getReferenceText()
                + "</p>");

    assertThat(metaData.getMeasureDefinitions()).hasSize(1);
    assertThat(metaData.getMeasureDefinitions().get(0).getDefinition())
        .isEqualTo(
            "<p>"
                + measure.getMeasureMetaData().getMeasureDefinitions().get(0).getDefinition()
                + "</p>");
    assertThat(metaData.getMeasureDefinitions().get(0).getTerm())
        .isEqualTo(measure.getMeasureMetaData().getMeasureDefinitions().get(0).getTerm());
  }

  @Test
  void testPopulationCriteriaTextFieldsHtmlify() {
    Measure htmlifiedMeasure = givenAnHtmlifiedMeasure();

    assertThat(htmlifiedMeasure.getId()).isEqualTo(measure.getId());
    assertThat(htmlifiedMeasure.getGroups()).hasSize(1);
    assertThat(htmlifiedMeasure.getGroups().get(0).getGroupDescription())
        .isEqualTo("<p>" + measure.getGroups().get(0).getGroupDescription() + "</p>");
    assertThat(htmlifiedMeasure.getGroups().get(0).getPopulations().get(0).getDescription())
        .isEqualTo(
            "<p>" + measure.getGroups().get(0).getPopulations().get(0).getDescription() + "</p>");
    assertThat(htmlifiedMeasure.getGroups().get(0).getStratifications().get(0).getDescription())
        .isEqualTo(
            "<p>"
                + measure.getGroups().get(0).getStratifications().get(0).getDescription()
                + "</p>");
    assertThat(htmlifiedMeasure.getGroups().get(0).getRateAggregation())
        .isEqualTo("<p>" + measure.getGroups().get(0).getRateAggregation() + "</p>");
    assertThat(htmlifiedMeasure.getGroups().get(0).getImprovementNotationDescription())
        .isEqualTo("<p>" + measure.getGroups().get(0).getImprovementNotationDescription() + "</p>");
  }

  @Test
  void testRavAndSdesHtmlify() {
    Measure htmlifiedMeasure = givenAnHtmlifiedMeasure();

    assertThat(htmlifiedMeasure.getId()).isEqualTo(measure.getId());
    assertThat(htmlifiedMeasure.getRiskAdjustments()).hasSize(1);
    assertThat(htmlifiedMeasure.getRiskAdjustments().get(0).getDescription())
        .isEqualTo("<p>" + measure.getRiskAdjustments().get(0).getDescription() + "</p>");
    assertThat(htmlifiedMeasure.getRiskAdjustments().get(0).getDefinition())
        .isEqualTo(measure.getRiskAdjustments().get(0).getDefinition());

    assertThat(htmlifiedMeasure.getSupplementalData()).hasSize(1);
    assertThat(htmlifiedMeasure.getSupplementalData().get(0).getDescription())
        .isEqualTo("<p>" + measure.getSupplementalData().get(0).getDescription() + "</p>");
    assertThat(htmlifiedMeasure.getSupplementalData().get(0).getDefinition())
        .isEqualTo(measure.getSupplementalData().get(0).getDefinition());
  }

  @Test
  void testHtmlifyQdmMeasure() {
    QdmMeasure qdmMeasure = new QdmMeasure();
    qdmMeasure.setId("qdm-id");
    qdmMeasure.setActive(true);
    qdmMeasure.setModel(ModelType.QDM_5_6.toString());
    qdmMeasure.setRateAggregation("QDM Rate Aggregation");
    qdmMeasure.setImprovementNotationDescription("QDM Improvement Notation Description");
    qdmMeasure.setMeasureMetaData(
        MeasureMetaData.builder()
            .draft(true)
            .transmissionFormat("QDM Transmission Format")
            .definition("QDM Definition")
            .measureSetTitle("QDM Measure Set Title")
            .build());

    when(mongoOperations.find(any(Query.class), any())).thenReturn(List.of(qdmMeasure));
    htmlifyTextData.htmlfiyText(measureRepository, mongoOperations);
    verify(measureRepository).save(measureCaptor.capture());

    Measure capturedMeasure = measureCaptor.getValue();
    assertThat(capturedMeasure).isNotNull();
    assertThat(capturedMeasure).isInstanceOf(QdmMeasure.class);
    assertThat(capturedMeasure.getId()).isEqualTo(qdmMeasure.getId());
    assertThat(capturedMeasure.getModel()).isEqualTo(ModelType.QDM_5_6.toString());

    if (capturedMeasure instanceof QdmMeasure capturedQdmMeasure) {
      assertThat(capturedQdmMeasure.getRateAggregation())
          .isEqualTo("<p>" + qdmMeasure.getRateAggregation() + "</p>");
      assertThat(capturedQdmMeasure.getImprovementNotationDescription())
          .isEqualTo("<p>" + qdmMeasure.getImprovementNotationDescription() + "</p>");
    }

    assertThat(capturedMeasure.getMeasureMetaData()).isNotNull();
    MeasureMetaData metaData = capturedMeasure.getMeasureMetaData();
    assertThat(metaData.getTransmissionFormat())
        .isEqualTo("<p>" + qdmMeasure.getMeasureMetaData().getTransmissionFormat() + "</p>");
    assertThat(metaData.getDefinition())
        .isEqualTo("<p>" + qdmMeasure.getMeasureMetaData().getDefinition() + "</p>");
    assertThat(metaData.getMeasureSetTitle())
        .isEqualTo("<p>" + qdmMeasure.getMeasureMetaData().getMeasureSetTitle() + "</p>");
  }
}
