package dev.cyr1en.promptcore.logic.condition;

import java.util.Objects;
import java.util.Set;

/** AST binary node representing logical OR ({@code ||}). */
public record OrNode(ConditionNode left, ConditionNode right) implements ConditionNode {

  public OrNode {
    Objects.requireNonNull(left, "left node cannot be null");
    Objects.requireNonNull(right, "right node cannot be null");
  }

  @Override
  public boolean evaluate(ConditionBindings bindings) throws ConditionEvaluationException {
    return left.evaluate(bindings) || right.evaluate(bindings);
  }

  @Override
  public int depth() {
    return Math.max(left.depth(), right.depth()) + 1;
  }

  @Override
  public int nodeCount() {
    return left.nodeCount() + right.nodeCount() + 1;
  }

  @Override
  public void collectAnswerIndices(Set<Integer> indices) {
    left.collectAnswerIndices(indices);
    right.collectAnswerIndices(indices);
  }

  @Override
  public void collectPapiPlaceholders(Set<String> placeholders) {
    left.collectPapiPlaceholders(placeholders);
    right.collectPapiPlaceholders(placeholders);
  }

  @Override
  public String toString() {
    return "(" + left + " || " + right + ")";
  }
}
