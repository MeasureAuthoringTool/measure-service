package cms.gov.madie.measure.services;

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

import cms.gov.madie.measure.exceptions.CqmConversionException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.utils.ControllerUtil;
import gov.cms.madie.models.cqm.datacriteria.basetypes.DataElement;
import gov.cms.madie.models.cqm.datacriteria.basetypes.TestCaseJson;
import gov.cms.madie.models.measure.TestCase;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class QdmTestCaseShiftDatesService {

  private final TestCaseService testCaseService;
  private final MeasureService measureService;

  private ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private static final String SEPARATOR = " |\n";

  @Autowired
  public QdmTestCaseShiftDatesService(
      TestCaseService testCaseService, MeasureService measureService) {
    this.testCaseService = testCaseService;
    this.measureService = measureService;
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

    List<String> savedTestCaseIds =
        testCases.stream()
            .map(
                testCase -> {
                  try {
                    TestCase shiftedTestCase = shiftDatesForTestCase(testCase, shifted);
                    TestCase updatedTestCase =
                        testCaseService.updateTestCase(
                            shiftedTestCase,
                            measureId,
                            principal.getName(),
                            accessToken,
                            ControllerUtil.SAVE_VALIDATION_QUEUE);
                    return updatedTestCase.getId();
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

    return testCases.stream()
        .filter(testCase -> !savedTestCaseIds.contains(testCase.getId()))
        .map(
            testCase ->
                StringUtils.isBlank(testCase.getSeries())
                    ? testCase.getTitle()
                    : testCase.getSeries() + " - " + testCase.getTitle())
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
    List<TestCase> testCases = testCaseService.findTestCasesByMeasureId(measureId);
    if (CollectionUtils.isEmpty(testCases)) {
      throw new ResourceNotFoundException("TestCases", measureId);
    }
    StringBuilder testCaseFailures = new StringBuilder();

    List<TestCase> allTestCases = new ArrayList<>();
    for (TestCase testCase : testCases) {
      try {
        TestCase shiftedTC = shiftDatesForTestCase(testCase, shifted);
        allTestCases.add(shiftedTC);
        testCaseService.updateTestCase(
            shiftedTC, measureId, username, accessToken, ControllerUtil.SAVE_VALIDATION_QUEUE);
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
}
