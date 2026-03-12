package cms.gov.madie.measure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class MeasureSearchCriteria {
  private String searchField;
  private List<String> optionalSearchProperties; // can be ["measureName", "version", "cmsId"] ..etc
  private String model;
  private Boolean draft;
  private List<String> excludeByMeasureIds;
  private boolean fromCompositeMeasureComponent;
  private List<String>
      allowedScoringTypes; // List of scoring types to include (e.g., ["Proportion", "Ratio"])
  private List<String> priorityMeasureSets; // List of measure set IDs to prioritize in sorting
}
