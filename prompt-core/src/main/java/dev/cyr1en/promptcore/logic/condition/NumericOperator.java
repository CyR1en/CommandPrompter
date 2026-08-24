package dev.cyr1en.promptcore.logic.condition;

import java.util.Objects;

/**
 * Numeric comparison operators supported in condition expressions. Comparisons strictly require
 * both operands to be valid decimal numbers with no string coercion.
 */
public enum NumericOperator {
  EQUALS("=="),
  NOT_EQUALS("!="),
  LESS_THAN("<"),
  LESS_THAN_OR_EQUAL("<="),
  GREATER_THAN(">"),
  GREATER_THAN_OR_EQUAL(">=");

  private final String symbol;

  NumericOperator(String symbol) {
    this.symbol = symbol;
  }

  public String symbol() {
    return symbol;
  }

  public static NumericOperator fromSymbol(String symbol) {
    Objects.requireNonNull(symbol, "symbol cannot be null");
    for (NumericOperator op : values()) {
      if (op.symbol.equals(symbol)) {
        return op;
      }
    }
    throw new IllegalArgumentException("Unknown numeric operator symbol: " + symbol);
  }
}
