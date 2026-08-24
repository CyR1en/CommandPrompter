package dev.cyr1en.promptcore.logic.condition;

/** Base exception for all condition-related compilation and evaluation failures. */
public abstract class ConditionException extends RuntimeException {

  protected ConditionException(String message) {
    super(message);
  }

  protected ConditionException(String message, Throwable cause) {
    super(message, cause);
  }
}
