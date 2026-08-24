package dev.cyr1en.promptcore.logic.condition;

/** Thrown when a condition expression exceeds the maximum permitted AST nesting depth of 10. */
public class ConditionDepthException extends ConditionParseException {

  public ConditionDepthException(String message) {
    super(message);
  }
}
