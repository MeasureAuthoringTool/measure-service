package cms.gov.madie.measure.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

/**
 * Names a clone "CLONE", then "CLONE2", "CLONE3", ... A clone of a clone replaces its prefix rather
 * than adding another ("CLONE2 Foo" becomes "CLONE3 Foo").
 */
@UtilityClass
public class CloneNamingUtil {
  static final String CLONE = "CLONE";
  static final int MEASURE_NAME_MAX_LENGTH = 500;
  static final int CQL_LIBRARY_NAME_MAX_LENGTH = 64;
  static final int ECQM_TITLE_MAX_LENGTH = 32;

  // the prefix added by an earlier clone, e.g. "CLONE2 " or "CLONE2"
  private static final Pattern MEASURE_NAME_CLONE_PREFIX = Pattern.compile("^CLONE\\d* ");
  private static final Pattern CQL_LIBRARY_NAME_CLONE_PREFIX = Pattern.compile("^CLONE\\d*");
  private static final Pattern ECQM_TITLE_CLONE_PREFIX = Pattern.compile("^CLONE ");

  // a trailing "FHIR", with the space or hyphen before it
  private static final Pattern FHIR_SUFFIX = Pattern.compile("[ -]?FHIR$");

  /** "CLONE{n} <name>", trimmed to 500 characters. */
  public String measureName(int cloneNumber, String measureName) {
    String originalName = MEASURE_NAME_CLONE_PREFIX.matcher(measureName).replaceFirst("");
    String prefix = clonePrefix(cloneNumber) + " ";
    return prefix + StringUtils.left(originalName, MEASURE_NAME_MAX_LENGTH - prefix.length());
  }

  /** "CLONE{n}<library name>", trimmed to 64 characters. */
  public String cqlLibraryName(int cloneNumber, String cqlLibraryName) {
    String originalName = CQL_LIBRARY_NAME_CLONE_PREFIX.matcher(cqlLibraryName).replaceFirst("");
    String prefix = clonePrefix(cloneNumber);
    return prefix + StringUtils.left(originalName, CQL_LIBRARY_NAME_MAX_LENGTH - prefix.length());
  }

  /** "CLONE <title>", trimmed to 32 characters, keeping a trailing "FHIR". */
  public String ecqmTitle(String ecqmTitle) {
    String originalTitle = ECQM_TITLE_CLONE_PREFIX.matcher(ecqmTitle).replaceFirst("");
    String prefix = CLONE + " ";
    if (prefix.length() + originalTitle.length() <= ECQM_TITLE_MAX_LENGTH) {
      return prefix + originalTitle;
    }
    Matcher fhirSuffix = FHIR_SUFFIX.matcher(originalTitle);
    String suffix = fhirSuffix.find() ? fhirSuffix.group() : "";
    String body = originalTitle.substring(0, originalTitle.length() - suffix.length());
    int maxBodyLength = ECQM_TITLE_MAX_LENGTH - prefix.length() - suffix.length();
    return prefix + StringUtils.stripEnd(StringUtils.left(body, maxBodyLength), null) + suffix;
  }

  private String clonePrefix(int cloneNumber) {
    return cloneNumber > 1 ? CLONE + cloneNumber : CLONE;
  }
}
