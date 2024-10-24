package cms.gov.madie.measure.exceptions;

import java.util.Arrays;

public class UnsupportedTypeException extends RuntimeException {

  public UnsupportedTypeException(String factory, String... params) {
    super(
        String.format(
            "Factory %s unable to create the requested instance"
                + " as the following are not yet support: %s",
            factory, Arrays.toString(params)));
  }

  public UnsupportedTypeException(String message) {
    super(message);
  }
}
