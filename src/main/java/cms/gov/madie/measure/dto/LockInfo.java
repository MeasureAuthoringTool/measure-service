package cms.gov.madie.measure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LockInfo {
  private boolean isLocked;
  private String lockedBy;
  private String lockedId;
  private String lockMessage;
}
