package cms.gov.madie.measure.services;

import cms.gov.madie.measure.factories.ModelValidatorFactory;
import cms.gov.madie.measure.factories.PackageServiceFactory;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.utils.MeasureUtil;

import gov.cms.madie.models.measure.Export;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import cms.gov.madie.measure.dto.HtmlDiffResponse;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import org.apache.commons.text.similarity.LevenshteinDistance;

@Slf4j
@AllArgsConstructor
@Service
public class HumanReadableService {

  private MeasureService measureService;
  private ExportRepository exportRepository;
  private final PackageServiceFactory packageServiceFactory;
  private final ModelValidatorFactory modelValidatorFactory;
  private final MeasureUtil measureUtil;

  public String getHumanReadableWithCSS(String measureId, String username, String accessToken) {
    Measure measure = measureService.findMeasureById(measureId);
    if (measure == null) {
      throw new ResourceNotFoundException("Measure", measureId);
    }

    PackageService packageService =
        packageServiceFactory.getPackageService(ModelType.valueOfName(measure.getModel()));
    if (measure.getMeasureMetaData() != null && !measure.getMeasureMetaData().isDraft()) {
      return packageService.getHumanReadableForVersionedMeasure(measure, username, accessToken);
    } else {
      ModelValidator modelValidator =
          modelValidatorFactory.getModelValidator(ModelType.valueOfName(measure.getModel()));
      measure = measureUtil.validateAllMeasureDependencies(measure);
      modelValidator.validateMetadata(measure);
      modelValidator.validateGroups(measure);
      modelValidator.validateCqlErrors(measure);
    }
    return packageService.getHumanReadable(measure, username, accessToken);
  }

  /**
   * @param newMeasureId New Measure Id
   * @param oldMeasureId Old Measure Id
   * @param username The username for fetching the draft measure's content
   * @param accessToken The access token for fetching the draft measure's content
   * @return HtmlDiffResponse containing the original HTML documents and a list of differences
   */
  public HtmlDiffResponse compareHtml(
      String newMeasureId, String oldMeasureId, String username, String accessToken) {
    Export oldMeasureExport = exportRepository.findByMeasureId(oldMeasureId).orElse(null);
    Export newMeasureExport = exportRepository.findByMeasureId(newMeasureId).orElse(null);
    String oldHtml =
        oldMeasureExport != null
            ? oldMeasureExport.getHumanReadable()
            : getHumanReadableWithCSS(oldMeasureId, username, accessToken);
    String newHtml =
        newMeasureExport != null
            ? newMeasureExport.getHumanReadable()
            : getHumanReadableWithCSS(newMeasureId, username, accessToken);
    return compareHtml(oldHtml, newHtml);
  }

  /**
   * Compares two HTML strings and returns a list of differences with highlighted changes.
   *
   * <p>This method compares HTML containing field-value pairs. It handles duplicate field names in
   * an order-agnostic manner using exact matching. For single-value fields that differ, it
   * generates word-level diff highlighting to show precisely what changed.
   *
   * <p>The comparison process:
   *
   * <ul>
   *   <li>Parses both HTML documents to extract field-value pairs (using table structure)
   *   <li>Identifies fields present in both, or unique to one
   *   <li>For common fields, compares values:
   *       <ul>
   *         <li>If single values differ: Generates highlighted diff
   *         <li>If multiple values: Matches identical values, marks unmatched as added/removed
   *       </ul>
   *   <li>Collects all differences into a response object
   * </ul>
   *
   * <p>Highlighting styles:
   *
   * <ul>
   *   <li>Modifications: Red strikethrough for removals, Green background for additions
   *   <li>Additions: Green background (#90EE90)
   *   <li>Deletions: Red background (#FFB6C1) with strikethrough
   * </ul>
   *
   * @param oldHtml The original HTML string
   * @param newHtml The new HTML string to compare against
   * @return HtmlDiffResponse containing the original HTML documents and a list of differences
   */
  public HtmlDiffResponse compareHtml(String oldHtml, String newHtml) {
    log.debug("Starting HTML comparison");

    // Extract field-value pairs from both HTML documents
    Map<String, List<Element>> oldFields = extractFieldsFromHtml(oldHtml);
    Map<String, List<Element>> newFields = extractFieldsFromHtml(newHtml);

    // Collect all unique field names from both documents
    Set<String> allFieldNames = new HashSet<>();
    allFieldNames.addAll(oldFields.keySet());
    allFieldNames.addAll(newFields.keySet());

    List<HtmlDiffResponse.DiffItem> differences = new ArrayList<>();

    // Process each field name
    for (String fieldName : allFieldNames) {
      List<Element> oldValues = oldFields.getOrDefault(fieldName, new ArrayList<>());
      List<Element> newValues = newFields.getOrDefault(fieldName, new ArrayList<>());

      // Compare values and find differences
      List<HtmlDiffResponse.DiffItem> fieldDiffs =
          compareFieldValues(fieldName, oldValues, newValues);
      differences.addAll(fieldDiffs);
    }

    log.debug("Found {} differences", differences.size());

    return HtmlDiffResponse.builder()
        .oldHtml(oldHtml)
        .newHtml(newHtml)
        .differences(differences)
        .build();
  }

