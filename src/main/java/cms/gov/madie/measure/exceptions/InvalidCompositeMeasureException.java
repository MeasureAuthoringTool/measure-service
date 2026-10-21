package cms.gov.madie.measure.exceptions;

import java.util.List;

public class InvalidCompositeMeasureException extends RuntimeException {
  private static final String MESSAGE = "Composite measure with ID %s is invalid: %s";

  private final List<String> errors;

  public InvalidCompositeMeasureException(String id, List<String> errors) {
    super(String.format(MESSAGE, id, String.join(", ", errors)));
    this.errors = List.copyOf(errors);
  }

  public List<String> getErrors() {
    return errors;
  }
}
