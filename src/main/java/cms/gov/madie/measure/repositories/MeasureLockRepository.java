package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.common.MeasureLock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MeasureLockRepository extends MongoRepository<MeasureLock, String> {
  Optional<MeasureLock> findByMeasureId(String measureId);
}
