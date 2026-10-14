package cms.gov.madie.measure.utils;

import gov.cms.madie.models.measure.DefDescPair;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureDefinition;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.MeasureObservation;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.Reference;
import gov.cms.madie.models.measure.Stratification;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.util.CollectionUtils;

public class HtmlSanitizerUtil {
  private static final Safelist RICH_TEXT_SAFE_LIST =
      Safelist.basic()
          .addTags("s", "br", "table", "tbody", "td", "th", "thead", "tr", "col", "colgroup", "del")
          .addAttributes("table", "style", "class", "id")
          .addAttributes("th", "rowspan", "colspan", "style", "colwidth")
          .addAttributes("td", "rowspan", "colspan", "style", "colwidth")
          .addAttributes("col", "style");

  public static String sanitize(String val) {
    if (StringUtils.isBlank(val)) {
      return val;
    }
    String safeHtml = Jsoup.clean(val, RICH_TEXT_SAFE_LIST);
    // col tags are not self-closing in html, so we need to close them to make them well-formed
    return safeHtml.replaceAll("<col ([^/>]*)>", "<col $1 />");
  }

  public static void sanitizeMeasure(Measure measure) {
    if (measure != null) {
      MeasureMetaData measureMetaData = measure.getMeasureMetaData();
      if (measureMetaData != null) {
        measureMetaData.setDescription(sanitize(measureMetaData.getDescription()));
        measureMetaData.setCopyright(sanitize(measureMetaData.getCopyright()));
        measureMetaData.setDisclaimer(sanitize(measureMetaData.getDisclaimer()));
        measureMetaData.setRationale(sanitize(measureMetaData.getRationale()));
        measureMetaData.setPurpose(sanitize(measureMetaData.getPurpose()));
        measureMetaData.setClinicalRecommendation(
            sanitize(measureMetaData.getClinicalRecommendation()));
        measureMetaData.setGuidance(sanitize(measureMetaData.getGuidance()));
        measureMetaData.setLimitations(sanitize(measureMetaData.getLimitations()));
        measureMetaData.setTransmissionFormat(sanitize(measureMetaData.getTransmissionFormat()));
        measureMetaData.setDefinition(sanitize(measureMetaData.getDefinition()));
        measureMetaData.setMeasureSetTitle(sanitize(measureMetaData.getMeasureSetTitle()));
        if (!CollectionUtils.isEmpty(measureMetaData.getReferences())) {
          for (Reference reference : measureMetaData.getReferences()) {
            reference.setReferenceText(sanitize(reference.getReferenceText()));
          }
        }
        if (!CollectionUtils.isEmpty(measureMetaData.getMeasureDefinitions())) {
          for (MeasureDefinition measureDefinition : measureMetaData.getMeasureDefinitions()) {
            measureDefinition.setDefinition(sanitize(measureDefinition.getDefinition()));
          }
        }
      }
      measure.setRiskAdjustmentDescription(sanitize(measure.getRiskAdjustmentDescription()));
      measure.setSupplementalDataDescription(sanitize(measure.getSupplementalDataDescription()));
      if (!CollectionUtils.isEmpty(measure.getRiskAdjustments())) {
        for (DefDescPair riskAdjustment : measure.getRiskAdjustments()) {
          riskAdjustment.setDescription(sanitize(riskAdjustment.getDescription()));
        }
      }
      if (!CollectionUtils.isEmpty(measure.getSupplementalData())) {
        for (DefDescPair supplementalData : measure.getSupplementalData()) {
          supplementalData.setDescription(sanitize(supplementalData.getDescription()));
        }
      }
      if (!CollectionUtils.isEmpty(measure.getGroups())) {
        for (Group group : measure.getGroups()) {
          group.setGroupDescription(sanitize(group.getGroupDescription()));
          group.setRateAggregation(sanitize(group.getRateAggregation()));
          group.setImprovementNotation(sanitize(group.getImprovementNotation()));
          group.setImprovementNotationDescription(
              sanitize(group.getImprovementNotationDescription()));
          if (!CollectionUtils.isEmpty(group.getPopulations())) {
            for (Population population : group.getPopulations()) {
              population.setDescription(sanitize(population.getDescription()));
            }
          }
          if (!CollectionUtils.isEmpty(group.getStratifications())) {
            for (Stratification stratification : group.getStratifications()) {
              stratification.setDescription(sanitize(stratification.getDescription()));
            }
          }
          if (!CollectionUtils.isEmpty(group.getMeasureObservations())) {
            for (MeasureObservation observation : group.getMeasureObservations()) {
              observation.setDescription(sanitize(observation.getDescription()));
            }
          }
        }
      }
    }
  }
}
