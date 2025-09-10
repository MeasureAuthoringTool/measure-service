package cms.gov.madie.measure.exceptions;

public class LockNotObtainedException extends RuntimeException {

  private static final long serialVersionUID = 3773507296919206651L;

  private static final String MESSAGE = "IDs: %s, are not able to acquire locks locked by: %s";

  public LockNotObtainedException(String id, String lockedBy) {
    super(String.format(MESSAGE, id, lockedBy));
  }

  public LockNotObtainedException(String message) {
    super(String.format(message));
  }
}
