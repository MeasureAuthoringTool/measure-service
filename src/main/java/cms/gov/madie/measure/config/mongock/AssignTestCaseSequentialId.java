package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.TestCaseSequenceRepository;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.TestCaseSequence;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@ChangeUnit(id = "assign_testcase_sequential_id", order = "1", author = "madie_dev")
public class AssignTestCaseSequentialId {

  @Execution
  public void updateTestCaseWithSequentialCaseNumber(
      MeasureRepository measureRepository, TestCaseSequenceRepository testCaseSequenceRepository) {
    log.info("Starting - assign_testcase_sequential_id");
    List<Measure> measures = measureRepository.findAll();
    if (CollectionUtils.isNotEmpty(measures)) {
      measures.stream()
          .filter(measure -> measure.getMeasureMetaData().isDraft())
          .forEach(
              measure -> {
                List<TestCase> testCases = measure.getTestCases();
                if (CollectionUtils.isNotEmpty(testCases)) {
                  List<TestCase> sortedTestCases =
                      measure.getTestCases().stream()
                          .sorted(Comparator.comparing(TestCase::getCreatedAt))
                          .toList();
                  List<TestCase> updatedTestCases =
                      IntStream.range(0, sortedTestCases.size())
                          .mapToObj(
                              i -> {
                                TestCase testCase = sortedTestCases.get(i);
                                testCase.setCaseNumber(i + 1);
                                return testCase;
                              })
                          .toList();
                  measure.setTestCases(updatedTestCases);
                  measureRepository.save(measure);

                  // Find the highest case number
                  int highestCaseNumber =
                      updatedTestCases.stream().mapToInt(TestCase::getCaseNumber).max().orElse(0);

                  // Save the highest case number to TestCaseSequenceRepository
                  TestCaseSequence testCaseSequence =
                      new TestCaseSequence()
                          .toBuilder().id(measure.getId()).sequence(highestCaseNumber).build();
                  testCaseSequenceRepository.save(testCaseSequence);
                }
              });
    }
    log.info("COMPLETED - assign_testcase_sequential_id");
  }

  @RollbackExecution
  public void rollbackExecution(MeasureRepository measureRepository) {
    log.info("Starting rollback - assign_testcase_sequential_id");
    List<Measure> measures = measureRepository.findAll();
    if (CollectionUtils.isNotEmpty(measures)) {
      measures.forEach(
          measure -> {
            List<TestCase> testCases = measure.getTestCases();
            if (CollectionUtils.isNotEmpty(testCases)) {
              testCases.forEach(testCase -> testCase.setCaseNumber(null));
              measure.setTestCases(testCases);
              measureRepository.save(measure);
            }
          });
    }
    log.info("COMPLETED rollback - assign_testcase_sequential_id");
  }
}
