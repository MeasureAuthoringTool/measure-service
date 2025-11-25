package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.CqlFileComparison;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for comparing and reordering CQL files to facilitate meaningful diffs. Based on the
 * JavaScript differentiator logic, this service: 1. Normalizes text (removes carriage returns,
 * converts tabs to spaces) 2. Uses Levenshtein edit distance to match similar code blocks 3.
 * Reorders new CQL code to align with old CQL structure
 */
@Service
@Slf4j
public class CqlDifferentiatorService {

  private static final Pattern DEFINE_PATTERN = Pattern.compile("define \"(.*)\":");
  private static final String CONTEXT_PATIENT_DELIMITER = "context Patient\n\n";
  private static final String PARAGRAPH_DELIMITER = "\n\n";
  private static final LevenshteinDistance LEVENSHTEIN = new LevenshteinDistance();

  /**
   * Compare CQL libraries from two measures and return normalized, reordered text ready for diff
   * display.
   *
   * @param oldLibraries Map of filename to CQL content for old measure
   * @param newLibraries Map of filename to CQL content for new measure
   * @param autoReorder Whether to auto-reorder the new measure CQL (default true)
   * @return List of file comparisons with normalized text
   */
  public List<CqlFileComparison> compareLibraries(
      Map<String, String> oldLibraries, Map<String, String> newLibraries, boolean autoReorder) {

    log.debug(
        "Comparing {} old libraries with {} new libraries",
        oldLibraries.size(),
        newLibraries.size());

    // Create mapping between old and new filenames based on similarity
    Map<String, String> libraryMap = createLibraryMap(oldLibraries, newLibraries);

    List<CqlFileComparison> comparisons = new ArrayList<>();

    for (Map.Entry<String, String> entry : libraryMap.entrySet()) {
      String oldFileName = entry.getKey();
      String newFileName = entry.getValue();

      // Ignore Mac temp files
      if (oldFileName.contains("MACOSX")) {
        continue;
      }

      String oldText;
      if (oldFileName.startsWith("NA-")) {
        // New file without a match in old measure
        oldText = "";
      } else {
        oldText = oldLibraries.getOrDefault(oldFileName, "");
      }

      String newText = newLibraries.getOrDefault(newFileName, "");

      // Normalize text
      oldText = normalizeText(oldText);
      newText = normalizeText(newText);

      // Reorder new library to match old structure if enabled
      if (autoReorder && !oldText.isEmpty()) {
        newText = reorderNewLibrary(oldText, newText);
      }

      // Update filename display for new files
      String displayOldFileName = oldFileName.startsWith("NA-") ? "not found" : oldFileName;

      comparisons.add(
          CqlFileComparison.builder()
              .oldFileName(displayOldFileName)
              .newFileName(newFileName)
              .oldText(oldText)
              .newText(newText)
              .build());
    }

    log.debug("Created {} file comparisons", comparisons.size());
    return comparisons;
  }

  /**
   * Normalize text by removing carriage returns and converting tabs to spaces. This handles
   * inconsistencies between different measure versions.
   *
   * @param text Original text
   * @return Normalized text
   */
  private String normalizeText(String text) {
    if (text == null) {
      return "";
    }
    // Remove carriage returns
    text = text.replace("\r", "");
    // Replace tabs with 2 spaces (handles inconsistencies between 2019/2020)
    text = text.replace("\t", "  ");
    return text;
  }

  /**
   * Reorder the new library's body to match the structure of the old library. This makes diffs more
   * meaningful by aligning similar code blocks.
   *
   * @param oldLibrary Old library CQL content
   * @param newLibrary New library CQL content
   * @return Reordered new library content
   */
  private String reorderNewLibrary(String oldLibrary, String newLibrary) {
    String oldLibraryBody = "";

    if (!oldLibrary.isEmpty()) {
      String[] oldParts = oldLibrary.split(CONTEXT_PATIENT_DELIMITER, 2);
      if (oldParts.length > 1) {
        oldLibraryBody = oldParts[1];
      }
    }

    String[] newParts = newLibrary.split(CONTEXT_PATIENT_DELIMITER, 2);
    if (newParts.length < 2) {
      // No context patient delimiter found, return as-is
      return newLibrary;
    }

    String newLibraryHeader = newParts[0];
    String newLibraryBody = newParts[1];

    // Split into paragraphs (separated by double newlines)
    List<String> oldParagraphs = Arrays.asList(oldLibraryBody.split(PARAGRAPH_DELIMITER));
    List<String> newParagraphs = Arrays.asList(newLibraryBody.split(PARAGRAPH_DELIMITER));

    // Map old paragraphs to new paragraphs based on similarity
    Map<String, String> paragraphMap = mapByEditDistance(oldParagraphs, newParagraphs);

    // Rebuild new library body in order that matches old library
    String reorderedBody = rebuildFromMapping(oldParagraphs, newParagraphs, paragraphMap);

    return newLibraryHeader + CONTEXT_PATIENT_DELIMITER + reorderedBody;
  }

  /**
   * Rebuild text from mapping, maintaining order of old strings and appending any new strings that
   * weren't matched.
   *
   * @param oldStrings List of old strings
   * @param newStrings List of new strings
   * @param mapping Map from old strings to matched new strings
   * @return Rebuilt text with reordered paragraphs
   */
  private String rebuildFromMapping(
      List<String> oldStrings, List<String> newStrings, Map<String, String> mapping) {

    List<String> reordered = new ArrayList<>();
    Set<String> usedNewStrings = new HashSet<>();

    // First, add matched new strings in the order of old strings
    for (String oldString : oldStrings) {
      String matchedNewString = mapping.get(oldString);
      if (matchedNewString != null) {
        reordered.add(matchedNewString);
        usedNewStrings.add(matchedNewString);
      }
    }

    // Then, append any new strings that weren't matched
    for (String newString : newStrings) {
      if (!usedNewStrings.contains(newString)) {
        reordered.add(newString);
      }
    }

    return String.join(PARAGRAPH_DELIMITER, reordered);
  }

