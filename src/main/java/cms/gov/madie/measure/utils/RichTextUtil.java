package cms.gov.madie.measure.utils;

import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.QdmMeasure;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class RichTextUtil {
  private static final Parser parser = Parser.builder().build();
  private static final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();
  private static final Safelist RICH_TEXT_SAFE_LIST =
      Safelist.basic()
          .addTags("s", "br", "table", "tbody", "td", "th", "thead", "tr", "col", "colgroup", "del")
          .addAttributes("table", "style", "class", "id")
          .addAttributes("th", "rowspan", "colspan", "style", "colwidth")
          .addAttributes("td", "rowspan", "colspan", "style", "colwidth")
          .addAttributes("col", "style");

  public static void htmlifyMeasureRichTextContents(Measure measure) {
    if (measure == null) {
      return;
    }
    htmlifyMeasureMetaData(measure);
    htmlifyPopulationCriteria(measure);
    htmlifyRavAndSdes(measure);
    if (measure instanceof QdmMeasure qdmMeasure) {
      htmlifyReporting(qdmMeasure);
    }
  }

  private static void htmlifyReporting(QdmMeasure qdmMeasure) {
    if (StringUtils.isNotBlank(qdmMeasure.getRateAggregation())) {
      qdmMeasure.setRateAggregation(toHtml(qdmMeasure.getRateAggregation()));
    }
    if (StringUtils.isNotBlank(qdmMeasure.getImprovementNotationDescription())) {
      qdmMeasure.setImprovementNotationDescription(
          toHtml(qdmMeasure.getImprovementNotationDescription()));
    }
  }

  private static void htmlifyRavAndSdes(Measure msr) {
    if (StringUtils.isNotBlank(msr.getRiskAdjustmentDescription())) {
      msr.setRiskAdjustmentDescription(toHtml(msr.getRiskAdjustmentDescription()));
    }
    if (CollectionUtils.isNotEmpty(msr.getRiskAdjustments())) {
      msr.getRiskAdjustments()
          .forEach(
              rav -> {
                if (StringUtils.isNotBlank(rav.getDescription())) {
                  rav.setDescription(toHtml(rav.getDescription()));
                }
              });
    }
    if (StringUtils.isNotBlank(msr.getSupplementalDataDescription())) {
      msr.setSupplementalDataDescription(toHtml(msr.getSupplementalDataDescription()));
    }
    if (CollectionUtils.isNotEmpty(msr.getSupplementalData())) {
      msr.getSupplementalData()
          .forEach(
              sde -> {
                if (StringUtils.isNotBlank(sde.getDescription())) {
                  sde.setDescription(toHtml(sde.getDescription()));
                }
              });
    }
  }

  private static void htmlifyPopulationCriteria(Measure msr) {
    if (CollectionUtils.isNotEmpty(msr.getGroups())) {
      msr.getGroups()
          .forEach(
              group -> {
                if (StringUtils.isNotBlank(group.getGroupDescription())) {
                  group.setGroupDescription(toHtml(group.getGroupDescription()));
                }
                if (CollectionUtils.isNotEmpty(group.getPopulations())) {
                  group
                      .getPopulations()
                      .forEach(
                          population -> {
                            if (StringUtils.isNotBlank(population.getDescription())) {
                              population.setDescription(toHtml(population.getDescription()));
                            }
                          });
                }
                if (CollectionUtils.isNotEmpty(group.getStratifications())) {
                  group
                      .getStratifications()
                      .forEach(
                          stratification -> {
                            if (StringUtils.isNotBlank(stratification.getDescription())) {
                              stratification.setDescription(
                                  toHtml(stratification.getDescription()));
                            }
                          });
                }
                if (StringUtils.isNotBlank(group.getRateAggregation())) {
                  group.setRateAggregation(toHtml(group.getRateAggregation()));
                }
                if (StringUtils.isNotBlank(group.getImprovementNotationDescription())) {
                  group.setImprovementNotationDescription(
                      toHtml(group.getImprovementNotationDescription()));
                }
              });
    }
  }

  private static void htmlifyMeasureMetaData(Measure msr) {
    if (msr == null || msr.getMeasureMetaData() == null) {
      return;
    }
    MeasureMetaData measureMetaData = msr.getMeasureMetaData();
    if (StringUtils.isNotBlank(measureMetaData.getDescription())) {
      measureMetaData.setDescription(toHtml(measureMetaData.getDescription()));
    }
    if (StringUtils.isNotBlank(measureMetaData.getRationale())) {
      measureMetaData.setRationale(toHtml(measureMetaData.getRationale()));
    }
    if (StringUtils.isNotBlank(measureMetaData.getPurpose())) {
      measureMetaData.setPurpose(toHtml(measureMetaData.getPurpose()));
    }
    if (StringUtils.isNotBlank(measureMetaData.getGuidance())) {
      measureMetaData.setGuidance(toHtml(measureMetaData.getGuidance()));
    }
    if (StringUtils.isNotBlank(measureMetaData.getClinicalRecommendation())) {
      measureMetaData.setClinicalRecommendation(
          toHtml(measureMetaData.getClinicalRecommendation()));
    }
    if (StringUtils.isNotBlank(measureMetaData.getCopyright())) {
      measureMetaData.setCopyright(toHtml(measureMetaData.getCopyright()));
    }
    if (StringUtils.isNotBlank(measureMetaData.getDisclaimer())) {
      measureMetaData.setDisclaimer(toHtml(measureMetaData.getDisclaimer()));
    }
    if (CollectionUtils.isNotEmpty(measureMetaData.getReferences())) {
      measureMetaData
          .getReferences()
          .forEach(
              reference -> {
                if (StringUtils.isNotBlank(reference.getReferenceText())) {
                  reference.setReferenceText(toHtml(reference.getReferenceText()));
                }
              });
    }
    if (CollectionUtils.isNotEmpty(measureMetaData.getMeasureDefinitions())) {
      measureMetaData
          .getMeasureDefinitions()
          .forEach(
              msrDefinition -> {
                if (StringUtils.isNotBlank(msrDefinition.getDefinition())) {
                  msrDefinition.setDefinition(toHtml(msrDefinition.getDefinition()));
                }
              });
    }
    if (msr instanceof QdmMeasure qdmMeasure) {
      if (StringUtils.isNotBlank(qdmMeasure.getMeasureMetaData().getTransmissionFormat())) {
        qdmMeasure
            .getMeasureMetaData()
            .setTransmissionFormat(toHtml(qdmMeasure.getMeasureMetaData().getTransmissionFormat()));
      }
      if (StringUtils.isNotBlank(qdmMeasure.getMeasureMetaData().getDefinition())) {
        qdmMeasure
            .getMeasureMetaData()
            .setDefinition(toHtml(qdmMeasure.getMeasureMetaData().getDefinition()));
      }
      if (StringUtils.isNotBlank(qdmMeasure.getMeasureMetaData().getMeasureSetTitle())) {
        qdmMeasure
            .getMeasureMetaData()
            .setMeasureSetTitle(toHtml(qdmMeasure.getMeasureMetaData().getMeasureSetTitle()));
      }
    }
  }

  private static String toHtml(String text) {
    if (text.startsWith("<p>")) {
      return text; // Already HTML formatted, return as is
    }

    Node document = parser.parse(text);
    // Sanitize HTML content
    return sanitizeText(htmlRenderer.render(document)).replace("\n", "");
  }

  private static String sanitizeText(String val) {
    if (StringUtils.isBlank(val)) {
      return val;
    }
    String safeHtml = Jsoup.clean(val, RICH_TEXT_SAFE_LIST);
    // col tags are not self-closing in html, so we need to close them to make them well-formed
    return safeHtml.replaceAll("<col ([^/>]*)>", "<col $1 />");
  }
}
