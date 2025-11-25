package cms.gov.madie.measure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of CQL differentiator operation containing all file comparisons between two measure
 * versions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CqlDiffResult {

  /** List of file comparisons with normalized and reordered text */
  private List<CqlFileComparison> comparisons;

  /** ID of the old measure */
  private String oldMeasureId;

  /** ID of the new measure */
  private String newMeasureId;
}
