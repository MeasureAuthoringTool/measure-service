package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.BulkTestCaseResult;
import cms.gov.madie.measure.dto.CopyTestCaseResult;
import cms.gov.madie.measure.dto.MadieFeatureFlag;
import cms.gov.madie.measure.dto.ValidList;
import cms.gov.madie.measure.exceptions.InvalidRequestException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.AppConfigService;
import cms.gov.madie.measure.services.MeasureService;
import cms.gov.madie.measure.services.QdmTestCaseShiftDatesService;
import cms.gov.madie.measure.services.TestCaseLockEnrichmentService;
import cms.gov.madie.measure.services.TestCaseLockService;
import cms.gov.madie.measure.utils.TestCaseServiceUtil;
import gov.cms.madie.models.common.ModelType;
import cms.gov.madie.measure.services.TestCaseService;
import cms.gov.madie.measure.utils.ControllerUtil;
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

import static cms.gov.madie.measure.utils.UserInputSanitizeUtil.sanitizeUserInput;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TestCaseController {

  private final TestCaseService testCaseService;
  private final MeasureRepository measureRepository;
  private final MeasureService measureService;
  private final QdmTestCaseShiftDatesService qdmTestCaseShiftDatesService;
  private final TestCaseLockEnrichmentService testCaseLockEnrichmentService;
  private final AppConfigService appConfigService;
  private final TestCaseLockService testCaseLockService;

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
  public ResponseEntity<BulkTestCaseResult> addTestCases(
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

    // Filter out locked test cases
    List<TestCase> unlocked = new ArrayList<>();
    List<String> failed = new ArrayList<>();

    for (TestCase tc : testCases) {
      if (tc.getId() != null) {
        var lock = testCaseLockService.findByTestCaseId(tc.getId());
        if (lock != null && !lock.getLockedBy().equals(username)) {
          failed.add(tc.getId());
          continue;
        }
      }
      unlocked.add(tc);
    }

    List<TestCase> saved =
        testCaseService.persistTestCases(
            ValidList.<TestCase>builder().list(unlocked).build(), measureId, username, accessToken);

    BulkTestCaseResult result =
        BulkTestCaseResult.builder().testCases(saved).failed(failed).build();

    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @GetMapping(ControllerUtil.TEST_CASES)
  public ResponseEntity<List<TestCase>> getTestCasesByMeasureId(
      @PathVariable String measureId, Principal principal) {
    List<TestCase> testCases =
        testCaseService.findTestCasesByMeasureId(measureId, principal.getName());
    // Enrich with lock information (excluding current user's locks)
    if (principal != null) {
      testCaseLockEnrichmentService.enrichTestCasesWithLockInfo(testCases, principal.getName());
    }
    return ResponseEntity.ok(testCases);
  }

  @GetMapping(ControllerUtil.TEST_CASES + "/{testCaseId}")
  public ResponseEntity<TestCase> getTestCase(
      Principal principal,
      @PathVariable String measureId,
      @PathVariable String testCaseId,
      @RequestParam(name = "validate", defaultValue = "true") boolean validate,
      @RequestHeader("Authorization") String accessToken) {
    final String username = principal.getName();
    return ResponseEntity.ok(
        testCaseService.getTestCase(measureId, testCaseId, validate, accessToken, username));
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

  @DeleteMapping(ControllerUtil.TEST_CASES)
  public ResponseEntity<String> deleteTestCases(
      @PathVariable String measureId, @RequestBody List<String> testCaseIds, Principal principal) {

    log.info(
        "User [{}] is attempting to delete following test cases with Ids [{}] from measure [{}]",
        principal.getName(),
        String.join(", ", testCaseIds),
        measureId);

    return ResponseEntity.ok(
        testCaseService.deleteTestCases(
            sanitizeUserInput(measureId), sanitizeUserInput(testCaseIds), principal.getName()));
  }

  @PutMapping(ControllerUtil.TEST_CASES + "/imports")
  public ResponseEntity<Map<String, Object>> importTestCases(
      @RequestBody List<TestCaseImportRequest> testCaseImportRequests,
      @PathVariable String measureId,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {
    final String userName = principal.getName();

    // Filter out locked test cases
    List<TestCaseImportRequest> unlocked = new ArrayList<>();
    List<String> failed = new ArrayList<>();

    for (TestCaseImportRequest request : testCaseImportRequests) {
      String patientId = request.getPatientId() != null ? request.getPatientId().toString() : null;
      if (patientId != null) {
        var lock = testCaseLockService.findByTestCaseId(patientId);
        if (lock != null && !lock.getLockedBy().equals(userName)) {
          failed.add(patientId);
          continue;
        }
      }
      unlocked.add(request);
    }

    var testCaseImportOutcomes =
        testCaseService.importTestCases(
            unlocked, measureId, userName, accessToken, ModelType.QI_CORE.getValue());

    Map<String, Object> response = new HashMap<>();
    response.put("outcomes", testCaseImportOutcomes);
    response.put("failed", failed);

    return ResponseEntity.ok().body(response);
  }

  private TestCase sanitizeTestCase(TestCase testCase) {
    testCase.setDescription(sanitizeUserInput(testCase.getDescription()));
    testCase.setTitle(sanitizeUserInput(testCase.getTitle()));
    testCase.setSeries(sanitizeUserInput(testCase.getSeries()));
    return testCase;
  }

  @PutMapping(ControllerUtil.TEST_CASES + "/qdm/shift-dates")
  public ResponseEntity<Map<String, Object>> shiftQdmTestCaseDates(
      @PathVariable String measureId,
      @RequestBody List<String> testCaseIds,
      @RequestParam(name = "shifted", defaultValue = "0") int shifted,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {

    Measure measure = checkMeasure(measureId, principal);

    List<String> unlockedIds =
        appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)
            ? filterOutLocked(testCaseIds, principal.getName())
            : testCaseIds;

    List<String> shiftedIds =
        qdmTestCaseShiftDatesService.shiftTestCaseDates(
            measure, unlockedIds, shifted, accessToken, principal);
    List<String> failedIds =
        testCaseIds.stream().filter(testCaseId -> !shiftedIds.contains(testCaseId)).toList();

    List<TestCase> testCases =
        measure.getTestCases().stream()
            .filter(testCase -> testCaseIds.contains(testCase.getId()))
            .toList();

    return ResponseEntity.ok(populateShiftedAndFailed(testCases, shiftedIds, failedIds));
  }

  @PutMapping(ControllerUtil.TEST_CASES + "/update-json-metadata")
  public ResponseEntity<Map<String, Object>> updateQiCoreJsonWithGroupAndTitle(
      @PathVariable String measureId,
      @RequestBody List<String> testCaseIds,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {

    Measure measure = checkQiCoreMeasure(measureId, principal);

    List<TestCase> testCasesToBeUpdated =
        measure.getTestCases().stream()
            .filter(testCase -> testCaseIds.contains(testCase.getId()))
            .toList();

    Map<String, Object> response =
        testCaseService.updateQiCoreJsonWithGroupAndTitle(
            testCasesToBeUpdated, principal.getName(), measureId, accessToken);

    return ResponseEntity.ok(response);
  }

  private Measure checkMeasure(String measureId, Principal principal) {
    Measure measure = measureService.findMeasureById(measureId);
    measureService.verifyAuthorization(principal.getName(), measure);
    if (measure instanceof FhirMeasure) {
      throw new ResourceNotFoundException("QDM Measure", measureId);
    }
    return measure;
  }

  private List<String> filterOutLocked(List<String> testCaseIds, String username) {
    // Filter out locked test cases
    List<String> unlocked = new ArrayList<>();

    for (String testCaseId : testCaseIds) {
      var lock = testCaseLockService.findByTestCaseId(testCaseId);
      if (lock != null && !lock.getLockedBy().equals(username)) {
        continue;
      }
      unlocked.add(testCaseId);
    }
    return unlocked;
  }

  private Map<String, Object> populateShiftedAndFailed(
      List<TestCase> allTestCases, List<String> shiftedIds, List<String> failedIds) {
    List<String> shiftedTestCases =
        new ArrayList<>(
            allTestCases.stream()
                .filter(testCase -> shiftedIds.contains(testCase.getId()))
                .map(
                    testCase ->
                        StringUtils.isBlank(testCase.getSeries())
                            ? testCase.getTitle()
                            : testCase.getSeries() + " - " + testCase.getTitle())
                .toList());

    List<String> failedTestCases =
        new ArrayList<>(
            allTestCases.stream()
                .filter(testCase -> failedIds.contains(testCase.getId()))
                .map(
                    testCase ->
                        StringUtils.isBlank(testCase.getSeries())
                            ? testCase.getTitle()
                            : testCase.getSeries() + " - " + testCase.getTitle())
                .toList());

    Map<String, Object> response = new HashMap<>();
    response.put("shifted", shiftedTestCases);
    response.put("failed", failedTestCases);
    log.info("shift dates qdm shiftedTestCases: {}", shiftedTestCases.toString());
    log.info("shift dates qdm failedTestCases: {}", failedTestCases.toString());
    return response;
  }

  @GetMapping(ControllerUtil.TEST_CASES + "/qdm/shift-all-dates")
  public ResponseEntity<Map<String, Object>> shiftAllQdmTestCaseDates(
      @PathVariable String measureId,
      @RequestParam(name = "shifted", defaultValue = "0") int shifted,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {

    Measure measure = checkMeasure(measureId, principal);

    List<String> testCaseIds =
        measure.getTestCases().stream().map(testCase -> testCase.getId()).toList();

    List<String> unlockedIds =
        appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)
            ? filterOutLocked(testCaseIds, principal.getName())
            : testCaseIds;

    List<String> shiftedIds =
        qdmTestCaseShiftDatesService.shiftTestCaseDates(
            measure, unlockedIds, shifted, accessToken, principal);
    List<String> failedIds =
        testCaseIds.stream().filter(testCaseId -> !shiftedIds.contains(testCaseId)).toList();

    return ResponseEntity.ok(
        populateShiftedAndFailed(measure.getTestCases(), shiftedIds, failedIds));
  }

  @PutMapping(ControllerUtil.TEST_CASES + "/qicore/shift-dates")
  public ResponseEntity<Map<String, Object>> shiftQiCoreTestCaseDates(
      @PathVariable String measureId,
      @RequestBody List<String> testCaseIds,
      @RequestParam(name = "shifted", defaultValue = "0") int shifted,
      @RequestHeader("Authorization") String accessToken,
      Principal principal) {

    Measure measure = checkQiCoreMeasure(measureId, principal);

    List<String> unlockedIds =
        appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)
            ? filterOutLocked(testCaseIds, principal.getName())
            : testCaseIds;

    return ResponseEntity.ok(
        populateShiftedAndFailedQiCore(
            measure, testCaseIds, unlockedIds, shifted, accessToken, principal.getName()));
  }

  private Measure checkQiCoreMeasure(String measureId, Principal principal) {
    Measure measure = measureService.findMeasureById(measureId);
    measureService.verifyAuthorization(principal.getName(), measure);
    if (measure instanceof QdmMeasure) {
      throw new ResourceNotFoundException("QICore Measure", measureId);
    }
    return measure;
  }

  private Map<String, Object> populateShiftedAndFailedQiCore(
      Measure measure,
      List<String> testCaseIds,
      List<String> unlockedIds,
      int shifted,
      String accessToken,
      String username) {
    List<TestCase> testCasesToBeShifted =
        measure.getTestCases().stream()
            .filter(testCase -> testCaseIds.contains(testCase.getId()))
            .toList();
    List<TestCase> unlockedTestCases =
        measure.getTestCases().stream()
            .filter(testCase -> unlockedIds.contains(testCase.getId()))
            .toList();

    List<TestCase> shiftedTestCases =
        testCaseService.shiftQiCoreTestCaseDates(
            unlockedTestCases, shifted, accessToken, measure.getId(), username);
    List<String> savedTestCaseIds = new ArrayList<>();

    for (TestCase shiftedTestCase : shiftedTestCases) {
      try {
        TestCase updatedTestCase =
            testCaseService.updateTestCase(
                shiftedTestCase, measure.getId(), username, accessToken, TestCaseServiceUtil.SAVE);
        savedTestCaseIds.add(updatedTestCase.getId());
      } catch (Exception e) {
        log.error(
            "Unable to save Test Case [{}] after successfully shifting dates:",
            shiftedTestCase.getId(),
            e);
      }
    }
    List<String> shiftedTestCasesInfo =
        new ArrayList<>(
            shiftedTestCases.stream()
                .filter(testCase -> savedTestCaseIds.contains(testCase.getId()))
                .map(
                    testCase ->
                        testCase.getSeries() != null
                            ? testCase.getSeries() + " - " + testCase.getTitle()
                            : testCase.getTitle())
                .toList());
    List<String> failedTestCases =
        new ArrayList<>(
            testCasesToBeShifted.stream()
                .filter(testCase -> !savedTestCaseIds.contains(testCase.getId()))
                .map(
                    testCase ->
                        testCase.getSeries() != null
                            ? testCase.getSeries() + " - " + testCase.getTitle()
                            : testCase.getTitle())
                .toList());

    Map<String, Object> response = new HashMap<>();
    response.put("shifted", savedTestCaseIds);
    response.put("failed", failedTestCases);
    log.info("shift dates qi-core shiftedTestCases: {}", shiftedTestCasesInfo.toString());
    log.info("shift dates qi-core failedTestCases: {}", failedTestCases.toString());
    return response;
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
  public ResponseEntity<Map<String, Object>> shiftAllQiCoreTestCaseDates(
      @PathVariable String measureId,
      @RequestParam(name = "shifted", defaultValue = "0") int shifted,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {

    Measure measure = checkQiCoreMeasure(measureId, principal);

    List<String> testCaseIds =
        measure.getTestCases().stream().map(testCase -> testCase.getId()).toList();
    List<String> unlockedIds =
        appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)
            ? filterOutLocked(testCaseIds, principal.getName())
            : testCaseIds;

    return ResponseEntity.ok(
        populateShiftedAndFailedQiCore(
            measure, testCaseIds, unlockedIds, shifted, accessToken, principal.getName()));
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
