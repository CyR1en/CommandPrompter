package dev.cyr1en.promptpaper.execution.dispatch;

import java.util.Objects;
import java.util.Optional;

/** Immutable result of a command dispatch invocation. */
public record DispatchOutcome(boolean isSuccess, DispatchError error) {
  public DispatchOutcome {
    if (!isSuccess && error == null) {
      error = DispatchError.of(DispatchErrorKind.DISPATCH_RETURNED_FALSE, "dispatch failed");
    }
  }

  public static DispatchOutcome success() {
    return new DispatchOutcome(true, null);
  }

  public static DispatchOutcome failure(DispatchError error) {
    Objects.requireNonNull(error, "error must not be null for failure outcome");
    return new DispatchOutcome(false, error);
  }

  public static DispatchOutcome failure(DispatchErrorKind kind, String detail) {
    return failure(DispatchError.of(kind, detail));
  }

  public static DispatchOutcome failure(DispatchErrorKind kind, Throwable cause) {
    return failure(DispatchError.of(kind, cause));
  }

  public boolean isFailure() {
    return !isSuccess;
  }

  public Optional<DispatchError> optionalError() {
    return Optional.ofNullable(error);
  }
}
