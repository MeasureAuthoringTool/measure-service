package cms.gov.madie.measure;

import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.clients.UserServiceRoleConverter;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@TestConfiguration
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
                    .requestMatchers("/admin/measures/**")
                    .hasAuthority("API_KEY")
                    .requestMatchers("/admin/**")
                    .hasRole("MADIE-ADMIN")
                    .anyRequest()
                    .authenticated())
        .csrf(csrf -> csrf.disable())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter)))
        // To ensure requests without the right api-key header are blocked by
        // Spring Security (returning 403 Forbidden)
        .addFilterBefore(new ApiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
