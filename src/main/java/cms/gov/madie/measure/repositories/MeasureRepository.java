package cms.gov.madie.measure.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import gov.cms.madie.models.measure.Measure;

import org.springframework.data.mongodb.repository.Query;

public interface MeasureRepository extends MongoRepository<Measure, String> {
  Optional<Measure> findByCqlLibraryName(String cqlLibraryName);

  Optional<Measure> findByIdAndActive(String id, Boolean active);

  Page<Measure> findAllByActive(Boolean active, Pageable page);

  Page<Measure> findAllByCreatedByAndActive(String user, Boolean active, Pageable page);

  @Query(value = "{_id: ?0}", fields = "{'testCases.series': 1, _id: 0}")
  Optional<Measure> findAllTestCaseSeriesByMeasureId(String measureId);

  @Query(value = "{'groups._id': ?0}")
  Optional<Measure> findGroupById(String groupId);
}
