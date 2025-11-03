package cms.gov.madie.measure.dto.excel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PopulationDTO {
  private String name;
  private Double expected;
  private Double actual;
  private Boolean pass;
}
