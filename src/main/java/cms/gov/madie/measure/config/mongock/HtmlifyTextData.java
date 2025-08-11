package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Measure;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@ChangeUnit(id = "htmlify_text_data", order = "1", author = "madie_dev")
public class HtmlifyTextData {

  private List<Measure> updatedMeasures = new ArrayList<>();

  private static final Safelist RICH_TEXT_SAFE_LIST =
    Safelist.basic()
      .addTags("s", "br", "table", "tbody", "td", "th", "thead", "tr", "col", "colgroup", "del")
      .addAttributes("table", "style", "class", "id")
      .addAttributes("th", "rowspan", "colspan", "style", "colwidth")
      .addAttributes("td", "rowspan", "colspan", "style", "colwidth")
      .addAttributes("col", "style");

  @Execution
  public void htmlfiyText(MeasureRepository measureRepository) {
    // Retrieve all DRAFT measures
    List<Measure> draftActiveMeasures = measureRepository.findAllMeasureIdsByActiveAndMeasureMetaDataDraft(true);

    // Duplicate list for modification
    //TODO I thought I added a deepClone to Measure, but maybe it was just Test Case.
    // Might be good to add for rollback.
//    for (Measure measure : draftActiveMeasures) {
//      updatedMeasures.add(measure.deepClone());
//    }

    // Convert text fields to HTML
    for (Measure msr : draftActiveMeasures) {
      // MetaData fields
      htlmifyMeasureMetaData(msr);

      // Population criteria
      htmlifyPopulationCriteria(msr);
    }
  }

  private void htmlifyPopulationCriteria(Measure msr) {

  }

  private void htlmifyMeasureMetaData(Measure msr) {
    if(msr.getMeasureMetaData() != null) {
      if (StringUtils.isNotBlank(msr.getMeasureMetaData().getDescription())) {
        msr.getMeasureMetaData().setDescription(toHtml(msr.getMeasureMetaData().getDescription()));
      }
      if( StringUtils.isNotBlank(msr.getMeasureMetaData().getRationale())) {
        msr.getMeasureMetaData().setRationale(toHtml(msr.getMeasureMetaData().getRationale()));
      }
      if( StringUtils.isNotBlank(msr.getMeasureMetaData().getGuidance())) {
        msr.getMeasureMetaData().setGuidance(
            toHtml(msr.getMeasureMetaData().getGuidance()));
      }
      if (StringUtils.isNotBlank(msr.getMeasureMetaData().getClinicalRecommendation())) {
        msr.getMeasureMetaData().setCopyright(
            toHtml(msr.getMeasureMetaData().getClinicalRecommendation()));
      }
      if( StringUtils.isNotBlank(msr.getMeasureMetaData().getClinicalRecommendation())) {
        msr.getMeasureMetaData().setClinicalRecommendation(
            toHtml(msr.getMeasureMetaData().getClinicalRecommendation()));
      }
      if( StringUtils.isNotBlank(msr.getMeasureMetaData().getCopyright())) {
        msr.getMeasureMetaData().setCopyright(
          toHtml(msr.getMeasureMetaData().getCopyright()));
      }
      if(StringUtils.isNotBlank(msr.getMeasureMetaData().getDisclaimer())) {
        msr.getMeasureMetaData().setDisclaimer(
          toHtml(msr.getMeasureMetaData().getDisclaimer()));
      }
      if(CollectionUtils.isNotEmpty(msr.getMeasureMetaData().getReferences())) {
        msr.getMeasureMetaData().getReferences().forEach(
          reference -> {
            if (StringUtils.isNotBlank(reference.getReferenceText())) {
              reference.setReferenceText(toHtml(reference.getReferenceText()));
            }
          });
      }
      if(CollectionUtils.isNotEmpty(msr.getMeasureMetaData().getMeasureDefinitions())) {
        msr.getMeasureMetaData().getMeasureDefinitions().forEach(
          msrDefinition -> {
            if(StringUtils.isNotBlank(msrDefinition.getDefinition())) {
              msrDefinition.setDefinition(toHtml(msrDefinition.getDefinition()));
            }
          }
        );
      }
    }
  }

  private String toHtml(String text) {
    if (StringUtils.isBlank(text)) {
      return text;
    }
    Parser parser = Parser.builder().build();
    Node document = parser.parse(text);
    HtmlRenderer renderer = HtmlRenderer.builder().build();
    // Sanitize HTML content
    return sanitizeText(renderer.render(document));
  }

  private String sanitizeText(String val) {
    if (StringUtils.isBlank(val)) {
      return val;
    }
    String safeHtml = Jsoup.clean(val, RICH_TEXT_SAFE_LIST);
    // col tags are not self-closing in html, so we need to close them to make them wel-formed
    return safeHtml.replaceAll("<col ([^/>]*)>", "<col $1 />");
  }

  @RollbackExecution
  public void rollbackExecution(MeasureRepository measureRepository) {
    log.info("Rolling back htmlify text data changelog");

    log.info("Rollback htmlify text data changelog complete");
  }
}
