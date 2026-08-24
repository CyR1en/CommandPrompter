package dev.cyr1en.promptcore.logic.condition;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/** An operand representing a decimal numeric literal. */
public record NumberLiteralOperand(BigDecimal value, String raw) implements ValueOperand {

  public NumberLiteralOperand {
    Objects.requireNonNull(value, "value cannot be null");
    Objects.requireNonNull(raw, "raw representation cannot be null");
  }

  public NumberLiteralOperand(BigDecimal value) {
    this(value, value.toPlainString());
  }

  @Override
  public String resolve(ConditionBindings bindings) {
    return raw;
  }

  @Override
  public void collectAnswerIndices(Set<Integer> indices) {
    // No answer indices
  }

  @Override
  public void collectPapiPlaceholders(Set<String> placeholders) {
    // No PAPI placeholders
  }

  @Override
  public String toString() {
    return raw;
  }
}
