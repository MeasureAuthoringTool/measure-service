package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.Measure;

public interface MeasurePatchRepository {
  Measure findAndModify(Measure measure);
}
