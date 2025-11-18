package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.*;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.services.*;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.measure.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MeasureController {

  private final MeasureRepository repository;
  private final MeasureService measureService;
  private final GroupService groupService;
  private final ActionLogService actionLogService;
  private final MeasureSetRepository measureSetRepository;
  private final MeasureSetService measureSetService;
  private final TestCaseService testCaseService;
  private final TestCaseLockService testCaseLockService;
  private final AppConfigService appConfigService;

  @PostMapping("/measures/draftstatus")
  public ResponseEntity<Map<String, Boolean>> getDraftStatuses(
      @RequestBody List<String> measureSetIds) {
    Map<String, Boolean> results = measureService.getMeasureDrafts(measureSetIds);
    return ResponseEntity.status(HttpStatus.CREATED).body(results);
  }

  @PutMapping("/measures/byMeasureSetId")
  public ResponseEntity<List<MeasureListDTO>> getMeasuresByMeasureSetId(
      @RequestParam(name = "measureSetId") String measureSetId,
      boolean sortByLatestVersion,
      @RequestBody(required = false) MeasureSearchCriteria searchCriteria) {
    List<MeasureListDTO> results =
        measureSetService.getMeasuresByMeasureSetId(
            measureSetId, sortByLatestVersion, searchCriteria);
    return ResponseEntity.status(HttpStatus.OK).body(results);
  }

  @GetMapping("/measures/recentsByMeasureSetId")
  public ResponseEntity<List<Measure>> getRecentMeasuresByMeasureSetId(
      @RequestParam(name = "measureSetIds") List<String> measureSetIds) {
    List<Measure> results = measureSetService.getRecentMeasuresByMeasureSetId(measureSetIds);
    return ResponseEntity.status(HttpStatus.OK).body(results);
  }

  @GetMapping("/measures")
  public ResponseEntity<Page<MeasureListDTO>> getMeasures(
      Principal principal,
      @RequestParam(name = "ownershipTypes", required = false) List<OwnershipType> ownershipTypes,
      @RequestParam(required = false, defaultValue = "10", name = "limit") int limit,
      @RequestParam(required = false, defaultValue = "0", name = "page") int page,
      @RequestParam(required = false, defaultValue = "lastModifiedAt", name = "sort") String sort,
      @RequestParam(required = false, defaultValue = "DESC", name = "direction") String direction) {
    final String username = principal.getName();
    Page<MeasureListDTO> measures;
    final Pageable pageReq =
        PageRequest.of(page, limit, Sort.by(Sort.Direction.valueOf(direction), sort));
    // TODO Remove parameter "measures" when either measureSearch or EditTestsOnVersionedMeasure is
    // removed.
    measures =
        measureService.getMeasuresByCriteria(null, ownershipTypes, pageReq, username, "measures");
    return ResponseEntity.ok(measures);
  }

  @GetMapping("/measures/count")
  public ResponseEntity<Map<String, Integer>> getCounts(Principal principal) {
    Map<String, Integer> results = new HashMap<>();
    results.put(
        "ownedMeasures",
        measureService.countMeasuresByOwnership(
            true, principal.getName(), List.of(OwnershipType.OWNED)));
    results.put(
        "sharedMeasures",
        measureService.countMeasuresByOwnership(
            true, principal.getName(), List.of(OwnershipType.SHARED)));
    results.put(
        "allMeasures",
        measureService.countMeasuresByOwnership(
            true, principal.getName(), List.of(OwnershipType.ALL)));

    return ResponseEntity.ok(results);
  }

  @GetMapping("/measures/{id}")
  public ResponseEntity<Measure> getMeasure(@PathVariable("id") String id, Principal principal) {
    final String username = principal.getName();
    Optional<Measure> measureOptional = repository.findByIdAndActive(id, true);
    if (measureOptional.isPresent()) {
      Measure measure = measureOptional.get();
      MeasureSet measureSet =
          measureSetRepository.findByMeasureSetId(measure.getMeasureSetId()).orElse(null);
      measure.setMeasureSet(measureSet);
      measure.setMeasureLock(measureService.getMeasureLock(id, username));
      return ResponseEntity.status(HttpStatus.OK).body(measure);
    }
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
  }

  @PostMapping("/measure")
  public ResponseEntity<Measure> addMeasure(
      @RequestBody @Validated(Measure.ValidationSequence.class) Measure measure,
      @RequestParam(required = false, defaultValue = "true", name = "addDefaultCQL")
          boolean addDefaultCQL,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {
    final String username = principal.getName();
    Measure savedMeasure =
        measureService.createMeasure(measure, username, accessToken, addDefaultCQL);
    return ResponseEntity.status(HttpStatus.CREATED).body(savedMeasure);
  }

  @PutMapping("/measures/{id}/test-case-config")
  public ResponseEntity<Measure> updateMeasureTestCaseConfiguration(
      @PathVariable("id") String id,
      @RequestBody TestCaseConfiguration testCaseConfig,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {
    final String username = principal.getName();
    final Measure existingMeasure = measureService.findMeasureById(id);
    checkMeasureLock(existingMeasure, username);
    Measure updatedMeasure =
        measureService.updateMeasureTestCaseConfiguration(username, id, testCaseConfig);
    actionLogService.logAction(id, Measure.class, ActionType.UPDATED, username);
    return ResponseEntity.ok().body(updatedMeasure);
  }

  @PutMapping("/measures/{id}")
  public ResponseEntity<Measure> updateMeasure(
      @PathVariable("id") String id,
      @RequestBody @Validated(Measure.ValidationSequence.class) Measure measure,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {
    ResponseEntity<Measure> response;
    final String username = principal.getName();
    if (id == null || id.isEmpty() || !id.equals(measure.getId())) {
      log.info("got invalid id [{}] vs measureId: [{}]", id, measure.getId());
      throw new InvalidIdException("Measure", "Update (PUT)", "(PUT [base]/[resource]/[id])");
    }

    log.info("getMeasureId [{}]", id);

    final Measure existingMeasure = measureService.findMeasureById(id);
    checkMeasureLock(existingMeasure, username);

    if (existingMeasure != null) {
      if (username != null && existingMeasure.getCreatedBy() != null) {
        log.info("got username [{}] vs createdBy: [{}]", username, existingMeasure.getCreatedBy());
        // either owner or shared-with role
        measureService.verifyAuthorization(username, existingMeasure);

        if (!existingMeasure.getMeasureMetaData().isDraft()) {
          throw new InvalidDraftStatusException(measure.getId());
        }

        // no user can update a soft-deleted measure
        if (!existingMeasure.isActive()) {
          throw new UnauthorizedException("Measure", existingMeasure.getId(), username);
        }
        // shared user should be able to edit Measure but won’t have delete access, only owner can
        // delete
        if (!measure.isActive()) {
          measureService.verifyAuthorization(username, measure, null);
        }

        if (appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)
            && testCaseLockService.isAnyTestCaseLockedByOthers(existingMeasure.getId(), username)) {
          throw new LockNotObtainedException(
              "Unable to update measure.  One or more test cases are locked by another user.");
        }

        // clear testcase groups for qdm when scoring or patient basis is changed.
        // for QDM, scoring and patient basis are present outside the group
        // therefor we need to clear testcase groups while updating measure
        if (existingMeasure.getModel().equalsIgnoreCase(ModelType.QDM_5_6.getValue())
            && !CollectionUtils.isEmpty(existingMeasure.getTestCases())) {
          QdmMeasure qdmExistingMeasure = (QdmMeasure) existingMeasure;
          QdmMeasure qdmUpdatingMeasure = (QdmMeasure) measure;

          if (!StringUtils.equals(qdmExistingMeasure.getScoring(), qdmUpdatingMeasure.getScoring())
              || (qdmExistingMeasure.isPatientBasis() != qdmUpdatingMeasure.isPatientBasis())) {
            existingMeasure
                .getTestCases()
                .forEach(
                    testcase -> {
                      testcase.setGroupPopulations(new ArrayList<>());
                      testCaseService.updateTestCase(testcase, id, username, accessToken);
                    });
          }
        }
      }

      response =
          ResponseEntity.ok()
              .body(measureService.updateMeasure(existingMeasure, username, measure, accessToken));
      if (!measure.isActive()) {
        actionLogService.logAction(id, Measure.class, ActionType.DELETED, username);
      } else {
        actionLogService.logAction(id, Measure.class, ActionType.UPDATED, username);
      }
    } else {
      throw new ResourceNotFoundException("Measure", id);
    }

    return response;
  }

  @DeleteMapping("/measures/{id}/delete")
  public ResponseEntity<Measure> deactivateMeasure(
      @PathVariable("id") String id, Principal principal) {

    return ResponseEntity.ok().body(measureService.deactivateMeasure(id, principal.getName()));
  }

  @PutMapping("/measures/{id}/acls")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<List<AclSpecification>> updateAccessControl(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestBody @Validated AclOperation aclOperation,
      @Value("${admin-api-key}") String apiKey) {
    final Measure existingMeasure = measureService.findMeasureById(id);
    checkMeasureLock(existingMeasure, "admin");
    List<AclSpecification> aclSpecifications =
        measureService.updateAccessControlList(id, aclOperation, "admin");
    return ResponseEntity.ok().body(aclSpecifications);
  }

  @GetMapping("/measures/shared")
  public ResponseEntity<Map<String, List<SharedUser>>> getSharedMeasures(
      HttpServletRequest request,
      @RequestParam(name = "measureIds") List<String> measureIds,
      Principal principal) {
    return ResponseEntity.ok()
        .body(measureService.getSharedMeasures(measureIds, principal.getName()));
  }

  @PutMapping("/measures/shared")
  public ResponseEntity<Map<String, List<AclSpecification>>> shareMeasures(
      @RequestBody Map<String, List<String>> measureUserIdMap, Principal principal) {
    final String username = principal.getName();
    // Check lock for each measure being shared
    measureUserIdMap
        .keySet()
        .forEach(
            measureId -> {
              final Measure existingMeasure = measureService.findMeasureById(measureId);
              checkMeasureLock(existingMeasure, username);
            });
    return ResponseEntity.ok(measureService.shareMeasures(measureUserIdMap, username));
  }

  @PutMapping("/measures/unshared")
  public ResponseEntity<Map<String, List<AclSpecification>>> unshareMeasures(
      @RequestBody Map<String, List<String>> measureUserIdMap, Principal principal) {
    final String username = principal.getName();
    // Check lock for each measure being unshared
    measureUserIdMap
        .keySet()
        .forEach(
            measureId -> {
              final Measure existingMeasure = measureService.findMeasureById(measureId);
              checkMeasureLock(existingMeasure, username);
            });
    return ResponseEntity.ok(measureService.unshareMeasures(measureUserIdMap, username));
  }

  @PutMapping("/measures/{id}/ownership")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<String> changeOwnership(
      HttpServletRequest request,
      @PathVariable("id") String id,
      @RequestParam(required = true, name = "userid") String userid,
      @Value("${admin-api-key}") String apiKey,
      Principal principal) {
    try {
      final Measure existingMeasure = measureService.findMeasureById(id);
      checkMeasureLock(existingMeasure, principal.getName());
      measureService.changeOwnership(id, userid, false, principal.getName());
      return ResponseEntity.ok(userid + " granted ownership to Measure successfully.");
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Measure does not exist.");
    } catch (RuntimeException e) {
      log.error(
          "Failed to change ownership for measure [{}] to user [{}]: {}",
          id,
          userid,
          e.getMessage(),
          e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to grant ownership.");
    }
  }

  @GetMapping("/measures/{measureId}/groups")
  public ResponseEntity<List<Group>> getGroups(@PathVariable String measureId) {
    return repository
        .findById(measureId)
        .map(
            measure -> {
              List<Group> groups = measure.getGroups() == null ? List.of() : measure.getGroups();
              return ResponseEntity.ok(groups);
            })
        .orElseThrow(() -> new ResourceNotFoundException("Measure", measureId));
  }

  @PostMapping("/measures/{measureId}/groups")
  public ResponseEntity<Group> createGroup(
      @RequestBody @Validated(Measure.ValidationSequence.class) Group group,
      @PathVariable String measureId,
      Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(groupService.createOrUpdateGroup(group, measureId, principal.getName()));
  }

  @PutMapping("/measures/{measureId}/groups")
  public ResponseEntity<Group> updateGroup(
      @RequestBody @Validated(Measure.ValidationSequence.class) Group group,
      @PathVariable String measureId,
      Principal principal) {
    final String username = principal.getName();
    final Measure existingMeasure = measureService.findMeasureById(measureId);
    checkMeasureLock(existingMeasure, username);
    return ResponseEntity.ok(groupService.createOrUpdateGroup(group, measureId, username));
  }

  @DeleteMapping("/measures/{measureId}/groups/{groupId}")
  public ResponseEntity<Measure> deleteMeasureGroup(
      @RequestBody @PathVariable String measureId,
      @PathVariable String groupId,
      Principal principal) {

    log.info(
        "User [{}] is attempting to delete a group with Id [{}] from measure [{}]",
        principal.getName(),
        groupId,
        measureId);
    return ResponseEntity.ok(
        groupService.deleteMeasureGroup(measureId, groupId, principal.getName()));
  }

  @PostMapping("/measures/{measureId}/groups/{groupId}/stratification")
  public ResponseEntity<Stratification> createStratification(
      @RequestBody Stratification stratification,
      @PathVariable String measureId,
      @PathVariable String groupId,
      Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            groupService.createOrUpdateStratification(
                groupId, measureId, stratification, principal.getName()));
  }

  @PutMapping("/measures/{measureId}/groups/{groupId}/stratification")
  public ResponseEntity<Stratification> updateStratification(
      @RequestBody Stratification stratification,
      @PathVariable String measureId,
      @PathVariable String groupId,
      Principal principal) {
    final String username = principal.getName();
    final Measure existingMeasure = measureService.findMeasureById(measureId);
    checkMeasureLock(existingMeasure, username);
    return ResponseEntity.ok(
        groupService.createOrUpdateStratification(groupId, measureId, stratification, username));
  }

  @DeleteMapping("/measures/{measureId}/groups/{groupId}/stratification/{stratificationId}")
  public ResponseEntity<Measure> deleteStratification(
      @RequestBody @PathVariable String measureId,
      @PathVariable String groupId,
      @PathVariable String stratificationId,
      Principal principal) {

    log.info(
        "User [{}] is attempting to delete a group with Id [{}] from measure [{}]",
        principal.getName(),
        groupId,
        measureId);
    return ResponseEntity.ok(
        groupService.deleteStratification(
            measureId, groupId, stratificationId, principal.getName()));
  }

  @PutMapping("/measures/searches")
  public ResponseEntity<Page<MeasureListDTO>> measureSearchByCriteria(
      Principal principal,
      @RequestParam(name = "ownershipTypes", required = false) List<OwnershipType> ownershipTypes,
      @RequestBody(required = false) MeasureSearchCriteria searchCriteria,
      @RequestParam(required = false, defaultValue = "10", name = "limit") int limit,
      @RequestParam(required = false, defaultValue = "0", name = "page") int page,
      @RequestParam(required = false, defaultValue = "lastModifiedAt", name = "sort") String sort,
      @RequestParam(required = false, defaultValue = "DESC", name = "direction") String direction,
      // TODO Remove parameter when either measureSearch or EditTestsOnVersionedMeasure is removed.
      // Determines the source of the nested measures invocation (i.e., measures page or testcase
      // copy page) as both measureSearch or EditTestsOnVersionedMeasures flags are used in this API
      // call.
      @RequestParam(required = false, defaultValue = "measures") String invocationSource) {

    final String username = principal.getName();
    final Pageable pageReq =
        PageRequest.of(page, limit, Sort.by(Sort.Direction.valueOf(direction), sort));

    Page<MeasureListDTO> measures =
        measureService.getMeasuresByCriteria(
            searchCriteria, ownershipTypes, pageReq, username, invocationSource);

    return ResponseEntity.ok(measures);
  }

  @PutMapping("/measures/{measureSetId}/create-cms-id")
  public ResponseEntity<MeasureSet> createCmsId(
      @PathVariable String measureSetId, Principal principal) {
    final String username = principal.getName();
    measureService.verifyAuthorizationByMeasureSetId(username, measureSetId, true);
    // Check lock for all measures in the measure set
    List<Measure> measuresInSet = repository.findAllByMeasureSetIdAndActive(measureSetId, true);
    measuresInSet.forEach(measure -> checkMeasureLock(measure, username));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(measureSetService.createAndUpdateCmsId(measureSetId, username));
  }

  @DeleteMapping("/measures/{measureId}/delete-cms-id")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<String> deleteCmsId(
      HttpServletRequest request,
      @PathVariable String measureId,
      @RequestParam(name = "cmsId") Integer cmsId,
      @Value("${admin-api-key}") String apiKey,
      @RequestHeader(name = "harpId") String harpId,
      Principal principal) {
    log.info(
        "User [{}] - Started admin task [deleteCmsId] and is attempting to delete "
            + "CMS id [{}] from measure with measure id [{}]",
        principal.getName(),
        cmsId,
        measureId);
    final Measure existingMeasure = measureService.findMeasureById(measureId);
    checkMeasureLock(existingMeasure, principal.getName());
    return ResponseEntity.status(HttpStatus.OK)
        .body(measureSetService.deleteCmsId(measureId, cmsId, harpId, principal.getName()));
  }

  @PutMapping("/measures/cms-id-association")
  public ResponseEntity<MeasureSet> associateCmsId(
      Principal principal,
      @RequestParam String qiCoreMeasureId,
      @RequestParam String qdmMeasureId,
      @RequestParam(defaultValue = "false") boolean copyMetaData) {
    final String username = principal.getName();
    // Check lock for both measures being associated
    final Measure qiCoreMeasure = measureService.findMeasureById(qiCoreMeasureId);
    final Measure qdmMeasure = measureService.findMeasureById(qdmMeasureId);
    checkMeasureLock(qiCoreMeasure, username);
    checkMeasureLock(qdmMeasure, username);
    return ResponseEntity.ok(
        measureService.associateCmsId(username, qiCoreMeasureId, qdmMeasureId, copyMetaData));
  }

  @GetMapping(
      value = "/measures/library/usage",
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<List<LibraryUsage>> getLibraryUsage(@RequestParam String libraryName) {
    return ResponseEntity.ok().body(measureService.findLibraryUsage(libraryName));
  }

  /**
   * Handles transfer of multiple measures to a new owner (identified by harpId).
   *
   * <p>Validates the input list of measure IDs. Delegates transfer logic to measureService, which
   * attempts to reassign each measure. Returns:
   *
   * <ul>
   *   <li>200 OK if all transfers succeed.
   *   <li>400 BAD REQUEST if the input list is empty.
   *   <li>207 MULTI_STATUS if some transfers fail, returning only the failed measure IDs in the
   *       body.
   * </ul>
   */
  @PutMapping("/measures/transfer")
  public ResponseEntity<List<String>> transferMeasures(
      @RequestBody List<String> measureIds,
      @RequestHeader(name = "harpId") String harpId,
      @RequestParam(defaultValue = "false") boolean retainShareAccess,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {
    log.info("transferMeasures to [{}] ", harpId);
    if (CollectionUtils.isEmpty(measureIds)) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.emptyList());
    }
    final String username = principal.getName();
    // Check lock for all measures being transferred
    measureIds.forEach(
        measureId -> {
          final Measure existingMeasure = measureService.findMeasureById(measureId);
          checkMeasureLock(existingMeasure, username);
        });
    List<String> failedTransfers =
        measureService.transferMeasures(measureIds, harpId, retainShareAccess, username);
    if (CollectionUtils.isEmpty(failedTransfers)) {
      return ResponseEntity.ok().build();
    } else {
      return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(failedTransfers);
    }
  }

  @GetMapping(value = "/measures/{id}/history")
  public ResponseEntity<List<Action>> getMeasureHistory(
      @PathVariable("id") String measureId, Principal principal) {
    return ResponseEntity.ok()
        .body(measureService.getMeasureHistory(measureId, principal.getName()));
  }

  /**
   * Checks if a measure is locked by another user and throws LockNotObtainedException if so. This
   * method only performs the check if the LOCKING feature flag is enabled.
   *
   * @param measure the measure to check
   * @param username the username of the current user
   * @throws LockNotObtainedException if the measure is locked by a different user
   */
  private void checkMeasureLock(Measure measure, String username) {
    if (appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)) {
      log.debug("Checking lock for measure [{}]", measure.getId());
      if (measure.getMeasureLock() != null) {
        log.debug(
            "Measure Lock found for measure [{}] locked by user [{}]",
            measure.getId(),
            measure.getMeasureLock().getLockedBy());
        if (!measure.getMeasureLock().getLockedBy().equalsIgnoreCase(username)) {
          throw new LockNotObtainedException(
              "Unable to update measure. Measure is locked by another user.");
        }
      }
    }
  }
}