  /**
   * Extracts field-value pairs from an HTML document.
   *
   * <p>Supports three formats:
   *
   * <ul>
   *   <li>Table format: th (field name) + td (value) pairs in table rows
   *   <li>List format: label.list-header (field name) + pre.cql-definition-body (value) in list
   *       items
   *   <li>Section format: h3 (field name) + following div with ul/li list (multi-item value)
   * </ul>
   *
   * @param html The HTML document to parse
   * @return A map of field names to lists of value Elements (supporting duplicate field names)
   */
  private Map<String, List<Element>> extractFieldsFromHtml(String html) {
    Map<String, List<Element>> fields = new LinkedHashMap<>();

    if (html == null || html.trim().isEmpty()) {
      return fields;
    }

    try {
      Document doc = Jsoup.parse(html);

      // Format 1: Extract from table rows (th + td pairs)
      extractTableFields(doc, fields);

      // Format 2: Extract from list items (label + pre pairs)
      extractListFields(doc, fields);

      // Format 3: Extract from h3 sections (h3 + div with list)
      extractSectionFields(doc, fields);

      log.debug("Total unique fields extracted: {}", fields.size());
    } catch (Exception e) {
      log.error("Error parsing HTML", e);
    }
    return fields;
  }

  /**
   * Extracts field-value pairs from table rows (th + td format). Supports FHIR (narrative-table)
   * and QDM (header_table) structures.
   */
  private void extractTableFields(Document doc, Map<String, List<Element>> fields) {
    // Find all table rows in the narrative table
    // Use direct child selector to avoid nested tables
    // Support both narrative-table (FHIR) and header_table (QDM)
    Elements rows =
        doc.select("table.narrative-table > tbody > tr, table.header_table > tbody > tr");
    if (rows.isEmpty()) {
      // Fallback for older HTML structures or if class is missing
      rows = doc.select("body > table > tbody > tr");
    }

    log.debug("Found {} table rows in HTML", rows.size());

    for (Element row : rows) {
      // Get all th and td elements that are DIRECT children of the row
      // Use direct child selector (>) to avoid selecting nested elements within value cells
      // QDM HTML can have multiple header-value pairs per row (e.g., CMS ID + eCQM Version)
      Elements headers = row.select("> th");
      Elements values = row.select("> td");

      // Handle rows with multiple th-td pairs (QDM structure)
      // or single th-td pair (FHIR structure)
      int pairCount = Math.min(headers.size(), values.size());

      for (int i = 0; i < pairCount; i++) {
        Element header = headers.get(i);
        Element value = values.get(i);

        String fieldName = header.text().trim();
        if (!fieldName.isEmpty()) {
          fields.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(value);
          log.debug(
              "Extracted table field '{}' with value length {}", fieldName, value.html().length());
        }
      }
    }
  }

