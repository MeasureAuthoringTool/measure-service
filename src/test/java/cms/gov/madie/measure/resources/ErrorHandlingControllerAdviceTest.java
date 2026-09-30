package cms.gov.madie.measure.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@ExtendWith(MockitoExtension.class)
class ErrorHandlingControllerAdviceTest {

  @Mock private ErrorAttributes errorAttributes;

  @InjectMocks private ErrorHandlingControllerAdvice advice;

  private WebRequest request;

  @BeforeEach
  void setUp() {
    request = new ServletWebRequest(new MockHttpServletRequest());
    when(errorAttributes.getErrorAttributes(
            any(WebRequest.class), any(ErrorAttributeOptions.class)))
        .thenAnswer(
            invocation -> {
              Map<String, Object> result = new HashMap<>();
              result.put("message", "Unexpected error");
              return result;
            });
  }

  @Test
  void onConstraintValidationExceptionAddsValidationErrors() {
    Path path = mock(Path.class);
    when(path.toString()).thenReturn("measure.model");

    ConstraintViolation<?> violation = mock(ConstraintViolation.class);
    when(violation.getPropertyPath()).thenReturn(path);
    when(violation.getMessage()).thenReturn("must not be blank");

    Map<String, Object> response =
        advice.onConstraintValidationException(
            new ConstraintViolationException(Set.of(violation)), request);

    assertEquals(HttpStatus.BAD_REQUEST.value(), response.get("status"));
    assertEquals(HttpStatus.BAD_REQUEST.getReasonPhrase(), response.get("error"));
    @SuppressWarnings("unchecked")
    Map<String, String> validationErrors = (Map<String, String>) response.get("validationErrors");
    assertEquals("must not be blank", validationErrors.get("measure.model"));
  }

  @Test
  void onMethodArgumentNotValidExceptionAddsFieldValidationErrors() throws NoSuchMethodException {
    TestDto dto = new TestDto();
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(dto, "testDto");
    bindingResult.addError(new FieldError("testDto", "model", "Model is required"));

    MethodParameter parameter =
        new MethodParameter(TestDto.class.getDeclaredMethod("getModel"), -1);
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(parameter, bindingResult);

    Map<String, Object> response = advice.onMethodArgumentNotValidException(exception, request);

    assertEquals(HttpStatus.BAD_REQUEST.value(), response.get("status"));
    @SuppressWarnings("unchecked")
    Map<String, String> validationErrors = (Map<String, String>) response.get("validationErrors");
    assertEquals("Model is required", validationErrors.get("model"));
  }

  @Test
  void onDuplicateKeyExceptionExceptionAddsValidationErrors() {
    Map<String, Object> response =
        advice.onDuplicateKeyExceptionException(
            new DuplicateKeyException("model", "duplicate model"), request);

    assertEquals(HttpStatus.BAD_REQUEST.value(), response.get("status"));
    @SuppressWarnings("unchecked")
    Map<String, String> validationErrors = (Map<String, String>) response.get("validationErrors");
    assertEquals("duplicate model", validationErrors.get("model"));
  }

  @Test
  void onHttpMessageNotReadableExceptionAddsMissingModelValidationError() {
    Map<String, Object> response =
        advice.onHttpMessageNotReadableException(
            new HttpMessageNotReadableException("missing type id property 'model'", null), request);

    assertEquals(HttpStatus.BAD_REQUEST.value(), response.get("status"));
    assertEquals("Model is required", response.get("message"));
    @SuppressWarnings("unchecked")
    Map<String, String> validationErrors = (Map<String, String>) response.get("validationErrors");
    assertEquals("Model is required", validationErrors.get("model"));
  }

  @Test
  void onHttpMessageNotReadableExceptionAddsUnsupportedModelValidationError() {
    Map<String, Object> response =
        advice.onHttpMessageNotReadableException(
            new HttpMessageNotReadableException(
                "known type ids = [Measure, QDM v5.6, QI-Core v4.1.1, QI-Core v6.0.0]", null),
            request);

    assertEquals(HttpStatus.BAD_REQUEST.value(), response.get("status"));
    assertEquals(
        "Model should be either QDM v5.6 or QI-Core v4.1.1 or QI-Core v6.0.0",
        response.get("message"));
    @SuppressWarnings("unchecked")
    Map<String, String> validationErrors = (Map<String, String>) response.get("validationErrors");
    assertEquals(
        "Model should be either QDM v5.6 or QI-Core v4.1.1 or QI-Core v6.0.0",
        validationErrors.get("model"));
  }

  @Test
  void onHttpMessageNotReadableExceptionLeavesResponseUnchangedWhenMessageDoesNotMatch() {
    Map<String, Object> response =
        advice.onHttpMessageNotReadableException(
            new HttpMessageNotReadableException("some other validation issue", null), request);

    assertNotNull(response);
    assertEquals(HttpStatus.BAD_REQUEST.value(), response.get("status"));
    assertEquals(HttpStatus.BAD_REQUEST.getReasonPhrase(), response.get("error"));
    assertEquals("Unexpected error", response.get("message"));
  }

  private static class TestDto {
    public String getModel() {
      return null;
    }
  }
}
