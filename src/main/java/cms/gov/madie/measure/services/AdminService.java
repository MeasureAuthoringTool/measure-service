package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.InvalidRequestException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
public class AdminService {
  private final MeasureService measureService;
  private final TestCaseService testCaseService;
  private final ActionLogService actionLogService;

  public List<Integer> updateCodeSystem(
      String id,
      String username,
      String incorrectCodeSystem,
      String correctCodeSystem,
      String accessToken) {
    Measure targetMeasure = measureService.findMeasureById(id);

    if (StringUtils.isBlank(incorrectCodeSystem) || StringUtils.isBlank(correctCodeSystem)) {
      throw new InvalidRequestException(
          "Please provide both incorrect and correct code system values");
    }

    if (targetMeasure == null) {
      throw new ResourceNotFoundException("Measure", id);
    }

    if (!Objects.equals(targetMeasure.getModel(), ModelType.QDM_5_6.getValue())) {
      log.info(
          "Measure with id: "
              + id
              + " is not a QDM measure. No HCPCSReleaseCodeSets updates made.");
      throw new InvalidRequestException(
          "Measure is not a QDM measure. No code system updates made.");
    }

    List<Integer> caseNumbers = new ArrayList<>();
    targetMeasure
        .getTestCases()
        .forEach(
            testCase -> {
              String updatedJson =
                  testCase.getJson().replace(incorrectCodeSystem, correctCodeSystem);
              if (!Objects.equals(testCase.getJson(), updatedJson)) {
                log.info("Updating code system in test case with id: " + testCase.getId());
                testCase.setJson(updatedJson);
                testCaseService.updateTestCase(testCase, id, username, accessToken);
                actionLogService.logAction(
                    id,
                    Measure.class,
                    ActionType.UPDATED,
                    username,
                    "Admin Action: Corrected code system values.");
                caseNumbers.add(testCase.getCaseNumber());
              }
            });

    return caseNumbers;
  }
}