  /**
   * Extracts field-value pairs from list items (label + pre format). Field names are in
   * label.list-header elements, values in pre.cql-definition-body elements.
   */
  private void extractListFields(Document doc, Map<String, List<Element>> fields) {
    // Find all labels with class "list-header" that contain field names
    Elements labels = doc.select("label.list-header");

    log.debug("Found {} list-header labels in HTML", labels.size());

    for (Element label : labels) {
      String fieldName = label.text().trim();
      if (fieldName.isEmpty()) {
        continue;
      }

      // Find the parent <li> element
      Element parentLi = label.parent();
      while (parentLi != null && !parentLi.tagName().equals("li")) {
        parentLi = parentLi.parent();
      }

      if (parentLi != null) {
        // Find the associated pre.cql-definition-body element within the same list item
        Elements preElements = parentLi.select("pre.cql-definition-body");

        if (!preElements.isEmpty()) {
          // Use the first pre element as the value
          Element value = preElements.first();
          fields.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(value);
          log.debug(
              "Extracted list field '{}' with value length {}", fieldName, value.html().length());
        }
      }
    }
  }

  /**
   * Extracts field-value pairs from h3 section headers (h3 + div with list format). Specifically
   * handles "Terminology" and "Data Criteria (QDM Data Elements)" sections. The entire div
   * containing the list is treated as a single value.
   */
  private void extractSectionFields(Document doc, Map<String, List<Element>> fields) {
    // Find all h3 elements that might be section headers
    Elements h3Elements = doc.select("h3");

    log.debug("Found {} h3 section headers in HTML", h3Elements.size());

    for (Element h3 : h3Elements) {
      // Extract field name from the anchor text within h3
      Element anchor = h3.selectFirst("a");
      if (anchor == null) {
        continue;
      }

      String fieldName = anchor.text().trim();

      // Only extract specific known sections
      if (!fieldName.equals("Terminology")
          && !fieldName.equals("Data Criteria (QDM Data Elements)")) {
        continue;
      }

      // Find the next sibling div that contains the value
      Element nextElement = h3.nextElementSibling();
      if (nextElement != null && nextElement.tagName().equals("div")) {
        // The entire div with its list is the value
        fields.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(nextElement);
        log.debug(
            "Extracted section field '{}' with value length {}",
            fieldName,
            nextElement.html().length());
      }
    }
  }

