package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.MeasureLock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MeasureLockRepository extends MongoRepository<MeasureLock, String> {
  Optional<MeasureLock> findByMeasureId(String measureId);

  void deleteByMeasureId(String measureId);
}
