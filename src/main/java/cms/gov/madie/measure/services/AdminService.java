package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class AdminService {
  private final MeasureService measureService;
  private final MeasureRepository measureRepository;

  public List<Integer> updateCodeSystem(
      String id, String username, String incorrectCodeSystem, String correctCodeSystem) {
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
                  testCase.getJson().replace(incorrectCodeSystem.trim(), correctCodeSystem.trim());
              if (!Objects.equals(testCase.getJson(), updatedJson)) {
                log.info(
                    "{} is updating the code system in the test case with id: {}",
                    username,
                    testCase.getId());
                testCase.setJson(updatedJson);
                caseNumbers.add(testCase.getCaseNumber());
              }
            });

    if (CollectionUtils.isNotEmpty(caseNumbers)) {
      measureRepository.save(targetMeasure);
    }
    return caseNumbers;
  }

  public Measure backfillTestCaseSetIds(Measure measure, String userName) {
    List<TestCase> testCases = measure.getTestCases();

    if (CollectionUtils.isEmpty(testCases)) {
      throw new InvalidResourceStateException("Test cases cannot be empty or null");
    }

    boolean anyHasTestCaseSetId = testCases.stream().anyMatch(tc -> tc.getTestCaseSetId() != null);
    if (anyHasTestCaseSetId) {
      throw new TestCaseSetIdsAlreadyAssignedException(
          "One or more test cases already have a testCaseSetId.");
    }

    List<Measure> allByMeasureSetIdAndActive =
        measureRepository.findAllByMeasureSetIdAndActive(measure.getMeasureSetId(), true);
    boolean measureSetHasTestCaseSetId =
        allByMeasureSetIdAndActive.stream()
            .filter(m -> CollectionUtils.isNotEmpty(m.getTestCases()))
            .flatMap(m -> m.getTestCases().stream())
            .anyMatch(tc -> tc.getTestCaseSetId() != null);

    if (measureSetHasTestCaseSetId) {
      throw new UnsupportedTypeException(
          "One or more test cases in this measure set already have a testCaseSetId.");
    }

    testCases.forEach(tc -> tc.setTestCaseSetId(UUID.randomUUID()));
    log.info("Admin {} had successfully added the test case set ids to test cases {}", userName);
    return measureRepository.save(measure);
  }
}
