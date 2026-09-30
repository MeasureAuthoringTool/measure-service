package cms.gov.madie.measure.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single user's owned and shared measures for the bulk Full User Export. Each list holds the
 * latest measure per family (one row per measureSetId), mirroring what the per-user search endpoint
 * returns - but assembled for many users in a single request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserMeasuresDTO {
  private List<MeasureListDTO> ownedMeasures = new ArrayList<>();
  private List<MeasureListDTO> sharedMeasures = new ArrayList<>();
}
