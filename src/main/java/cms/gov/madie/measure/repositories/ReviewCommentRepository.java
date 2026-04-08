package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.Comment.ReviewComment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewCommentRepository extends MongoRepository<ReviewComment, String> {

  List<ReviewComment> findAllByMeasureIdOrderByCreatedAtAsc(String measureId);
}
