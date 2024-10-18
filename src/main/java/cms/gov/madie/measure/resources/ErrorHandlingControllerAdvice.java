package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.exceptions.*;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
@ControllerAdvice
public class ErrorHandlingControllerAdvice {

  @Autowired private final ErrorAttributes errorAttributes;

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onConstraintValidationException(
      ConstraintViolationException ex, WebRequest request) {
    // Collect simplified validation errors
    Map<String, String> validationErrors = new HashMap<>();
    ex.getConstraintViolations()
        .forEach(
            (error) ->
                validationErrors.put(error.getPropertyPath().toString(), error.getMessage()));
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.BAD_REQUEST);
    errorAttributes.put("validationErrors", validationErrors);
    return errorAttributes;
  }

  // Handle HQMFServiceException separately
  @ExceptionHandler(HQMFServiceException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> handleHQMFServiceException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(DuplicateKeyException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onDuplicateKeyExceptionException(
      DuplicateKeyException ex, WebRequest request) {
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.BAD_REQUEST);
    errorAttributes.put(
        "validationErrors", Map.of(ex.getKey(), Objects.requireNonNull(ex.getMessage())));
    return errorAttributes;
  }

  @ExceptionHandler({UnauthorizedException.class, InvalidDeletionCredentialsException.class})
  @ResponseStatus(HttpStatus.FORBIDDEN)
  @ResponseBody
  Map<String, Object> onUserNotAuthorizedExceptions(Exception ex, WebRequest request) {
    return getErrorAttributes(request, HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onMethodArgumentNotValidException(
      MethodArgumentNotValidException ex, WebRequest request) {
    // Collect simplified validation errors
    Map<String, String> validationErrors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              if (error instanceof FieldError) {
                String fieldName = ((FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                validationErrors.put(fieldName, errorMessage);
              } else {
                validationErrors.put(error.getObjectName(), error.getDefaultMessage());
              }
            });
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.BAD_REQUEST);
    errorAttributes.put("validationErrors", validationErrors);
    return errorAttributes;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onHttpMessageNotReadableException(
      HttpMessageNotReadableException ex, WebRequest request) {
    Map<String, String> validationErrors = new HashMap<>();
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.BAD_REQUEST);
    String errorMessage = null;
    if (ex.getMessage().contains("missing type id property 'model'")) {
      errorMessage = "Model is required";
    }
    if (ex.getMessage()
        .contains("known type ids = [Measure, QDM v5.6, QI-Core v4.1.1, QI-Core v6.0.0]")) {
      errorMessage = "Model should be either QDM v5.6 or QI-Core v4.1.1 or QI-Core v6.0.0";
    }
    if (StringUtils.isNotBlank(errorMessage)) {
      validationErrors.put("model", errorMessage);
      errorAttributes.put("message", errorMessage);
      errorAttributes.put("validationErrors", validationErrors);
    }
    return errorAttributes;
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ResponseBody
  Map<String, Object> onResourceNotFoundException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(InvalidIdException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onInvalidKeyException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler({
    InvalidResourceStateException.class,
    CqlElmTranslationErrorException.class,
    InvalidDraftStatusException.class,
    InvalidMeasureObservationException.class,
    InvalidMeasureStateException.class,
    DuplicateMeasureException.class
  })
  @ResponseStatus(HttpStatus.CONFLICT)
  @ResponseBody
  Map<String, Object> onResourceNotDraftableException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.CONFLICT);
  }

  @ExceptionHandler({BundleOperationException.class, CqlElmTranslationServiceException.class})
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ResponseBody
  Map<String, Object> onBundleOperationFailedException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(InvalidMeasurementPeriodException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onInvalidMeasurementPeriodException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler({
    InvalidReturnTypeException.class,
    InvalidReturnTypeForQdmException.class,
    InvalidFhirGroupException.class,
    InvalidGroupException.class,
    SpecialCharacterException.class,
    CqmConversionException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onInvalidReturnTypeException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(InvalidVersionIdException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onInvalidVersionIdException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler({InvalidCmsIdException.class, InvalidRequestException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onInvalidCmsIdException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(BadVersionRequestException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onBadVersionRequestException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MeasureNotDraftableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onMeasureNotDraftableException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(DuplicateTestCaseNameException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onNonUniqueTestCaseNameException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }

  private Map<String, Object> getErrorAttributes(WebRequest request, HttpStatus httpStatus) {
    // BINDING_ERRORS and STACK_TRACE are too detailed and confusing to parse
    // Let's just add a list of simplified validation errors
    ErrorAttributeOptions errorOptions =
        ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE);
    Map<String, Object> errorAttributes =
        this.errorAttributes.getErrorAttributes(request, errorOptions);
    errorAttributes.put("status", httpStatus.value());
    errorAttributes.put("error", httpStatus.getReasonPhrase());
    return errorAttributes;
  }
}
