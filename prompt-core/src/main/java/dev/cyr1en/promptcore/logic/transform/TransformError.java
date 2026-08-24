package dev.cyr1en.promptcore.logic.transform;

import java.util.Objects;

/**
 * Immutable typed error representing a transformation or template failure.
 *
 * @param code the error classification
 * @param message diagnostic description
 */
public record TransformError(TransformErrorCode code, String message) {

  public TransformError {
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(message, "message must not be null");
  }
}
