package cms.gov.madie.measure.resources;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import cms.gov.madie.measure.exceptions.*;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.measure.Group;
import cms.gov.madie.measure.services.ActionLogService;
import cms.gov.madie.measure.services.MeasureService;

import com.nimbusds.oauth2.sdk.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import gov.cms.madie.models.measure.Measure;
import cms.gov.madie.measure.repositories.MeasureRepository;
import lombok.RequiredArgsConstructor;

import javax.validation.Valid;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MeasureController {

  private final MeasureRepository repository;
  private final MeasureService measureService;
  private final ActionLogService actionLogService;

  @GetMapping("/measures")
  public ResponseEntity<Page<Measure>> getMeasures(
      Principal principal,
      @RequestParam(required = false, defaultValue = "false", name = "currentUser")
          boolean filterByCurrentUser,
      @RequestParam(required = false, defaultValue = "10", name = "limit") int limit,
      @RequestParam(required = false, defaultValue = "0", name = "page") int page) {
    final String username = principal.getName();
    final Pageable pageReq = PageRequest.of(page, limit, Sort.by("lastModifiedAt").descending());
    Page<Measure> measures =
        filterByCurrentUser
            ? repository.findAllByCreatedByAndActive(username, true, pageReq)
            : repository.findAllByActive(true, pageReq);
    return ResponseEntity.ok(measures);
  }

  @GetMapping("/measures/{id}")
  public ResponseEntity<Measure> getMeasure(@PathVariable("id") String id) {
    Optional<Measure> measure = repository.findByIdAndActive(id, true);
    return measure
        .map(ResponseEntity::ok)
        .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  @PostMapping("/measure")
  public ResponseEntity<Measure> addMeasure(
      @RequestBody @Validated(Measure.ValidationSequence.class) Measure measure,
      Principal principal) {
    final String username = principal.getName();
    log.info("User [{}] is attempting to create a new measure", username);
    measureService.checkDuplicateCqlLibraryName(measure.getCqlLibraryName());
    measureService.validateMeasurementPeriod(
        measure.getMeasurementPeriodStart(), measure.getMeasurementPeriodEnd());

    // Clear ID so that the unique GUID from MongoDB will be applied
    Instant now = Instant.now();
    measure.setId(null);
    measure.setCreatedBy(username);
    measure.setCreatedAt(now);
    measure.setLastModifiedBy(username);
    measure.setLastModifiedAt(now);

    Measure savedMeasure = repository.save(measure);
    log.info("User [{}] successfully created new measure with ID [{}]", username, measure.getId());

    actionLogService.logAction(savedMeasure.getId(), Measure.class, ActionType.CREATED, username);

    return ResponseEntity.status(HttpStatus.CREATED).body(savedMeasure);
  }

  @PutMapping("/measures/{id}")
  public ResponseEntity<String> updateMeasure(
      @PathVariable("id") String id,
      @RequestBody @Validated(Measure.ValidationSequence.class) Measure measure,
      Principal principal) {
    ResponseEntity<String> response = ResponseEntity.badRequest().body("Measure does not exist.");
    final String username = principal.getName();
    if (id == null || id.isEmpty() || !id.equals(measure.getId())) {
      log.info("got invalid id [{}] vs measureId: [{}]", id, measure.getId());
      throw new InvalidIdException("Measure", "Update (PUT)", "(PUT [base]/[resource]/[id])");
    }

    log.info("getMeasureId [{}]", id);
    Optional<Measure> persistedMeasure = repository.findById(id);

    if (persistedMeasure.isPresent()) {
      if (username != null
          && persistedMeasure.get().getCreatedBy() != null
          && !persistedMeasure.get().isActive()) {
        log.info(
            "got username [{}] vs createdBy: [{}]",
            username,
            persistedMeasure.get().getCreatedBy());
        measureService.checkDeletionCredentials(username, persistedMeasure.get().getCreatedBy());
      }
      if (isCqlLibraryNameChanged(measure, persistedMeasure.get())) {
        measureService.checkDuplicateCqlLibraryName(measure.getCqlLibraryName());
      }

      if (isMeasurementPeriodChanged(measure, persistedMeasure.get())) {
        measureService.verifyAuthorization(username, persistedMeasure.get());
        measureService.validateMeasurementPeriod(
            measure.getMeasurementPeriodStart(), measure.getMeasurementPeriodEnd());
      }
      measure.setLastModifiedBy(username);
      measure.setLastModifiedAt(Instant.now());
      // prevent users from overwriting the createdAt/By
      measure.setCreatedAt(persistedMeasure.get().getCreatedAt());
      measure.setCreatedBy(persistedMeasure.get().getCreatedBy());
      repository.save(measure);
      response = ResponseEntity.ok().body("Measure updated successfully.");
      if (!measure.isActive()) {
        actionLogService.logAction(measure.getId(), Measure.class, ActionType.DELETED, username);
      } else {
        actionLogService.logAction(measure.getId(), Measure.class, ActionType.UPDATED, username);
      }
    }
    return response;
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
      @RequestBody @Valid Group group, @PathVariable String measureId, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(measureService.createOrUpdateGroup(group, measureId, principal.getName()));
  }

  @PutMapping("/measures/{measureId}/groups")
  public ResponseEntity<Group> updateGroup(
      @RequestBody @Valid Group group, @PathVariable String measureId, Principal principal) {
    return ResponseEntity.ok(
        measureService.createOrUpdateGroup(group, measureId, principal.getName()));
  }

  @DeleteMapping("/measures/{measureId}/groups/{groupId}")
  public ResponseEntity<Measure> deleteMeasureGroup(
      @RequestBody @PathVariable String measureId,
      @PathVariable String groupId,
      Principal principal) {

    Optional<Measure> measureOptional = repository.findById(measureId);
    if (!measureOptional.isPresent()) {
      throw new ResourceNotFoundException("Measure", measureId);
    }
    Measure measure = measureOptional.get();
    if (!principal.getName().equals(measure.getCreatedBy())) {
      throw new UnauthorizedException("Measure", measureId, principal.getName());
    }

    if (groupId == null || groupId.trim().isEmpty()) {
      throw new InvalidIdException("Measure group Id cannot be null");
    }

    log.info(
        "User [{}] is attempting to delete a group with Id [{}] from measure [{}]",
        principal.getName(),
        groupId,
        measureId);
    return ResponseEntity.ok(
        measureService.deleteMeasureGroup(measure, groupId, principal.getName()));
  }

  @GetMapping(path = "/measures/{measureId}/bundles", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getMeasureBundle(
      @PathVariable String measureId,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {
    Optional<Measure> measureOptional = repository.findById(measureId);
    if (measureOptional.isEmpty()) {
      throw new ResourceNotFoundException("Measure", measureId);
    }
    Measure measure = measureOptional.get();
    if (!principal.getName().equals(measure.getCreatedBy())) {
      throw new UnauthorizedException("Measure", measureId, principal.getName());
    }
    if (measure.isCqlErrors()) {
      throw new InvalidResourceBundleStateException(
          "Measure", measureId, "since CQL errors exist.");
    }
    if (CollectionUtils.isEmpty(measure.getGroups())) {
      throw new InvalidResourceBundleStateException(
          "Measure", measureId, "since there are no associated measure groups.");
    }
    if (measure.getElmJson() == null || StringUtils.isBlank(measure.getElmJson())) {
      throw new InvalidResourceBundleStateException(
          "Measure", measureId, "since there are issues with the CQL.");
    }
    return ResponseEntity.ok(measureService.bundleMeasure(measure, accessToken));
  }

  private boolean isCqlLibraryNameChanged(Measure measure, Measure persistedMeasure) {
    return !Objects.equals(persistedMeasure.getCqlLibraryName(), measure.getCqlLibraryName());
  }

  private boolean isMeasurementPeriodChanged(Measure measure, Measure persistedMeasure) {
    return !Objects.equals(
            persistedMeasure.getMeasurementPeriodStart(), measure.getMeasurementPeriodStart())
        || !Objects.equals(
            persistedMeasure.getMeasurementPeriodEnd(), measure.getMeasurementPeriodEnd());
  }
}
