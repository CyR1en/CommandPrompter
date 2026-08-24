package dev.cyr1en.promptcore.logic.condition;

import java.util.Objects;
import java.util.Set;

/**
 * AST leaf node representing a case-sensitive string comparison between two operands. Evaluated in
 * linear time without regular expression engines.
 */
public record StringComparisonNode(ValueOperand left, StringOperator operator, ValueOperand right)
    implements ConditionNode {

  public StringComparisonNode {
    Objects.requireNonNull(left, "left operand cannot be null");
    Objects.requireNonNull(operator, "operator cannot be null");
    Objects.requireNonNull(right, "right operand cannot be null");
  }

  @Override
  public boolean evaluate(ConditionBindings bindings) throws ConditionEvaluationException {
    String leftStr = left.resolve(bindings);
    String rightStr = right.resolve(bindings);

    return switch (operator) {
      case EQUALS -> leftStr.equals(rightStr);
      case CONTAINS -> leftStr.contains(rightStr);
      case STARTS_WITH -> leftStr.startsWith(rightStr);
      case ENDS_WITH -> leftStr.endsWith(rightStr);
    };
  }

  @Override
  public int depth() {
    return 1;
  }

  @Override
  public int nodeCount() {
    return 1;
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
    return left + " " + operator.keyword() + " " + right;
  }
}
