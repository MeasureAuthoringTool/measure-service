package cms.gov.madie.measure.services;

import cms.gov.madie.measure.factories.ModelValidatorFactory;
import cms.gov.madie.measure.factories.PackageServiceFactory;
import cms.gov.madie.measure.utils.MeasureUtil;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
    Map<String, List<Element>> oldFieldMap = buildFieldMap(oldDoc);
    Map<String, List<Element>> newFieldMap = buildFieldMap(newDoc);
    Set<String> allFields = new HashSet<>();
    allFields.addAll(oldFieldMap.keySet());
    allFields.addAll(newFieldMap.keySet());
    for (String fieldName : allFields) {
      List<Element> oldValues = oldFieldMap.getOrDefault(fieldName, Collections.emptyList());
      List<Element> newValues = newFieldMap.getOrDefault(fieldName, Collections.emptyList());
      // Special handling for single-value fields
      if (oldValues.size() == 1 && newValues.size() == 1) {
        String oldNorm = normalizeVisibleHtml(oldValues.get(0));
        String newNorm = normalizeVisibleHtml(newValues.get(0));
        if (!oldNorm.equals(newNorm)) {
          String[] highlighted =
              generateHighlightedDiff(oldValues.get(0).html(), newValues.get(0).html());
          HtmlDiffResponse.DiffItem item = new HtmlDiffResponse.DiffItem();
          item.setField(fieldName);
          item.setOldValue(highlighted[0]);
          item.setNewValue(highlighted[1]);
          diffs.add(item);
        }
        continue;
      }
      // Multi-value fields: order-agnostic matching
      List<String> oldNorms = new ArrayList<>();
      List<String> newNorms = new ArrayList<>();
      List<String> oldHtmls = new ArrayList<>();
      List<String> newHtmls = new ArrayList<>();
      for (Element e : oldValues) {
        oldNorms.add(e != null ? normalizeVisibleHtml(e) : "");
        oldHtmls.add(e != null ? e.html() : "");
      }
      for (Element e : newValues) {
        newNorms.add(e != null ? normalizeVisibleHtml(e) : "");
        newHtmls.add(e != null ? e.html() : "");
      }
      boolean[] matched = new boolean[newNorms.size()];
      for (int i = 0; i < oldNorms.size(); i++) {
        String oldNorm = oldNorms.get(i);
        int matchIdx = -1;
        for (int j = 0; j < newNorms.size(); j++) {
          if (!matched[j] && oldNorm.equals(newNorms.get(j))) {
            matched[j] = true;
            matchIdx = j;
            break;
          }
        }
        if (matchIdx == -1) {
          String[] highlighted = generateHighlightedDiff(oldHtmls.get(i), "");
          HtmlDiffResponse.DiffItem item = new HtmlDiffResponse.DiffItem();
          item.setField(fieldName);
          item.setOldValue(highlighted[0]);
          item.setNewValue("");
          diffs.add(item);
        }
      }
      for (int j = 0; j < newNorms.size(); j++) {
        if (!matched[j]) {
          String[] highlighted = generateHighlightedDiff("", newHtmls.get(j));
          HtmlDiffResponse.DiffItem item = new HtmlDiffResponse.DiffItem();
          item.setField(fieldName);
          item.setOldValue("");
          item.setNewValue(highlighted[1]);
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

  // Normalize HTML for visible formatting: canonicalize tags, remove non-visible attrs, ignore
  // whitespace
  private String normalizeVisibleHtml(Element element) {
    if (element == null) return "";
    return element.html()
        .replaceAll("<(b|strong)( [^>]*)?>", "<strong>")
        .replaceAll("</(b|strong)>", "</strong>")
        .replaceAll("<(i|em)( [^>]*)?>", "<em>")
        .replaceAll("</(i|em)>", "</em>")
        .replaceAll("<(u)( [^>]*)?>", "<u>")
        .replaceAll("</u>", "</u>")
        .replaceAll("\s+", " ") // collapse whitespace
        .replaceAll(" style=\"[^\"]*\"", "") // remove inline style
        .trim();
  }

  // Helper to generate highlighted HTML diff for old and new values
  private String[] generateHighlightedDiff(String oldHtml, String newHtml) {
    // Strip tags for word diff, but keep original HTML for reconstruction
    String oldText = Jsoup.parse(oldHtml).text();
    String newText = Jsoup.parse(newHtml).text();
    DiffRowGenerator generator =
        DiffRowGenerator.create()
            .showInlineDiffs(true)
            .inlineDiffByWord(true)
            .oldTag(f -> "<span style='background:#f7c5c5;text-decoration:line-through;'>")
            .newTag(f -> "<span style='background:#c8f7c5;'>")
            .build();
    List<DiffRow> rows =
        generator.generateDiffRows(
            Arrays.asList(oldText.split("\n")), Arrays.asList(newText.split("\n")));
    StringBuilder oldResult = new StringBuilder();
    StringBuilder newResult = new StringBuilder();
    for (DiffRow row : rows) {
      oldResult.append(row.getOldLine());
      newResult.append(row.getNewLine());
    }
    return new String[] {oldResult.toString(), newResult.toString()};
  }
}
