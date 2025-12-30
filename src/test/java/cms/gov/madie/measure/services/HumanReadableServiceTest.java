package cms.gov.madie.measure.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.factories.ModelValidatorFactory;
import cms.gov.madie.measure.factories.PackageServiceFactory;
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

    assertThat(result.getDifferences().size(), is(equalTo(1)));
    var diff = result.getDifferences().get(0);
    assertThat(diff.getField(), is(equalTo("Measure Name")));
    // The diff values may contain HTML markup from the diff generator
    assertThat(diff.getOldValue(), is(not(equalTo(""))));
    assertThat(diff.getNewValue(), is(not(equalTo(""))));
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
  void testCompareHtml_ComplexHtmlWithMultipleDifferences() {
    String oldHtml =
        "<table>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>Old Measure</td></tr>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'>Description text</td></tr>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Initial Population</td></tr>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Denominator</td></tr>"
            + "</table>";
    String newHtml =
        "<table>"
            + "<tr><th class='row-header'>Measure Name</th><td class='content-container'>New Measure</td></tr>"
            + "<tr><th class='row-header'>Description</th><td class='content-container'>Description text</td></tr>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Initial Population</td></tr>"
            + "<tr><th class='row-header'>Population</th><td class='content-container'>Numerator</td></tr>"
            + "</table>";

    var result = humanReadableService.compareHtml(oldHtml, newHtml);

    // Should have differences for Measure Name change and Population changes
    assertThat(result.getDifferences().size() >= 2, is(true));
    assertThat(
        result.getDifferences().stream().anyMatch(d -> d.getField().equals("Measure Name")),
        is(true));
    assertThat(
        result.getDifferences().stream()
            .anyMatch(
                d -> d.getField().equals("Population") && d.getOldValue().contains("Denominator")),
        is(true));
    assertThat(
        result.getDifferences().stream()
            .anyMatch(
                d -> d.getField().equals("Population") && d.getNewValue().contains("Numerator")),
        is(true));
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
}
