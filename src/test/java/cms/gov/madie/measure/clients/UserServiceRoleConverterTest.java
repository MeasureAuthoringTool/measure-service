package cms.gov.madie.measure.clients;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import gov.cms.madie.models.dto.UserRolesDto;

@ExtendWith(MockitoExtension.class)
public class UserServiceRoleConverterTest {
  @Mock private UserServiceClient userServiceClient;
  @Mock private Jwt jwt;
  @InjectMocks private UserServiceRoleConverter converter;

  @BeforeEach
  public void setUp() {
    when(jwt.getTokenValue()).thenReturn("token");
  }

  @Test
  public void testConvert() {
    String HARP_ID = "testUser";
    when(jwt.getSubject()).thenReturn(HARP_ID);
    UserRolesDto userRolesDto =
        UserRolesDto.builder()
            .harpId(HARP_ID)
            .roles((List.of("MADiE-User", "MADiE-admin")))
            .build();
    when(userServiceClient.getUserRoles(anyString(), anyString())).thenReturn(userRolesDto);

    var result = converter.convert(jwt);
    // Assert that the result is not null and contains the expected authorities
    assertNotNull(result);
    assertTrue(
        result.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_MADIE-USER")));
    assertTrue(
        result.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_MADIE-ADMIN")));
  }

  @Test
  public void testConvertNoUserRoles() {
    String HARP_ID = "testUserNoUserRoles";
    when(jwt.getSubject()).thenReturn(HARP_ID);
    when(userServiceClient.getUserRoles(anyString(), anyString())).thenReturn(null);

    var result = converter.convert(jwt);
    // Assert that the result is not null and contains no authorities
    assertNotNull(result);
    assertTrue(result.getAuthorities().isEmpty());
  }

  @Test
  public void testConvertNoRoles() {
    String HARP_ID = "testUserNoRoles";
    when(jwt.getSubject()).thenReturn(HARP_ID);
    UserRolesDto userRolesDto = UserRolesDto.builder().harpId(HARP_ID).roles(List.of()).build();
    when(userServiceClient.getUserRoles(anyString(), anyString())).thenReturn(userRolesDto);

    var result = converter.convert(jwt);
    // Assert that the result is not null and contains no authorities
    assertNotNull(result);
    assertTrue(result.getAuthorities().isEmpty());
  }
}
