package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
public class AdminService {
  private final MeasureService measureService;
  private final TestCaseService testCaseService;
  private final ActionLogService actionLogService;

  public Measure updateHcpcCodes(String id, String username, String accessToken) {
    Measure targetMeasure = measureService.findMeasureById(id);

    if (targetMeasure == null) {
      throw new ResourceNotFoundException("Measure", id);
    }

    if (!Objects.equals(targetMeasure.getModel(), ModelType.QDM_5_6.getValue())) {
      log.info(
          "Measure with id: "
              + id
              + " is not a QDM measure. No HCPCSReleaseCodeSets updates made.");
      return targetMeasure;
    }

    targetMeasure
        .getTestCases()
        .forEach(
            testCase -> {
              String updatedJson =
                  testCase
                      .getJson()
                      .replace(
                          "http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets",
                          "2.16.840.1.113883.6.285");
              if (!Objects.equals(testCase.getJson(), updatedJson)) {
                log.info(
                    "Updating HCPCSReleaseCodeSets values in test case with id: "
                        + testCase.getId());
                testCase.setJson(updatedJson);
                testCaseService.updateTestCase(testCase, id, username, accessToken);
                actionLogService.logAction(
                    id,
                    Measure.class,
                    ActionType.UPDATED,
                    username,
                    "Admin Action: Overwrote HCPCSReleaseCodeSets Values.");
              }
            });

    return measureService.findMeasureById(id);
  }
}
