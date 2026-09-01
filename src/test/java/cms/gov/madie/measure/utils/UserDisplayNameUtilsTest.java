package cms.gov.madie.measure.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import gov.cms.madie.models.dto.UserDetailsDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class UserDisplayNameUtilsTest {

  private UserDetailsDto userDetails(String firstName, String lastName) {
    UserDetailsDto userDetails = new UserDetailsDto();
    userDetails.setFirstName(firstName);
    userDetails.setLastName(lastName);
    return userDetails;
  }

  @Test
  public void testGetFullNameJoinsFirstAndLastName() {
    assertEquals("Ada Lovelace", UserDisplayNameUtils.getFullName(userDetails("Ada", "Lovelace")));
  }

  @Test
  public void testGetFullNameFallsBackToWhicheverHalfIsPresent() {
    assertEquals("Ada", UserDisplayNameUtils.getFullName(userDetails("Ada", " ")));
    assertEquals("Lovelace", UserDisplayNameUtils.getFullName(userDetails(null, "Lovelace")));
  }

  @Test
  public void testGetFullNameReturnsEmptyForUnknownOrUnnamedUser() {
    assertEquals("", UserDisplayNameUtils.getFullName(null));
    assertEquals("", UserDisplayNameUtils.getFullName(userDetails(null, null)));
  }

  @Test
  public void testToReviewerDisplayNamesResolvesNamesAndFallsBackToHarpId() {
    Map<String, UserDetailsDto> userDetailsMap =
        Map.of("harp-1", userDetails("Ada", "Lovelace"), "harp-2", userDetails(null, null));

    assertEquals(
        List.of("Ada Lovelace", "harp-2", "harp-3"),
        UserDisplayNameUtils.toReviewerDisplayNames(
            List.of("harp-1", "harp-2", "harp-3"), userDetailsMap));
  }

  @Test
  public void testToReviewerDisplayNamesSkipsBlankHarpIds() {
    assertEquals(
        List.of("harp-1"),
        UserDisplayNameUtils.toReviewerDisplayNames(List.of("harp-1", " "), Map.of()));
  }

  @Test
  public void testToReviewerDisplayNamesReturnsNullWhenNoReviewersAssigned() {
    assertNull(UserDisplayNameUtils.toReviewerDisplayNames(null, Map.of()));
    assertNull(UserDisplayNameUtils.toReviewerDisplayNames(List.of(), Map.of()));
  }
}
