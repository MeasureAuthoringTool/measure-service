package cms.gov.madie.measure.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LockResponse {
  private boolean isLocked;
  private String lockedBy;
}
