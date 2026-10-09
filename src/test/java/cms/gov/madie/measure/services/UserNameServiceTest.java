package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.clients.UserServiceClient;
import gov.cms.madie.models.dto.UserDetailsDto;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserNameServiceTest {

  @Mock private UserServiceClient userServiceClient;

  @InjectMocks private UserNameService userNameService;

  @Test
  void resolvesTheHarpIdToTheUsersFullName() {
    when(userServiceClient.getBulkUserDetails(List.of("jjones")))
        .thenReturn(
            Map.of(
                "jjones",
                UserDetailsDto.builder().firstName("Jonathan").lastName("Jones").build()));

    assertEquals("Jonathan Jones", userNameService.getUserDisplayName("jjones"));
  }

  @Test
  void keepsTheHarpIdWhenTheUserHasNoNameOnFile() {
    when(userServiceClient.getBulkUserDetails(List.of("jjones")))
        .thenReturn(Map.of("jjones", UserDetailsDto.builder().build()));

    assertEquals("jjones", userNameService.getUserDisplayName("jjones"));
  }

  @Test
  void keepsTheHarpIdWhenTheUserServiceReturnsNothing() {
    when(userServiceClient.getBulkUserDetails(List.of("jjones"))).thenReturn(Map.of());

    assertEquals("jjones", userNameService.getUserDisplayName("jjones"));
  }

  @Test
  void keepsTheHarpIdWhenTheUserServiceReturnsNull() {
    when(userServiceClient.getBulkUserDetails(List.of("jjones"))).thenReturn(null);

    assertEquals("jjones", userNameService.getUserDisplayName("jjones"));
  }

  @Test
  void skipsTheLookupWhenThereIsNoHarpId() {
    assertNull(userNameService.getUserDisplayName(null));
    assertEquals("  ", userNameService.getUserDisplayName("  "));

    verify(userServiceClient, never()).getBulkUserDetails(any());
  }

  @Test
  void resolvesSeveralHarpIdsWithASingleUserServiceCall() {
    when(userServiceClient.getBulkUserDetails(List.of("jjones", "asmith")))
        .thenReturn(
            Map.of(
                "jjones",
                UserDetailsDto.builder().firstName("Jonathan").lastName("Jones").build(),
                "asmith",
                UserDetailsDto.builder().firstName("Ann").lastName("Smith").build()));

    Map<String, String> displayNames =
        userNameService.getUsersDisplayNames(List.of("jjones", "asmith", "jjones"));

    assertEquals(2, displayNames.size());
    assertEquals("Jonathan Jones", displayNames.get("jjones"));
    assertEquals("Ann Smith", displayNames.get("asmith"));
    verify(userServiceClient, times(1)).getBulkUserDetails(any());
  }

  @Test
  void ignoresBlankHarpIdsInTheBulkLookup() {
    when(userServiceClient.getBulkUserDetails(List.of("jjones")))
        .thenReturn(
            Map.of(
                "jjones",
                UserDetailsDto.builder().firstName("Jonathan").lastName("Jones").build()));

    Map<String, String> displayNames =
        userNameService.getUsersDisplayNames(Arrays.asList("jjones", null, ""));

    assertEquals(Map.of("jjones", "Jonathan Jones"), displayNames);
  }

  @Test
  void toleratesNullAndEmptyHarpIdLists() {
    assertTrue(userNameService.getUsersDisplayNames(null).isEmpty());
    assertTrue(userNameService.getUsersDisplayNames(List.of()).isEmpty());
    verify(userServiceClient, never()).getBulkUserDetails(any());
  }
}
