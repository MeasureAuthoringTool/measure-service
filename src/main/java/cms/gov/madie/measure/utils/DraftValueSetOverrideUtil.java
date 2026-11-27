package cms.gov.madie.measure.utils;

import gov.cms.madie.models.measure.DraftValueSetOverride;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Helpers for identifying the value set a {@link DraftValueSetOverride} refers to. A value set may
 * be sent as an OID (QDM) or as a VSAC canonical url (FHIR), so both are reduced to the same
 * identifier, which is what duplicate detection and removal match on.
 */
public final class DraftValueSetOverrideUtil {

  private static final String URN_OID_PREFIX = "urn:oid:";

  // An OID is a dot separated sequence of numbers, e.g. 2.16.840.1.113883.3.666.5.1004
  private static final Pattern OID_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)+");

  private DraftValueSetOverrideUtil() {}

  /**
   * Reduces an override to the identifier of the value set it refers to: the OID when one is
   * provided, otherwise the OID carried by the url. A url that holds no OID is used as the
   * identifier itself, so overrides are still matched consistently.
   *
   * @param override the override to identify
   * @return the lower cased identifier, or null if the override identifies no value set
   */
  public static String getValueSetIdentifier(DraftValueSetOverride override) {
    if (override == null) {
      return null;
    }
    String oid = removeUrnOidPrefix(StringUtils.trimToNull(override.getOid()));
    if (oid != null) {
      return oid.toLowerCase();
    }
    String url = StringUtils.trimToNull(override.getUrl());
    if (url == null) {
      return null;
    }
    String oidFromUrl = extractOid(url);
    return oidFromUrl != null ? oidFromUrl.toLowerCase() : url.toLowerCase();
  }

  /** Strips the "urn:oid:" prefix VSAC uses on QDM value set OIDs. */
  public static String removeUrnOidPrefix(String oid) {
    if (StringUtils.startsWithIgnoreCase(oid, URN_OID_PREFIX)) {
      return StringUtils.trimToNull(oid.substring(URN_OID_PREFIX.length()));
    }
    return oid;
  }

  /** Returns the last OID found in the given value, e.g. the OID of a VSAC ValueSet url. */
  private static String extractOid(String value) {
    Matcher matcher = OID_PATTERN.matcher(value);
    String oid = null;
    while (matcher.find()) {
      oid = matcher.group();
    }
    return oid;
  }
}
