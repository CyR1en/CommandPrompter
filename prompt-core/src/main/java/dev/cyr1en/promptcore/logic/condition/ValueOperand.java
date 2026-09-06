package dev.cyr1en.promptcore.logic.condition;

import java.util.Set;

/** Represents a value operand in a condition comparison. */
public interface ValueOperand {

  int MAX_OPERAND_LENGTH = 1024;

  /**
   * Resolves this operand to an opaque string value using the provided bindings.
   *
   * @param bindings runtime bindings supplying answers and placeholders
   * @return resolved string value (never null)
   * @throws ConditionEvaluationException if resolution fails, the operand is missing, or the
   *     resolved value exceeds 1024 characters
   */
  String resolve(ConditionBindings bindings) throws ConditionEvaluationException;

  /**
   * Collects all answer indices referenced by this operand into the given set.
   *
   * @param indices target set for answer indices
   */
  default void collectAnswerIndices(Set<Integer> indices) {}

  /**
   * Collects all PlaceholderAPI placeholder keys referenced by this operand into the given set.
   *
   * @param placeholders target set for placeholder keys
   */
  default void collectPapiPlaceholders(Set<String> placeholders) {}
}
