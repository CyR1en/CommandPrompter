package dev.cyr1en.promptcore.logic.condition;

import java.util.Objects;
import java.util.Set;

/** AST unary node representing logical NOT ({@code !}). */
public record NotNode(ConditionNode child) implements ConditionNode {

  public NotNode {
    Objects.requireNonNull(child, "child node cannot be null");
  }

  @Override
  public boolean evaluate(ConditionBindings bindings) throws ConditionEvaluationException {
    return !child.evaluate(bindings);
  }

  @Override
  public int depth() {
    return child.depth() + 1;
  }

  @Override
  public int nodeCount() {
    return child.nodeCount() + 1;
  }

  @Override
  public void collectAnswerIndices(Set<Integer> indices) {
    child.collectAnswerIndices(indices);
  }

  @Override
  public void collectPapiPlaceholders(Set<String> placeholders) {
    child.collectPapiPlaceholders(placeholders);
  }

  @Override
  public String toString() {
    return "!" + child;
  }
}
