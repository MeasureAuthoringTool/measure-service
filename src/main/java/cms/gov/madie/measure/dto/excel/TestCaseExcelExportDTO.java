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
public class TestCaseExcelExportDTO {
  private String groupId;
  private String groupNumber;
  private List<TestCaseExecutionResultDTO> testCaseExecutionResults;
}
