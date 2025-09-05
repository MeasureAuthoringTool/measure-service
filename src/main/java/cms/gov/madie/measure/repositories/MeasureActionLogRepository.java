package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.common.ActionLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MeasureActionLogRepository
    extends MongoRepository<ActionLog, String>, ActionLogRepository {
  ActionLog findByTargetId(String targetId);
}
