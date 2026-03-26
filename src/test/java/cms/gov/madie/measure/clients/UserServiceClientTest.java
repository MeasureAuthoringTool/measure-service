package cms.gov.madie.measure.clients;

import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.dto.DetailsRequestDto;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.dto.UserRolesDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceClientTest {

  @Mock private RestTemplate userServiceRestTemplate;
  @InjectMocks private UserServiceClient userServiceClient;
  @Captor private ArgumentCaptor<HttpEntity<Void>> httpEntityCaptor;

  private static final String HARP_ID = "12345";
  private final String TOKEN = "token";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(userServiceClient, "userServiceBaseUrl", "http://test-url");
  }

  @Test
  void testGetBulkUserDetailsSuccess() {
    // Arrange
    List<String> harpIds = List.of("user1", "user2");
    UserDetailsDto user1 = UserDetailsDto.builder().harpId("user1").build();
    UserDetailsDto user2 = UserDetailsDto.builder().harpId("user2").build();
    Map<String, UserDetailsDto> expectedUsers = Map.of("user1", user1, "user2", user2);

    ResponseEntity<Map<String, UserDetailsDto>> responseEntity = ResponseEntity.ok(expectedUsers);

    when(userServiceRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(responseEntity);

    // Act
    Map<String, UserDetailsDto> result = userServiceClient.getBulkUserDetails(harpIds);

    // Assert
    assertThat(result, is(notNullValue()));
    assertThat(result.size(), is(2));
    assertThat(result.get("user1").getHarpId(), is("user1"));
    assertThat(result.get("user2").getHarpId(), is("user2"));

    verify(userServiceRestTemplate, times(1))
        .exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));
  }

  @Test
  void testGetBulkUserDetailsWithNullInput() {
    // Act
    Map<String, UserDetailsDto> result = userServiceClient.getBulkUserDetails(null);

    // Assert
    assertThat(result, is(notNullValue()));
    assertThat(result.isEmpty(), is(true));
    verify(userServiceRestTemplate, never())
        .exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));
  }

  @Test
  void testGetBulkUserDetailsWithEmptyInput() {
    // Act
    Map<String, UserDetailsDto> result =
        userServiceClient.getBulkUserDetails(Collections.emptyList());

    // Assert
    assertThat(result, is(notNullValue()));
    assertThat(result.isEmpty(), is(true));
    verify(userServiceRestTemplate, never())
        .exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));
  }

  @Test
  void testGetBulkUserDetailsWithNullResponseBody() {
    // Arrange
    List<String> harpIds = List.of("user1");
    ResponseEntity<Map<String, UserDetailsDto>> responseEntity = ResponseEntity.ok(null);

    when(userServiceRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(responseEntity);

    // Act
    Map<String, UserDetailsDto> result = userServiceClient.getBulkUserDetails(harpIds);

    // Assert
    assertThat(result, is(notNullValue()));
    assertThat(result.isEmpty(), is(true));
  }

  @Test
  void testGetBulkUserDetailsWithRestClientException() {
    // Arrange
    List<String> harpIds = List.of("user1");

    when(userServiceRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenThrow(new RestClientException("Connection error"));

    // Act
    Map<String, UserDetailsDto> result = userServiceClient.getBulkUserDetails(harpIds);

    // Assert
    assertThat(result, is(notNullValue()));
    assertThat(result.isEmpty(), is(true));
  }

  @Test
  void testGetBulkUserDetailsWithGenericException() {
    // Arrange
    List<String> harpIds = List.of("user1");

    when(userServiceRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("Unexpected error"));

    // Act
    Map<String, UserDetailsDto> result = userServiceClient.getBulkUserDetails(harpIds);

    // Assert
    assertThat(result, is(notNullValue()));
    assertThat(result.isEmpty(), is(true));
  }

  @Test
  void testGetBulkUserDetailsUrl() {
    // Arrange
    List<String> harpIds = List.of("user1");
    ResponseEntity<Map<String, UserDetailsDto>> responseEntity =
        ResponseEntity.ok(Collections.emptyMap());

    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

    when(userServiceRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(responseEntity);

    // Act
    userServiceClient.getBulkUserDetails(harpIds);

    // Assert
    verify(userServiceRestTemplate)
        .exchange(
            urlCaptor.capture(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));

    assertThat(urlCaptor.getValue(), is("http://test-url/users/details"));
  }

  @Test
  void testGetBulkUserDetailsRequestBody() {
    // Arrange
    List<String> harpIds = List.of("user1", "user2");
    ResponseEntity<Map<String, UserDetailsDto>> responseEntity =
        ResponseEntity.ok(Collections.emptyMap());

    ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

    when(userServiceRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(responseEntity);

    // Act
    userServiceClient.getBulkUserDetails(harpIds);

    // Assert
    verify(userServiceRestTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.POST),
            entityCaptor.capture(),
            any(ParameterizedTypeReference.class));

    HttpEntity<?> capturedEntity = entityCaptor.getValue();
    assertThat(capturedEntity, is(notNullValue()));
    assertThat(capturedEntity.getBody(), is(notNullValue()));

    @SuppressWarnings("unchecked")
    DetailsRequestDto requestBody = (DetailsRequestDto) capturedEntity.getBody();
    assertThat(requestBody.getHarpIds().size(), is(2));
    assertThat(requestBody.getHarpIds().contains("user1"), is(true));
    assertThat(requestBody.getHarpIds().contains("user2"), is(true));

    assertThat(capturedEntity.getHeaders(), is(notNullValue()));
    assertThat(capturedEntity.getHeaders().getContentType().toString(), is("application/json"));
  }

  @Test
  void testGetUserDetails() {
    UserDetailsDto expectedDetails =
        UserDetailsDto.builder().harpId(HARP_ID).userStatus(UserStatus.ACTIVE).build();

    when(userServiceRestTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserDetailsDto.class)))
        .thenReturn(ResponseEntity.ok(expectedDetails));

    UserDetailsDto result = userServiceClient.getUserDetails(HARP_ID, TOKEN);

    assertThat(result, is(notNullValue()));
    assertThat(result.getHarpId(), is(HARP_ID));
    assertThat(result.getUserStatus(), is(UserStatus.ACTIVE));
    verify(userServiceRestTemplate, times(1))
        .exchange(
            eq("http://test-url/users/" + HARP_ID + "/details"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(UserDetailsDto.class));
  }

  @Test
  void testGetUserDetailsReturnsNullWhenHarpIdIsNull() {
    UserDetailsDto result = userServiceClient.getUserDetails(null, TOKEN);

    assertNull(result);
    verify(userServiceRestTemplate, never())
        .exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(UserDetailsDto.class));
  }

  @Test
  void testGetUserDetailsWithException() {
    when(userServiceRestTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserDetailsDto.class)))
        .thenThrow(new RestClientException("Service unavailable"));

    UserDetailsDto result = userServiceClient.getUserDetails(HARP_ID, TOKEN);
    assertNull(result);
  }

  @Test
  public void testGetUserRoles() {
    UserRolesDto expectedUserRolesDto =
        UserRolesDto.builder().harpId(HARP_ID).roles(List.of("MADiE-User")).build();

    when(userServiceRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            eq(UserRolesDto.class)))
        .thenReturn(ResponseEntity.ok(expectedUserRolesDto));

    UserRolesDto actualUserRolesDto = userServiceClient.getUserRoles(HARP_ID, TOKEN);

    assertThat(actualUserRolesDto, is(notNullValue()));
    assertThat(actualUserRolesDto.getHarpId(), is(equalTo(HARP_ID)));
    assertEquals(expectedUserRolesDto, actualUserRolesDto);
    verify(userServiceRestTemplate, times(1))
        .exchange(
            anyString(), eq(HttpMethod.GET), httpEntityCaptor.capture(), eq(UserRolesDto.class));
    HttpHeaders headers = httpEntityCaptor.getValue().getHeaders();
    assertThat(headers.getContentType(), is(MediaType.APPLICATION_JSON));
  }

  @Test
  void testGetUserRolesWithException() {
    when(userServiceRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            eq(UserRolesDto.class)))
        .thenThrow(new RestClientException("Connection error"));

    UserRolesDto actualUserRolesDto = userServiceClient.getUserRoles(HARP_ID, TOKEN);

    assertNull(actualUserRolesDto);
    verify(userServiceRestTemplate, times(1))
        .exchange(
            anyString(), eq(HttpMethod.GET), httpEntityCaptor.capture(), eq(UserRolesDto.class));
  }

  @Test
  void testGetUserRolesReturnsNullWhenHarpIdIsNull() {
    UserRolesDto result = userServiceClient.getUserRoles(null, TOKEN);

    assertNull(result);
    verify(userServiceRestTemplate, never())
        .exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(UserRolesDto.class));
  }

  @Test
  void testHasRoleReturnsTrueWhenUserHasRole() {
    // Arrange
    String role = "Admin";
    UserRolesDto userRolesDto =
        UserRolesDto.builder().harpId(HARP_ID).roles(List.of("User", role)).build();

    when(userServiceRestTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserRolesDto.class)))
        .thenReturn(ResponseEntity.ok(userRolesDto));

    // Act
    boolean result = userServiceClient.hasRole(HARP_ID, role, TOKEN);

    // Assert
    assertTrue(result);
    verify(userServiceRestTemplate, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserRolesDto.class));
  }

  @Test
  void testHasRoleReturnsFalseWhenUserDoesNotHaveRole() {
    // Arrange
    String role = "Admin";
    UserRolesDto userRolesDto =
        UserRolesDto.builder().harpId(HARP_ID).roles(List.of("User")).build();

    when(userServiceRestTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserRolesDto.class)))
        .thenReturn(ResponseEntity.ok(userRolesDto));

    // Act
    boolean result = userServiceClient.hasRole(HARP_ID, role, TOKEN);

    // Assert
    assertFalse(result);
    verify(userServiceRestTemplate, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserRolesDto.class));
  }

  @Test
  void testHasRoleReturnsFalseWhenUserHasNoRoles() {
    // Arrange
    String role = "Admin";
    UserRolesDto userRolesDto =
        UserRolesDto.builder().harpId(HARP_ID).roles(Collections.emptyList()).build();

    when(userServiceRestTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserRolesDto.class)))
        .thenReturn(ResponseEntity.ok(userRolesDto));

    // Act
    boolean result = userServiceClient.hasRole(HARP_ID, role, TOKEN);

    // Assert
    assertFalse(result);
    verify(userServiceRestTemplate, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserRolesDto.class));
  }

  @Test
  void testHasRoleReturnsFalseWhenGetUserRolesReturnsNull() {
    // Arrange
    String role = "Admin";

    when(userServiceRestTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserRolesDto.class)))
        .thenThrow(new RestClientException("Connection error"));

    // Act
    boolean result = userServiceClient.hasRole(HARP_ID, role, TOKEN);

    // Assert
    assertFalse(result);
    verify(userServiceRestTemplate, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserRolesDto.class));
  }

  @Test
  void testHasRoleReturnsFalseWhenHarpIdIsNull() {
    // Arrange
    String role = "Admin";

    // Act
    boolean result = userServiceClient.hasRole(null, role, TOKEN);

    // Assert
    assertFalse(result);
    verify(userServiceRestTemplate, never())
        .exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(UserRolesDto.class));
  }
}
