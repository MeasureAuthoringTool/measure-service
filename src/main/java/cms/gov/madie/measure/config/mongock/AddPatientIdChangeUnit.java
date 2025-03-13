package cms.gov.madie.measure.config.mongock;

import java.util.List;
import java.util.UUID;

import org.apache.commons.collections4.CollectionUtils;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.Setter;

@ChangeUnit(id = "add_testcase-patientid", order = "1", author = "madie_dev")
public class AddPatientIdChangeUnit {

  @Setter private List<Measure> tempMeasures;

  @Execution
  public void addTestCasePatientId(MeasureRepository measureRepository) {
    List<Measure> measures = measureRepository.findAll();
    if (CollectionUtils.isNotEmpty(measures)) {
      setTempMeasures(measures);
      measures.forEach(
          measure -> {
            List<TestCase> testCases = measure.getTestCases();
            if (CollectionUtils.isNotEmpty(testCases)) {
              testCases.forEach(
                  testCase -> {
                    if (testCase.getPatientId() == null) {
                      UUID temp = UUID.randomUUID();
                      testCase.setPatientId(temp);
                    }
                  });
              measure.setTestCases(testCases);
              measureRepository.save(measure);
            }
          });
    }
  }

  @RollbackExecution
  public void rollbackExecution(MeasureRepository measureRepository) {
    if (CollectionUtils.isNotEmpty(tempMeasures)) {
      tempMeasures.forEach(
          measure -> {
            measureRepository.save(measure);
          });
    }
  }
}
