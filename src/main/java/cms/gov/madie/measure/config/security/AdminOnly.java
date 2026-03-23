package cms.gov.madie.measure.config.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation for endpoints that require admin role. This is a cleaner alternative to
 * repeating @PreAuthorize on every admin endpoint.
 *
 * <p>Usage:
 *
 * <pre>
 * @AdminOnly
 * @GetMapping("/admin/endpoint")
 * public ResponseEntity<?> adminEndpoint() { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole(@roleConstants.getAdminRole())")
public @interface AdminOnly {}
