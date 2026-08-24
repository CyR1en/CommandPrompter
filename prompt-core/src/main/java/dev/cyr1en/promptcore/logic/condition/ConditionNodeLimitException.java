package dev.cyr1en.promptcore.logic.condition;

/** Thrown when a condition expression exceeds the maximum permitted AST node count of 128. */
public class ConditionNodeLimitException extends ConditionParseException {

  public ConditionNodeLimitException(String message) {
    super(message);
  }
}
