package cms.gov.madie.measure.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import cms.gov.madie.measure.locks.TestCaseLock;

public interface TestCaseLockRepository extends MongoRepository<TestCaseLock, String> {

  Optional<TestCaseLock> findByTestCaseId(String testCaseId);

  void deleteByTestCaseId(String testCaseId);

  List<TestCaseLock> findAllByLockedBy(String lockedBy);

  boolean existsByMeasureIdAndLockedByNot(String measureId, String lockedBy);
}
