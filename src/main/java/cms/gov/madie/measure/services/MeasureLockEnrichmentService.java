package cms.gov.madie.measure.services;

import cms.gov.madie.measure.repositories.MeasureLockRepository;
import gov.cms.madie.models.measure.Measure;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for enriching measures with lock information. This service adds lock data to
 * measures when they are retrieved, showing which measures are locked by other users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeasureLockEnrichmentService {

  private final MeasureLockRepository measureLockRepository;
  private final TestCaseLockService testCaseLockService;

  /**
   * Enriches a single measure with lock information.
   *
   * @param measure the measure to enrich
   * @param username the current username
   */
  public void enrichMeasureWithLockInfo(Measure measure, String username) {
    if (measure == null) {
      return;
    }

    // Get measure lock if it exists and is not owned by current user
    Optional<cms.gov.madie.measure.locks.MeasureLock> lock =
        measureLockRepository.findByMeasureId(measure.getId());
    if (lock.isPresent() && !lock.get().getLockedBy().equals(username)) {
      measure.setMeasureLock(convertToMeasureLock(lock.get()));
    }

    // Check if any test cases are locked by others
    boolean hasLockedTestCases =
        testCaseLockService.isAnyTestCaseLockedByOthers(measure.getId(), username);
    measure.setHasLockedTestCases(hasLockedTestCases);
  }

  /**
   * Converts a MeasureLock entity to a MeasureLock model DTO.
   *
   * @param entity the lock entity from the database
   * @return the lock model DTO
   */
  private gov.cms.madie.models.measure.MeasureLock convertToMeasureLock(
      cms.gov.madie.measure.locks.MeasureLock entity) {
    return gov.cms.madie.models.measure.MeasureLock.builder()
        .id(entity.getId())
        .measureId(entity.getMeasureId())
        .lockedBy(entity.getLockedBy())
        .lockedAt(entity.getLockedAt())
        .expiresAt(entity.getExpiresAt())
        .build();
  }
}
