package cms.gov.madie.measure.poc.combined.model;

import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class QDMMeasure extends Measure {

  @NotNull private String scoring;
}
