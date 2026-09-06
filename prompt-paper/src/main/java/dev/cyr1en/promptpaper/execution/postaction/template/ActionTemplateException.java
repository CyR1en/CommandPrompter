package dev.cyr1en.promptpaper.execution.postaction.template;

import java.util.Objects;

/** Exception thrown when action template compilation fails. */
public class ActionTemplateException extends RuntimeException {

  private final ActionTemplateError error;

  public ActionTemplateException(ActionTemplateError error) {
    super(error.message(), error.cause());
    this.error = Objects.requireNonNull(error, "error must not be null");
  }

  public ActionTemplateException(ActionTemplateErrorCode code, String message) {
    this(ActionTemplateError.of(code, message));
  }

  public ActionTemplateException(ActionTemplateErrorCode code, String message, Throwable cause) {
    this(ActionTemplateError.of(code, message, cause));
  }

  public ActionTemplateError error() {
    return error;
  }

  public ActionTemplateErrorCode errorCode() {
    return error.code();
  }
}
