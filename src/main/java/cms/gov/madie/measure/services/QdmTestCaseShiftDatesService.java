package cms.gov.madie.measure.services;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.cms.madie.models.measure.FhirMeasure;
import gov.cms.madie.models.measure.Measure;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import cms.gov.madie.measure.dto.LockInfo;
import cms.gov.madie.measure.dto.MadieFeatureFlag;
import cms.gov.madie.measure.exceptions.CqmConversionException;
import cms.gov.madie.measure.exceptions.LockNotObtainedException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;

import gov.cms.madie.models.cqm.datacriteria.basetypes.DataElement;
import gov.cms.madie.models.cqm.datacriteria.basetypes.TestCaseJson;
import gov.cms.madie.models.measure.TestCase;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class QdmTestCaseShiftDatesService {

  private final TestCaseService testCaseService;
  private final MeasureService measureService;
  private final TestCaseLockService testCaseLockService;
  private final AppConfigService appConfigService;

  private ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private static final String SEPARATOR = " |\n";

  @Autowired
  public QdmTestCaseShiftDatesService(
      TestCaseService testCaseService,
      MeasureService measureService,
      TestCaseLockService testCaseLockService,
      AppConfigService appConfigService) {
    this.testCaseService = testCaseService;
    this.measureService = measureService;
    this.testCaseLockService = testCaseLockService;
    this.appConfigService = appConfigService;
  }

  public List<String> shiftTestCaseDates(
      String measureId,
      List<String> testCaseIds,
      int shifted,
      String accessToken,
      Principal principal) {
    Measure measure = measureService.findMeasureById(measureId);
    measureService.verifyAuthorization(principal.getName(), measure);
    if (measure instanceof FhirMeasure) {
      throw new ResourceNotFoundException("QDM Measure", measureId);
    }

    List<TestCase> testCases =
        measure.getTestCases().stream()
            .filter(testCase -> testCaseIds.contains(testCase.getId()))
            .toList();

    if (appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)) {
      List<TestCase> shiftedAndUpdatedTestCases =
          shiftDatesWhenFeatureFlagOn(testCases, measureId, shifted, principal, accessToken);
      List<String> savedTestCaseIds =
          shiftedAndUpdatedTestCases.stream().map(testCase -> testCase.getId()).toList();
      return testCases.stream()
          .filter(testCase -> !savedTestCaseIds.contains(testCase.getId()))
          .map(
              testCase ->
                  StringUtils.isBlank(testCase.getSeries())
                      ? testCase.getTitle()
                      : testCase.getSeries() + " - " + testCase.getTitle())
          .toList();
    } else {
      List<TestCase> shiftedAndUpdatedTestCases =
          shiftAndUpdate(testCases, shifted, measureId, principal, accessToken);
      List<String> savedTestCaseIds =
          shiftedAndUpdatedTestCases.stream().map(testCase -> testCase.getId()).toList();
      return testCases.stream()
          .filter(testCase -> !savedTestCaseIds.contains(testCase.getId()))
          .map(
              testCase ->
                  StringUtils.isBlank(testCase.getSeries())
                      ? testCase.getTitle()
                      : testCase.getSeries() + " - " + testCase.getTitle())
          .toList();
    }
  }

  protected List<TestCase> shiftAndUpdate(
      List<TestCase> testCases,
      int shifted,
      String measureId,
      Principal principal,
      String accessToken) {
    return testCases.stream()
        .map(
            testCase -> {
              try {
                TestCase shiftedTestCase = shiftDatesForTestCase(testCase, shifted);
                TestCase updatedTestCase =
                    testCaseService.updateTestCase(
                        shiftedTestCase, measureId, principal.getName(), accessToken);
                return updatedTestCase;
              } catch (CqmConversionException e) {
                log.error(
                    "Error shifting dates for test case [{}]: {}",
                    testCase.getId(),
                    e.getMessage());
                return null;
              } catch (Exception e) {
                log.error(
                    "Unable to save Test Case [{}] after successfully shifting dates:",
                    testCase.getId(),
                    e);
                return null;
              }
            })
        .filter(Objects::nonNull)
        .toList();
  }

  protected TestCase shiftDatesForTestCase(TestCase testCase, int shifted) {
    try {
      TestCaseJson testCaseJson = mapper.readValue(testCase.getJson(), TestCaseJson.class);
      testCaseJson.setBirthDatetime(testCaseJson.shiftDateByYear(shifted));
      List<DataElement> elements = testCaseJson.getDataElements();
      if (CollectionUtils.isNotEmpty(elements)) {
        for (DataElement element : elements) {
          shiftDates(element, shifted);
        }
      }
      String newJson = mapper.writeValueAsString(testCaseJson);
      testCase.setJson(newJson);
    } catch (JsonProcessingException e) {
      log.error(
          "An issue occurred while shifting the test case dates for the test case id: "
              + testCase.getId()
              + " JsonProcessingException -> "
              + e.getMessage());
      throw new CqmConversionException(testCase.getTitle() + " - " + testCase.getId() + SEPARATOR);
    } catch (Exception e) {
      log.error(
          "An issue occurred while shifting the test case dates for the test case id: "
              + testCase.getId()
              + " Exception -> "
              + e.getMessage());
      throw new CqmConversionException(testCase.getTitle() + " - " + testCase.getId() + SEPARATOR);
    }
    return testCase;
  }

  void shiftDates(DataElement dataElement, int shifted) {
    try {
      dataElement.shiftDates(shifted);
    } catch (Exception ex) {
      log.error("Unsupported data type: " + dataElement.toString());
      throw new CqmConversionException(
          "Unsupported data type: " + dataElement.toString() + SEPARATOR);
    }
  }

  public List<TestCase> shiftAllTestCaseDates(
      String measureId, int shifted, String username, String accessToken) {
    List<TestCase> testCases = testCaseService.findTestCasesByMeasureId(measureId, username);
    if (CollectionUtils.isEmpty(testCases)) {
      throw new ResourceNotFoundException("TestCases", measureId);
    }

    List<TestCase> allTestCases = new ArrayList<>();
    if (appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)) {
      List<String> testCaseIds = testCases.stream().map(testCase -> testCase.getId()).toList();
      log.info(
          "User: [{}] is trying to shift dates for measureId: [{}] - testCaseIds: {}",
          username,
          measureId,
          testCaseIds);
      List<LockInfo> failedLocks =
          testCaseLockService.lockAllTestCases(measureId, testCaseIds, username);
      // only when all locks are acquired can test cases' dates be shifted
      if (isEmpty(failedLocks)) {
        log.info("Locking all test cases for testCaseIds: {} successful", testCaseIds);
        allTestCases = shiftAllTestCase(testCases, shifted, measureId, username, accessToken);
        testCaseLockService.unlockAllTestCases(testCaseIds, username);
      } else {
        // otherwise, unlock previously locked test cases, and shift dates should not happen
        List<String> failedIds =
            failedLocks.stream().map(failedLock -> failedLock.getLockedId()).toList();
        log.info("Failed locking test cases for testCaseIds: {}", failedIds);
        List<String> successLocks =
            testCaseIds.stream().filter(testCaseId -> !failedIds.contains(testCaseId)).toList();
        log.info("Revert locking test cases for testCaseIds: {}", successLocks);
        testCaseLockService.unlockAllTestCases(successLocks, username);
        List<String> failedMsgs =
            failedLocks.stream()
                .map(
                    failedLock ->
                        "Test Case: "
                            + failedLock.getLockedId()
                            + " is locked by user: "
                            + failedLock.getLockedBy()
                            + ".\n")
                .toList();
        throw new LockNotObtainedException(failedMsgs.toString());
      }
    } else {
      allTestCases = shiftAllTestCase(testCases, shifted, measureId, username, accessToken);
    }

    return allTestCases;
  }

  protected List<TestCase> shiftAllTestCase(
      List<TestCase> testCases,
      int shifted,
      String measureId,
      String username,
      String accessToken) {
    StringBuilder testCaseFailures = new StringBuilder();

    List<TestCase> allTestCases = new ArrayList<>();
    for (TestCase testCase : testCases) {
      try {
        TestCase shiftedTC = shiftDatesForTestCase(testCase, shifted);
        allTestCases.add(shiftedTC);
        testCaseService.updateTestCase(shiftedTC, measureId, username, accessToken);
      } catch (CqmConversionException ex) {
        testCaseFailures.append(ex.getMessage());
        allTestCases.add(testCase);
      }
    }
    if (StringUtils.isNotBlank(testCaseFailures.toString())) {
      throw new CqmConversionException(
          StringUtils.removeEndIgnoreCase(testCaseFailures.toString(), SEPARATOR));
    }
    return allTestCases;
  }

  protected List<TestCase> shiftDatesWhenFeatureFlagOn(
      List<TestCase> testCases,
      String measureId,
      int shifted,
      Principal principal,
      String accessToken) {
    List<String> testCaseIds = testCases.stream().map(testCase -> testCase.getId()).toList();
    log.info(
        "User: [{}} is trying to shift dates for measureId: [{}] - testCaseIds: {}",
        principal.getName(),
        measureId,
        testCaseIds);
    List<LockInfo> failedLocks =
        testCaseLockService.lockAllTestCases(measureId, testCaseIds, principal.getName());
    // only when all locks are acquired can test cases' dates be shifted
    if (isEmpty(failedLocks)) {
      log.info("Locking all test cases for testCaseIds: {} successful", testCaseIds);
      List<TestCase> shiftedAndUpdated =
          shiftAndUpdate(testCases, shifted, measureId, principal, accessToken);
      testCaseLockService.unlockAllTestCases(testCaseIds, principal.getName());
      return shiftedAndUpdated;
    } else {
      // otherwise, unlock previously locked test cases, and shift dates should not happen
      List<String> failedIds =
          failedLocks.stream().map(failedLock -> failedLock.getLockedId()).toList();
      log.info("Failed locking test cases for testCaseIds: {}", failedIds);
      List<String> successLocks =
          testCaseIds.stream().filter(testCaseId -> !failedIds.contains(testCaseId)).toList();
      log.info("Revert locking test cases for testCaseIds: {}", successLocks);
      testCaseLockService.unlockAllTestCases(successLocks, principal.getName());
      List<String> failedMsgs =
          failedLocks.stream()
              .map(
                  failedLock ->
                      "Test Case: "
                          + failedLock.getLockedId()
                          + " is locked by user: "
                          + failedLock.getLockedBy()
                          + ".\n")
              .toList();
      throw new LockNotObtainedException(failedMsgs.toString());
    }
  }
}
