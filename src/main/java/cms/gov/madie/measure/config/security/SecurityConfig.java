package cms.gov.madie.measure.config.security;

import cms.gov.madie.measure.CustomAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import cms.gov.madie.measure.clients.UserServiceRoleConverter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] CSRF_WHITELIST = {"/log/**"};
  private static final String[] AUTH_WHITELIST = {"/actuator/**", "/log/**"};

  @Bean
  protected SecurityFilterChain filterChain(
      HttpSecurity http,
      CustomAccessDeniedHandler customAccessDeniedHandler,
      UserServiceRoleConverter roleConverter)
      throws Exception {

    http.cors(withDefaults())
        .csrf(csrfConfigure -> csrfConfigure.ignoringRequestMatchers(CSRF_WHITELIST))
        .authorizeHttpRequests(
            authorizeRequests ->
                authorizeRequests
                    .requestMatchers(AUTH_WHITELIST)
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .hasRole(RoleConstants.MADiE_ADMIN)
                    .anyRequest()
                    .authenticated())
        .sessionManagement(
            sessionManagement ->
                sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer(
            oAuth2ResourceServerConfigurer ->
                oAuth2ResourceServerConfigurer.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(roleConverter)))
        .headers(
            headers ->
                headers
                    .xssProtection(withDefaults())
                    .contentSecurityPolicy(
                        contentSecurityPolicyConfig ->
                            contentSecurityPolicyConfig.policyDirectives("script-src 'self'")))
        .exceptionHandling(
            exceptionHandlingConfig ->
                exceptionHandlingConfig.accessDeniedHandler(customAccessDeniedHandler));
    return http.build();
  }
}
