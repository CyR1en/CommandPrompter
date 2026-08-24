package dev.cyr1en.promptcore.logic.transform;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An individual arithmetic operation step consisting of an operator and a decimal operand.
 *
 * @param operator the arithmetic operator
 * @param operand the decimal operand
 */
public record MathOperation(MathOperator operator, BigDecimal operand) {

  public MathOperation {
    Objects.requireNonNull(operator, "operator must not be null");
    Objects.requireNonNull(operand, "operand must not be null");
  }
}
