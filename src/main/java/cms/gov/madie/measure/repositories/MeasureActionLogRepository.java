package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.common.ActionLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MeasureActionLogRepository
    extends MongoRepository<ActionLog, String>, ActionLogRepository {
  Optional<ActionLog> findByTargetId(String targetId);
}
