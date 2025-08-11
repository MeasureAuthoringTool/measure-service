package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.Measure;

import java.util.List;

public interface MeasurePatchRepository {
  Measure findAndModify(Measure measure);

  Measure findAndModify(Measure measure, List<String> excludedFields);
}
