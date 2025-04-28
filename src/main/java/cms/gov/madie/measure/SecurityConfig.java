package cms.gov.madie.measure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] CSRF_WHITELIST = {
    "/measure-transfer/**",
    "/log/**",
    "/measures/*/grant",
    "/organizations/**",
    "/measures/*/ownership",
    "/sse/**"
  };
  private static final String[] AUTH_WHITELIST = {
    "/measure-transfer/**",
    "/actuator/**",
    "/log/**",
    "/measures/*/grant",
    "/measures/*/ownership",
          "/sse/**"
  };

  @Bean
  protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors()
        .and()
        .csrf()
        .ignoringRequestMatchers(CSRF_WHITELIST)
        .and()
        .authorizeHttpRequests()
        .requestMatchers(HttpMethod.POST, "/organizations/**")
        .permitAll()
        .requestMatchers(AUTH_WHITELIST)
        .permitAll()
        .and()
        .authorizeHttpRequests()
        .anyRequest()
        .authenticated()
        .and()
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
        .oauth2ResourceServer()
        .jwt()
        .and()
        .and()
        .headers()
        .xssProtection()
        .and()
        .contentSecurityPolicy("script-src 'self'");
    return http.build();
  }
}
