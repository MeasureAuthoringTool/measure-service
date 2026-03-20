package cms.gov.madie.measure.dto.excel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class MeasureAccessReportDTO {
  private String id;
  private String measureName;
  private String measureModel;
  private String cmsId;
  private String owner;
  private List<SharedWithUser> sharedWith;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SharedWithUser {
    private String userId;
    private String dateShared;
  }
}
