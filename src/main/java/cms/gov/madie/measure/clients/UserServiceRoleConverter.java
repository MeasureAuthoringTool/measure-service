package cms.gov.madie.measure.clients;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.CollectionUtils;

import gov.cms.madie.models.dto.UserRolesDto;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UserServiceRoleConverter implements Converter<Jwt, JwtAuthenticationToken> {
  private final UserServiceClient userServiceClient;

  @Autowired
  public UserServiceRoleConverter(UserServiceClient userServiceClient) {
    this.userServiceClient = userServiceClient;
  }

  @Override
  public JwtAuthenticationToken convert(Jwt jwt) {
    String userId = jwt.getSubject();
    UserRolesDto userRolesDto = userServiceClient.getUserRoles(userId, jwt.getTokenValue());
    if (userRolesDto == null) {
      log.warn("No User found for user harp ID: {}", userId);
      return new JwtAuthenticationToken(jwt, Collections.emptyList());
    }
    Collection<String> roles = userRolesDto.getRoles();
    if (CollectionUtils.isEmpty(roles)) {
      log.warn("No roles found for user: {}", userId);
      return new JwtAuthenticationToken(jwt, Collections.emptyList());
    }

    // Convert roles to SimpleGrantedAuthority with "ROLE_" prefix
    List<GrantedAuthority> authorities =
        roles.stream()
            .map(role -> "ROLE_" + role.toUpperCase())
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

    log.info("User {} has roles: {}", userId, roles);
    return new JwtAuthenticationToken(jwt, authorities);
  }
}
