package dev.cyr1en.promptcore.logic.condition;

import java.util.Set;

/** Common AST node interface for condition expressions. */
public interface ConditionNode {

  /**
   * Evaluates this AST node against the provided runtime bindings.
   *
   * @param bindings runtime bindings supplying answers and placeholders
   * @return boolean result of evaluating this node
   * @throws ConditionEvaluationException if evaluation fails due to missing operands, non-numeric
   *     values in numeric comparisons, oversized resolved operands, etc.
   */
  boolean evaluate(ConditionBindings bindings) throws ConditionEvaluationException;

  /**
   * Returns the maximum nesting depth of this AST subtree. A single leaf comparison node has a
   * depth of 1.
   *
   * @return nesting depth
   */
  int depth();

  /**
   * Returns the total count of AST nodes in this subtree.
   *
   * @return total node count
   */
  int nodeCount();

  /**
   * Collects all zero-based answer indices referenced within this subtree.
   *
   * @param indices target set
   */
  void collectAnswerIndices(Set<Integer> indices);

  /**
   * Collects all PlaceholderAPI placeholder keys referenced within this subtree.
   *
   * @param placeholders target set
   */
  void collectPapiPlaceholders(Set<String> placeholders);
}
