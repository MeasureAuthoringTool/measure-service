package cms.gov.madie.measure.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.LOCKED)
public class LockNotObtainedException extends RuntimeException {

  private static final long serialVersionUID = 3773507296919206651L;

  public LockNotObtainedException(String message) {
    super(String.format(message));
  }
}
