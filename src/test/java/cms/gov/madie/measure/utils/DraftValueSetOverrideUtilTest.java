package cms.gov.madie.measure.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import gov.cms.madie.models.measure.DraftValueSetOverride;
import org.junit.jupiter.api.Test;

class DraftValueSetOverrideUtilTest {

  @Test
  void getValueSetIdentifierUsesOid() {
    DraftValueSetOverride override =
        DraftValueSetOverride.builder().oid("2.16.840.1.113883.3.666.5.1004").build();
    assertEquals(
        "2.16.840.1.113883.3.666.5.1004",
        DraftValueSetOverrideUtil.getValueSetIdentifier(override));
  }

  @Test
  void getValueSetIdentifierStripsUrnOidPrefix() {
    DraftValueSetOverride override =
        DraftValueSetOverride.builder().oid("urn:oid:2.16.840.1.113883.3.666.5.1004").build();
    assertEquals(
        "2.16.840.1.113883.3.666.5.1004",
        DraftValueSetOverrideUtil.getValueSetIdentifier(override));
  }

  @Test
  void getValueSetIdentifierPrefersOidOverUrl() {
    DraftValueSetOverride override =
        DraftValueSetOverride.builder()
            .oid("2.16.840.1.113883.3.666.5.1004")
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.666.5.1002")
            .build();
    assertEquals(
        "2.16.840.1.113883.3.666.5.1004",
        DraftValueSetOverrideUtil.getValueSetIdentifier(override));
  }

  @Test
  void getValueSetIdentifierUsesOidFromUrl() {
    DraftValueSetOverride override =
        DraftValueSetOverride.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.666.5.1004")
            .build();
    assertEquals(
        "2.16.840.1.113883.3.666.5.1004",
        DraftValueSetOverrideUtil.getValueSetIdentifier(override));
  }

  @Test
  void getValueSetIdentifierUsesOidFromUrlWithTrailingSegment() {
    DraftValueSetOverride override =
        DraftValueSetOverride.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.666.5.1004/expansion")
            .build();
    assertEquals(
        "2.16.840.1.113883.3.666.5.1004",
        DraftValueSetOverrideUtil.getValueSetIdentifier(override));
  }

  @Test
  void getValueSetIdentifierFallsBackToUrlWithoutOid() {
    DraftValueSetOverride override =
        DraftValueSetOverride.builder().url("http://example.com/ValueSet/Hypertension").build();
    assertEquals(
        "http://example.com/valueset/hypertension",
        DraftValueSetOverrideUtil.getValueSetIdentifier(override));
  }

  @Test
  void getValueSetIdentifierReturnsNullWhenNothingIdentifiesAValueSet() {
    assertNull(DraftValueSetOverrideUtil.getValueSetIdentifier(null));
    assertNull(
        DraftValueSetOverrideUtil.getValueSetIdentifier(DraftValueSetOverride.builder().build()));
    assertNull(
        DraftValueSetOverrideUtil.getValueSetIdentifier(
            DraftValueSetOverride.builder().oid("  ").url("  ").name("Hypertension").build()));
  }

  @Test
  void removeUrnOidPrefixLeavesOtherValuesAlone() {
    assertNull(DraftValueSetOverrideUtil.removeUrnOidPrefix(null));
    assertEquals("2.16.840", DraftValueSetOverrideUtil.removeUrnOidPrefix("2.16.840"));
    assertEquals("2.16.840", DraftValueSetOverrideUtil.removeUrnOidPrefix("URN:OID:2.16.840"));
    assertNull(DraftValueSetOverrideUtil.removeUrnOidPrefix("urn:oid:"));
  }
}