  /**
   * Compares values for a specific field and generates diff items.
   *
   * <p>Uses exact matching to pair old and new values order-agnostically. For single-value fields
   * that differ, generates word-level diff highlighting. For multi-value fields, unmatched values
   * are reported as deletions and additions.
   *
   * @param fieldName The name of the field being compared
   * @param oldValues List of value Elements from the old HTML
   * @param newValues List of value Elements from the new HTML
   * @return List of diff items representing changes for this field
   */
  private List<HtmlDiffResponse.DiffItem> compareFieldValues(
      String fieldName, List<Element> oldValues, List<Element> newValues) {

    List<HtmlDiffResponse.DiffItem> diffs = new ArrayList<>();

    // Handling for single-value fields
    if (oldValues.size() == 1 && newValues.size() == 1) {
      String oldNormalized = normalizeHtml(oldValues.get(0));
      String newNormalized = normalizeHtml(newValues.get(0));

      if (!oldNormalized.equals(newNormalized)) {
        // Check if value contains lists (ul or ol) - if so, use list-aware comparison
        Element oldElement = oldValues.get(0);
        Element newElement = newValues.get(0);

        if (containsList(oldElement) || containsList(newElement)) {
          // List-aware comparison: highlight only changed items
          String[] highlighted = compareListContent(oldElement.html(), newElement.html());
          if (highlighted != null) {
            diffs.add(
                HtmlDiffResponse.DiffItem.builder()
                    .field(fieldName)
                    .oldValue(highlighted[0])
                    .newValue(highlighted[1])
                    .build());
          }
        } else {
          // Regular word-level diff highlighting
          String[] highlighted = generateHighlightedDiff(oldElement.html(), newElement.html());

          // Only add diff if highlighted is not null (whitespace-only changes return null)
          if (highlighted != null) {
            diffs.add(
                HtmlDiffResponse.DiffItem.builder()
                    .field(fieldName)
                    .oldValue(highlighted[0])
                    .newValue(highlighted[1])
                    .build());
          }
        }
      }
      return diffs;
    }

    // Multi-value fields: order-agnostic matching with fuzzy matching for modifications
    boolean[] matchedOldValues = new boolean[oldValues.size()];
    boolean[] matchedNewValues = new boolean[newValues.size()];

    // Phase 1: Exact matching - find unchanged values
    for (int i = 0; i < oldValues.size(); i++) {
      if (matchedOldValues[i]) {
        continue;
      }

      String oldNormalized = normalizeHtml(oldValues.get(i));

      for (int j = 0; j < newValues.size(); j++) {
        if (!matchedNewValues[j]) {
          String newNormalized = normalizeHtml(newValues.get(j));

          if (oldNormalized.equals(newNormalized)) {
            // Found exact match - no difference to report
            matchedOldValues[i] = true;
            matchedNewValues[j] = true;
            break;
          }
        }
      }
    }

    // Phase 2: Fuzzy matching - detect modifications (not just deletions/additions)
    // For each unmatched old value, find the best matching new value
    for (int i = 0; i < oldValues.size(); i++) {
      if (matchedOldValues[i]) {
        continue;
      } // Already matched exactly

      Element oldValue = oldValues.get(i);
      String oldNormalized = normalizeHtml(oldValue);

      double bestSimilarity = 0.0;
      int bestMatchIndex = -1;

      // Find the most similar unmatched new value
      for (int j = 0; j < newValues.size(); j++) {
        if (matchedNewValues[j]) {
          continue;
        } // Already matched

        String newNormalized = normalizeHtml(newValues.get(j));
        double similarity = calculateSimilarity(oldNormalized, newNormalized);

        if (similarity > bestSimilarity) {
          bestSimilarity = similarity;
          bestMatchIndex = j;
        }
      }

      // If similarity is above threshold, treat as modification (not deletion + addition)
      // Threshold of 0.4 (40%) - adjust based on testing
      if (bestMatchIndex >= 0 && bestSimilarity >= 0.4) {
        // Found a modification - generate word-level diff
        Element newValue = newValues.get(bestMatchIndex);
        matchedOldValues[i] = true;
        matchedNewValues[bestMatchIndex] = true;

        String[] highlighted = generateHighlightedDiff(oldValue.html(), newValue.html());
        if (highlighted != null) {
          diffs.add(
              HtmlDiffResponse.DiffItem.builder()
                  .field(fieldName)
                  .oldValue(highlighted[0])
                  .newValue(highlighted[1])
                  .build());
        }
      } else {
        // No good match found - treat as deletion
        diffs.add(
            HtmlDiffResponse.DiffItem.builder()
                .field(fieldName)
                .oldValue(applyDeletionStyle(oldValue.html()))
                .newValue("")
                .build());
        matchedOldValues[i] = true;
      }
    }

    // Phase 3: Report any remaining unmatched new values as additions
    for (int j = 0; j < newValues.size(); j++) {
      if (!matchedNewValues[j]) {
        diffs.add(
            HtmlDiffResponse.DiffItem.builder()
                .field(fieldName)
                .oldValue("")
                .newValue(applyAdditionStyle(newValues.get(j).html()))
                .build());
      }
    }

    return diffs;
  }

  /** Checks if an HTML element contains ul or ol lists. */
  private boolean containsList(Element element) {
    if (element == null) {
      return false;
    }
    return !element.select("ul, ol").isEmpty();
  }

  /**
   * Compares HTML content that contains lists (ul/ol), applying highlighting only to changed list
   * items. Preserves the list structure and highlights individual items that were added, removed,
   * or modified.
   *
   * @param oldHtml Original HTML content
   * @param newHtml New HTML content
   * @return Array with [highlightedOldHtml, highlightedNewHtml], or null if no differences
   */
  private String[] compareListContent(String oldHtml, String newHtml) {
    try {
      Document oldDoc = Jsoup.parse(oldHtml);
      Document newDoc = Jsoup.parse(newHtml);

      // Find all lists (ul and ol)
      Elements oldLists = oldDoc.select("ul, ol");
      Elements newLists = newDoc.select("ul, ol");

      if (oldLists.isEmpty() && newLists.isEmpty()) {
        // No lists found, fall back to regular diff
        return generateHighlightedDiff(oldHtml, newHtml);
      }

      // Process each list
      boolean hasChanges = false;

      // For each old list, find corresponding new list and compare items
      for (int listIdx = 0; listIdx < Math.max(oldLists.size(), newLists.size()); listIdx++) {
        Element oldList = listIdx < oldLists.size() ? oldLists.get(listIdx) : null;
        Element newList = listIdx < newLists.size() ? newLists.get(listIdx) : null;

        if (oldList != null && newList != null) {
          // Both lists exist - compare their items
          Elements oldItems = oldList.select("> li");
          Elements newItems = newList.select("> li");

          if (compareAndHighlightListItems(oldItems, newItems)) {
            hasChanges = true;
          }
        } else if (oldList != null) {
          // List was removed - highlight all old items as deleted
          Elements oldItems = oldList.select("> li");
          for (Element item : oldItems) {
            item.html(applyDeletionStyle(item.html()));
          }
          hasChanges = true;
        } else if (newList != null) {
          // List was added - highlight all new items as added
          Elements newItems = newList.select("> li");
          for (Element item : newItems) {
            item.html(applyAdditionStyle(item.html()));
          }
          hasChanges = true;
        }
      }

      if (!hasChanges) {
        // All list items matched exactly - no differences
        return null;
      }

      return new String[] {oldDoc.body().html(), newDoc.body().html()};

    } catch (Exception e) {
      log.error("Error comparing list content", e);
      // Fall back to regular diff
      return generateHighlightedDiff(oldHtml, newHtml);
    }
  }

