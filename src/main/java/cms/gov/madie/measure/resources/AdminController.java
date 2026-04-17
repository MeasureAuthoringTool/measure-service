package cms.gov.madie.measure.resources;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import cms.gov.madie.measure.config.security.AdminOnly;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.CqmMeasureRepository;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.services.*;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Organization;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.cqm.CqmMeasure;
import gov.cms.madie.models.measure.*;
import jakarta.validation.Valid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.*;

import cms.gov.madie.measure.dto.ImpactedMeasureValidationReport;
import cms.gov.madie.measure.dto.MeasureTestCaseValidationReport;
import cms.gov.madie.measure.dto.MeasureTestCaseValidationReportSummary;
import cms.gov.madie.measure.dto.TestCaseValidationReport;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.OrganizationRepository;
import gov.cms.madie.models.common.ActionType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@AdminOnly
public class AdminController extends AbstractMeasureController {
  private final MeasureService measureService;
  private final TestCaseService testCaseService;
  private final TestCaseValidationService testCaseValidationService;
  private final MeasureSetService measureSetService;
  private final ActionLogService actionLogService;
  private final VersionService versionService;
  private final ExportService exportService;

  private final MeasureRepository measureRepository;
  private final ExportRepository exportRepository;
  private final CqmMeasureRepository cqmMeasureRepository;
  private final OrganizationRepository organizationRepository;

  private final MeasureLockService measureLockService;
  private final TestCaseLockService testCaseLockService;
  private final AdminService adminService;
  private final AppConfigService appConfigService;

  @Override
  protected AppConfigService getAppConfigService() {
    return appConfigService;
  }

  @Value("${madie.admin.concurrency-limit}")
  private int concurrencyLimit;

