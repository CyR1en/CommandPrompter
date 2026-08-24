package dev.cyr1en.promptcore.logic.condition;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * AST leaf node representing a numeric comparison between two operands. Requires both operands to
 * evaluate to valid decimal numbers without string coercion.
 */
public record NumericComparisonNode(ValueOperand left, NumericOperator operator, ValueOperand right)
    implements ConditionNode {

  public NumericComparisonNode {
    Objects.requireNonNull(left, "left operand cannot be null");
    Objects.requireNonNull(operator, "operator cannot be null");
    Objects.requireNonNull(right, "right operand cannot be null");
  }

  @Override
  public boolean evaluate(ConditionBindings bindings) throws ConditionEvaluationException {
    String leftStr = left.resolve(bindings);
    String rightStr = right.resolve(bindings);

    BigDecimal leftNum;
    try {
      leftNum = new BigDecimal(leftStr.trim());
    } catch (NumberFormatException e) {
      throw new ConditionEvaluationException(
          "Cannot perform numeric comparison: left operand '"
              + leftStr
              + "' is not a valid decimal number",
          e);
    }

    BigDecimal rightNum;
    try {
      rightNum = new BigDecimal(rightStr.trim());
    } catch (NumberFormatException e) {
      throw new ConditionEvaluationException(
          "Cannot perform numeric comparison: right operand '"
              + rightStr
              + "' is not a valid decimal number",
          e);
    }

    int cmp = leftNum.compareTo(rightNum);
    return switch (operator) {
      case EQUALS -> cmp == 0;
      case NOT_EQUALS -> cmp != 0;
      case LESS_THAN -> cmp < 0;
      case LESS_THAN_OR_EQUAL -> cmp <= 0;
      case GREATER_THAN -> cmp > 0;
      case GREATER_THAN_OR_EQUAL -> cmp >= 0;
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
    return left + " " + operator.symbol() + " " + right;
  }
}
