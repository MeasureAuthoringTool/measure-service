package cms.gov.madie.measure.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

import cms.gov.madie.measure.locks.MeasureLock;

public interface MeasureLockRepository extends MongoRepository<MeasureLock, String> {
  Optional<MeasureLock> findByMeasureId(String measureId);

  void deleteByMeasureId(String measureId);

  List<MeasureLock> findAllByLockedBy(String lockedBy);
}
