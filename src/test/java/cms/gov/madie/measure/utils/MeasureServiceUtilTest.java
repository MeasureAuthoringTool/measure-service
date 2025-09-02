package cms.gov.madie.measure.utils;

import cms.gov.madie.measure.exceptions.InvalidMeasurementPeriodException;
import cms.gov.madie.measure.exceptions.InvalidVersionIdException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.measure.MeasureSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MeasureServiceUtilTest {

  @Test
  void shouldAllowWhenUserIsOwner() {
    MeasureSet measureSet = MeasureSet.builder().owner("owner").acls(List.of()).build();

    assertDoesNotThrow(
        () ->
            MeasureServiceUtil.verifyMeasureSetAuthorization(
                "owner", "target", "123", List.of(RoleEnum.SHARED_WITH), measureSet));
  }

  @Test
  void shouldThrowWhenUserNotOwnerAndNotInAcl() {
    MeasureSet measureSet = MeasureSet.builder().owner("owner").acls(List.of()).build();

    assertThrows(
        UnauthorizedException.class,
        () ->
            MeasureServiceUtil.verifyMeasureSetAuthorization(
                "otherUser", "target", "123", List.of(RoleEnum.SHARED_WITH), measureSet));
  }

  @Test
  void shouldThrowWhenUserInAclButRoleDoesNotMatch() {
    AclSpecification acl = AclSpecification.builder().userId("user1").roles(Set.of()).build();
    MeasureSet measureSet = MeasureSet.builder().owner("owner").acls(List.of(acl)).build();

    assertThrows(
        UnauthorizedException.class,
        () ->
            MeasureServiceUtil.verifyMeasureSetAuthorization(
                "user1", "target", "123", List.of(RoleEnum.SHARED_WITH), measureSet));
  }

  @Test
  void shouldAllowWhenUserInAclWithSharedWithRole() {
    AclSpecification acl =
        AclSpecification.builder().userId("user1").roles(Set.of(RoleEnum.SHARED_WITH)).build();
    MeasureSet measureSet = MeasureSet.builder().owner("owner").acls(List.of(acl)).build();

    assertDoesNotThrow(
        () ->
            MeasureServiceUtil.verifyMeasureSetAuthorization(
                "user1", "target", "123", List.of(RoleEnum.SHARED_WITH), measureSet));
  }

  @Test
  public void testValidateMeasureMeasurementPeriodWithNullStartDate() {
    LocalDate endDate = LocalDate.parse("2022-12-31");

    assertThrows(
        InvalidMeasurementPeriodException.class,
        () ->
            MeasureServiceUtil.validateMeasurementPeriod(
                null, Date.from(endDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant())));
  }

  @Test
  public void testValidateMeasureMeasurementPeriodWithNullEndDate() {
    LocalDate startDate = LocalDate.parse("2022-01-01");

    assertThrows(
        InvalidMeasurementPeriodException.class,
        () ->
            MeasureServiceUtil.validateMeasurementPeriod(
                Date.from(startDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()),
                null));
  }

  @Test
  public void testValidateMeasureMeasurementPeriodTooEarlyDate() {
    LocalDate startDate = LocalDate.parse("0001-01-01");
    LocalDate endDate = LocalDate.parse("2022-12-31");

    assertThrows(
        InvalidMeasurementPeriodException.class,
        () ->
            MeasureServiceUtil.validateMeasurementPeriod(
                Date.from(startDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()),
                Date.from(endDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant())));
  }

  @Test
  public void testValidateMeasureMeasurementPeriodFlippedDates() {
    LocalDate startDate = LocalDate.parse("2022-01-01");
    LocalDate endDate = LocalDate.parse("2022-12-31");

    assertThrows(
        InvalidMeasurementPeriodException.class,
        () ->
            MeasureServiceUtil.validateMeasurementPeriod(
                Date.from(endDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()),
                Date.from(startDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant())));
  }

  @Test
  public void testValidateMeasureMeasurementPeriodEndDateEqualStartDate() {
    LocalDate startDate = LocalDate.parse("2022-12-31");
    LocalDate endDate = LocalDate.parse("2022-12-31");

    assertThrows(
        InvalidMeasurementPeriodException.class,
        () ->
            MeasureServiceUtil.validateMeasurementPeriod(
                Date.from(startDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()),
                Date.from(endDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant())));
  }

  @Test
  public void testValidateMeasureMeasurementPeriod() {
    try {
      LocalDate startDate = LocalDate.parse("2022-01-01");
      LocalDate endDate = LocalDate.parse("2023-01-01");

      MeasureServiceUtil.validateMeasurementPeriod(
          Date.from(startDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()),
          Date.from(endDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()));

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testInvalidVersionIdThrowsExceptionForDifferentVersionIds() {
    assertThrows(
        InvalidVersionIdException.class,
        () -> MeasureServiceUtil.checkVersionIdChanged("versionId1", "versionId2"));
  }

  @Test
  public void testInvalidVersionThrowsExceptionWhenPassedInVersionIsNull() {
    assertThrows(
        InvalidVersionIdException.class,
        () -> MeasureServiceUtil.checkVersionIdChanged("", "versionId1"));
  }

  @Test
  public void testInvalidVersionIdDoesNotThrowExceptionWhenMatch() {
    try {
      MeasureServiceUtil.checkVersionIdChanged("versionId1", "versionId1");
    } catch (Exception e) {
      fail("Should not throw unexpected exception");
    }
  }

  @Test
  public void testInvalidVersionIdDoesNotThrowExceptionWhenBothAreNull() {
    try {
      MeasureServiceUtil.checkVersionIdChanged(null, null);
    } catch (Exception e) {
      fail("Should not throw unexpected exception");
    }
  }

  @Test
  public void testInvalidVersionIdDoesNotThrowExceptionWhenVersionIdFromDBIsNull() {
    try {
      MeasureServiceUtil.checkVersionIdChanged("versionId1", null);
    } catch (Exception e) {
      fail("Should not throw unexpected exception");
    }
  }
}
