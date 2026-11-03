package cms.gov.madie.measure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class CloneMeasureRequest {
  @NotBlank(message = "Measure ID is required.")
  private String measureId;
}
