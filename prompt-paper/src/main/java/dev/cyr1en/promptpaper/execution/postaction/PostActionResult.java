package dev.cyr1en.promptpaper.execution.postaction;

import dev.cyr1en.promptpaper.execution.dispatch.DispatchError;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchErrorKind;
import dev.cyr1en.promptpaper.execution.postaction.template.ActionTemplateError;
import java.util.Objects;
import java.util.Optional;

/** Immutable outcome of a post-action execution run. */
public record PostActionResult(boolean isSuccess, DispatchError error) {
  public PostActionResult {
    if (!isSuccess && error == null) {
      error =
          DispatchError.of(
              DispatchErrorKind.DISPATCH_RETURNED_FALSE, "Post-action execution failed");
    }
  }

  public static PostActionResult success() {
    return new PostActionResult(true, null);
  }

  public static PostActionResult failure(DispatchError error) {
    Objects.requireNonNull(error, "error must not be null for failure outcome");
    return new PostActionResult(false, error);
  }

  public static PostActionResult failure(DispatchErrorKind kind, String detail) {
    return failure(DispatchError.of(kind, detail));
  }

  public static PostActionResult failure(DispatchErrorKind kind, Throwable cause) {
    return failure(DispatchError.of(kind, cause));
  }

  public static PostActionResult failure(DispatchErrorKind kind, String detail, Throwable cause) {
    return failure(DispatchError.of(kind, detail, cause));
  }

  public static PostActionResult failure(ActionTemplateError templateError) {
    Objects.requireNonNull(templateError, "templateError must not be null");
    return failure(DispatchErrorKind.INVALID_REQUEST, "Template error: " + templateError.message());
  }

  public boolean isFailure() {
    return !isSuccess;
  }

  public Optional<DispatchError> optionalError() {
    return Optional.ofNullable(error);
  }
}
