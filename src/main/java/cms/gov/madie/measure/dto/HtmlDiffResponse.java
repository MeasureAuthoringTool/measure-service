package cms.gov.madie.measure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HtmlDiffResponse {

  private String oldHtml;
  private String newHtml;
  private List<DiffItem> differences;

  @Data
  public static class DiffItem {
    private String field;
    private String oldValue;
    private String newValue;
    private boolean styleChange;
    private Map<String, Map<String, String>> styleDiff;
  }
}
