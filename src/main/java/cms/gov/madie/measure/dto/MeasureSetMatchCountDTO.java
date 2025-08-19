package cms.gov.madie.measure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@AllArgsConstructor
public class MeasureSetMatchCountDTO {
  @Field("_id")
  private String measureSetId;

  private int matchCount;
  private String matchedMeasureId;
}
