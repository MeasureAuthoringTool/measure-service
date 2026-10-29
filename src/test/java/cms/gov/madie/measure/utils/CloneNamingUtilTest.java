package cms.gov.madie.measure.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CloneNamingUtilTest {

  @ParameterizedTest
  @CsvSource({
    "1, Measure, CLONE Measure",
    "2, Measure, CLONE2 Measure",
    // a clone of a clone joins the same numbered family
    "1, CLONE Measure, CLONE Measure",
    "3, CLONE2 Measure, CLONE3 Measure",
    // "CLONED" is not a clone prefix
    "1, CLONED Measure, CLONE CLONED Measure"
  })
  void measureName(int cloneNumber, String measureName, String expected) {
    assertEquals(expected, CloneNamingUtil.measureName(cloneNumber, measureName));
  }

  @Test
  void measureNameIsTrimmedTo500Characters() {
    assertEquals("CLONE2 " + "a".repeat(493), CloneNamingUtil.measureName(2, "a".repeat(500)));
  }

  @ParameterizedTest
  @CsvSource({
    "1, Lib, CLONELib",
    "2, Lib, CLONE2Lib",
    // a trailing digit stays readable
    "2, Lib1, CLONE2Lib1",
    // QDM library names may contain underscores
    "1, Qdm_Lib, CLONEQdm_Lib",
    // a clone of a clone joins the same numbered family
    "1, CLONELib, CLONELib",
    "3, CLONE2Lib, CLONE3Lib",
    // only an upper-case CLONE prefix comes from cloning
    "2, CloneLib, CLONE2CloneLib"
  })
  void cqlLibraryName(int cloneNumber, String cqlLibraryName, String expected) {
    assertEquals(expected, CloneNamingUtil.cqlLibraryName(cloneNumber, cqlLibraryName));
  }

  @Test
  void cqlLibraryNameIsTrimmedTo64Characters() {
    assertEquals("CLONE2" + "A".repeat(58), CloneNamingUtil.cqlLibraryName(2, "A".repeat(64)));
  }

  @ParameterizedTest
  @CsvSource({
    "Title, CLONE Title",
    // exactly 32 characters
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ, CLONE ABCDEFGHIJKLMNOPQRSTUVWXYZ",
    // too long: trimmed, keeping a trailing FHIR
    "ABCDEFGHIJKLMNOPQRSTUVWXYZFHIR, CLONE ABCDEFGHIJKLMNOPQRSTUVFHIR",
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ FHIR, CLONE ABCDEFGHIJKLMNOPQRSTU FHIR",
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ-FHIR, CLONE ABCDEFGHIJKLMNOPQRSTU-FHIR",
    // whitespace left by the trim is dropped
    "ABCDEFGHIJKLMNOPQRST UVWXYZ FHIR, CLONE ABCDEFGHIJKLMNOPQRST FHIR",
    // too long, no FHIR: trimmed
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123, CLONE ABCDEFGHIJKLMNOPQRSTUVWXYZ",
    // a clone of a clone keeps a single prefix
    "CLONE Title, CLONE Title",
    "CLONE ABCDEFGHIJKLMNOPQRSTUVWXYZ, CLONE ABCDEFGHIJKLMNOPQRSTUVWXYZ"
  })
  void ecqmTitle(String ecqmTitle, String expected) {
    assertEquals(expected, CloneNamingUtil.ecqmTitle(ecqmTitle));
  }
}
