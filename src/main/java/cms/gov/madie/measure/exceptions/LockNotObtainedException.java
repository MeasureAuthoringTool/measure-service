package cms.gov.madie.measure.exceptions;

import lombok.Getter;

import java.io.Serial;

@Getter
public class LockNotObtainedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 3773507296919206651L;

  private static final String MESSAGE = "ID: %s, is not able to acquire lock, locked by: %s";

  private String lockedBy;

  public LockNotObtainedException() {
    super("Lock is not obtained");
  }

  public LockNotObtainedException(String id, String lockedBy) {
    super(String.format(MESSAGE, id, lockedBy));
    this.lockedBy = lockedBy;
  }

  public LockNotObtainedException(String message) {
    super(String.format(message));
  }
}