  /**
   * Map strings from old list to new list based on edit distance similarity. Uses a greedy matching
   * algorithm that prioritizes closest matches. For CQL define statements, uses weighted distance
   * (50% label + 50% full statement).
   *
   * @param oldStrings List of old strings to map from
   * @param newStrings List of new strings to map to
   * @return Map from old strings to best matching new strings
   */
  private Map<String, String> mapByEditDistance(List<String> oldStrings, List<String> newStrings) {
    // Calculate distances from each old string to all new strings
    Map<String, List<DistanceMatch>> distances = new HashMap<>();

    for (String oldString : oldStrings) {
      List<DistanceMatch> matches = new ArrayList<>();

      for (String newString : newStrings) {
        double distance = calculateDistance(oldString, newString);
        matches.add(new DistanceMatch(distance, newString));
      }

      // Sort by distance (closest first)
      matches.sort(Comparator.comparingDouble(m -> m.distance));
      distances.put(oldString, matches);
    }

    // Create list of old strings sorted by their minimum distance
    List<StringWithMinDist> oldStringsWithMinDist =
        oldStrings.stream()
            .map(
                oldString ->
                    new StringWithMinDist(oldString, distances.get(oldString).get(0).distance))
            .sorted(Comparator.comparingDouble(s -> s.minDistance))
            .toList();

    // Match each old string to its best new string, avoiding reuse
    Map<String, String> matches = new HashMap<>();
    Set<String> usedNewStrings = new HashSet<>();

    for (StringWithMinDist oldStringWithDist : oldStringsWithMinDist) {
      String oldString = oldStringWithDist.string;
      List<DistanceMatch> candidates = distances.get(oldString);

      // Find first candidate that hasn't been matched yet
      String match = null;
      for (DistanceMatch candidate : candidates) {
        if (!usedNewStrings.contains(candidate.newString)) {
          match = candidate.newString;
          usedNewStrings.add(match);
          break;
        }
      }

      // If no match found (extra paragraph in old measure), map to null
      matches.put(oldString, match);
    }

    return matches;
  }

  /**
   * Calculate edit distance between two strings. For CQL define statements, uses weighted sum of
   * label distance and statement distance.
   *
   * @param oldString Old string
   * @param newString New string
   * @return Distance score (lower is more similar)
   */
  private double calculateDistance(String oldString, String newString) {
    int statementDist = LEVENSHTEIN.apply(oldString, newString);

    // Check if both strings are CQL define statements
    Matcher oldMatcher = DEFINE_PATTERN.matcher(oldString);
    Matcher newMatcher = DEFINE_PATTERN.matcher(newString);

    if (oldMatcher.find() && newMatcher.find()) {
      String oldDefineLabel = oldMatcher.group(1);
      String newDefineLabel = newMatcher.group(1);

      int defineDist = LEVENSHTEIN.apply(oldDefineLabel, newDefineLabel);

      // If define labels match exactly, distance is 0
      if (defineDist == 0) {
        return 0.0;
      }

      // Otherwise use weighted sum: 50% label + 50% full statement
      return 0.5 * defineDist + 0.5 * statementDist;
    }

    // Not define statements, just use statement distance
    return statementDist;
  }

  /**
   * Create a map from old filenames to new filenames based on similarity. Handles case where new
   * measure has more files than old measure.
   *
   * @param oldLibraries Old measure libraries
   * @param newLibraries New measure libraries
   * @return Map from old filename to new filename
   */
  private Map<String, String> createLibraryMap(
      Map<String, String> oldLibraries, Map<String, String> newLibraries) {

    // Filter out Mac temp files
    List<String> oldFileNames =
        oldLibraries.keySet().stream()
            .filter(fn -> !fn.contains("MACOSX"))
            .collect(Collectors.toList());

    List<String> newFileNames =
        newLibraries.keySet().stream()
            .filter(fn -> !fn.contains("MACOSX"))
            .collect(Collectors.toList());

    // Map filenames based on similarity
    Map<String, String> libraryMap = mapByEditDistance(oldFileNames, newFileNames);

    // If new measure has more files, add unmapped new files with "NA-" prefix
    if (newFileNames.size() > oldFileNames.size()) {
      Set<String> matchedNewFiles = new HashSet<>(libraryMap.values());
      int newFileIndex = 0;

      for (String newFileName : newFileNames) {
        if (!matchedNewFiles.contains(newFileName)) {
          libraryMap.put("NA-" + newFileIndex, newFileName);
          newFileIndex++;
        }
      }
    }

    return libraryMap;
  }

  /** Helper class to hold distance and matched string */
  private static class DistanceMatch {
    final double distance;
    final String newString;

    DistanceMatch(double distance, String newString) {
      this.distance = distance;
      this.newString = newString;
    }
  }

  /** Helper class to hold string with its minimum distance */
  private static class StringWithMinDist {
    final String string;
    final double minDistance;

    StringWithMinDist(String string, double minDistance) {
      this.string = string;
      this.minDistance = minDistance;
    }
  }
}