  /**
   * Compares list items and applies highlighting to changed items. Uses fuzzy matching to pair
   * similar items.
   *
   * @param oldItems Old list items
   * @param newItems New list items
   * @return true if any changes were found
   */
  private boolean compareAndHighlightListItems(Elements oldItems, Elements newItems) {
    boolean hasChanges = false;
    boolean[] matchedOld = new boolean[oldItems.size()];
    boolean[] matchedNew = new boolean[newItems.size()];

    // Phase 1: Exact matching
    for (int i = 0; i < oldItems.size(); i++) {
      if (matchedOld[i]) {
        continue;
      }

      String oldNormalized = normalizeHtml(oldItems.get(i));

      for (int j = 0; j < newItems.size(); j++) {
        if (matchedNew[j]) {
          continue;
        }

        String newNormalized = normalizeHtml(newItems.get(j));

        if (oldNormalized.equals(newNormalized)) {
          // Exact match - no highlighting needed
          matchedOld[i] = true;
          matchedNew[j] = true;
          break;
        }
      }
    }

    // Phase 2: Fuzzy matching for modifications
    for (int i = 0; i < oldItems.size(); i++) {
      if (matchedOld[i]) {
        continue;
      }

      Element oldItem = oldItems.get(i);
      String oldNormalized = normalizeHtml(oldItem);

      double bestSimilarity = 0.0;
      int bestMatchIndex = -1;

      for (int j = 0; j < newItems.size(); j++) {
        if (matchedNew[j]) {
          continue;
        }

        String newNormalized = normalizeHtml(newItems.get(j));
        double similarity = calculateSimilarity(oldNormalized, newNormalized);

        if (similarity > bestSimilarity) {
          bestSimilarity = similarity;
          bestMatchIndex = j;
        }
      }

      if (bestMatchIndex >= 0 && bestSimilarity >= 0.4) {
        // Found a modification - apply word-level diff to the item content
        Element newItem = newItems.get(bestMatchIndex);
        matchedOld[i] = true;
        matchedNew[bestMatchIndex] = true;

        String[] highlighted = generateHighlightedDiff(oldItem.html(), newItem.html());
        if (highlighted != null) {
          oldItem.html(highlighted[0]);
          newItem.html(highlighted[1]);
          hasChanges = true;
        }
      } else {
        // No good match - mark as deleted
        oldItem.html(applyDeletionStyle(oldItem.html()));
        matchedOld[i] = true;
        hasChanges = true;
      }
    }

    // Phase 3: Mark remaining new items as added
    for (int j = 0; j < newItems.size(); j++) {
      if (!matchedNew[j]) {
        Element newItem = newItems.get(j);
        newItem.html(applyAdditionStyle(newItem.html()));
        hasChanges = true;
      }
    }

    return hasChanges;
  }

  /**
   * Calculates similarity between two strings using Levenshtein distance.
   *
   * <p>Returns a value between 0.0 (completely different) and 1.0 (identical). This is used to
   * detect modifications in multi-value fields where the order may change.
   *
   * @param str1 First string
   * @param str2 Second string
   * @return Similarity score between 0.0 and 1.0
   */
  private double calculateSimilarity(String str1, String str2) {
    if (str1 == null || str2 == null) {
      return 0.0;
    }

    if (str1.equals(str2)) {
      return 1.0;
    }

    int maxLength = Math.max(str1.length(), str2.length());
    if (maxLength == 0) {
      return 1.0;
    }

    int distance = LevenshteinDistance.getDefaultInstance().apply(str1, str2);
    return 1.0 - ((double) distance / maxLength);
  }

