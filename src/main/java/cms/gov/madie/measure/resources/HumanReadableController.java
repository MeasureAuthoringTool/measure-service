package cms.gov.madie.measure.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.security.Principal;

import cms.gov.madie.measure.dto.HtmlDiffResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import cms.gov.madie.measure.services.HumanReadableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HumanReadableController {

  private final HumanReadableService humanReadableService;

  @GetMapping("/humanreadable/{id}")
  public ResponseEntity<String> getHumanReadableWithCSS(
      @PathVariable("id") String id,
      Principal principal,
      @RequestHeader("Authorization") String accessToken)
      throws Exception {
    final String username = principal.getName();
    log.info(
        "User [{}] is attempting to get human readable with CSS for measure [{}]", username, id);
    return ResponseEntity.ok(
        humanReadableService.getHumanReadableWithCSS(id, username, accessToken));
  }

  @GetMapping("/html-diff")
  public HtmlDiffResponse compare() throws IOException {
    // Load HTML files from resources
    String oldHtml = loadHtmlFromResource("html/CMS1272-v0.0.000-FHIR.html");
    String newHtml = loadHtmlFromResource("html/CMS1272-v0.0.000-FHIR-New.html");

    return humanReadableService.compareHtml(oldHtml, newHtml);
  }

  private String loadHtmlFromResource(String path) throws IOException {
    ClassPathResource resource = new ClassPathResource(path);
    return Files.readString(resource.getFile().toPath());
  }
}
