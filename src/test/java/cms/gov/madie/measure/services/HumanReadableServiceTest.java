package cms.gov.madie.measure.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.factories.ModelValidatorFactory;
import cms.gov.madie.measure.factories.PackageServiceFactory;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.dto.HtmlDiffResponse;
import cms.gov.madie.measure.utils.MeasureUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.UnsupportedTypeException;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;

@ExtendWith(MockitoExtension.class)
public class HumanReadableServiceTest {
  @Mock MeasureService measureService;
  @Mock private PackageServiceFactory packageServiceFactory;
  @Mock private QicorePackageService qicorePackageService;
  @Mock private QdmPackageService qdmPackageService;
  @Mock private ModelValidatorFactory modelValidatorFactory;
  @Mock ExportRepository exportRepository;
  @Mock private QiCoreModelValidator qicoreModelValidator;
  @Mock private QdmModelValidator qdmModelValidator;
  @Mock private MeasureUtil measureUtil;
  @InjectMocks HumanReadableService humanReadableService;

  private static final String TEST_USER = "test-user";
  private static final String TEST_ACCESS_TOKEN = "test-access-token";
  private static final String TEST_MEASURE_ID = "testMeasureId";

  @Test
  public void testGetHumanReadableWithCSSThrowsResourceNotFoundException() {
    when(measureService.findMeasureById(anyString())).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            humanReadableService.getHumanReadableWithCSS(
                TEST_MEASURE_ID, TEST_USER, TEST_ACCESS_TOKEN));
  }

  @Test
  public void testGetHumanReadableWithCSSThrowsUnsupportedTypeException() {
    Measure existingMeasure = Measure.builder().id(TEST_MEASURE_ID).model("invalid model").build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);
    when(modelValidatorFactory.getModelValidator(any())).thenThrow(UnsupportedTypeException.class);

    assertThrows(
        UnsupportedTypeException.class,
        () ->
            humanReadableService.getHumanReadableWithCSS(
                TEST_MEASURE_ID, TEST_USER, TEST_ACCESS_TOKEN));
  }

  @Test
  void testGetQdmMeasurePackage() {
    Measure existingMeasure =
        Measure.builder()
            .id(TEST_MEASURE_ID)
            .model("QDM v5.6")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);
    when(modelValidatorFactory.getModelValidator(any())).thenReturn(qdmModelValidator);
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    when(packageServiceFactory.getPackageService(any())).thenReturn(qdmPackageService);
    when(qdmPackageService.getHumanReadable(any(Measure.class), anyString(), anyString()))
        .thenReturn("valid QDM Human Readable");
    String output =
        humanReadableService.getHumanReadableWithCSS(TEST_MEASURE_ID, TEST_USER, TEST_ACCESS_TOKEN);
    assertThat(output, is(equalTo("valid QDM Human Readable")));
  }

  @Test
  void testGetQiCoreMeasurePackage() {
    Measure existingMeasure = Measure.builder().id(TEST_MEASURE_ID).model("QI-Core v4.1.1").build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);
    when(modelValidatorFactory.getModelValidator(any())).thenReturn(qicoreModelValidator);
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    when(packageServiceFactory.getPackageService(any())).thenReturn(qicorePackageService);
    when(qicorePackageService.getHumanReadable(any(Measure.class), anyString(), anyString()))
        .thenReturn("valid QICore Human Readable");
    String output =
        humanReadableService.getHumanReadableWithCSS(TEST_MEASURE_ID, TEST_USER, TEST_ACCESS_TOKEN);
    assertThat(output, is(equalTo("valid QICore Human Readable")));
  }

  @Test
  void testGetQdmMeasurePackageForVersioned() {
    Measure existingMeasure =
        Measure.builder()
            .id(TEST_MEASURE_ID)
            .model("QDM v5.6")
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);
    when(packageServiceFactory.getPackageService(any())).thenReturn(qdmPackageService);
    when(qdmPackageService.getHumanReadableForVersionedMeasure(
            any(Measure.class), anyString(), anyString()))
        .thenReturn("valid QDM Human Readable");
    String output =
        humanReadableService.getHumanReadableWithCSS(TEST_MEASURE_ID, TEST_USER, TEST_ACCESS_TOKEN);
    assertThat(output, is(equalTo("valid QDM Human Readable")));
  }

  // ========== HTML Diff Tests ==========

  @Test
  void testCompareHtml_IdenticalHtml_NoDifferences() {
    String html =
        "<table>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>Test Measure</td></tr>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'>Test Description</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(html, html);

    assertThat(result.getOldHtml(), is(equalTo(html)));
    assertThat(result.getNewHtml(), is(equalTo(html)));
    assertThat(result.getDifferences().isEmpty(), is(true));
  }

  @Test
  void testCompareHtml_SingleFieldDifference() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>Old Measure Name</td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>New Measure Name</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // With exact matching, a modified field shows as 1 deletion + 1 addition = 2 diffs
    assertThat(result.getDifferences().size(), is(equalTo(1)));

    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Measure Name")));
    assertThat(diff.getOldValue().isEmpty(), is(false));
    assertThat(diff.getNewValue().isEmpty(), is(false));
  }

  @Test
  void testCompareHtml_MultipleFieldDifferences() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>Old Name</td></tr>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'>Old Description</td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>New Name</td></tr>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'>New Description</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // 2 single-value fields changed = 2 diffs (1 for each field with word-level highlighting)
    assertThat(result.getDifferences().size(), is(equalTo(2)));
    assertThat(
        result.getDifferences().stream().anyMatch(d -> d.getField().equals("Measure Name")),
        is(true));
    assertThat(
        result.getDifferences().stream().anyMatch(d -> d.getField().equals("Description")),
        is(true));
  }

  @Test
  void testCompareHtml_FieldAddedInNewHtml() {
    String oldHtml = "<table>" + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>New Field</th><td class='content-container'>New Value</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("New Field")));
    assertThat(diff.getOldValue(), is(equalTo("")));
    assertThat(diff.getNewValue().contains("New Value"), is(true));
    assertTrue(diff.getNewValue().contains("#90EE90")); // Green highlight
  }

  @Test
  void testCompareHtml_FieldRemovedInNewHtml() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Old Field</th><td class='content-container'>Old Value</td></tr>"
            + "</table>";
    String newHtml = "<table>" + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Old Field")));
    assertThat(diff.getOldValue().contains("Old Value"), is(true));
    assertThat(diff.getNewValue(), is(equalTo("")));
    assertTrue(diff.getOldValue().contains("#FFB6C1")); // Red highlight
    assertTrue(diff.getOldValue().contains("line-through")); // Strikethrough
  }

  @Test
  void testCompareHtml_FormattingDifferencesIgnored() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'><b>Bold Text</b></td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'><strong>Bold Text</strong></td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should be no differences since <b> and <strong> are normalized
    assertThat(result.getDifferences().isEmpty(), is(true));
  }

  @Test
  void testCompareHtml_WhitespaceDifferencesIgnored() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'>Text    with   spaces</td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'>Text with spaces</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should be no differences since whitespace is normalized
    assertThat(result.getDifferences().isEmpty(), is(true));
  }

  @Test
  void testCompareHtml_MultiValueField_ItemAdded() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Initial Population</td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Initial Population</td></tr>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Denominator</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Population")));
    assertThat(diff.getOldValue(), is(equalTo("")));
    assertThat(diff.getNewValue().contains("Denominator"), is(true));
  }

  @Test
  void testCompareHtml_MultiValueField_ItemRemoved() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Initial Population</td></tr>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Denominator</td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Initial Population</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Population")));
    assertThat(diff.getOldValue().contains("Denominator"), is(true));
    assertThat(diff.getNewValue(), is(equalTo("")));
  }

  @Test
  void testCompareHtml_MultiValueField_OrderAgnostic() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Initial Population</td></tr>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Denominator</td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Denominator</td></tr>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Initial Population</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should be no differences since order doesn't matter for multi-value fields
    assertThat(result.getDifferences().isEmpty(), is(true));
  }

  @Test
  void testCompareHtml_EmptyHtmlDocuments() {
    String oldHtml = "<table></table>";
    String newHtml = "<table></table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().isEmpty(), is(true));
  }

  @Test
  void testCompareHtml_HtmlWithInlineStyles() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Description</th><td class='content-container' style='color:red;'>Red Text</td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Description</th><td class='content-container' style='color:blue;'>Red Text</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Inline styles should be ignored in comparison
    assertThat(result.getDifferences().isEmpty(), is(true));
  }

  @Test
  void testCompareHtml_PartialDateModification() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Measurement Period</th><td class='content-container'>January 1, 2027 through December 31, 2027</td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Measurement Period</th><td class='content-container'>December 31, 2026 through December 31, 2027</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    HtmlDiffResponse.DiffItem diff = result.getDifferences().get(0);

    // Check that the common suffix is present in both without being wrapped in the span logic
    // We expect " through December 31, 2027" to be preserved as common text
    assertThat(diff.getNewValue().contains("through December 31, 2027"), is(true));
  }

  @Test
  void testCompareHtml_ComplexAdditionHighlighting() {
    String oldHtml = "<table></table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Reference</th><td class='content-container'><p>Reference Type: Citation</p><p>Reference Text: Rohit</p></td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    HtmlDiffResponse.DiffItem diff = result.getDifferences().get(0);

    // Verify it uses a block-level wrapper (div) for green highlight instead of span
    assertThat(diff.getNewValue(), startsWith("<div style=\"background-color: #90EE90;\">"));
    assertThat(diff.getNewValue(), endsWith("</div>"));
  }

  @Test
  void testCompareHtml_RichTextUnderlineAdded() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'><b>Old Bold Text</b></td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'><b>New Bold Text</b></td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Text content changed, single-value field = 1 diff with word-level highlighting
    assertThat(result.getDifferences().size(), is(equalTo(1)));
  }

  @Test
  void testCompareHtml_NullOldHtml() {
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Field</th><td class='content-container'>Value</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(null, newHtml);

    // Should handle null gracefully - all fields in newHtml are additions
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getOldValue(), is(equalTo("")));
  }

  @Test
  void testCompareHtml_NullNewHtml() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Field</th><td class='content-container'>Value</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, null);

    // Should handle null gracefully - all fields in oldHtml are deletions
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getNewValue(), is(equalTo("")));
  }

  @Test
  void testCompareHtml_MultipleAuthors_OrderAgnostic() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Author</th><td class='content-container'>John Doe</td></tr>"
            + "<tr><th class='row-header'>Author</th><td class='content-container'>Jane Smith</td></tr>"
            + "<tr><th class='row-header'>Author</th><td class='content-container'>Bob Johnson</td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Author</th><td class='content-container'>Bob Johnson</td></tr>"
            + "<tr><th class='row-header'>Author</th><td class='content-container'>John Doe</td></tr>"
            + "<tr><th class='row-header'>Author</th><td class='content-container'>Jane Smith</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Order change should not be flagged as a difference
    assertThat(result.getDifferences().isEmpty(), is(true));
  }

  @Test
  void testCompareHtml_ItalicAndEmEquivalent() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'><i>Italic Text</i></td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'><em>Italic Text</em></td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // <i> and <em> should be treated as equivalent
    assertThat(result.getDifferences().isEmpty(), is(true));
  }

  @Test
  void testCompareHtml_PartialTextModification() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>A Change in Severity of the Level of Food Insecurity</td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>A Change in Severity of the Level Food Insecurity Level</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // With exact matching, this is treated as deletion + addition = 2 diffs
    assertThat(result.getDifferences().size(), is(equalTo(1)));

    // Verify both diffs are for the same field
    assertTrue(result.getDifferences().stream().allMatch(d -> d.getField().equals("Measure Name")));
  }

  @Test
  void testCompareHtml_ComplexScenario() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>Old Measure</td></tr>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'>Same description</td></tr>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Initial Population</td></tr>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Denominator</td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>New Measure</td></tr>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'>Same description</td></tr>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Initial Population</td></tr>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Numerator</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Measure Name changed (2 diffs) + Denominator->Numerator changed (2 diffs) = 4 total
    assertThat(result.getDifferences().size(), is(equalTo(3)));

    // Verify we have diffs for both fields
    assertTrue(result.getDifferences().stream().anyMatch(d -> d.getField().equals("Measure Name")));
    assertTrue(result.getDifferences().stream().anyMatch(d -> d.getField().equals("Population")));
  }

  @Test
  void testCompareHtml_QdmStructure_MultiplePairsPerRow() {
    // QDM HTML structure has multiple th-td pairs per row
    String oldHtml =
        "<table class='header_table'>"
            + "<tr>"
            + "<th scope='row' class='row-header'><span class='td_label'>CMS ID</span></th>"
            + "<td style='width:30%'>1173</td>"
            + "<th scope='row' class='row-header'><span class='td_label'>eCQM Version Number</span></th>"
            + "<td style='width:30%'>1.4.000</td>"
            + "</tr>"
            + "<tr>"
            + "<th scope='row' class='row-header'><span class='td_label'>CBE Number</span></th>"
            + "<td style='width:30%'>3749e</td>"
            + "<th scope='row' class='row-header'><span class='td_label'>GUID</span></th>"
            + "<td style='width:30%'>bd7ed96b-6e53-4276-8840-842fe56f06b3</td>"
            + "</tr>"
            + "</table>";

    String newHtml =
        "<table class='header_table'>"
            + "<tr>"
            + "<th scope='row' class='row-header'><span class='td_label'>CMS ID</span></th>"
            + "<td style='width:30%'>1173</td>"
            + "<th scope='row' class='row-header'><span class='td_label'>eCQM Version Number</span></th>"
            + "<td style='width:30%'>2.0.000</td>"
            + "</tr>"
            + "<tr>"
            + "<th scope='row' class='row-header'><span class='td_label'>CBE Number</span></th>"
            + "<td style='width:30%'>3749e</td>"
            + "<th scope='row' class='row-header'><span class='td_label'>GUID</span></th>"
            + "<td style='width:30%'>bd7ed96b-6e53-4276-8840-842fe56f06b3</td>"
            + "</tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Only eCQM Version Number should be different
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("eCQM Version Number")));
    assertTrue(diff.getOldValue().contains("1.4.000"));
    assertTrue(diff.getNewValue().contains("2.0.000"));
  }

  @Test
  void testCompareHtml_ValueWithNestedTable_OnlyTopLevelFieldsExtracted() {
    // Test that th/td elements nested inside a value cell are not treated as top-level fields
    String oldHtml =
        "<table class='narrative-table'>"
            + "<tr>"
            + "<th class='row-header'>Description</th>"
            + "<td class='content-container'>"
            + "<p>Some text with a nested table:</p>"
            + "<table><tr><th>Nested Header</th><td>Nested Value</td></tr></table>"
            + "</td>"
            + "</tr>"
            + "</table>";

    String newHtml =
        "<table class='narrative-table'>"
            + "<tr>"
            + "<th class='row-header'>Description</th>"
            + "<td class='content-container'>"
            + "<p>Some text with a nested table:</p>"
            + "<table><tr><th>Nested Header</th><td>Different Nested Value</td></tr></table>"
            + "</td>"
            + "</tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should only see "Description" as a field, not "Nested Header"
    // And it should show as different because the nested table content changed
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Description")));
    assertTrue(diff.getOldValue().contains("Nested Value"));
    assertTrue(diff.getNewValue().contains("Different Nested Value"));
  }

  @Test
  void testCompareHtml_QdmWithRichTextValues_DetectsDifferences() {
    // Test QDM structure with rich text (bold, italic, etc.) in values
    String oldHtml =
        "<table class='header_table'>"
            + "<tr>"
            + "<th scope='row' class='row-header'>Measure Name</th>"
            + "<td style='width:30%'><b>Old</b> Measure</td>"
            + "<th scope='row' class='row-header'>Description</th>"
            + "<td style='width:30%'><p>Same <i>description</i></p></td>"
            + "</tr>"
            + "</table>";

    String newHtml =
        "<table class='header_table'>"
            + "<tr>"
            + "<th scope='row' class='row-header'>Measure Name</th>"
            + "<td style='width:30%'><b>New</b> Measure</td>"
            + "<th scope='row' class='row-header'>Description</th>"
            + "<td style='width:30%'><p>Same <i>description</i></p></td>"
            + "</tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Only Measure Name should be different, Description is same
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Measure Name")));
    assertTrue(diff.getOldValue().contains("Old"));
    assertTrue(diff.getNewValue().contains("New"));
  }

  @Test
  void testCompareHtml_MultiValueField_ModificationDetectedWithFuzzyMatching() {
    // Test that when a multi-value field has a value modified,
    // it shows as a modification (not deletion + addition)
    String oldHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Reference</th><td class='content-container'>Library: MATGlobalCommonFunctionsQDM version 1.0.0</td></tr>"
            + "<tr><th class='row-header'>Reference</th><td class='content-container'>Library: SupplementalDataElements version 2.0.0</td></tr>"
            + "<tr><th class='row-header'>Reference</th><td class='content-container'>Code system: LOINC version 2.73</td></tr>"
            + "</table>";

    String newHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Reference</th><td class='content-container'>Library: MATGlobalCommonFunctionsQDM version 1.0.0</td></tr>"
            + "<tr><th class='row-header'>Reference</th><td class='content-container'>Library: SupplementalDataElements version 3.0.0</td></tr>"
            + "<tr><th class='row-header'>Reference</th><td class='content-container'>Code system: LOINC version 2.73</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should show 1 modification (version 2.0.0 -> 3.0.0), not 2 diffs (1 deletion + 1 addition)
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Reference")));
    assertTrue(diff.getOldValue().contains("2.0.0"));
    assertTrue(diff.getNewValue().contains("3.0.0"));
    // Should have highlighting (not full deletion/addition style)
    assertTrue(diff.getOldValue().contains("line-through"));
    assertTrue(diff.getNewValue().contains("#90EE90")); // Green highlight
  }

  @Test
  void testCompareHtml_MultiValueField_TrueAdditionAndDeletion() {
    // Test that true additions and deletions are still detected
    // (when similarity is too low to be considered a modification)
    String oldHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Data Requirement</th><td class='content-container'>Type: Patient</td></tr>"
            + "<tr><th class='row-header'>Data Requirement</th><td class='content-container'>Type: Encounter, Performed</td></tr>"
            + "</table>";

    String newHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Data Requirement</th><td class='content-container'>Type: Patient</td></tr>"
            + "<tr><th class='row-header'>Data Requirement</th><td class='content-container'>Type: Procedure, Performed</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should show 2 diffs: 1 deletion (Encounter) + 1 addition (Procedure)
    assertThat(result.getDifferences().size(), is(equalTo(2)));

    var deletionDiff =
        result.getDifferences().stream()
            .filter(d -> d.getOldValue().contains("Encounter"))
            .findFirst()
            .orElse(null);
    assertNotNull(deletionDiff);
    assertThat(deletionDiff.getField(), is(equalTo("Data Requirement")));
    assertThat(deletionDiff.getNewValue(), is(equalTo("")));

    var additionDiff =
        result.getDifferences().stream()
            .filter(d -> d.getNewValue().contains("Procedure"))
            .findFirst()
            .orElse(null);
    assertNotNull(additionDiff);
    assertThat(additionDiff.getField(), is(equalTo("Data Requirement")));
    assertThat(additionDiff.getOldValue(), is(equalTo("")));
  }

  @Test
  void testCompareHtml_MultiValueField_OrderChangedWithModification() {
    // Test that order changes + modifications are handled correctly
    String oldHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Value Set</th><td class='content-container'>Office Visit (2.16.840.1.113883.3.464.1003.101.12.1001)</td></tr>"
            + "<tr><th class='row-header'>Value Set</th><td class='content-container'>Annual Wellness Visit (2.16.840.1.113883.3.526.3.1240)</td></tr>"
            + "<tr><th class='row-header'>Value Set</th><td class='content-container'>Preventive Care Services (2.16.840.1.113883.3.464.1003.101.12.1025)</td></tr>"
            + "</table>";

    String newHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Value Set</th><td class='content-container'>Preventive Care Services (2.16.840.1.113883.3.464.1003.101.12.1025)</td></tr>"
            + "<tr><th class='row-header'>Value Set</th><td class='content-container'>Annual Wellness Visit Updated (2.16.840.1.113883.3.526.3.1240)</td></tr>"
            + "<tr><th class='row-header'>Value Set</th><td class='content-container'>Office Visit (2.16.840.1.113883.3.464.1003.101.12.1001)</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should show 1 modification (Annual Wellness Visit -> Annual Wellness Visit Updated)
    // Order change should not affect the detection
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Value Set")));
    assertTrue(
        diff.getOldValue().contains("Annual Wellness Visit")
            && !diff.getOldValue().contains("Updated"));
    assertTrue(diff.getNewValue().contains("Annual Wellness Visit Updated"));
  }

  @Test
  void testCompareHtml_ListFormat_LabelAndPreExtraction() {
    // Test extraction of fields in label.list-header + pre.cql-definition-body format
    String oldHtml =
        "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Numerator</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>\"Qualifying Delayed VTE Encounter\"</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    String newHtml =
        "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Numerator</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>\"Updated Qualifying Delayed VTE Encounter\"</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should detect the change in the Numerator field
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Numerator")));
    assertTrue(diff.getOldValue().contains("Qualifying Delayed VTE Encounter"));
    assertTrue(diff.getNewValue().contains("Updated Qualifying Delayed VTE Encounter"));
  }

  @Test
  void testCompareHtml_ListFormat_MultipleFields() {
    // Test multiple fields in list format
    String oldHtml =
        "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Initial Population</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Initial Pop Definition</pre></div></li></ul>"
            + "</li>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Denominator</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Denominator Definition</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    String newHtml =
        "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Initial Population</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Initial Pop Definition</pre></div></li></ul>"
            + "</li>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Denominator</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Updated Denominator Definition</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Only Denominator should be different
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Denominator")));
    assertTrue(diff.getOldValue().contains("Denominator Definition"));
    assertTrue(diff.getNewValue().contains("Updated Denominator Definition"));
  }

  @Test
  void testCompareHtml_MixedFormat_TableAndList() {
    // Test that both table and list formats can coexist
    String oldHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>Test Measure</td></tr>"
            + "</table>"
            + "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Numerator</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Numerator Logic</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    String newHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>Updated Test Measure</td></tr>"
            + "</table>"
            + "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Numerator</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Updated Numerator Logic</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Both fields should show changes
    assertThat(result.getDifferences().size(), is(equalTo(2)));
    assertTrue(result.getDifferences().stream().anyMatch(d -> d.getField().equals("Measure Name")));
    assertTrue(result.getDifferences().stream().anyMatch(d -> d.getField().equals("Numerator")));
  }

  @Test
  void testCompareHtml_ListFormat_NoChangeDetected() {
    // Test that identical list format fields don't show differences
    String html =
        "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Denominator Exclusions</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>None</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    var result = humanReadableService.compareHtml(html, html);

    assertThat(result.getDifferences().isEmpty(), is(true));
  }

  @Test
  void testCompareHtml_ListFormat_FieldAdded() {
    // Test adding a new field in list format
    String oldHtml =
        "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Initial Population</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Initial Pop</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    String newHtml =
        "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Initial Population</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Initial Pop</pre></div></li></ul>"
            + "</li>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Numerator Exclusions</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>None</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should show addition of Numerator Exclusions
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Numerator Exclusions")));
    assertThat(diff.getOldValue(), is(equalTo("")));
    assertTrue(diff.getNewValue().contains("None"));
  }

  @Test
  void testCompareHtml_ListFormat_FieldRemoved() {
    // Test removing a field in list format
    String oldHtml =
        "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Initial Population</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Initial Pop</pre></div></li></ul>"
            + "</li>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Denominator Exceptions</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Exception Logic</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    String newHtml =
        "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Initial Population</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Initial Pop</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should show removal of Denominator Exceptions
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Denominator Exceptions")));
    assertTrue(diff.getOldValue().contains("Exception Logic"));
    assertThat(diff.getNewValue(), is(equalTo("")));
  }

  @Test
  void testCompareHtml_ListFormat_DuplicateFieldNames() {
    // Test that duplicate field names in list format are handled correctly
    String oldHtml =
        "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Definition</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Definition 1</pre></div></li></ul>"
            + "</li>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Definition</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Definition 2</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    String newHtml =
        "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Definition</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Definition 1</pre></div></li></ul>"
            + "</li>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Definition</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Definition 2 Updated</pre></div></li></ul>"
            + "</li>"
            + "</ul>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should detect modification of the second Definition using fuzzy matching
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Definition")));
    assertTrue(
        diff.getOldValue().contains("Definition 2") && !diff.getOldValue().contains("Updated"));
    assertTrue(diff.getNewValue().contains("Definition 2 Updated"));
  }

  @Test
  void testCompareHtml_SectionFormat_Terminology() {
    // Test extraction of Terminology section (h3 + div with list)
    String oldHtml =
        "<h3><a name=\"d1e555\" href=\"#toc\">Terminology</a></h3>"
            + "<div>"
            + "<ul style=\"padding-left: 50px;\">"
            + "<li style=\"width:80%\">code \"Discharge to home\" (\"SNOMEDCT Code (12345)\")</li>"
            + "<li style=\"width:80%\">valueset \"Office Visit\" (2.16.840.1.113883.3.464.1003.101.12.1001)</li>"
            + "</ul>"
            + "</div>";

    String newHtml =
        "<h3><a name=\"d1e555\" href=\"#toc\">Terminology</a></h3>"
            + "<div>"
            + "<ul style=\"padding-left: 50px;\">"
            + "<li style=\"width:80%\">code \"Discharge to home\" (\"SNOMEDCT Code (12345)\")</li>"
            + "<li style=\"width:80%\">valueset \"Office Visit\" (2.16.840.1.113883.3.464.1003.101.12.1001)</li>"
            + "<li style=\"width:80%\">valueset \"Outpatient Encounter\" (2.16.840.1.113883.3.464.1003.101.12.1087)</li>"
            + "</ul>"
            + "</div>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should detect addition in Terminology section
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Terminology")));
    assertTrue(diff.getNewValue().contains("Outpatient Encounter"));
  }

  @Test
  void testCompareHtml_SectionFormat_DataCriteria() {
    // Test extraction of Data Criteria section
    String oldHtml =
        "<h3><a name=\"d1e647\" href=\"#toc\">Data Criteria (QDM Data Elements)</a></h3>"
            + "<div>"
            + "<ul style=\"padding-left: 50px;\">"
            + "<li style=\"width:80%\">\"Assessment, Performed: Functional Assessment\"</li>"
            + "<li style=\"width:80%\">\"Encounter, Performed: Office Visit\"</li>"
            + "</ul>"
            + "</div>";

    String newHtml =
        "<h3><a name=\"d1e647\" href=\"#toc\">Data Criteria (QDM Data Elements)</a></h3>"
            + "<div>"
            + "<ul style=\"padding-left: 50px;\">"
            + "<li style=\"width:80%\">\"Assessment, Performed: Functional Assessment\"</li>"
            + "<li style=\"width:80%\">\"Encounter, Performed: Office Visit Updated\"</li>"
            + "</ul>"
            + "</div>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should detect change in Data Criteria section
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Data Criteria (QDM Data Elements)")));
    assertTrue(
        diff.getOldValue().contains("Office Visit") && !diff.getOldValue().contains("Updated"));
    assertTrue(diff.getNewValue().contains("Office Visit Updated"));
  }

  @Test
  void testCompareHtml_SectionFormat_NoChange() {
    // Test that identical section content shows no differences
    String html =
        "<h3><a name=\"d1e555\" href=\"#toc\">Terminology</a></h3>"
            + "<div>"
            + "<ul style=\"padding-left: 50px;\">"
            + "<li style=\"width:80%\">code \"Test Code\" (\"SNOMEDCT Code (123)\")</li>"
            + "</ul>"
            + "</div>";

    var result = humanReadableService.compareHtml(html, html);

    assertThat(result.getDifferences().isEmpty(), is(true));
  }

  @Test
  void testCompareHtml_SectionFormat_ItemRemoved() {
    // Test removal of an item from Terminology section
    String oldHtml =
        "<h3><a name=\"d1e555\" href=\"#toc\">Terminology</a></h3>"
            + "<div>"
            + "<ul style=\"padding-left: 50px;\">"
            + "<li style=\"width:80%\">valueset \"Office Visit\" (2.16.840.1.113883.3.464.1003.101.12.1001)</li>"
            + "<li style=\"width:80%\">valueset \"Outpatient Encounter\" (2.16.840.1.113883.3.464.1003.101.12.1087)</li>"
            + "</ul>"
            + "</div>";

    String newHtml =
        "<h3><a name=\"d1e555\" href=\"#toc\">Terminology</a></h3>"
            + "<div>"
            + "<ul style=\"padding-left: 50px;\">"
            + "<li style=\"width:80%\">valueset \"Office Visit\" (2.16.840.1.113883.3.464.1003.101.12.1001)</li>"
            + "</ul>"
            + "</div>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should detect removal in Terminology section
    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Terminology")));
    assertTrue(diff.getOldValue().contains("Outpatient Encounter"));
    assertFalse(diff.getNewValue().contains("Outpatient Encounter"));
  }

  @Test
  void testCompareHtml_MixedFormats_TableListAndSection() {
    // Test all three formats together
    String oldHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>Test</td></tr>"
            + "</table>"
            + "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Numerator</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Logic</pre></div></li></ul>"
            + "</li>"
            + "</ul>"
            + "<h3><a name=\"d1e555\" href=\"#toc\">Terminology</a></h3>"
            + "<div>"
            + "<ul style=\"padding-left: 50px;\">"
            + "<li style=\"width:80%\">code \"Test\" (\"123\")</li>"
            + "</ul>"
            + "</div>";

    String newHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>Updated Test</td></tr>"
            + "</table>"
            + "<ul>"
            + "<li class='list-unstyled'>"
            + "<label class='list-header'><strong>Numerator</strong></label>"
            + "<ul class='code'><li><div><pre class='cql-definition-body'>Updated Logic</pre></div></li></ul>"
            + "</li>"
            + "</ul>"
            + "<h3><a name=\"d1e555\" href=\"#toc\">Terminology</a></h3>"
            + "<div>"
            + "<ul style=\"padding-left: 50px;\">"
            + "<li style=\"width:80%\">code \"Test\" (\"123\")</li>"
            + "<li style=\"width:80%\">code \"New\" (\"456\")</li>"
            + "</ul>"
            + "</div>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // All three formats should show changes
    assertThat(result.getDifferences().size(), is(equalTo(3)));
    assertTrue(result.getDifferences().stream().anyMatch(d -> d.getField().equals("Measure Name")));
    assertTrue(result.getDifferences().stream().anyMatch(d -> d.getField().equals("Numerator")));
    assertTrue(result.getDifferences().stream().anyMatch(d -> d.getField().equals("Terminology")));
  }

  @Test
  void testCompareHtml_ListInValue_ItemAdded() {
    // Test that when a value contains a list, only the added item is highlighted
    String oldHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Terminology</th>"
            + "<td class='content-container'>"
            + "<ul>"
            + "<li>code \"Discharge to home\" (\"SNOMEDCT Code (12345)\")</li>"
            + "<li>valueset \"Office Visit\" (2.16.840.1.113883.3.464.1003.101.12.1001)</li>"
            + "</ul>"
            + "</td></tr>"
            + "</table>";

    String newHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Terminology</th>"
            + "<td class='content-container'>"
            + "<ul>"
            + "<li>code \"Discharge to home\" (\"SNOMEDCT Code (12345)\")</li>"
            + "<li>valueset \"Office Visit\" (2.16.840.1.113883.3.464.1003.101.12.1001)</li>"
            + "<li>valueset \"Outpatient Encounter\" (2.16.840.1.113883.3.464.1003.101.12.1087)</li>"
            + "</ul>"
            + "</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Terminology")));

    // New value should have green highlighting only on the added item
    assertTrue(diff.getNewValue().contains("Outpatient Encounter"));
    assertTrue(diff.getNewValue().contains("#90EE90")); // Green highlight

    // Old items should remain unhighlighted in the new value
    assertTrue(diff.getNewValue().contains("Discharge to home"));
    assertTrue(diff.getNewValue().contains("Office Visit"));
  }

  @Test
  void testCompareHtml_ListInValue_ItemRemoved() {
    // Test that when an item is removed from a list, only that item is highlighted
    String oldHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Description</th>"
            + "<td class='content-container'>"
            + "<ul>"
            + "<li>Item 1</li>"
            + "<li>Item 2</li>"
            + "<li>Item 3</li>"
            + "</ul>"
            + "</td></tr>"
            + "</table>";

    String newHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Description</th>"
            + "<td class='content-container'>"
            + "<ul>"
            + "<li>Item 1</li>"
            + "<li>Item 3</li>"
            + "</ul>"
            + "</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Description")));

    // Old value should have red highlighting with strikethrough on removed item
    assertTrue(diff.getOldValue().contains("Item 2"));
    assertTrue(diff.getOldValue().contains("#FFB6C1")); // Red highlight
    assertTrue(diff.getOldValue().contains("line-through"));

    // Unchanged items should not be highlighted
    assertTrue(diff.getOldValue().contains("Item 1"));
    assertTrue(diff.getOldValue().contains("Item 3"));
  }

  @Test
  void testCompareHtml_ListInValue_ItemModified() {
    // Test that when a list item is modified, only that item shows word-level diff
    String oldHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Copyright</th>"
            + "<td class='content-container'>"
            + "<p>Some introductory text.</p>"
            + "<ul>"
            + "<li>LOINC copyright 2004-2023, Regenstrief Institute</li>"
            + "<li>SNOMED copyright 2023, IHTSDO</li>"
            + "</ul>"
            + "</td></tr>"
            + "</table>";

    String newHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Copyright</th>"
            + "<td class='content-container'>"
            + "<p>Some introductory text.</p>"
            + "<ul>"
            + "<li>LOINC copyright 2004-2024, Regenstrief Institute</li>"
            + "<li>SNOMED copyright 2023, IHTSDO</li>"
            + "</ul>"
            + "</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Copyright")));

    // Should have word-level highlighting on "2023" -> "2024"
    assertTrue(diff.getOldValue().contains("2023"));
    assertTrue(diff.getNewValue().contains("2024"));
    assertTrue(diff.getOldValue().contains("line-through"));
    assertTrue(diff.getNewValue().contains("#90EE90"));

    // Unchanged SNOMED item should not be highlighted
    assertTrue(diff.getOldValue().contains("SNOMED copyright 2023, IHTSDO"));
  }

  @Test
  void testCompareHtml_OrderedListInValue() {
    // Test that ordered lists (<ol>) are also handled correctly
    String oldHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Steps</th>"
            + "<td class='content-container'>"
            + "<ol>"
            + "<li>Step 1: Initialize</li>"
            + "<li>Step 2: Process</li>"
            + "<li>Step 3: Finalize</li>"
            + "</ol>"
            + "</td></tr>"
            + "</table>";

    String newHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Steps</th>"
            + "<td class='content-container'>"
            + "<ol>"
            + "<li>Step 1: Initialize</li>"
            + "<li>Step 2: Process and Validate</li>"
            + "<li>Step 3: Finalize</li>"
            + "</ol>"
            + "</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Steps")));

    // Step 2 should show modification
    assertTrue(diff.getOldValue().contains("Process"));
    assertTrue(diff.getNewValue().contains("Process and Validate"));

    // Steps 1 and 3 should remain unchanged
    assertTrue(diff.getOldValue().contains("Step 1: Initialize"));
    assertTrue(diff.getNewValue().contains("Step 3: Finalize"));
  }

  @Test
  void testCompareHtml_MultipleListsInValue() {
    // Test value with multiple lists
    String oldHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Info</th>"
            + "<td class='content-container'>"
            + "<p>First list:</p>"
            + "<ul><li>Item A</li><li>Item B</li></ul>"
            + "<p>Second list:</p>"
            + "<ul><li>Item X</li><li>Item Y</li></ul>"
            + "</td></tr>"
            + "</table>";

    String newHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Info</th>"
            + "<td class='content-container'>"
            + "<p>First list:</p>"
            + "<ul><li>Item A</li><li>Item B Modified</li></ul>"
            + "<p>Second list:</p>"
            + "<ul><li>Item X</li><li>Item Y</li><li>Item Z</li></ul>"
            + "</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Info")));

    // Should detect changes in both lists
    assertTrue(diff.getOldValue().contains("Item B"));
    assertTrue(diff.getNewValue().contains("Item B Modified"));
    assertTrue(diff.getNewValue().contains("Item Z"));
  }

  @Test
  void testCompareHtml_ListInValue_NoChanges() {
    // Test that identical lists don't trigger false differences
    String html =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Data</th>"
            + "<td class='content-container'>"
            + "<ul>"
            + "<li>Item 1</li>"
            + "<li>Item 2</li>"
            + "</ul>"
            + "</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(html, html);

    assertThat(result.getDifferences().isEmpty(), is(true));
  }

  @Test
  void testCompareHtml_MixedContent_ListAndText() {
    // Test value with both regular text and lists
    String oldHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Description</th>"
            + "<td class='content-container'>"
            + "<p>This measure includes:</p>"
            + "<ul>"
            + "<li>Patient encounters</li>"
            + "<li>Lab results</li>"
            + "</ul>"
            + "<p>Additional criteria apply.</p>"
            + "</td></tr>"
            + "</table>";

    String newHtml =
        "<table class='narrative-table'>"
            + "<tr><th class='row-header'>Description</th>"
            + "<td class='content-container'>"
            + "<p>This measure includes:</p>"
            + "<ul>"
            + "<li>Patient encounters</li>"
            + "<li>Lab results</li>"
            + "<li>Imaging studies</li>"
            + "</ul>"
            + "<p>Additional criteria apply.</p>"
            + "</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Description")));

    // Only the new list item should be highlighted
    assertTrue(diff.getNewValue().contains("Imaging studies"));
    assertTrue(diff.getNewValue().contains("#90EE90"));

    // Text outside lists should remain unchanged
    assertTrue(diff.getNewValue().contains("This measure includes:"));
    assertTrue(diff.getNewValue().contains("Additional criteria apply."));
  }
}
