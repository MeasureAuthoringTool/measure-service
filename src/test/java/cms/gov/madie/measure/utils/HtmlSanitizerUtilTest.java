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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HtmlSanitizerUtilTest {

  @Test
  void sanitizeValidHtml() {
    String html =
        "<h1>Title</h1><p>Paragraph</p><a href=\"http://example.com\" target=\"_blank\">Link</a>";
    String sanitized = HtmlSanitizerUtil.sanitize(html);

    assertEquals(
        "Title\n<p>Paragraph</p>\n<a href=\"http://example.com\" rel=\"nofollow\">Link</a>",
        sanitized);
  }

  @Test
  void sanitizeHtmlWithDisallowedTags() {
    String html = "<script>alert('XSS');</script><h1>Title</h1>";
    String sanitized = HtmlSanitizerUtil.sanitize(html);

    assertEquals("Title", sanitized);
  }

  @Test
  void sanitizeHtmlWithDisallowedAttributes() {
    String html = "<a href=\"http://example.com\" onclick=\"alert('XSS')\">Link</a>";
    String sanitized = HtmlSanitizerUtil.sanitize(html);

    assertEquals("<a href=\"http://example.com\" rel=\"nofollow\">Link</a>", sanitized);
  }

  @Test
  void sanitizeHtmlWithDisallowedProtocols() {
    String html = "<a href=\"javascript:alert('XSS')\">Link</a>";
    String sanitized = HtmlSanitizerUtil.sanitize(html);

    assertEquals("<a rel=\"nofollow\">Link</a>", sanitized);
  }

  @Test
  void sanitizeKeepsRichTextTableAndSelfClosesCol() {
    String html =
        "<table style=\"width: 100%\" onclick=\"alert(1)\"><colgroup><col style=\"width: 50px\">"
            + "</colgroup><tbody><tr><td colspan=\"2\"><del>old</del><s>struck</s></td></tr>"
            + "</tbody></table>";
    String sanitized = HtmlSanitizerUtil.sanitize(html).replace("\n", "").replace(" ", "");

    assertEquals(
        "<tablestyle=\"width:100%\"><colgroup><colstyle=\"width:50px\"/></colgroup><tbody><tr>"
            + "<tdcolspan=\"2\"><del>old</del><s>struck</s></td></tr></tbody></table>",
        sanitized);
  }

  @Test
  void sanitizeEmptyHtml() {
    assertEquals("", HtmlSanitizerUtil.sanitize(""));
  }

  @Test
  void sanitizeNullHtml() {
    assertNull(HtmlSanitizerUtil.sanitize(null));
  }

  @Test
  void sanitizeMeasureHandlesNullMeasure() {
    assertDoesNotThrow(() -> HtmlSanitizerUtil.sanitizeMeasure(null));
  }

  @Test
  void sanitizeMeasureHandlesMeasureWithoutMetaDataOrGroups() {
    Measure measure = Measure.builder().riskAdjustmentDescription("<b>risk</b>").build();

    HtmlSanitizerUtil.sanitizeMeasure(measure);

    assertNull(measure.getMeasureMetaData());
    assertNull(measure.getGroups());
    assertEquals("<b>risk</b>", measure.getRiskAdjustmentDescription());
  }

  @Test
  void sanitizeMeasure() {
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDescription("<script>bad()</script>desc");
    metaData.setCopyright("<b>copy</b>");
    metaData.setDisclaimer("Not allowed <img src=x onerror=alert(1)>");
    metaData.setRationale("<h1>rat</h1>");
    metaData.setPurpose("<iframe src=\"http://evil\"></iframe>purpose");
    metaData.setClinicalRecommendation("<a href=\"javascript:alert(1)\">rec</a>");
    metaData.setGuidance("<span>guide</span>");
    metaData.setLimitations("<p onmouseover=\"alert(1)\">limits</p>");
    metaData.setTransmissionFormat("<div>format</div>");
    metaData.setDefinition("<u>def</u>");
    metaData.setMeasureSetTitle("<i>title</i>");
    metaData.setReferences(
        List.of(
            Reference.builder()
                .referenceType("citation")
                .referenceText("<div>This is Citation</div>")
                .build()));
    metaData.setMeasureDefinitions(
        List.of(
            MeasureDefinition.builder()
                .term("term")
                .definition("<script>x()</script>msr def")
                .build()));

    DefDescPair riskAdjustment =
        DefDescPair.builder().definition("RAV").description("<img src=x>rav desc").build();
    DefDescPair supplementalData =
        DefDescPair.builder().definition("SDE").description("<b>sde desc</b>").build();

    Population pop = new Population();
    pop.setDescription("<b>pop</b>");
    Stratification strat = new Stratification();
    strat.setDescription("<img src=x>");
    MeasureObservation obs = new MeasureObservation();
    obs.setDescription("<u>obs</u>");

    Group group = new Group();
    group.setGroupDescription("Test <script>group</script>");
    group.setRateAggregation("This is not allowed either<img src=x>");
    group.setImprovementNotation("<script>x()</script>Increased score indicates improvement");
    group.setImprovementNotationDescription("<span>impdesc</span>");
    group.setPopulations(List.of(pop));
    group.setStratifications(List.of(strat));
    group.setMeasureObservations(List.of(obs));

    Measure measure =
        Measure.builder()
            .model("QI-Core v4.1.1")
            .measureMetaData(metaData)
            .riskAdjustmentDescription("This is <script>risky</script>")
            .supplementalDataDescription("<b>supp</b>")
            .riskAdjustments(List.of(riskAdjustment))
            .supplementalData(List.of(supplementalData))
            .groups(List.of(group))
            .build();

    HtmlSanitizerUtil.sanitizeMeasure(measure);

    assertEquals("desc", metaData.getDescription());
    assertEquals("<b>copy</b>", metaData.getCopyright());
    assertEquals("Not allowed", metaData.getDisclaimer());
    assertEquals("rat", metaData.getRationale());
    assertEquals("purpose", metaData.getPurpose());
    assertEquals("<a rel=\"nofollow\">rec</a>", metaData.getClinicalRecommendation());
    assertEquals("<span>guide</span>", metaData.getGuidance());
    assertEquals("<p>limits</p>", metaData.getLimitations());
    assertEquals("format", metaData.getTransmissionFormat());
    assertEquals("<u>def</u>", metaData.getDefinition());
    assertEquals("<i>title</i>", metaData.getMeasureSetTitle());
    assertEquals("This is Citation", metaData.getReferences().get(0).getReferenceText());
    assertEquals("msr def", metaData.getMeasureDefinitions().get(0).getDefinition());

    assertEquals("This is", measure.getRiskAdjustmentDescription());
    assertEquals("<b>supp</b>", measure.getSupplementalDataDescription());
    assertEquals("rav desc", riskAdjustment.getDescription());
    assertEquals("RAV", riskAdjustment.getDefinition());
    assertEquals("<b>sde desc</b>", supplementalData.getDescription());

    assertEquals("Test", group.getGroupDescription());
    assertEquals("This is not allowed either", group.getRateAggregation());
    assertEquals("Increased score indicates improvement", group.getImprovementNotation());
    assertEquals("<span>impdesc</span>", group.getImprovementNotationDescription());
    assertEquals("<b>pop</b>", pop.getDescription());
    assertEquals("", strat.getDescription());
    assertEquals("<u>obs</u>", obs.getDescription());
  }
}
