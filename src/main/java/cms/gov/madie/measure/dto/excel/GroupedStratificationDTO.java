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
public class GroupedStratificationDTO {
  private String testCaseId;
  private String stratId;
  private String stratName;
  private List<StratificationDTO> stratificationDtos;
}