  @PutMapping("/measures/test-cases/validations")
  public ResponseEntity<MeasureTestCaseValidationReportSummary> validateAllMeasureTestCases(
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

  /**
   * Reset/Populate the validation queue for QI Core v6 measures test cases. By default, retrieves
   * all active draft QI Core v6 measures with test cases and places their test cases back on the
   * validation queue if their status is PENDING or VALIDATING.
   *
   * <p>Optionally, can be forced to place all test cases back on the validation queue regardless of
   * their current validation status by setting the 'force' parameter to true.
   *
   * <p>Validation requests generated via this endpoint will be processed via the import queue to
   * avoid excessive load on the save queue.
   *
   * @param principal Security principal.
   * @param draftOnly (Optional, default=true) If true, only draft measures are considered. False
   *     includes all active measures.
   * @param force (Optional, default=false) If true, places all test cases from filtered STU6
   *     Measures back on the validation queue regardless of their current validation status.
   * @param excludeUsers (Optional) List of harpIds to exclude measures created by those users.
   * @param measureIds (Optional) List of measureIds to specifically include. If provided, only
   *     measures with these Ids are considered.
   * @param accessToken Okta access token.
   * @return Count of test cases placed back on the validation queue.
   */
  @PutMapping("/measures/test-cases/restart-validation")
  public ResponseEntity<Integer> resetTestCaseValidationQueue(
      Principal principal,
      @RequestParam(name = "draftOnly", defaultValue = "true") boolean draftOnly,
      @RequestParam(name = "force", defaultValue = "false") boolean force,
      @RequestParam(name = "excludeUsers", required = false) List<String> excludeUsers,
      @RequestParam(name = "measureIds", required = false) List<String> measureIds,
      @RequestHeader("Authorization") String accessToken) {

    log.info(
        "User [{}] - Starting admin task to place QI Core v6 testcases back on the validation queue",
        principal.getName());
    StopWatch timer = new StopWatch();
    timer.start("Find All QI Core v6 Measures");
    List<Measure> allQiCore6Measures =
        measureRepository.findAllByModel(ModelType.QI_CORE_6_0_0.getValue());
    timer.stop();
    if (draftOnly) {
      log.info("Admin::Test Case Validation::Only considering draft measures");
    }
    if (CollectionUtils.isNotEmpty(measureIds)) {
      log.info(
          "Admin::Test Case Validation::Only considering measures Ids: {}",
          String.join(", ", measureIds));
    }
    if (CollectionUtils.isNotEmpty(excludeUsers)) {
      log.info(
          "Admin::Test Case Validation::Excluding measures created by users: {}",
          String.join(", ", excludeUsers));
    }

    timer.start("Filter Measures");
    List<Measure> targetQiCore6Measures =
        allQiCore6Measures.stream()
            .filter(measure -> CollectionUtils.isNotEmpty(measure.getTestCases()))
            .filter(
                measure -> {
                  if (draftOnly) {
                    return measure.getMeasureMetaData().isDraft();
                  }
                  return true;
                })
            .filter(Measure::isActive)
            .filter(
                measure -> {
                  if (CollectionUtils.isNotEmpty(excludeUsers)) {
                    return !excludeUsers.contains(measure.getCreatedBy());
                  }
                  return true;
                })
            .filter(
                measure -> {
                  if (CollectionUtils.isNotEmpty(measureIds)) {
                    return measureIds.contains(measure.getId());
                  }
                  return true;
                })
            .toList();
    timer.stop();
    if (CollectionUtils.isEmpty(targetQiCore6Measures)) {
      return ResponseEntity.ok(0);
    }
    timer.start("Kickoff Test Case Validations");
    targetQiCore6Measures.forEach(
        measure -> {
          if (CollectionUtils.isNotEmpty(measure.getTestCases())) {
            measure
                .getTestCases()
                .forEach(
                    testCase -> {
                      if (TestCaseValidationStatus.PENDING
                          .toString()
                          .equalsIgnoreCase(testCase.getValidationStatus())) {
                        // Submit test case already in PENDING status
                        testCaseValidationService.submitOnImportValidationTask(
                            measure.getId(),
                            testCase,
                            accessToken,
                            ModelType.valueOfName(measure.getModel()));
                      } else if (force
                          || testCase.getValidationStatus() == null
                          || TestCaseValidationStatus.VALIDATING
                              .toString()
                              .equalsIgnoreCase(testCase.getValidationStatus())) {
                        Measure updatedMeasure =
                            measureRepository.setValidationStatusToPending(
                                testCase.getId(), measure.getId());
                        if (updatedMeasure != null) {
                          // submit the test case after updating its status
                          testCaseValidationService.submitOnImportValidationTask(
                              measure.getId(),
                              testCase,
                              accessToken,
                              ModelType.valueOfName(measure.getModel()));
                        }
                      }
                    });
          }
        });
    timer.stop();
    log.info(
        "User [{}] - Successfully placed QI Core v6 test cases back on the validation queue",
        principal.getName());
    log.info("Admin::Test Case Validation::{}", timer.prettyPrint());
    int resultCount = 0;
    if (!force) {
      for (Measure measure : targetQiCore6Measures) {
        for (TestCase testCase : measure.getTestCases()) {
          if (testCase.getValidationStatus() == null
              || TestCaseValidationStatus.PENDING
                  .toString()
                  .equalsIgnoreCase(testCase.getValidationStatus())
              || TestCaseValidationStatus.VALIDATING
                  .toString()
                  .equalsIgnoreCase(testCase.getValidationStatus())) {
            resultCount++;
          }
        }
      }
    } else {
      resultCount =
          targetQiCore6Measures.stream()
              .map(Measure::getTestCases)
              .mapToInt(Collection::size)
              .sum();
    }
    return ResponseEntity.ok(resultCount);
  }

  @DeleteMapping("/measures/{id}")
  public ResponseEntity<Measure> permDeleteMeasure(
      Principal principal, @RequestHeader(name = "harpId") String harpId, @PathVariable String id) {

    Measure measureToDelete = measureService.findMeasureById(id);

    if (measureToDelete != null) {
      if (!measureToDelete.getMeasureSet().getOwner().equalsIgnoreCase(harpId)) {
        throw new HarpIdMismatchException(
            harpId, measureToDelete.getMeasureSet().getOwner(), measureToDelete.getId());
      }

      measureRepository.delete(measureToDelete);
      actionLogService.logAction(
          id, Measure.class, ActionType.DELETED, principal.getName().toLowerCase());
      return ResponseEntity.ok(measureToDelete);
    }
    throw new ResourceNotFoundException(id);
  }

  @GetMapping("/measures/sharedWith")
  public ResponseEntity<List<Map<String, Object>>> getMeasureSharedWith(
      @RequestHeader(name = "harpId") String harpId,
      @RequestParam(name = "measureids") String measureids) {

    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    String[] ids = StringUtils.split(measureids, ",");
    for (String id : ids) {
      Measure measureToGet = measureService.findMeasureById(id);
      if (measureToGet != null) {
        if (!measureToGet.getMeasureSet().getOwner().equalsIgnoreCase(harpId)) {
          throw new HarpIdMismatchException(
              harpId, measureToGet.getMeasureSet().getOwner(), measureToGet.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("measureName", measureToGet.getMeasureName());
        result.put("measureId", measureToGet.getId());
        result.put("measureOwner", measureToGet.getMeasureSet().getOwner().toLowerCase());
        result.put("sharedWith", measureToGet.getMeasureSet().getAcls());

        results.add(result);
      } else {
        throw new ResourceNotFoundException(id);
      }
    }
    return ResponseEntity.ok(results);
  }

  @PutMapping("/measures/{id}/correct-version")
  public ResponseEntity<Measure> correctMeasureVersion(
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

    if (!measureToCorrectVersion.getMeasureSet().getOwner().equalsIgnoreCase(harpId)) {
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

    actionLogService.logAction(
        measureToCorrectVersion.getId(),
        Measure.class,
        ActionType.VERSION_REVERT,
        principal.getName().toLowerCase(),
        String.format("Reverted from version %s to %s", inCorrectVersion, correctVersion));

    return ResponseEntity.ok(correctedVersionMeasure);
  }

  @PutMapping("/measures/{id}")
  public ResponseEntity<Measure> overwriteExpectedValues(
      Principal principal, @PathVariable String id, @RequestBody @Valid Measure sourceMeasure) {
    Measure targetMeasure = measureService.findMeasureById(id);
    if (targetMeasure == null) {
      throw new ResourceNotFoundException("Measure", id);
    }

    checkMeasureLock(targetMeasure, principal.getName());

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
    return (StringUtils.equals(
            sourceMeasure.getMeasureSet().getMeasureSetId(),
            targetMeasure.getMeasureSet().getMeasureSetId())
        && (targetMeasure.getVersion().getMajor() == sourceMeasure.getVersion().getMajor()
            && targetMeasure.getVersion().getMinor() == sourceMeasure.getVersion().getMinor()
            && targetMeasure.getVersion().getRevisionNumber()
                == sourceMeasure.getVersion().getRevisionNumber()));
  }

  private void correctIdsAndExpectedValueType(
      List<TestCaseGroupPopulation> tcGroupPopulations, Measure msr) {
    for (int i = 0; i < tcGroupPopulations.size(); i++) {
      TestCaseGroupPopulation tcGroup = tcGroupPopulations.get(i);
      tcGroup.setGroupId(msr.getGroups().get(i).getId());
      // Correct Stratification IDs
      if (CollectionUtils.isNotEmpty(msr.getGroups().get(i).getStratifications())
          && CollectionUtils.isNotEmpty(tcGroup.getStratificationValues())) {
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

  @DeleteMapping("/measures/test-cases/locks")
  public ResponseEntity<List<String>> unlockAllByUser(
      @RequestHeader(name = "harpId") String harpId) {
    log.info("Unlock measures, test cases for user: " + harpId);
    List<String> messages = new ArrayList<>();
    messages.addAll(measureLockService.unlockByUser(harpId.toLowerCase()));
    messages.addAll(testCaseLockService.unlockByUser(harpId.toLowerCase()));
    return ResponseEntity.ok(messages);
  }

  /**
   * Admin endpoint to correct an incorrect code system reference inside all test case JSON objects
   * for the specified Measure. Replaces occurrences of the provided incorrectCodeSystem with
   * correctCodeSystem in each test case resource tied to the Measure id.
   *
   * @param principal Authenticated user performing the correction
   * @param id Measure identifier whose test cases are to be updated
   * @param incorrectCodeSystem Code system string to search for
   * @param correctCodeSystem Replacement code system string
   * @return ResponseEntity wrapping a List<Integer> case numbers of updated test cases
   */
  @PutMapping("/measures/{id}/testcases/code-system-correction")
  public ResponseEntity<List<Integer>> updateCodeSystemInTestCaseJson(
      Principal principal,
      @PathVariable String id,
      @RequestParam String incorrectCodeSystem,
      @RequestParam String correctCodeSystem) {

    return ResponseEntity.ok(
        adminService.updateCodeSystem(
            id, principal.getName().toLowerCase(), incorrectCodeSystem, correctCodeSystem));
  }

  @DeleteMapping("/measures/{measureId}/delete-cms-id")
  public ResponseEntity<String> deleteCmsId(
      @PathVariable String measureId,
      @RequestParam(name = "cmsId") Integer cmsId,
      @RequestHeader(name = "harpId") String harpId,
      Principal principal) {
    final String username = principal.getName().toLowerCase();
    log.info(
        "User [{}] - Started admin task [deleteCmsId] and is attempting to delete "
            + "CMS id [{}] from measure with measure id [{}]",
        username,
        cmsId,
        measureId);
    final Measure existingMeasure = measureService.findMeasureById(measureId);
    checkMeasureLock(existingMeasure, username);
    return ResponseEntity.status(HttpStatus.OK)
        .body(measureSetService.deleteCmsId(measureId, cmsId, harpId.toLowerCase(), username));
  }

  @PostMapping("/organizations")
  public ResponseEntity<List<Organization>> addOrganizations(
      HttpServletRequest request, @RequestBody List<Organization> organizations) {
    String userName = request.getUserPrincipal().getName();
    log.info("User {} is attempting to add new organizations {}", userName, organizations);
    try {
      List<Organization> savedOrganizations = organizationRepository.saveAll(organizations);
      log.info("User {} successfully added new organizations", userName);
      return ResponseEntity.status(HttpStatus.CREATED).body(savedOrganizations);
    } catch (org.springframework.dao.DuplicateKeyException duplicateKeyException) {
      log.error("One of the organizations is already available with same OID");
      throw new DuplicateKeyException(
          duplicateKeyException.getLocalizedMessage(), "Duplicate oid found");
    }
  }

  @PutMapping("/measures/shared-access-report")
  public ResponseEntity<byte[]> getMeasureAccessReport(
      Principal principal,
      @RequestBody List<String> measureIds,
      @RequestHeader("Authorization") String accessToken) {
    final String username = principal.getName().toLowerCase();

    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"MeasureSharingExport.xlsx\"")
        .header(
            HttpHeaders.CONTENT_TYPE,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .body(exportService.getSharedAccessReportForMeasures(measureIds, username, accessToken));
  }

  @PutMapping("/measure/{measureId}/test-cases/backfill-set-ids")
  public ResponseEntity<Measure> backfillTestCaseSetIds(
      Principal principal, @PathVariable String measureId) {
    final String userName = principal.getName().toLowerCase();
    final Measure existingMeasure = measureService.findMeasureById(measureId);
    checkMeasureLock(existingMeasure, userName);

    log.info(
        "Admin {} is attempting to add test case set ids for measure {}",
        userName,
        existingMeasure.getId());
    var isQdm = StringUtils.equals(existingMeasure.getModel(), ModelType.QDM_5_6.getValue());
    if (isQdm) {
      throw new UnsupportedTypeException("Please provide QI Core model measure.");
    }

    Measure updatedMeasure = adminService.backfillTestCaseSetIds(existingMeasure, userName);
    log.info(
        "Admin {} had successfully added the test case set ids for measure {}",
        userName,
        existingMeasure.getId());
    return ResponseEntity.ok().body(updatedMeasure);
  }
}
