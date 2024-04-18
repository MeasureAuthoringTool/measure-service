package cms.gov.madie.measure.dto;

import gov.cms.madie.models.dto.MeasureList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FacetDTO {

  List<Object> count;
  List<MeasureList> queryResults;
}
