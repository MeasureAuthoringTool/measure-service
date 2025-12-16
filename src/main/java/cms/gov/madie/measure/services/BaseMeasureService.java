package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.MadieFeatureFlag;
import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.exceptions.InvalidMeasureStateException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.locks.MeasureLock;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.MeasureServiceUtil;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.MeasureSet;
import gov.cms.madie.models.measure.Reference;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
@NoArgsConstructor(force = true)
public abstract class BaseMeasureService {
  private final MeasureRepository measureRepository;
  private final MeasureSetService measureSetService;
  private final AppConfigService appConfigService;
  private final MeasureLockService measureLockService;

  // Abstract methods to be implemented by subclasses
  abstract void enrichWithUserDetails(List<MeasureListDTO> measures);

  abstract String getOwnerDisplayName(MeasureListDTO measure);

  AclOperation buildShareAclOperation(List<String> userIds) {
    return AclOperation.builder()
        .acls(buildShareAclSpecifications(userIds))
        .action(AclOperation.AclAction.GRANT)
        .build();
  }

  // TODO: start replacing usage of measureRepository.findById with this method
  public Measure findMeasureById(final String id) {
    return measureRepository
        .findById(id)
        .map(
            m -> {
              Measure.MeasureBuilder builder =
                  m.toBuilder()
                      .measureSet(measureSetService.findByMeasureSetId(m.getMeasureSetId()));

              // Map measure lock if locking feature is enabled
              if (appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)) {
                MeasureLock lock = measureLockService.findByMeasureId(m.getId());
                if (lock != null) {
                  builder.measureLock(
                      gov.cms.madie.models.measure.MeasureLock.builder()
                          .id(lock.getId())
                          .lockedBy(lock.getLockedBy())
                          .build());
                }
              }

              return builder.build();
            })
        .orElse(null);
  }

  /**
   * Verifies that the user is authorized to perform share/unshare operations on the given measures.
   * - Restrict sharing to owners only (ownerOnly = true). - Allow unsharing by owners or users who
   * the measure is already shared with (ownerOnly = false).
   */
  void verifyShareAuthorization(
      Map<String, List<String>> measureUserIdMap, String username, boolean ownerOnly) {
    log.info(
        "User [{}] has called verifyShareAuthorization to determine whether operation with [{}]"
            + " is allowed to be performed",
        username,
        measureUserIdMap);

    measureUserIdMap
        .keySet()
        .forEach(
            measureId -> {
              Measure measure = findMeasureById(measureId);

              if (measure == null) {
                log.error(
                    "User [{}] called verifyShareAuthorization with measureUserIdMap [{}] but "
                        + "failed because the measure with measure ID [{}] does not exist.",
                    username,
                    measureUserIdMap,
                    measureId);
                throw new ResourceNotFoundException("Measure does not exist: " + measureId);
              }
              verifyAuthorization(
                  username, measure, ownerOnly ? List.of() : List.of(RoleEnum.SHARED_WITH));
            });

    log.info(
        "User [{}] successfully called verifyShareAuthorization and determined that operation "
            + "with [{}] is allowed to be performed",
        username,
        measureUserIdMap);
  }

  List<AclSpecification> buildShareAclSpecifications(List<String> userIds) {
    return userIds.stream()
        .map(
            userId ->
                AclSpecification.builder()
                    .userId(userId)
                    .roles(Set.of(RoleEnum.SHARED_WITH))
                    .build())
        .toList();
  }

  /**
   * Verifies the specified user has privileges on the given measure based on measure owner and the
   * passed roles. Providing null or empty roles will perform an authorization check for owner only.
   *
   * @param username username to be authorized
   * @param measure measure to be authorized
   * @param roles additional roles besides 'Owner'
   */
  public void verifyAuthorization(String username, Measure measure, List<RoleEnum> roles) {
    MeasureSet measureSet =
        measure.getMeasureSet() == null
            ? measureSetService.findByMeasureSetId(measure.getMeasureSetId())
            : measure.getMeasureSet();
    if (measureSet == null) {
      log.error(
          "User [{}] called verifyAuthorization but failed because no measure set exists for "
              + "measure with measure ID [{}]",
          username,
          measure.getId());
      throw new InvalidMeasureStateException(
          "No measure set exists for measure with ID " + measure.getId());
    }
    MeasureServiceUtil.verifyMeasureSetAuthorization(
        username, "Measure", measure.getId(), roles, measureSet);
  }

  void extractUserDeets(Map<String, UserDetailsDto> userDetailsMap, MeasureListDTO measure) {
    if (measure.getMeasureSet() != null && measure.getMeasureSet().getOwner() != null) {
      String ownerId = measure.getMeasureSet().getOwner();
      UserDetailsDto userDetails = userDetailsMap.get(ownerId);

      if (userDetails != null) {
        measure.setOwnerFirstName(userDetails.getFirstName());
        measure.setOwnerLastName(userDetails.getLastName());
        measure.setOwnerEmail(userDetails.getEmail());
        log.debug(
            "Enriched measure {} with owner: {} {}",
            measure.getId(),
            userDetails.getFirstName(),
            userDetails.getLastName());
      } else {
        log.debug("No user details found for owner ID: {}", ownerId);
      }
    }
  }

  protected void updateReferences(MeasureMetaData metaData) {
    if (metaData != null && !CollectionUtils.isEmpty(metaData.getReferences())) {
      List<Reference> references =
          metaData.getReferences().stream().map(this::updateReference).toList();
      metaData.setReferences(references);
    }
  }

  Reference updateReference(Reference reference) {
    return Reference.builder()
        .id(
            StringUtils.isBlank(reference.getId())
                ? UUID.randomUUID().toString()
                : reference.getId())
        .referenceText(reference.getReferenceText())
        .referenceType(reference.getReferenceType())
        .build();
  }

  Page<MeasureListDTO> getPageContent(
      MeasureSearchCriteria searchCriteria,
      List<OwnershipType> ownershipTypes,
      Pageable pageReq,
      String username,
      String invocationSource) {
    Page<MeasureListDTO> measuresPage;
    // For owner sorting, we need to fetch all results, enrich, sort, then paginate
    // Use a default sort by _id to avoid empty sort error in MongoDB
    Pageable defaultSortPageable =
        PageRequest.of(0, 10000, Sort.by(Sort.Direction.ASC, "_id")); // Fetch up to 10k measures
    Page<MeasureListDTO> allMeasures =
        measureRepository.searchMeasuresByCriteria(
            username, defaultSortPageable, searchCriteria, ownershipTypes, invocationSource);

    log.debug(
        "Fetched {} measures for owner sorting and enrichment", allMeasures.getContent().size());

    // Enrich all measures with user details
    enrichWithUserDetails(allMeasures.getContent());

    // Sort by owner display name
    Sort.Order ownerSortOrder =
        pageReq.getSort().stream()
            .filter(order -> "ownerSortField".equals(order.getProperty()))
            .findFirst()
            .orElseThrow();

    log.debug("Sorting by owner, direction: {}", ownerSortOrder.getDirection());

    List<MeasureListDTO> sortedContent =
        allMeasures.getContent().stream()
            .sorted(
                (m1, m2) -> {
                  String name1 = getOwnerDisplayName(m1);
                  String name2 = getOwnerDisplayName(m2);
                  int comparison = name1.compareToIgnoreCase(name2);
                  return ownerSortOrder.isAscending() ? comparison : -comparison;
                })
            .collect(Collectors.toList());

    // Log first few results to verify sorting
    log.debug("After sorting, first 5 measures:");
    sortedContent.stream()
        .limit(5)
        .forEach(m -> log.debug("  {} - {}", getOwnerDisplayName(m), m.getMeasureName()));

    // Apply pagination manually
    int start = (int) pageReq.getOffset();
    int end = Math.min(start + pageReq.getPageSize(), sortedContent.size());
    List<MeasureListDTO> pageContent =
        start < sortedContent.size() ? sortedContent.subList(start, end) : Collections.emptyList();

    measuresPage = new PageImpl<>(pageContent, pageReq, allMeasures.getTotalElements());
    return measuresPage;
  }
}
