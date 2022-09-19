package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.repositories.OrganizationRepository;
import gov.cms.madie.models.common.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class OrganizationController {
  private final OrganizationRepository organizationRepository;

  @Cacheable("organizations")
  @GetMapping("/organizations")
  public ResponseEntity<List<Organization>> getAllOrganizations() {
    List<Organization> organizationList = organizationRepository.findAll();
    if (CollectionUtils.isEmpty(organizationList)) {
      log.debug("No organizations are available");
      throw new RuntimeException("No organizations are available");
    }
    return ResponseEntity.status(HttpStatus.OK).body(organizationList);
  }

  @PostMapping("/organizations")
  public ResponseEntity<List<Organization>> addOrganizations(
      @RequestBody List<Organization> organizations, Principal principal) {
    final String userName = principal.getName();
    log.info("User [{}] is attempting to add new organizations", organizations);
    List<Organization> organizationList = organizationRepository.saveAll(organizations);
    log.info("User [{}] successfully added new organizations", userName);
    return ResponseEntity.status(HttpStatus.CREATED).body(organizationList);
  }
}
