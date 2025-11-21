package cms.gov.madie.measure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
  }
}
