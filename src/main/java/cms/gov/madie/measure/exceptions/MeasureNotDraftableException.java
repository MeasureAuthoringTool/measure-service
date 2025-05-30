package cms.gov.madie.measure.exceptions;

public class MeasureNotDraftableException extends RuntimeException {
  private static final String MESSAGE = "Can not create a draft for the measure \"%s\". %s";

  public MeasureNotDraftableException(String measureName, String reason) {
    super(String.format(MESSAGE, measureName, reason));
  }
}
