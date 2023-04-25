package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.Export;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ExportRepository extends MongoRepository<Export, String> {
  Optional<Export> findByMeasureId(String measureId);
}
