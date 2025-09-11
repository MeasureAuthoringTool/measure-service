package cms.gov.madie.measure.exceptions;

public class LockNotObtainedException extends RuntimeException {

  private static final long serialVersionUID = 3773507296919206651L;

  private static final String MESSAGE = "ID: %s, is not able to acquire lock, locked by: %s";

  public LockNotObtainedException() {
    super("Lock is not obtained");
  }

  public LockNotObtainedException(String id, String lockedBy) {
    super(String.format(MESSAGE, id, lockedBy));
  }

  public LockNotObtainedException(String message) {
    super(String.format(message));
  }
}
