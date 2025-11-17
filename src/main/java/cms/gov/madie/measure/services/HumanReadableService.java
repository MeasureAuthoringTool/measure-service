package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.HtmlDiffResponse;
import cms.gov.madie.measure.factories.ModelValidatorFactory;
import cms.gov.madie.measure.factories.PackageServiceFactory;
import cms.gov.madie.measure.utils.MeasureUtil;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@AllArgsConstructor
@Service
public class HumanReadableService {

  private MeasureService measureService;
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

  public HtmlDiffResponse compareHtml(String oldHtml, String newHtml) {
    Document oldDoc = Jsoup.parse(oldHtml);
    Document newDoc = Jsoup.parse(newHtml);

    List<HtmlDiffResponse.DiffItem> diffs = new ArrayList<>();

    // Build field name -> list of value elements for old and new
    Map<String, List<Element>> oldFieldMap = buildFieldMap(oldDoc);
    Map<String, List<Element>> newFieldMap = buildFieldMap(newDoc);

    for (Map.Entry<String, List<Element>> entry : oldFieldMap.entrySet()) {
      String fieldName = entry.getKey();
      List<Element> oldValues = entry.getValue();
      List<Element> newValues = newFieldMap.getOrDefault(fieldName, Collections.emptyList());
      int max = Math.max(oldValues.size(), newValues.size());
      for (int i = 0; i < max; i++) {
        Element oldValueCell = i < oldValues.size() ? oldValues.get(i) : null;
        Element newValueCell = i < newValues.size() ? newValues.get(i) : null;
        String oldValueHtml = oldValueCell != null ? normalizeHtml(oldValueCell.html()) : "";
        String newValueHtml = newValueCell != null ? normalizeHtml(newValueCell.html()) : "";
        boolean changed = !oldValueHtml.equals(newValueHtml);
        Map<String, Map<String, String>> styleDiff =
            (oldValueCell != null && newValueCell != null)
                ? computeStyleDiff(oldValueCell, newValueCell)
                : Collections.emptyMap();
        if (changed) {
          HtmlDiffResponse.DiffItem item = new HtmlDiffResponse.DiffItem();
          item.setField(fieldName + (max > 1 ? " [" + (i + 1) + "]" : ""));
          item.setOldValue(oldValueCell != null ? oldValueCell.html() : "");
          item.setNewValue(newValueCell != null ? newValueCell.html() : "");
          item.setStyleChange(!styleDiff.isEmpty());
          item.setStyleDiff(styleDiff);
          diffs.add(item);
        }
      }
    }
    HtmlDiffResponse response = new HtmlDiffResponse();
    response.setOldHtml(oldHtml);
    response.setNewHtml(newHtml);
    response.setDifferences(diffs);
    return response;
  }

  // Build a map of field name to list of value <td> elements
  private Map<String, List<Element>> buildFieldMap(Document doc) {
    Map<String, List<Element>> map = new LinkedHashMap<>();
    for (Element row : doc.select("tr")) {
      Element th = row.selectFirst("th.row-header");
      Element td = row.selectFirst("td.content-container");
      if (th != null && td != null) {
        String field = th.text();
        map.computeIfAbsent(field, k -> new ArrayList<>()).add(td);
      }
    }
    return map;
  }

  // Helper to find row by <th> text
  private Element findMatchingRowByTh(Document doc, String fieldName) {
    for (Element row : doc.select("tr")) {
      Element th = row.selectFirst("th.row-header");
      if (th != null && th.text().equals(fieldName)) {
        return row;
      }
    }
    return null;
  }

  // Normalize HTML for comparison: trim, remove empty <p/>, collapse whitespace
  private String normalizeHtml(String html) {
    if (html == null) return "";
    // Remove empty <p> tags
    html = html.replaceAll("<p>\\s*</p>", "");
    // Collapse whitespace
    html = html.replaceAll("\\s+", " ").trim();
    return html;
  }

  private Map<String, Map<String, String>> computeStyleDiff(
      Element oldElement, Element newElement) {
    Map<String, String> oldStyles = parseInlineStyle(oldElement.attr("style"));
    Map<String, String> newStyles = parseInlineStyle(newElement.attr("style"));

    Map<String, Map<String, String>> diff = new HashMap<>();
    Set<String> allKeys = new HashSet<>();
    allKeys.addAll(oldStyles.keySet());
    allKeys.addAll(newStyles.keySet());

    for (String key : allKeys) {
      String oldVal = oldStyles.getOrDefault(key, "default");
      String newVal = newStyles.getOrDefault(key, "default");
      if (!oldVal.equals(newVal)) {
        Map<String, String> change = new HashMap<>();
        change.put("old", oldVal);
        change.put("new", newVal);
        diff.put(key, change);
      }
    }
    return diff;
  }

  private Map<String, String> parseInlineStyle(String styleAttr) {
    Map<String, String> styles = new HashMap<>();
    if (styleAttr != null && !styleAttr.isEmpty()) {
      String[] parts = styleAttr.split(";");
      for (String part : parts) {
        String[] kv = part.split(":");
        if (kv.length == 2) {
          styles.put(kv[0].trim(), kv[1].trim());
        }
      }
    }
    return styles;
  }
}
