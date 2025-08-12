package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.TestCaseLock;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TestCaseLockRepository extends MongoRepository<TestCaseLock, String> {

  Optional<TestCaseLock> findByTestCaseId(String testCaseId);

  void deleteByTestCaseId(String testCaseId);
}