  /**
   * Normalizes HTML content for comparison.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Normalizes whitespace (multiple spaces become single space)
   *   <li>Converts equivalent tags (b/strong, i/em) to a standard form
   *   <li>Removes inline styles that don't affect content
   * </ul>
   *
   * @param element The HTML element to normalize
   * @return Normalized HTML string
   */
  private String normalizeHtml(Element element) {
    if (element == null) {
      return "";
    }

    String html = element.html();

    // Normalize equivalent tags and whitespace
    return html.replaceAll("<(b|strong)( [^>]*)?>", "<strong>")
        .replaceAll("</(b|strong)>", "</strong>")
        .replaceAll("<(i|em)( [^>]*)?>", "<em>")
        .replaceAll("</(i|em)>", "</em>")
        .replaceAll("\\s+", " ") // collapse whitespace
        .replaceAll(" style=\"[^\"]*\"", "") // remove inline style
        .trim();
  }

  /**
   * Generates word-level diff highlighting for two HTML values.
   *
   * <p>Extracts text content from HTML, performs word-level diffing, and applies color-coded
   * highlighting to show changes:
   *
   * <ul>
   *   <li>Deletions: Red background (#FFB6C1) with strikethrough
   *   <li>Additions: Green background (#90EE90)
   * </ul>
   *
   * <p>Returns null if the only difference is whitespace, to avoid highlighting when only spacing
   * changes.
   *
   * @param oldValue Original value
   * @param newValue New value
   * @return Array with [highlightedOldValue, highlightedNewValue], or null if whitespace-only
   *     change
   */
  private String[] generateHighlightedDiff(String oldValue, String newValue) {
    try {
      // Extract text content while preserving structure
      String oldText = Jsoup.parse(oldValue).text();
      String newText = Jsoup.parse(newValue).text();

      // Check if difference is only whitespace
      String oldTextNormalized = oldText.replaceAll("\\s+", " ").trim();
      String newTextNormalized = newText.replaceAll("\\s+", " ").trim();

      if (oldTextNormalized.equals(newTextNormalized)) {
        // Only whitespace difference - don't show as a diff
        return null;
      }

      // Create diff generator with word-level highlighting
      DiffRowGenerator generator =
          DiffRowGenerator.create()
              .showInlineDiffs(true)
              .inlineDiffByWord(true)
              .mergeOriginalRevised(false)
              .oldTag(f -> "<span style='background-color:#FFB6C1;text-decoration:line-through;'>")
              .newTag(f -> "<span style='background-color:#90EE90;'>")
              .build();

      // Generate diff rows
      List<DiffRow> rows =
          generator.generateDiffRows(
              Arrays.asList(oldText.split("\n")), Arrays.asList(newText.split("\n")));

      // Combine results
      StringBuilder oldResult = new StringBuilder();
      StringBuilder newResult = new StringBuilder();

      for (DiffRow row : rows) {
        oldResult.append(row.getOldLine());
        newResult.append(row.getNewLine());
      }

      return new String[] {oldResult.toString(), newResult.toString()};

    } catch (Exception e) {
      log.warn("Error generating diff highlighting, using simple highlighting", e);
      // Fallback to simple highlighting
      return new String[] {applyDeletionStyle(oldValue), applyAdditionStyle(newValue)};
    }
  }

  /**
   * Applies deletion styling to text (red background with strikethrough).
   *
   * @param text The text to style
   * @return Styled HTML
   */
  private String applyDeletionStyle(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    // Use div instead of span to safely wrap block-level elements
    return "<div style=\"background-color: #FFB6C1; text-decoration: line-through;\">"
        + text
        + "</div>";
  }

  /**
   * Applies addition styling to text (green background).
   *
   * @param text The text to style
   * @return Styled HTML
   */
  private String applyAdditionStyle(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    // Use div instead of span to safely wrap block-level elements
    return "<div style=\"background-color: #90EE90;\">" + text + "</div>";
  }
}
