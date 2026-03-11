package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.CqlFileComparisonDTO;
import org.apache.commons.text.similarity.LevenshteinDistance;
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

    // Verify document order is preserved - new text should maintain order from new CQL
    assertTrue(
        comparison.getNewText().indexOf("Numerator")
            < comparison.getNewText().indexOf("Initial Population"),
        "New text should preserve original document order (Numerator before Initial Population)");
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

  @Test
  void testCompareLibrariesWithDeletedCommentsNotDuplicated() {
    Map<String, String> oldLibraries = new HashMap<>();
    Map<String, String> newLibraries = new HashMap<>();

    String oldCql =
        "library CMS1017HHFI version '2.1.006'\n\n"
            + "context Patient\n\n"
            + "define \"Risk Variable Encounter With Antidepressant Active At Admission\":\n"
            + "  \"Initial Population\" QualifyingEncounter\n"
            + "    with [\"Medication, Active\": \"Antidepressants\"] AntidepressantActive\n"
            + "      such that AntidepressantActive.relevantPeriod contains start of Global.\"HospitalizationWithObservation\" ( QualifyingEncounter )\n\n"
            + "/*\n"
            + "@comment:QDM doesn't have an \"Admission Medication\".  So use Med, Active where the med relevant period contains the enc start date if the time when the pt is first known to have been taking the med before encounter can't be captured.\n"
            + "*/\n\n"
            + "define \"Risk Variable Encounter With Antihypertensive Active At Admission\":\n"
            + "  \"Initial Population\" QualifyingEncounter\n"
            + "    with [\"Medication, Active\": \"Antihypertensives\"] AntihypertensiveActive\n"
            + "      such that AntihypertensiveActive.relevantPeriod contains start of Global.\"HospitalizationWithObservation\" ( QualifyingEncounter )\n\n"
            + "/*\n"
            + "@comment:QDM doesn't have an \"Admission Medication\".  So use Med, Active where the med relevant period contains the enc start date if the time when the pt is first known to have been taking the med before encounter can't be captured.\n"
            + "*/\n\n"
            + "define \"Risk Variable Encounter With CNS Depressant Active At Admission\":\n"
            + "  \"Initial Population\" QualifyingEncounter\n"
            + "    with [\"Medication, Active\": \"Central Nervous System Depressants\"] CNSMedicationActive\n"
            + "      such that CNSMedicationActive.relevantPeriod contains start of Global.\"HospitalizationWithObservation\" ( QualifyingEncounter )\n\n"
            + "define \"Encounter With A Fall Diagnosis\":\n"
            + "  \"Initial Population\" QualifyingEncounter\n"
            + "    where exists ( QualifyingEncounter.diagnoses QualifyingFall\n"
            + "        where QualifyingFall.code in \"Inpatient Falls\"\n"
            + "    )\n\n"
            + "define \"Encounter Where A Fall Occurred\":\n"
            + "  \"Encounter With A Fall Event\"\n"
            + "    union \"Encounter With A Fall Diagnosis\"";

    String newCql =
        "library CMS1017HHFI version '2.1.006'\n\n"
            + "context Patient\n\n"
            + "define \"Risk Variable Encounter With Antidepressant Active At Admission\":\n"
            + "  \"Initial Population\" QualifyingEncounter\n"
            + "    with [\"Medication, Active\": \"Antidepressants\"] AntidepressantActive\n"
            + "      such that AntidepressantActive.relevantPeriod contains start of Global.\"HospitalizationWithObservation\" ( QualifyingEncounter )\n\n"
            + "define \"Risk Variable Encounter With Antihypertensive Active At Admission\":\n"
            + "  \"Initial Population\" QualifyingEncounter\n"
            + "    with [\"Medication, Active\": \"Antihypertensives\"] AntihypertensiveActive\n"
            + "      such that AntihypertensiveActive.relevantPeriod contains start of Global.\"HospitalizationWithObservation\" ( QualifyingEncounter )\n\n"
            + "define \"Risk Variable Encounter With CNS Depressant Active At Admission\":\n"
            + "  \"Initial Population\" QualifyingEncounter\n"
            + "    with [\"Medication, Active\": \"Central Nervous System Depressants\"] CNSMedicationActive\n"
            + "      such that CNSMedicationActive.relevantPeriod contains start of Global.\"HospitalizationWithObservation\" ( QualifyingEncounter )\n\n"
            + "define \"Encounter With A Fall Diagnosis\":\n"
            + "  \"Initial Population\" QualifyingEncounter\n"
            + "    where exists ( QualifyingEncounter.diagnoses QualifyingFall\n"
            + "        where QualifyingFall.code in \"Inpatient Falls\"\n"
            + "    )\n\n"
            + "define \"Encounter Where A Fall Occurred\":\n"
            + "  \"Encounter With A Fall Event\"\n"
            + "    union \"Encounter With A Fall Diagnosis\"";

    oldLibraries.put("Test.cql", oldCql);
    newLibraries.put("Test.cql", newCql);

    List<CqlFileComparisonDTO> result = service.compareLibraries(oldLibraries, newLibraries, true);
    assertNotNull(result);
    assertEquals(1, result.size());

    CqlFileComparisonDTO comparison = result.get(0);
    long fallDiagnosisCount =
        comparison.getNewText().split("define \"Encounter With A Fall Diagnosis\"", -1).length - 1;

    // Should only appear once, not multiple times (once per deleted comment)
    assertEquals(
        1, fallDiagnosisCount, "Encounter With A Fall Diagnosis should appear exactly once");
  }

  @Test
  void testDebugDistanceBetweenCommentAndDefinition() {
    String comment1 =
        "/*\n@comment:QDM doesn't have an \"Admission Medication\".  So use Med, Active where the med relevant period contains the enc start date if the time when the pt is first known to have been taking the med before encounter can't be captured.\n*/";

    String definition =
        "define \"Encounter With A Fall Diagnosis\":\n  \"Initial Population\" QualifyingEncounter\n    where exists ( QualifyingEncounter.diagnoses QualifyingFall\n        where QualifyingFall.code in \"Inpatient Falls\"\n    )";

    LevenshteinDistance levenshtein = new LevenshteinDistance();
    int distance = levenshtein.apply(comment1, definition);

    // If distance > 150, it should NOT match (which is what we want)
    assertTrue(
        distance > 150,
        "Distance (" + distance + ") should be > 150 to prevent matching comment to definition");
  }
}
