package cms.gov.madie.measure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] CSRF_WHITELIST = {
    "/measure-transfer/**",
    "/log/**",
    "/measures/*/grant",
    "/organizations/**",
    "/measures/*/ownership"
  };
  private static final String[] AUTH_WHITELIST = {
    "/measure-transfer/**", "/actuator/**", "/log/**", "/measures/*/grant", "/measures/*/ownership"
  };

  @Bean
  protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(withDefaults())
        .csrf(csrfConfigure -> csrfConfigure.ignoringRequestMatchers(CSRF_WHITELIST))
        .authorizeHttpRequests(
            authorizeRequests ->
                authorizeRequests.requestMatchers(HttpMethod.POST, "/organizations/**").permitAll())
        .authorizeHttpRequests(
            authorizeRequests -> authorizeRequests.requestMatchers(AUTH_WHITELIST).permitAll())
        .authorizeHttpRequests(authorizeRequests -> authorizeRequests.anyRequest().authenticated())
        .sessionManagement(
            sessionManagement ->
                sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer(
            oAuth2ResourceServerConfigurer -> oAuth2ResourceServerConfigurer.jwt(withDefaults()))
        .headers(
            headers ->
                headers
                    .xssProtection(withDefaults())
                    .contentSecurityPolicy(
                        contentSecurityPolicyConfig ->
                            contentSecurityPolicyConfig.policyDirectives("script-src 'self'")));
    return http.build();
  }
}
