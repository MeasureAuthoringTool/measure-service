package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.TestCaseValidationResult;
import gov.cms.madie.models.measure.TestCase;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class ValidationWebSocketService {

  private final SimpMessagingTemplate messagingTemplate;

  public void notifyValidation(TestCase testCase) {
    TestCaseValidationResult result = new TestCaseValidationResult();
    result.setTestCaseId(testCase.getId());
    result.setValidResource(testCase.getHapiOperationOutcome().isSuccessful());
    result.setOperationOutcome(testCase.getHapiOperationOutcome());

    log.info("testCase Id in the backemd {}", testCase.getId());
    messagingTemplate.convertAndSend("/topic/validation-results/" + testCase.getId(), result);
  }
}
