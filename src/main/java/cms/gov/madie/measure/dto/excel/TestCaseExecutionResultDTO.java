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
public class TestCaseExecutionResultDTO {
  private String testCaseId;
  private List<PopulationDTO> populations;
  private String notes;
  private String last;
  private String first;
  private String birthdate;
  private String expired;
  private String deathdate;
  private String ethnicity;
  private String race;
  private String gender;
  private List<DefinitionDTO> definitions;
  private List<FunctionDTO> functions;
  private List<GroupedStratificationDTO> stratifications;
}
