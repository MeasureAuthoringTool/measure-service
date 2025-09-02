package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.common.ActionLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MeasureActionLogRepository
    extends MongoRepository<ActionLog, String>, ActionLogRepository {
  List<ActionLog> findByTargetId(String targetId);
}
