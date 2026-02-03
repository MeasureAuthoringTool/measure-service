package cms.gov.madie.measure;

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
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            authz ->
                authz
                    .requestMatchers("/log/*")
                    .permitAll()
                    .requestMatchers("/admin/measures/**")
                    .hasAuthority("API_KEY")
                    .anyRequest()
                    .authenticated())
        .csrf(csrf -> csrf.disable())
        // To ensure requests without the right api-key header are blocked by
        // Spring Security (returning 403 Forbidden)
        .addFilterBefore(new ApiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
