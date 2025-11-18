package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.HtmlDiffResponse;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class HumanReadableServiceHtmlDiffTest {
  @Test
  void testOnlyDescriptionFieldIsDifferent() throws Exception {
    String oldHtml =
        new String(
            Files.readAllBytes(
                Paths.get("src/main/resources/html/CMS1272-v0.0.000-FHIR-Old.html")));
    String newHtml =
        new String(
            Files.readAllBytes(
                Paths.get("src/main/resources/html/CMS1272-v0.0.000-FHIR-New.html")));
    HumanReadableService service = new HumanReadableService(null, null, null, null);
    HtmlDiffResponse response = service.compareHtml(oldHtml, newHtml);
    assertNotNull(response);
    List<HtmlDiffResponse.DiffItem> diffs = response.getDifferences();
    assertNotNull(diffs);
    // There should be only one difference and it should be Description
    assertEquals(1, diffs.size(), "Only Description should be different");
    HtmlDiffResponse.DiffItem diff = diffs.get(0);
    assertTrue(diff.getField().startsWith("Description"), "Field should be Description");
    assertTrue(
        diff.getOldValue()
            .contains(
                "Median time (in minutes) from Emergency Department (ED) arrival to initial administration of pain medication"));
    assertTrue(diff.getNewValue().contains("I am updated this line"));
  }
}
