package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.MeasureListDTO;

import java.util.Collection;
import java.util.List;

/**
 * Custom repository for bulk fetching measures with measure sets. Uses aggregation pipelines to
 * avoid N+1 query problems.
 */
public interface MeasureBulkFetchRepository {

  /**
   * Fetches measures by their IDs with MeasureSet joined in a single query. Uses MongoDB
   * aggregation pipeline with lookup to avoid N+1 queries.
   *
   * @param measureIds Collection of measure IDs to fetch
   * @return List of MeasureListDTO with measureSet populated
   */
  List<MeasureListDTO> findAllByIdInWithMeasureSet(Collection<String> measureIds);
}
