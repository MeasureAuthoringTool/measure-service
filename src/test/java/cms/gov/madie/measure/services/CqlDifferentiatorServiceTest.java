package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.CqlFileComparisonDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CqlDifferentiatorServiceTest {
  private CqlDifferentiatorService service;

  @BeforeEach
  void setUp() {
    service = new CqlDifferentiatorService();
  }

  @Test
  void testCompareLibrariesWithSimpleCql() {
    Map<String, String> oldLibraries = new HashMap<>();
    Map<String, String> newLibraries = new HashMap<>();

    String oldCql =
        "library TestMeasure version '1.0.0'\n\n"
            + "context Patient\n\n"
            + "define \"Initial Population\":\n"
            + "  true\n\n"
            + "define \"Numerator\":\n"
            + "  true";

    String newCql =
        "library TestMeasure version '2.0.0'\n\n"
            + "context Patient\n\n"
            + "define \"Numerator\":\n"
            + "  false\n\n"
            + "define \"Initial Population\":\n"
            + "  false";

    oldLibraries.put("TestMeasure.cql", oldCql);
    newLibraries.put("TestMeasure.cql", newCql);

    List<CqlFileComparisonDTO> result = service.compareLibraries(oldLibraries, newLibraries, true);

    assertNotNull(result);
    assertEquals(1, result.size());

    CqlFileComparisonDTO comparison = result.get(0);
    assertEquals("TestMeasure.cql", comparison.getOldFileName());
    assertEquals("TestMeasure.cql", comparison.getNewFileName());

    // Verify text was normalized (no carriage returns, tabs converted to spaces)
    assertFalse(comparison.getOldText().contains("\r"));
    assertFalse(comparison.getNewText().contains("\r"));
    assertFalse(comparison.getOldText().contains("\t"));
    assertFalse(comparison.getNewText().contains("\t"));

    // Verify reordering occurred - new text should have Initial Population before Numerator
    String[] newLines = comparison.getNewText().split("\n\n");
    assertTrue(
        comparison.getNewText().indexOf("Initial Population")
            < comparison.getNewText().indexOf("Numerator"),
        "New text should be reordered to match old structure");
  }

  @Test
  void testCompareLibrariesWithoutReordering() {
    Map<String, String> oldLibraries = new HashMap<>();
    Map<String, String> newLibraries = new HashMap<>();

    String oldCql =
        "library TestMeasure version '1.0.0'\n\n"
            + "context Patient\n\n"
            + "define \"First\":\n"
            + "  true";

    String newCql =
        "library TestMeasure version '2.0.0'\n\n"
            + "context Patient\n\n"
            + "define \"Second\":\n"
            + "  true";

    oldLibraries.put("TestMeasure.cql", oldCql);
    newLibraries.put("TestMeasure.cql", newCql);

    List<CqlFileComparisonDTO> result = service.compareLibraries(oldLibraries, newLibraries, false);

    assertNotNull(result);
    assertEquals(1, result.size());

    CqlFileComparisonDTO comparison = result.get(0);
    // Without reordering, text should just be normalized
    assertTrue(comparison.getNewText().contains("define \"Second\""));
  }

  @Test
  void testCompareLibrariesWithNewFiles() {
    Map<String, String> oldLibraries = new HashMap<>();
    Map<String, String> newLibraries = new HashMap<>();

    String oldCql = "library Old version '1.0.0'\n\ncontext Patient\n\ndefine \"Test\":\n  true";
    String newCql1 = "library Old version '2.0.0'\n\ncontext Patient\n\ndefine \"Test\":\n  false";
    String newCql2 =
        "library NewLib version '1.0.0'\n\ncontext Patient\n\ndefine \"NewDefine\":\n  true";

    oldLibraries.put("Old.cql", oldCql);
    newLibraries.put("Old.cql", newCql1);
    newLibraries.put("NewLib.cql", newCql2);

    List<CqlFileComparisonDTO> result = service.compareLibraries(oldLibraries, newLibraries, true);

    assertNotNull(result);
    assertEquals(2, result.size());

    // One comparison should be for the matched file
    CqlFileComparisonDTO matched =
        result.stream().filter(c -> c.getOldFileName().equals("Old.cql")).findFirst().orElse(null);
    assertNotNull(matched);
    assertEquals("Old.cql", matched.getNewFileName());

    // One comparison should be for the new file without a match
    CqlFileComparisonDTO newFile =
        result.stream()
            .filter(c -> c.getOldFileName().equals("not found"))
            .findFirst()
            .orElse(null);
    assertNotNull(newFile);
    assertEquals("NewLib.cql", newFile.getNewFileName());
    assertEquals("", newFile.getOldText());
  }

  @Test
  void testCompareLibrariesWithEmptyLibraries() {
    Map<String, String> oldLibraries = new HashMap<>();
    Map<String, String> newLibraries = new HashMap<>();

    List<CqlFileComparisonDTO> result = service.compareLibraries(oldLibraries, newLibraries, true);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void testCompareLibrariesNormalizesTabsAndCarriageReturns() {
    Map<String, String> oldLibraries = new HashMap<>();
    Map<String, String> newLibraries = new HashMap<>();

    String oldCql =
        "library Test version '1.0.0'\r\n\r\ncontext Patient\r\n\r\ndefine \"Test\":\r\n\ttrue";
    String newCql =
        "library Test version '2.0.0'\r\n\r\ncontext Patient\r\n\r\ndefine \"Test\":\r\n\t\tfalse";

    oldLibraries.put("Test.cql", oldCql);
    newLibraries.put("Test.cql", newCql);

    List<CqlFileComparisonDTO> result = service.compareLibraries(oldLibraries, newLibraries, true);

    assertNotNull(result);
    assertEquals(1, result.size());

    CqlFileComparisonDTO comparison = result.get(0);

    // Verify carriage returns are removed
    assertFalse(comparison.getOldText().contains("\r"));
    assertFalse(comparison.getNewText().contains("\r"));

    // Verify tabs are converted to spaces
    assertFalse(comparison.getOldText().contains("\t"));
    assertFalse(comparison.getNewText().contains("\t"));
    assertTrue(comparison.getOldText().contains("  ")); // Should have 2 spaces
    assertTrue(comparison.getNewText().contains("  ")); // Should have 2 spaces
  }
}
