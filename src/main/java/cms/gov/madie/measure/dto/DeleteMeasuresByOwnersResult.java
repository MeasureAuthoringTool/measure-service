package cms.gov.madie.measure.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteMeasuresByOwnersResult {

  private List<String> harpIds = new ArrayList<>();
  private int measureSetCount;
  private int measureCount;
  private int exportCount;
  private int exportGridFsFileCount;
  private long measureActionLogCount;
  private long measureSetActionLogCount;
  private long testCaseActionLogCount;

  private long measureLockCount;
  private long testCaseLockCount;

  private List<String> measureSetIds = new ArrayList<>();
  private List<String> measureIds = new ArrayList<>();
}
