package cms.gov.madie.measure.config.mongock;

import tools.jackson.core.JacksonException;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.JsonUtil;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "reset_testcase_json_datetime", order = "1", author = "madie_dev")
public class TestCaseJsonDateTimeChangeUnit {
  @Setter private List<Measure> tempMeasures;
  ObjectMapper mapper = new ObjectMapper();

  @Execution
  public void resetTestCaseJsonDateTimeZone(MeasureRepository measureRepository) {
    log.info("Entering resetTestCaseJsonDateTimeZone()");

    List<Measure> allMeasures = measureRepository.findAll();
    if (CollectionUtils.isEmpty(allMeasures)) {
      log.error("No measures found! Exiting resetTestCaseJsonDateTimeZone()!");
      return;
    }

    setTempMeasures(allMeasures);
    allMeasures.stream()
        .forEach(
            measure -> {
              if (!ModelType.QDM_5_6.toString().equalsIgnoreCase(measure.getModel())) {
                List<TestCase> testCases = measure.getTestCases();
                if (CollectionUtils.isEmpty(testCases)) {
                  return;
                }
                testCases.stream()
                    .forEach(
                        testCase -> {
                          if (StringUtils.isNotBlank(testCase.getJson())) {
                            try {
                              JsonNode rootNode = mapper.readTree(testCase.getJson());
                              JsonUtil.replaceNestedDateTimeStringValue(rootNode);
                              String modifiedJsonString = mapper.writeValueAsString(rootNode);
                              testCase.setJson(modifiedJsonString);
                            } catch (JacksonException e) {
                              log.error("Invalid test case json");
                            }
                          }
                        });
                measureRepository.save(measure);
              }
            });
  }

  @RollbackExecution
  public void rollbackExecution(MeasureRepository measureRepository) throws Exception {
    log.debug("Entering rollbackExecution()");

    if (CollectionUtils.isNotEmpty(tempMeasures)) {
      measureRepository.saveAll(tempMeasures);
    }
  }
}
