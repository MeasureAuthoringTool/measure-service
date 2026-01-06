package cms.gov.madie.measure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a comparison between old and new versions of a CQL file. Contains normalized and
 * reordered text ready for diff display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CqlFileComparisonDTO {
  // Original filename from the old measure (may be "not found" for new files)
  private String oldFileName;

  // Filename from the new measure
  private String newFileName;

  // Normalized text content from old measure
  private String oldText;

  // Normalized and reordered text content from new measure
  private String newText;
}
