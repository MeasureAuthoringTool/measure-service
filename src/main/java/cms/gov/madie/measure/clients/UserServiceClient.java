package cms.gov.madie.measure.clients;

import gov.cms.madie.models.dto.DetailsRequestDto;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.dto.UserRolesDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

  private final RestTemplate userServiceRestTemplate;

  @Value("${madie.user-service.base-url}")
  private String userServiceBaseUrl;

  /**
   * Fetches user details in bulk from the user service.
   *
   * @param harpIds List of HARP IDs to fetch details for
   * @return Map of HARP ID to UserDetailsDto, empty map if service call fails
   */
  public Map<String, UserDetailsDto> getBulkUserDetails(List<String> harpIds) {
    if (CollectionUtils.isEmpty(harpIds)) {
      return Collections.emptyMap();
    }

    try {
      String url = userServiceBaseUrl + "/users/details";

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      DetailsRequestDto requestBody = DetailsRequestDto.builder().harpIds(harpIds).build();

      HttpEntity<DetailsRequestDto> request = new HttpEntity<>(requestBody, headers);

      log.debug("Requesting user details for HARP IDs: {}", harpIds);

      ResponseEntity<Map<String, UserDetailsDto>> responseEntity =
          userServiceRestTemplate.exchange(
              url,
              HttpMethod.POST,
              request,
              new ParameterizedTypeReference<Map<String, UserDetailsDto>>() {});

      Map<String, UserDetailsDto> response = responseEntity.getBody();

      log.debug("Received response from user service: {}", response);

      if (response != null && log.isDebugEnabled()) {
        response.forEach(
            (harpId, details) -> {
              log.debug(
                  "User details for {}: firstName={}, lastName={}, email={}",
                  harpId,
                  details != null ? details.getFirstName() : "null",
                  details != null ? details.getLastName() : "null",
                  details != null ? details.getEmail() : "null");
            });
      }

      return response != null ? response : Collections.emptyMap();

    } catch (Exception e) {
      log.error(
          "Failed to fetch user details from user service for {} HARP IDs: {}",
          harpIds.size(),
          e.getMessage(),
          e);
      return Collections.emptyMap();
    }
  }

  /**
   * Fetches UserRolesDto from the user service.
   *
   * @param harpId: HARP ID to fetch UserRolesDto for
   * @return UserRolesDto which contains the HARP ID and associated roles, or null if service call
   *     fails
   */
  public UserRolesDto getUserRoles(String harpId, String accessToken) {
    log.debug("Requesting user roles for HARP ID: [{}]", harpId);
    if (StringUtils.isBlank(harpId)) {
      return null;
    }

    String url = userServiceBaseUrl + "/users/" + harpId + "/roles";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    try {
      log.debug("Calling user-service to request user roles for HARP ID: [{}]", harpId);
      ResponseEntity<UserRolesDto> responseEntity =
          userServiceRestTemplate.exchange(url, HttpMethod.GET, request, UserRolesDto.class);
      UserRolesDto response = responseEntity.getBody();
      log.debug("Successfully retrieved user roles for HARP ID: [{}]", harpId);
      return response;
    } catch (Exception e) {
      log.error(
          "Failed to fetch user roles from user service for HARP ID: [{}]",
          harpId,
          e.getMessage(),
          e);
      return null;
    }
  }

  /**
   * Fetches UserDetailsDto from the user service.
   *
   * @param harpId: HARP ID to fetch UserRolesDto for
   * @return UserRolesDto which contains the HARP ID and associated roles, or null if service call
   *     fails
   */
  public UserDetailsDto getUserDetails(String harpId, String accessToken) {
    log.debug("Requesting user details for HARP ID: [{}]", harpId);
    if (StringUtils.isBlank(harpId)) {
      return null;
    }

    String url = userServiceBaseUrl + "/users/" + harpId + "/details";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    try {
      log.debug("Calling user-service to request user details for HARP ID: [{}]", harpId);
      ResponseEntity<UserDetailsDto> responseEntity =
          userServiceRestTemplate.exchange(url, HttpMethod.GET, request, UserDetailsDto.class);
      UserDetailsDto response = responseEntity.getBody();
      log.debug("Successfully retrieved user details for HARP ID: [{}]", harpId);
      return response;
    } catch (Exception e) {
      log.error(
          "Failed to fetch user details from user service for HARP ID: [{}]",
          harpId,
          e.getMessage(),
          e);
      return null;
    }
  }

  public boolean hasRole(String harpId, String role, String accessToken) {
    if (StringUtils.isBlank(harpId)) {
      return false;
    }
    UserRolesDto userRolesDto = getUserRoles(harpId, accessToken);
    return userRolesDto != null
        && CollectionUtils.isNotEmpty(userRolesDto.getRoles())
        && userRolesDto.getRoles().stream().anyMatch(r -> r.equalsIgnoreCase(role));
  }
}
