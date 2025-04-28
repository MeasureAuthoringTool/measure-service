package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.services.SseEmitters;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sse")
public class ValidationSseController {

  private final SseEmitters sseEmitters;

  @GetMapping(
      value = "/validation-results/{testCaseId}",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamValidationResults(
      @PathVariable String testCaseId, HttpServletRequest request) {
    // Todo Add Authentication interceptor for JWT
    log.info("Client connected for SSE testCaseId={}", testCaseId);
    return sseEmitters.createEmitter(testCaseId);
  }
}
