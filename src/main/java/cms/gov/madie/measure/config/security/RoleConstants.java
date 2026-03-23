package cms.gov.madie.measure.config.security;

import org.springframework.stereotype.Component;

@Component("roleConstants")
public final class RoleConstants {
  public static final String MADiE_ADMIN = "MADIE-ADMIN";

  // Method for use in SpEL expressions
  public String getAdminRole() {
    return MADiE_ADMIN;
  }
}
