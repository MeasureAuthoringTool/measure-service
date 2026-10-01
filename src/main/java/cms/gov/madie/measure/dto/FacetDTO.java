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
public class FacetDTO {

  List<Object> count;
  List<TotalCountDTO> countFacet;
  List<MeasureListDTO> queryResults;

  /**
   * Holds the result of a {@code $count} facet sub-pipeline (i.e. {@code [{ total: <n> }]}). Used
   * to obtain a reliable total document count that does not depend on aggregation field-name
   * mapping (unlike {@code sortByCount}).
   */
  @Data
  @Builder(toBuilder = true)
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TotalCountDTO {
    private long total;
  }
}
