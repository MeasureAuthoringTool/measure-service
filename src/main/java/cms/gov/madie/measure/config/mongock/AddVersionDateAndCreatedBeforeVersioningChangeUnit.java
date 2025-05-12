package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

@Slf4j
@ChangeUnit(
    id = "add_version_date_and_created_before_versioning",
    order = "1",
    author = "madie_dev")
public class AddVersionDateAndCreatedBeforeVersioningChangeUnit {

  @Execution
  public void addVersionDateAndCreatedBeforeVersioningChangeUnit(
      MeasureRepository measureRepository) {
    List<Measure> measures = measureRepository.findAll();
    measures.forEach(
        measure -> {
          if (!measure.getMeasureMetaData().isDraft()) {
            measure.getMeasureMetaData().setVersionDate(measure.getLastModifiedAt());

            List<TestCase> testCases = measure.getTestCases();
            if (CollectionUtils.isEmpty(testCases)) {
              return;
            }

            testCases.stream()
                .forEach(
                    testCase -> {
                      testCase.setCreatedBeforeVersioning(true);
                    });
            measureRepository.save(measure);
          }
        });
  }

  @RollbackExecution
  public void rollbackExecution() {
    log.debug(
        "Something went wrong while updating measure version date and createdBeforeVersioning fields.");
  }
}
