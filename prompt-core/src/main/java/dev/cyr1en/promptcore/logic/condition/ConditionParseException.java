package dev.cyr1en.promptcore.logic.condition;

/**
 * Thrown when a condition expression cannot be parsed or compiled due to syntax errors, illegal
 * tokens, or static constraint violations.
 */
public class ConditionParseException extends ConditionException {

  public ConditionParseException(String message) {
    super(message);
  }

  public ConditionParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
