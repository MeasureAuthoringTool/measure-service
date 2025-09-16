package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.CopyTestCaseResult;
import cms.gov.madie.measure.dto.ValidList;
import cms.gov.madie.measure.exceptions.InvalidRequestException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.MeasureService;
import cms.gov.madie.measure.services.QdmTestCaseShiftDatesService;
import cms.gov.madie.measure.utils.TestCaseServiceUtil;
import gov.cms.madie.models.common.ModelType;
import cms.gov.madie.measure.services.TestCaseService;
import cms.gov.madie.measure.utils.ControllerUtil;
import cms.gov.madie.measure.utils.UserInputSanitizeUtil;
import gov.cms.madie.models.measure.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TestCaseController {

  private final TestCaseService testCaseService;
  private final MeasureRepository measureRepository;
  private final MeasureService measureService;
  private final QdmTestCaseShiftDatesService qdmTestCaseShiftDatesService;

  @PostMapping(ControllerUtil.TEST_CASES)
  public ResponseEntity<TestCase> addTestCase(
      @RequestBody @Validated(TestCase.ValidationSequence.class) TestCase testCase,
      @PathVariable String measureId,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {

    sanitizeTestCase(testCase);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            testCaseService.persistTestCase(testCase, measureId, principal.getName(), accessToken));
  }

  @PostMapping(ControllerUtil.TEST_CASES + "/list")
  public ResponseEntity<List<TestCase>> addTestCases(
      @RequestBody @Validated(TestCase.ValidationSequence.class) ValidList<TestCase> testCases,
      @PathVariable String measureId,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {
    final String username = principal.getName();
    Optional<Measure> measureOptional = measureRepository.findById(measureId);
    if (measureOptional.isEmpty()) {
      throw new ResourceNotFoundException("Measure", measureId);
    }
    Measure measure = measureOptional.get();
    measureService.verifyAuthorization(username, measure);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            testCaseService.persistTestCases(
                testCases, measureId, principal.getName(), accessToken));
  }

  @GetMapping(ControllerUtil.TEST_CASES)
  public ResponseEntity<List<TestCase>> getTestCasesByMeasureId(@PathVariable String measureId) {
    return ResponseEntity.ok(testCaseService.findTestCasesByMeasureId(measureId));
  }

  @GetMapping(ControllerUtil.TEST_CASES + "/{testCaseId}")
  public ResponseEntity<TestCase> getTestCase(
      @PathVariable String measureId,
      @PathVariable String testCaseId,
      @RequestParam(name = "validate", defaultValue = "true") boolean validate,
      @RequestHeader("Authorization") String accessToken) {
    return ResponseEntity.ok(
        testCaseService.getTestCase(measureId, testCaseId, validate, accessToken));
  }

  @PutMapping(ControllerUtil.TEST_CASES + "/{testCaseId}")
  public ResponseEntity<TestCase> updateTestCase(
      @RequestBody @Validated(TestCase.ValidationSequence.class) TestCase testCase,
      @PathVariable String measureId,
      @PathVariable String testCaseId,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {
    if (testCase.getId() == null || !testCase.getId().equals(testCaseId)) {
      throw new ResourceNotFoundException("Test Case", testCaseId);
    }
    sanitizeTestCase(testCase);

    return ResponseEntity.ok(
        testCaseService.updateTestCase(
            testCase, measureId, principal.getName(), accessToken, TestCaseServiceUtil.SAVE));
  }

  @GetMapping(ControllerUtil.TEST_CASES + "/series")
  public ResponseEntity<List<String>> getTestCaseSeriesByMeasureId(@PathVariable String measureId) {
    return ResponseEntity.ok(testCaseService.findTestCaseSeriesByMeasureId(measureId));
  }

  @DeleteMapping(ControllerUtil.TEST_CASES + "/{testCaseId}")
  public ResponseEntity<String> deleteTestCase(
      @RequestBody @PathVariable String measureId,
      @PathVariable String testCaseId,
      Principal principal) {

    log.info(
        "User [{}] is attempting to delete a test case with Id [{}] from measure [{}]",
        principal.getName(),
        testCaseId,
        measureId);
    return ResponseEntity.ok(
        testCaseService.deleteTestCase(
            UserInputSanitizeUtil.sanitizeUserInput(measureId),
            UserInputSanitizeUtil.sanitizeUserInput(testCaseId),
            principal.getName()));
  }

  @DeleteMapping(ControllerUtil.TEST_CASES)
  public ResponseEntity<String> deleteTestCases(
      @PathVariable String measureId, @RequestBody List<String> testCaseIds, Principal principal) {

    log.info(
        "User [{}] is attempting to delete following test cases with Ids [{}] from measure [{}]",
        principal.getName(),
        String.join(", ", testCaseIds),
        measureId);

    return ResponseEntity.ok(
        testCaseService.deleteTestCases(measureId, testCaseIds, principal.getName()));
  }

  @PutMapping(ControllerUtil.TEST_CASES + "/imports")
  public ResponseEntity<List<TestCaseImportOutcome>> importTestCases(
      @RequestBody List<TestCaseImportRequest> testCaseImportRequests,
      @PathVariable String measureId,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {
    final String userName = principal.getName();
    var testCaseImportOutcomes =
        testCaseService.importTestCases(
            testCaseImportRequests, measureId, userName, accessToken, ModelType.QI_CORE.getValue());
    return ResponseEntity.ok().body(testCaseImportOutcomes);
  }

  private TestCase sanitizeTestCase(TestCase testCase) {
    testCase.setDescription(UserInputSanitizeUtil.sanitizeUserInput(testCase.getDescription()));
    testCase.setTitle(UserInputSanitizeUtil.sanitizeUserInput(testCase.getTitle()));
    testCase.setSeries(UserInputSanitizeUtil.sanitizeUserInput(testCase.getSeries()));
    return testCase;
  }

  @PutMapping(ControllerUtil.TEST_CASES + "/qdm/shift-dates")
  public ResponseEntity<List<String>> shiftQdmTestCaseDates(
      @PathVariable String measureId,
      @RequestBody List<String> testCaseIds,
      @RequestParam(name = "shifted", defaultValue = "0") int shifted,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {
    return ResponseEntity.ok(
        qdmTestCaseShiftDatesService.shiftTestCaseDates(
            measureId, testCaseIds, shifted, accessToken, principal));
  }

  @GetMapping(ControllerUtil.TEST_CASES + "/qdm/shift-all-dates")
  public ResponseEntity<List<TestCase>> shiftAllQdmTestCaseDates(
      @PathVariable String measureId,
      @RequestParam(name = "shifted", defaultValue = "0") int shifted,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {
    return ResponseEntity.ok(
        qdmTestCaseShiftDatesService.shiftAllTestCaseDates(
            measureId, shifted, principal.getName(), accessToken));
  }

  @PutMapping(ControllerUtil.TEST_CASES + "/qicore/shift-dates")
  public ResponseEntity<List<String>> shiftQiCoreTestCaseDates(
      @PathVariable String measureId,
      @RequestBody List<String> testCaseIds,
      @RequestParam(name = "shifted", defaultValue = "0") int shifted,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {
    Measure measure = measureService.findMeasureById(measureId);
    measureService.verifyAuthorization(principal.getName(), measure);
    if (measure instanceof QdmMeasure) {
      throw new ResourceNotFoundException("QICore Measure", measureId);
    }

    List<TestCase> testCases =
        measure.getTestCases().stream()
            .filter(testCase -> testCaseIds.contains(testCase.getId()))
            .toList();

    List<TestCase> shiftedTestCases =
        testCaseService.shiftQiCoreTestCaseDates(
            testCases, shifted, accessToken, measureId, principal.getName());
    List<String> savedTestCaseIds = new ArrayList<>();

    for (TestCase shiftedTestCase : shiftedTestCases) {
      try {
        TestCase updatedTestCase =
            testCaseService.updateTestCase(
                shiftedTestCase,
                measureId,
                principal.getName(),
                accessToken,
                TestCaseServiceUtil.SAVE);
        savedTestCaseIds.add(updatedTestCase.getId());
      } catch (Exception e) {
        log.error(
            "Unable to save Test Case [{}] after successfully shifting dates:",
            shiftedTestCase.getId(),
            e);
      }
    }
    List<String> failedTestCases =
        testCases.stream()
            .filter(testCase -> !savedTestCaseIds.contains(testCase.getId()))
            .map(
                testCase ->
                    StringUtils.isBlank(testCase.getSeries())
                        ? testCase.getTitle()
                        : testCase.getSeries() + " - " + testCase.getTitle())
            .toList();
    return ResponseEntity.ok(failedTestCases);
  }

  /**
   * Adds/subtracts years from all date/dateTime values across all Test Cases associated with the
   * provided measure, saving the modified Test Cases, and returning the Test Case names for
   * unprocessable Test Cases.
   *
   * @param measureId ID for target measure
   * @param shifted Positive or negative integer indicating number of years to add/sub.
   * @param principal User making the request.
   * @param accessToken Requesting user's access token.
   * @return List of Test Case names that could not be processed.
   */
  @PutMapping(ControllerUtil.TEST_CASES + "/qicore/shift-all-dates")
  public ResponseEntity<List<String>> shiftAllQiCoreTestCaseDates(
      @PathVariable String measureId,
      @RequestParam(name = "shifted", defaultValue = "0") int shifted,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {
    Measure measure = measureService.findMeasureById(measureId);
    measureService.verifyAuthorization(principal.getName(), measure);
    if (measure instanceof QdmMeasure) {
      throw new ResourceNotFoundException("QICore Measure", measureId);
    }
    List<TestCase> testCases = testCaseService.findTestCasesByMeasureId(measureId);
    List<TestCase> shiftedTestCases =
        testCaseService.shiftQiCoreTestCaseDates(
            testCases, shifted, accessToken, measureId, principal.getName());
    List<String> savedTestCaseIds = new ArrayList<>();
    for (TestCase shiftedTestCase : shiftedTestCases) {
      try {
        TestCase updatedTestCase =
            testCaseService.updateTestCase(
                shiftedTestCase,
                measureId,
                principal.getName(),
                accessToken,
                TestCaseServiceUtil.SAVE);
        savedTestCaseIds.add(updatedTestCase.getId());
      } catch (Exception e) {
        log.error(
            "Unable to save Test Case [{}] after successfully shifting dates:",
            shiftedTestCase.getId(),
            e);
      }
    }
    List<String> failedTestCases =
        testCases.stream()
            .filter(testCase -> !savedTestCaseIds.contains(testCase.getId()))
            .map(
                testCase ->
                    StringUtils.isBlank(testCase.getSeries())
                        ? testCase.getTitle()
                        : testCase.getSeries() + " - " + testCase.getTitle())
            .toList();
    return ResponseEntity.ok(failedTestCases);
  }

  @PutMapping(ControllerUtil.TEST_CASES + "/copy-to")
  public ResponseEntity<CopyTestCaseResult> copyTestCasesToMeasure(
      @PathVariable String measureId,
      @RequestParam(name = "targetMeasureId") String targetMeasureId,
      @RequestBody List<String> testCaseIds,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {

    Measure targetMeasure = measureService.findMeasureById(targetMeasureId);
    Measure sourceMeasure = measureService.findMeasureById(measureId);
    measureService.verifyAuthorization(principal.getName(), targetMeasure);
    if (CollectionUtils.isEmpty(testCaseIds)) {
      throw new InvalidRequestException("Test Case List cannot be empty");
    }

    if (!sameModelFamily(targetMeasure, sourceMeasure)) {
      throw new InvalidRequestException("Target Measure has different model.");
    }

    List<TestCase> sourceTestCases =
        sourceMeasure.getTestCases().stream()
            .filter(stc -> testCaseIds.stream().anyMatch(stc.getId()::equalsIgnoreCase))
            .toList();
    CopyTestCaseResult result =
        testCaseService.copyTestCasesToMeasure(
            targetMeasureId, sourceTestCases, principal.getName(), accessToken);

    return ResponseEntity.ok(result);
  }

  private boolean sameModelFamily(Measure m1, Measure m2) {
    if (m1 == null || m2 == null) {
      return false;
    }
    if (StringUtils.isBlank(m1.getModel())) {
      return StringUtils.isBlank(m2.getModel());
    }
    if (StringUtils.equalsIgnoreCase(m1.getModel(), m2.getModel())) {
      return true;
    }
    if (StringUtils.containsIgnoreCase(m1.getModel(), "QI-Core")) {
      return StringUtils.containsIgnoreCase(m2.getModel(), "QI-Core");
    }
    return false;
  }
}
