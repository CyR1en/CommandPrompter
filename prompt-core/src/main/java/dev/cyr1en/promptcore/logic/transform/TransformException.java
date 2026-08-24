package dev.cyr1en.promptcore.logic.transform;

import java.util.Objects;

/** Exception thrown when template compilation or transformation encounters a fatal error. */
public class TransformException extends RuntimeException {

  private final TransformError error;

  public TransformException(TransformError error) {
    super(Objects.requireNonNull(error, "error must not be null").message());
    this.error = error;
  }

  public TransformException(TransformErrorCode code, String message) {
    this(new TransformError(code, message));
  }

  public TransformError error() {
    return error;
  }

  public TransformErrorCode code() {
    return error.code();
  }
}
