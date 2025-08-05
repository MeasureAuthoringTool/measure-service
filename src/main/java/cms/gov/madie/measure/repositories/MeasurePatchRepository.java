package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.MeasureField;
import gov.cms.madie.models.measure.Measure;

public interface MeasurePatchRepository {
  Measure partialUpdate(String measureId, MeasureField update);

  Measure patchMeasure(Measure measure);
}
