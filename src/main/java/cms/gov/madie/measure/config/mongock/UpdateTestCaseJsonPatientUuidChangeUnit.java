package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.TestCaseService;
import cms.gov.madie.measure.utils.JsonUtil;
import tools.jackson.core.JacksonException;
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
import java.util.UUID;

@Slf4j
@ChangeUnit(id = "update_testcase_json_patient_uuid", order = "1", author = "madie_dev")
public class UpdateTestCaseJsonPatientUuidChangeUnit {

  @Setter private List<Measure> tempMeasures;

  @Execution
  public void updatedTestCaseJsonWithPatientUuid(
      MeasureRepository measureRepository, TestCaseService testCaseService) {
    log.info("STARTING - update_testcase_json_patient_uuid");
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
                              testCase.setJson(
                                  updateJsonForUuids(measure.getId(), testCase, testCaseService));
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

  protected String updateJsonForUuids(
      final String measureId, TestCase testCase, TestCaseService testCaseService)
      throws JacksonException {
    UUID patientIdUuid = testCase.getPatientId();
    if (patientIdUuid == null) {
      patientIdUuid = UUID.randomUUID();
      testCase.setPatientId(patientIdUuid);
      log.warn(
          "Measure [{}], TestCase [{}] - patientId was missing, generated new UUID!",
          measureId,
          testCase.getId());
    }
    final String newPatientId = patientIdUuid.toString();
    final String oldFullUrl = JsonUtil.getPatientFullUrl(testCase.getJson());

    // Refs update makes the assumption that the ref will start with
    // Patient/
    String updatedJson =
        JsonUtil.enforcePatientId(testCase, testCaseService.getMadieJsonResourcesBaseUri());

    final String previousJson = updatedJson;
    updatedJson = JsonUtil.replacePatientRefs(updatedJson, newPatientId);

    if (!StringUtils.isBlank(oldFullUrl)) {
      updatedJson = JsonUtil.replaceFullUrlRefs(updatedJson, oldFullUrl, newPatientId);
    }

    if (previousJson.equals(updatedJson)) {
      log.warn(
          "Measure [{}], TestCase [{}] - no patient refs were updated!",
          measureId,
          testCase.getId());
    }

    return updatedJson;
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
