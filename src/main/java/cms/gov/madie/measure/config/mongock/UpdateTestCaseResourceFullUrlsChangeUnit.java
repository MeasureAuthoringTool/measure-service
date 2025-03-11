package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.TestCaseService;
import cms.gov.madie.measure.utils.JsonUtil;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Slf4j
@ChangeUnit(id = "update_fhir_resource_full_urls", order = "1", author = "madie_dev")
public class UpdateTestCaseResourceFullUrlsChangeUnit {

  @Setter private List<Measure> tempMeasures;

  @Execution
  public void updateTestCaseResourceFullUrls(
      MeasureRepository measureRepository, TestCaseService testCaseService) {
    log.info("STARTING - update_testcase_json resource full urls");
    List<Measure> measures = measureRepository.findAll();
    if (CollectionUtils.isNotEmpty(measures)) {
      setTempMeasures(measures);
      measures.stream()
          .filter(m -> ModelType.QI_CORE.getValue().equals(m.getModel()))
          .forEach(
              measure -> {
                List<TestCase> testCases = measure.getTestCases();
                if (CollectionUtils.isNotEmpty(testCases)) {
                  testCases.stream()
                      .filter(tc -> !StringUtils.isBlank(tc.getJson()))
                      .forEach(
                          testCase -> {
                            if (!JsonUtil.isValidJson(testCase.getJson())) {
                              log.warn(
                                  "Skipping test case [{}] on measure [{}] as JSON is invalid",
                                  testCase.getId(),
                                  measure.getId());
                              return;
                            }
                            try {
                              String updatedJson =
                                  JsonUtil.updateResourceFullUrls(
                                      testCase, testCaseService.getMadieJsonResourcesBaseUri());
                              testCase.setJson(updatedJson);
                            } catch (Exception ex) {
                              log.info(
                                  "Error updating Measure [{}], TestCase [{}]",
                                  measure.getId(),
                                  testCase.getId(),
                                  ex);
                            }
                          });
                  measure.setTestCases(testCases);
                  measureRepository.save(measure);
                }
              });
    }
    log.info("COMPLETED - update_testcase_json_patient_uuid");
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
