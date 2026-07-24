package cms.gov.madie.measure.repositories;

import gov.cms.madie.models.measure.MeasureReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MeasureReviewRepository extends MongoRepository<MeasureReview, String> {

  Optional<MeasureReview> findByMeasureId(String measureId);

  List<MeasureReview> findAllByMeasureSetId(String measureSetId);

  boolean existsByMeasureId(String measureId);
}
