package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.TestCaseValidationResult;
import gov.cms.madie.models.measure.TestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

@Slf4j
@Service
public class SseEmitters {

  // Todo Should handle multiple users watching same testCase
  private final ConcurrentHashMap<String, List<SseEmitter>> emittersMap = new ConcurrentHashMap<>();

  public SseEmitter createEmitter(String testCaseId) {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

    emittersMap.computeIfAbsent(testCaseId, key -> new ArrayList<>()).add(emitter);

    emitter.onCompletion(() -> removeEmitter(testCaseId, emitter));
    emitter.onTimeout(() -> removeEmitter(testCaseId, emitter));
    emitter.onError((e) -> removeEmitter(testCaseId, emitter));

    return emitter;
  }

  private void removeEmitter(String testCaseId, SseEmitter emitter) {
    List<SseEmitter> emitters = emittersMap.get(testCaseId);
    if (emitters != null) {
      emitters.remove(emitter);
      if (emitters.isEmpty()) {
        emittersMap.remove(testCaseId);
      }
    }
  }

  // sends validation result to all users watching a specific testCaseId
  public void sendValidationResult(TestCase testCase) {
    TestCaseValidationResult result = new TestCaseValidationResult();
    result.setTestCaseId(testCase.getId());
    result.setValidResource(testCase.getHapiOperationOutcome().isSuccessful());
    result.setOperationOutcome(testCase.getHapiOperationOutcome());

    log.info("testCase Id in the backend {}", testCase.getId());

    List<SseEmitter> emitters = emittersMap.get(testCase.getId());
    if (emitters != null) {
      for (SseEmitter emitter : emitters) {
        try {
          emitter.send(SseEmitter.event().name("validation-result/" + testCase.getId()).data(result));
        } catch (IOException e) {
          log.error("Error sending SSE message to user", e);
          removeEmitter(testCase.getId(), emitter);
        }
      }
    }
  }
}
