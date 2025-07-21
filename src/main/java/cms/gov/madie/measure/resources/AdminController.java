package cms.gov.madie.measure.resources;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import cms.gov.madie.measure.exceptions.HarpIdMismatchException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.InvalidRequestException;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import cms.gov.madie.measure.exceptions.MeasureNotDraftableException;
import cms.gov.madie.measure.repositories.CqmMeasureRepository;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.services.*;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.cqm.CqmMeasure;
import gov.cms.madie.models.measure.*;
import jakarta.validation.Valid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.*;

import cms.gov.madie.measure.dto.ImpactedMeasureValidationReport;
import cms.gov.madie.measure.dto.MeasureTestCaseValidationReport;
import cms.gov.madie.measure.dto.MeasureTestCaseValidationReportSummary;
import cms.gov.madie.measure.dto.TestCaseValidationReport;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ActionType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
  private final MeasureService measureService;
  private final TestCaseService testCaseService;
  private final TestCaseValidationService testCaseValidationService;
  private final MeasureSetService measureSetService;
  private final ActionLogService actionLogService;
  private final VersionService versionService;

  private final MeasureRepository measureRepository;
  private final ExportRepository exportRepository;
  private final CqmMeasureRepository cqmMeasureRepository;

  @Value("${madie.admin.concurrency-limit}")
  private int concurrencyLimit;

  @PutMapping("/measures/test-cases/validations")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<MeasureTestCaseValidationReportSummary> validateAllMeasureTestCases(
      HttpServletRequest request,
      @Value("${admin-api-key}") String apiKey,
      Principal principal,
      @RequestHeader("Authorization") String accessToken,
      @RequestParam(name = "draftOnly", defaultValue = "true") boolean draftOnly)
      throws InterruptedException, ExecutionException {
    log.info("User [{}] - Starting admin task [validateAllMeasureTestCases]", principal.getName());
    StopWatch timer = new StopWatch();
    timer.start();
    List<String> measureIds = measureService.getAllActiveMeasureIds(draftOnly);
    List<Callable<MeasureTestCaseValidationReport>> tasks = new ArrayList<>();
    List<MeasureTestCaseValidationReport> reports = new ArrayList<>();
    List<ImpactedMeasureValidationReport> impactedMeasures = new ArrayList<>();

    log.info("User [{}] - Building callable tasks for [] measures", measureIds.size());
    for (String measureId : measureIds) {
      tasks.add(buildCallableForMeasureId(measureId, accessToken));
    }

    ExecutorService executorService = Executors.newFixedThreadPool(concurrencyLimit);
    List<Future<MeasureTestCaseValidationReport>> futures = executorService.invokeAll(tasks);
    executorService.shutdown();

    for (Future<MeasureTestCaseValidationReport> f : futures) {
      MeasureTestCaseValidationReport measureTestCaseValidationReport = f.get();
      reports.add(measureTestCaseValidationReport);

      int diffCount = 0;
      for (TestCaseValidationReport tcValReport :
          measureTestCaseValidationReport.getTestCaseValidationReports()) {
        if (tcValReport.isPreviousValidResource() && !tcValReport.isCurrentValidResource()) {
          diffCount++;
        }
      }
      if (diffCount > 0) {
        MeasureSet measureSet =
            measureSetService.findByMeasureSetId(measureTestCaseValidationReport.getMeasureSetId());

        impactedMeasures.add(
            ImpactedMeasureValidationReport.builder()
                .measureId(measureTestCaseValidationReport.getMeasureId())
                .measureSetId(measureTestCaseValidationReport.getMeasureSetId())
                .measureVersionId(measureTestCaseValidationReport.getMeasureVersionId())
                .measureName(measureTestCaseValidationReport.getMeasureName())
                .measureOwner(measureSet.getOwner())
                .impactedTestCasesCount(diffCount)
                .build());
      }
    }

    timer.stop();
    log.info(
        "User [{}] - Admin task [validateAllMeasureTestCases] took [{}s] to complete",
        principal.getName(),
        timer.getTotalTimeSeconds());

    return ResponseEntity.ok(
        MeasureTestCaseValidationReportSummary.builder()
            .reports(reports)
            .impactedMeasures(impactedMeasures)
            .build());
  }

  // This endpoint is used to reset the validation queue for QI Core v6 measures test cases
  // that are in validating state
  @PutMapping("/measures/test-cases/restart-validation")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public void resetTestCaseValidationQueue(
      HttpServletRequest request,
      @Value("${admin-api-key}") String apiKey,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {

    log.info(
        "User [{}] - Starting admin task to place QI Core v6 testcases back on the validation queue",
        principal.getName());
    List<Measure> measureList =
        measureRepository.findAllByModel(ModelType.QI_CORE_6_0_0.getValue());

    if (CollectionUtils.isNotEmpty(measureList)) {
      measureList.forEach(
          measure -> {
            if (CollectionUtils.isNotEmpty(measure.getTestCases())) {
              measure
                  .getTestCases()
                  .forEach(
                      testCase -> {
                        if (TestCaseValidationStatus.VALIDATING
                            .toString()
                            .equalsIgnoreCase(testCase.getValidationStatus())) {
                          testCase.setValidationStatus(TestCaseValidationStatus.PENDING.toString());
                          // submit the test case after updating its status
                          testCaseValidationService.submitOnSaveValidationTask(
                              measure.getId(),
                              testCase,
                              accessToken,
                              ModelType.valueOfName(measure.getModel()));
                        } else if (TestCaseValidationStatus.PENDING
                            .toString()
                            .equalsIgnoreCase(testCase.getValidationStatus())) {
                          // Submit test cases already in PENDING status
                          testCaseValidationService.submitOnSaveValidationTask(
                              measure.getId(),
                              testCase,
                              accessToken,
                              ModelType.valueOfName(measure.getModel()));
                        }
                      });
            }
          });
      log.info(
          "User [{}] - Successfully placed QI Core v6 test cases back on the validation queue",
          principal.getName());
      measureRepository.saveAll(measureList);
    }
  }

  @DeleteMapping("/measures/{id}")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<Measure> permDeleteMeasure(
      HttpServletRequest request,
      @Value("${admin-api-key}") String apiKey,
      Principal principal,
      @RequestHeader("Authorization") String accessToken,
      @RequestHeader(name = "harpId") String harpId,
      @PathVariable String id) {

    Measure measureToDelete = measureService.findMeasureById(id);

    if (measureToDelete != null) {
      if (!measureToDelete.getMeasureSet().getOwner().equals(harpId)) {
        throw new HarpIdMismatchException(
            harpId, measureToDelete.getMeasureSet().getOwner(), measureToDelete.getId());
      }

      measureRepository.delete(measureToDelete);
      actionLogService.logAction(id, Measure.class, ActionType.DELETED, principal.getName());
      return ResponseEntity.ok(measureToDelete);
    }
    throw new ResourceNotFoundException(id);
  }

  @GetMapping("/measures/sharedWith")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<List<Map<String, Object>>> getMeasureSharedWith(
      HttpServletRequest request,
      @Value("${admin-api-key}") String apiKey,
      Principal principal,
      @RequestHeader("Authorization") String accessToken,
      @RequestHeader(name = "harpId") String harpId,
      @RequestParam(required = true, name = "measureids") String measureids) {

    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    String[] ids = StringUtils.split(measureids, ",");
    for (String id : ids) {
      Measure measureToGet = measureService.findMeasureById(id);
      if (measureToGet != null) {
        if (!measureToGet.getMeasureSet().getOwner().equals(harpId)) {
          throw new HarpIdMismatchException(
              harpId, measureToGet.getMeasureSet().getOwner(), measureToGet.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("measureName", measureToGet.getMeasureName());
        result.put("measureId", measureToGet.getId());
        result.put("measureOwner", measureToGet.getMeasureSet().getOwner());
        result.put("sharedWith", measureToGet.getMeasureSet().getAcls());

        results.add(result);
      } else {
        throw new ResourceNotFoundException(id);
      }
    }
    return ResponseEntity.ok(results);
  }

  @PutMapping("/measures/{id}/correct-version")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<Measure> correctMeasureVersion(
      HttpServletRequest request,
      @Value("${admin-api-key}") String apiKey,
      @RequestHeader(name = "harpId") String harpId,
      Principal principal,
      @PathVariable String id,
      @RequestParam String inCorrectVersion,
      @RequestParam String correctVersion,
      @RequestParam String draftVersion) {
    Measure measureToCorrectVersion = measureService.findMeasureById(id);
    if (measureToCorrectVersion == null
        || !measureToCorrectVersion.getVersion().toString().equals(inCorrectVersion)) {
      String error =
          String.format(
              "Could not find Measure with id of %s and / or a version of %s",
              id, inCorrectVersion);
      throw new ResourceNotFoundException(error);
    }

    if (!measureToCorrectVersion.getMeasureSet().getOwner().equals(harpId)) {
      throw new HarpIdMismatchException(
          harpId,
          measureToCorrectVersion.getMeasureSet().getOwner(),
          measureToCorrectVersion.getId());
    }

    // check if the associated measure set already has a draft
    List<Measure> relatedMeasures =
        measureRepository.findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(
            List.of(measureToCorrectVersion.getMeasureSetId()), true, true);

    if (!CollectionUtils.isEmpty(relatedMeasures)
        && !relatedMeasures.get(0).getId().equals(measureToCorrectVersion.getId())) {
      throw new MeasureNotDraftableException(
          measureToCorrectVersion.getId(), "Only one draft is permitted per measure.");
    }

    // check if the draftVersion is less than correctVersion
    if (!isLessThan(correctVersion, draftVersion)) {
      throw new InvalidRequestException("Draft version should be always less than correct version");
    }

    // check if the given version is already associated
    if (!checkIfVersionIsAlreadyAssociated(
        measureToCorrectVersion.getMeasureSetId(), correctVersion, draftVersion)) {
      throw new InvalidResourceStateException(
          "Version number cannot be corrected. "
              + "The given draft or correct version number is already associated");
    }

    Version newDraftVersion = Version.parse(draftVersion);
    String newCql =
        measureToCorrectVersion
            .getCql()
            .replace(
                versionService.generateLibraryContentLine(
                    measureToCorrectVersion.getCqlLibraryName(),
                    measureToCorrectVersion.getVersion()),
                versionService.generateLibraryContentLine(
                    measureToCorrectVersion.getCqlLibraryName(), newDraftVersion));

    measureToCorrectVersion.setCql(newCql);
    measureToCorrectVersion.setVersion(newDraftVersion);
    measureToCorrectVersion.getMeasureMetaData().setDraft(true);

    deleteRelevantPackageData(id, measureToCorrectVersion);

    Measure correctedVersionMeasure = measureRepository.save(measureToCorrectVersion);
    actionLogService.logAction(id, Measure.class, ActionType.UPDATED, principal.getName());
    return ResponseEntity.ok(correctedVersionMeasure);
  }

  @PutMapping("/measures/{id}")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<Measure> overwriteExpectedValues(
      HttpServletRequest request,
      @Value("${admin-api-key}") String apiKey,
      Principal principal,
      @PathVariable String id,
      @RequestBody @Valid Measure sourceMeasure) {
    Measure targetMeasure = measureService.findMeasureById(id);
    if (targetMeasure == null) {
      throw new ResourceNotFoundException("Measure", id);
    }

    if (!StringUtils.equals((sourceMeasure.getId()), targetMeasure.getId())
        && !isDirectAncestor(sourceMeasure, targetMeasure)) {
      throw new InvalidRequestException("Cannot overwrite differing measure versions.");
    }

    List<TestCase> targetTestCases = targetMeasure.getTestCases();
    List<TestCase> sourceTestCases = sourceMeasure.getTestCases();

    for (TestCase target : targetTestCases) {
      for (TestCase source : sourceTestCases) {
        if (target.getId().equals(source.getId())
            || (target.getPatientId().equals(source.getPatientId())
                && target.getTitle().equals(source.getTitle())
                && target.getSeries().equals(source.getSeries()))) {
          target.setGroupPopulations(source.getGroupPopulations());
          correctIdsAndExpectedValueType(target.getGroupPopulations(), targetMeasure);
        }
      }
    }

    measureRepository.save(targetMeasure);
    actionLogService.logAction(
        id,
        Measure.class,
        ActionType.UPDATED,
        principal.getName(),
        "Admin Action: Overwrote Expected Values.");
    return ResponseEntity.ok(targetMeasure);
  }

  private boolean isDirectAncestor(Measure sourceMeasure, Measure targetMeasure) {
    // Verify source and target are part of the same Measure Set
    if (!StringUtils.equals(
        sourceMeasure.getMeasureSet().getMeasureSetId(),
        targetMeasure.getMeasureSet().getMeasureSetId())) {
      throw new InvalidRequestException("Source measure from different Measure family/set.");
    }

    return targetMeasure.getVersion().getMajor() == sourceMeasure.getVersion().getMajor()
        && targetMeasure.getVersion().getMinor() == sourceMeasure.getVersion().getMinor()
        && targetMeasure.getVersion().getRevisionNumber()
            == sourceMeasure.getVersion().getRevisionNumber();
  }

  private void correctIdsAndExpectedValueType(
      List<TestCaseGroupPopulation> tcGroupPopulations, Measure msr) {
    for (int i = 0; i < tcGroupPopulations.size(); i++) {
      TestCaseGroupPopulation tcGroup = tcGroupPopulations.get(i);
      tcGroup.setGroupId(msr.getGroups().get(i).getId());
      // Correct Stratification IDs
      if (CollectionUtils.isNotEmpty(msr.getGroups().get(i).getStratifications())) {
        for (int j = 0; j < msr.getGroups().get(i).getStratifications().size(); j++) {
          tcGroup
              .getStratificationValues()
              .get(j)
              .setId(msr.getGroups().get(i).getStratifications().get(j).getId());
        }
      }
      if (msr instanceof FhirMeasure) {
        if (tcGroup.getPopulationBasis() != null
            && tcGroup.getPopulationBasis().equalsIgnoreCase("boolean")) {
          for (TestCasePopulationValue populationValue : tcGroup.getPopulationValues()) {
            if (populationValue.getExpected() instanceof String originalValue) {
              if (originalValue.equalsIgnoreCase("1")) {
                populationValue.setExpected(Boolean.TRUE);
              } else {
                populationValue.setExpected(Boolean.FALSE);
              }
            }
          }
        }
      } else if (((QdmMeasure) msr).isPatientBasis()) {
        for (TestCasePopulationValue populationValue : tcGroup.getPopulationValues()) {
          if (populationValue.getExpected() instanceof Integer originalValue) {
            if (originalValue == 1) {
              populationValue.setExpected(Boolean.TRUE);
            } else {
              populationValue.setExpected(Boolean.FALSE);
            }
          }
        }
      }
    }
  }

  private void deleteRelevantPackageData(String id, Measure measureToCorrectVersion) {
    // QI-Core measure: delete the export
    // QDM Measures: delete the export and cqmMeasure
    Export export = exportRepository.findByMeasureId(id).orElse(null);
    if (export != null) {
      exportRepository.delete(export);
    }

    if (ModelType.QDM_5_6.getValue().equals(measureToCorrectVersion.getModel())) {
      CqmMeasure cqmMeasure =
          cqmMeasureRepository.findByHqmfSetIdAndHqmfVersionNumber(
              measureToCorrectVersion.getMeasureSetId(), measureToCorrectVersion.getVersionId());
      if (cqmMeasure != null) {
        cqmMeasureRepository.delete(cqmMeasure);
      }
    }
  }

  private boolean checkIfVersionIsAlreadyAssociated(
      String measureSetId, String correctVersion, String draftVersion) {
    List<Measure> allByMeasureSetIdAndActive =
        measureRepository.findAllByMeasureSetIdAndActive(measureSetId, true);
    List<Measure> measureStream =
        allByMeasureSetIdAndActive.stream()
            .filter(
                measure ->
                    measure.getVersion().toString().equals(correctVersion)
                        || measure.getVersion().toString().equals(draftVersion))
            .toList();
    return CollectionUtils.isEmpty(measureStream);
  }

  private boolean isLessThan(String correctVersion, String draftVersion) {
    String[] correctVersionParts = correctVersion.split("\\.");
    String[] draftVersionParts = draftVersion.split("\\.");

    int length = Math.max(correctVersionParts.length, draftVersionParts.length);

    for (int i = 0; i < length; i++) {
      // parse the parts as integers for comparison. If a part is missing, treat it as zero.
      int correctVersionPart =
          i < correctVersionParts.length ? Integer.parseInt(correctVersionParts[i]) : 0;
      int draftVersionPart =
          i < draftVersionParts.length ? Integer.parseInt(draftVersionParts[i]) : 0;

      //  compare corresponding parts of the version strings
      if (draftVersionPart < correctVersionPart) {
        return true;
      } else if (draftVersionPart > correctVersionPart) {
        return false;
      }
    }
    // if all parts are equal, draftVersion is not less than correctVersion
    return false;
  }

  private Callable<MeasureTestCaseValidationReport> buildCallableForMeasureId(
      final String measureId, final String accessToken) {
    return () -> testCaseService.updateTestCaseValidResourcesWithReport(measureId, accessToken);
  }
}
