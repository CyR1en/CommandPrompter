package dev.cyr1en.promptcore.logic.condition;

/**
 * Thrown when a compiled condition fails to evaluate at runtime due to missing operands,
 * non-numeric values in numeric comparisons, oversized resolved operands, or other fail-closed
 * errors.
 */
public class ConditionEvaluationException extends ConditionException {

  public ConditionEvaluationException(String message) {
    super(message);
  }

  public ConditionEvaluationException(String message, Throwable cause) {
    super(message, cause);
  }
}
