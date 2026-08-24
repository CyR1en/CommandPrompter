package dev.cyr1en.promptcore.logic.transform;

import java.util.List;
import java.util.Objects;

/**
 * Result of evaluating a single transformer on an input string.
 *
 * @param value the transformed value on success, or null on failure
 * @param notices any non-fatal notices produced
 * @param error the failure reason if evaluation failed closed
 */
public record SingleTransformResult(
    String value, List<TransformNotice> notices, TransformError error) {

  public SingleTransformResult {
    notices = notices == null ? List.of() : List.copyOf(notices);
  }

  public static SingleTransformResult success(String value, List<TransformNotice> notices) {
    Objects.requireNonNull(value, "value must not be null");
    return new SingleTransformResult(value, notices, null);
  }

  public static SingleTransformResult success(String value) {
    return success(value, List.of());
  }

  public static SingleTransformResult failure(TransformError error) {
    Objects.requireNonNull(error, "error must not be null");
    return new SingleTransformResult(null, List.of(), error);
  }

  public static SingleTransformResult failure(TransformErrorCode code, String message) {
    return failure(new TransformError(code, message));
  }

  public boolean isSuccess() {
    return error == null;
  }

  public boolean isFailure() {
    return error != null;
  }
}
