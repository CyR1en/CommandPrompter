package dev.cyr1en.promptcore.logic.transform;

import java.util.List;
import java.util.Objects;

/**
 * Result of rendering a compiled template against bindings.
 *
 * @param renderedText the fully rendered output on success, or null on failure
 * @param notices any non-fatal notices produced across all transformed references
 * @param error the failure reason if rendering failed closed
 */
public record RenderResult(
    String renderedText, List<TransformNotice> notices, TransformError error) {

  public RenderResult {
    notices = notices == null ? List.of() : List.copyOf(notices);
  }

  public static RenderResult success(String renderedText, List<TransformNotice> notices) {
    Objects.requireNonNull(renderedText, "renderedText must not be null");
    return new RenderResult(renderedText, notices, null);
  }

  public static RenderResult success(String renderedText) {
    return success(renderedText, List.of());
  }

  public static RenderResult failure(TransformError error) {
    Objects.requireNonNull(error, "error must not be null");
    return new RenderResult(null, List.of(), error);
  }

  public static RenderResult failure(TransformErrorCode code, String message) {
    return failure(new TransformError(code, message));
  }

  public boolean isSuccess() {
    return error == null;
  }

  public boolean isFailure() {
    return error != null;
  }

  public String getOrThrow() {
    if (isFailure()) {
      throw new TransformException(error);
    }
    return renderedText;
  }
}
