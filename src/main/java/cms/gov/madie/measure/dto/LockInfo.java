package cms.gov.madie.measure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LockInfo {
  @JsonProperty("locked")
  private boolean isLocked;

  private String lockedBy;
  private String lockedId;
}
