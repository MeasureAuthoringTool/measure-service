package cms.gov.madie.measure.utils;

import cms.gov.madie.measure.exceptions.InvalidMeasurementPeriodException;
import cms.gov.madie.measure.exceptions.InvalidVersionIdException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.measure.MeasureSet;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Slf4j
@UtilityClass
public class MeasureServiceUtil {

  public void verifyMeasureSetAuthorization(
      String username,
      String target,
      String targetId,
      List<RoleEnum> roles,
      MeasureSet measureSet) {
    List<RoleEnum> allowedRoles = roles == null ? List.of() : roles;
    if (!measureSet.getOwner().equalsIgnoreCase(username)
        && (CollectionUtils.isEmpty(measureSet.getAcls())
            || measureSet.getAcls().stream()
                .noneMatch(
                    acl ->
                        acl.getUserId().equalsIgnoreCase(username)
                            && acl.getRoles().stream().anyMatch(allowedRoles::contains)))) {
      log.error("User {} is not authorized for {} with target ID {}", username, target, targetId);
      throw new UnauthorizedException(target, targetId, username);
    }
  }

  public void validateMeasurementPeriod(Date measurementPeriodStart, Date measurementPeriodEnd) {
    if (measurementPeriodStart == null || measurementPeriodEnd == null) {
      throw new InvalidMeasurementPeriodException(
          "Measurement period date is required and must be valid");
    }
    SimpleDateFormat checkYear = new SimpleDateFormat("yyyy");
    int checkMeasurementPeriodStart = Integer.parseInt(checkYear.format(measurementPeriodStart));
    int checkMeasurementPeriodEnd = Integer.parseInt(checkYear.format(measurementPeriodEnd));

    if (1900 > checkMeasurementPeriodStart
        || checkMeasurementPeriodStart > 2099
        || 1900 > checkMeasurementPeriodEnd
        || checkMeasurementPeriodEnd > 2099) {
      throw new InvalidMeasurementPeriodException(
          "Measurement periods should be between the years 1900 and 2099.");
    }

    if (measurementPeriodEnd.compareTo(measurementPeriodStart) < 1) {
      throw new InvalidMeasurementPeriodException(
          "Measurement period end date should be greater than measurement period start date.");
    }
  }

  public void checkVersionIdChanged(String changedVersionId, String originalVersionId) {
    if (StringUtils.isBlank(changedVersionId) && !StringUtils.isBlank(originalVersionId)) {
      throw new InvalidVersionIdException(changedVersionId);
    } else if (!StringUtils.isBlank(changedVersionId)
        && !StringUtils.isBlank(originalVersionId)
        && !changedVersionId.equalsIgnoreCase(originalVersionId)) {
      throw new InvalidVersionIdException(changedVersionId);
    }
  }
}
