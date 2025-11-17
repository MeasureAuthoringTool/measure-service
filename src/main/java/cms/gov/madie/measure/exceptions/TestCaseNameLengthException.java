package cms.gov.madie.measure.exceptions;

public class TestCaseNameLengthException extends RuntimeException {

  private static final String MESSAGE =
      "The Test Case Group and Title combination is too long. "
          + "The combination must be less than 255 characters (case insensitive, spaces ignored).";

  public TestCaseNameLengthException() {
    super(String.format(MESSAGE));
  }

  public TestCaseNameLengthException(String testCaseId) {
    super(String.format("%s TestCaseId: %s", MESSAGE, testCaseId));
  }
}
