package cms.gov.madie.measure.exceptions;

public class DuplicateTestCaseNameException extends RuntimeException {

  private static final String MESSAGE =
      "The Test Case Group and Title combination is not unique. "
          + "The combination must be unique (case insensitive, spaces ignored) "
          + "across all test cases associated with the measure.";

  public DuplicateTestCaseNameException() {
    super(String.format(MESSAGE));
  }
}
