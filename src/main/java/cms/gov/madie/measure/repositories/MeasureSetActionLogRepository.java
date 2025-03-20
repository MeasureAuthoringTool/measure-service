package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.common.MeasureSetActionLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MeasureSetActionLogRepository
    extends MongoRepository<MeasureSetActionLog, String>, ActionLogRepository {
  Optional<MeasureSetActionLog> findByTargetId(String targetId);
}
