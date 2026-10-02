package cms.gov.madie.measure.utils;

import static org.assertj.core.api.Assertions.assertThat;

import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import org.junit.jupiter.api.Test;

class RichTextUtilTest {

  private Measure htmlify(MeasureMetaData metaData) {
    Measure measure = new Measure().toBuilder().id("id").measureMetaData(metaData).build();
    RichTextUtil.htmlifyMeasureRichTextContents(measure);
    return measure;
  }

  @Test
  void htmlifiesPlainTextLimitations() {
    Measure measure = htmlify(MeasureMetaData.builder().limitations("some limitation").build());

    assertThat(measure.getMeasureMetaData().getLimitations()).isEqualTo("<p>some limitation</p>");
  }

  @Test
  void htmlifiesMarkdownLimitations() {
    Measure measure = htmlify(MeasureMetaData.builder().limitations("**bold** limitation").build());

    assertThat(measure.getMeasureMetaData().getLimitations())
        .isEqualTo("<p><strong>bold</strong> limitation</p>");
  }

  @Test
  void leavesAlreadyHtmlifiedLimitationsUnchanged() {
    Measure measure = htmlify(MeasureMetaData.builder().limitations("<p>already html</p>").build());

    assertThat(measure.getMeasureMetaData().getLimitations()).isEqualTo("<p>already html</p>");
  }

  @Test
  void sanitizesUnsafeHtmlInLimitations() {
    Measure measure =
        htmlify(
            MeasureMetaData.builder()
                .limitations("limitation <script>alert('xss')</script>")
                .build());

    assertThat(measure.getMeasureMetaData().getLimitations()).doesNotContain("script");
  }

  @Test
  void leavesNullLimitationsUnchanged() {
    Measure measure = htmlify(MeasureMetaData.builder().limitations(null).build());

    assertThat(measure.getMeasureMetaData().getLimitations()).isNull();
  }

  @Test
  void leavesBlankLimitationsUnchanged() {
    Measure measure = htmlify(MeasureMetaData.builder().limitations("   ").build());

    assertThat(measure.getMeasureMetaData().getLimitations()).isEqualTo("   ");
  }

  @Test
  void doesNotHtmlifyAuthoritativeSource() {
    Measure measure =
        htmlify(MeasureMetaData.builder().authoritativeSource("https://www.test.in").build());

    assertThat(measure.getMeasureMetaData().getAuthoritativeSource())
        .isEqualTo("https://www.test.in");
  }

  @Test
  void doesNotHtmlifyAuthoritativeSourceThatLooksLikeMarkdown() {
    Measure measure =
        htmlify(MeasureMetaData.builder().authoritativeSource("urn:oid:*2.16.840*").build());

    assertThat(measure.getMeasureMetaData().getAuthoritativeSource())
        .isEqualTo("urn:oid:*2.16.840*");
  }

  @Test
  void htmlifiesLimitationsWithoutTouchingAuthoritativeSource() {
    Measure measure =
        htmlify(
            MeasureMetaData.builder()
                .limitations("a limitation")
                .authoritativeSource("https://ec.test.testing/eh-test")
                .build());

    MeasureMetaData metaData = measure.getMeasureMetaData();
    assertThat(metaData.getLimitations()).isEqualTo("<p>a limitation</p>");
    assertThat(metaData.getAuthoritativeSource()).isEqualTo("https://ec.test.testing/eh-test");
  }

  @Test
  void handlesNullMeasure() {
    RichTextUtil.htmlifyMeasureRichTextContents(null);
  }

  @Test
  void handlesNullMeasureMetaData() {
    Measure measure = new Measure().toBuilder().id("id").measureMetaData(null).build();

    RichTextUtil.htmlifyMeasureRichTextContents(measure);

    assertThat(measure.getMeasureMetaData()).isNull();
  }
}
