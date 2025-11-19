package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.MadieFeatureFlag;
import cms.gov.madie.measure.exceptions.LockNotObtainedException;
import cms.gov.madie.measure.services.AppConfigService;
import gov.cms.madie.models.measure.Measure;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base controller providing common functionality for measure-related controllers. Provides
 * lock checking functionality that can be enabled/disabled via feature flags.
 */
@Slf4j
public abstract class AbstractMeasureController {

  /**
   * Get the AppConfigService instance from the subclass.
   *
   * @return the AppConfigService instance
   */
  protected abstract AppConfigService getAppConfigService();

  /**
   * Checks if a measure is locked by another user. This method respects the feature flag to
   * enable/disable lock checking.
   *
   * @param measure the measure to check
   * @param username the username attempting to access the measure
   * @throws LockNotObtainedException if the measure is locked by another user
   */
  protected void checkMeasureLock(Measure measure, String username) {
    if (getAppConfigService().isFlagEnabled(MadieFeatureFlag.LOCKING)) {
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
