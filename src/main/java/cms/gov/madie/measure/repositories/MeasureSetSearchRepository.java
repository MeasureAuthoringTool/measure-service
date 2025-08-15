package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;

import java.util.List;

public interface MeasureSetSearchRepository {
  List<MeasureListDTO> findMeasuresByMeasureSetId(
      String measureSetId,
      boolean sortByLatestVersion,
      MeasureSearchCriteria measureSearchCriteria);
}
