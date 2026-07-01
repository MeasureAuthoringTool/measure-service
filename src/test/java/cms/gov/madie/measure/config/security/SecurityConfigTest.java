package cms.gov.madie.measure.config.security;

import cms.gov.madie.measure.CustomAccessDeniedHandler;
import cms.gov.madie.measure.JwtRoleTestFilter;
import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.clients.UserServiceRoleConverter;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
@EnableWebSecurity
public class SecurityConfigTest {
  @Bean
  public CustomAccessDeniedHandler customAccessDeniedHandler() {
    return Mockito.mock(CustomAccessDeniedHandler.class);
  }

  @Bean
  public UserServiceClient userServiceClient() {
    return Mockito.mock(UserServiceClient.class);
  }

  @Bean
  public UserServiceRoleConverter roleConverter(UserServiceClient userServiceClient) {
    return new UserServiceRoleConverter(userServiceClient);
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, UserServiceRoleConverter roleConverter)
      throws Exception {

    http.authorizeHttpRequests(
            authz ->
                authz
                    .requestMatchers("/log/*")
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .hasRole(RoleConstants.MADiE_ADMIN)
                    .anyRequest()
                    .authenticated())
        .csrf(csrf -> csrf.disable())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter)))
        .addFilterBefore(
            new JwtRoleTestFilter("ROLE_MADIE-ADMIN"), BearerTokenAuthenticationFilter.class);

    return http.build();
  }
}
