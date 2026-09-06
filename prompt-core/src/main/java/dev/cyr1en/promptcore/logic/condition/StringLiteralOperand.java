package dev.cyr1en.promptcore.logic.condition;

import java.util.Objects;

/** An operand representing a quoted string literal. */
public record StringLiteralOperand(String value) implements ValueOperand {

  public StringLiteralOperand {
    Objects.requireNonNull(value, "value cannot be null");
    if (value.length() > MAX_OPERAND_LENGTH) {
      throw new IllegalArgumentException(
          "String literal length exceeds maximum limit of " + MAX_OPERAND_LENGTH);
    }
  }

  @Override
  public String resolve(ConditionBindings bindings) {
    return value;
  }

  @Override
  public String toString() {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
