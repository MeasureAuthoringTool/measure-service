package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.MeasureSet;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface MeasureSetRepository
    extends MongoRepository<MeasureSet, String>, MeasureSetSearchRepository {

  boolean existsByMeasureSetId(String measureSetId);

  Optional<MeasureSet> findByMeasureSetId(String measureSetId);
}
