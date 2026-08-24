package dev.cyr1en.promptcore.logic.transform;

/** Supported arithmetic operators in transformer math expressions. */
public enum MathOperator {
  ADD('+'),
  SUBTRACT('-'),
  MULTIPLY('*'),
  DIVIDE('/');

  private final char symbol;

  MathOperator(char symbol) {
    this.symbol = symbol;
  }

  public char symbol() {
    return symbol;
  }

  public static MathOperator fromChar(char c) {
    return switch (c) {
      case '+' -> ADD;
      case '-' -> SUBTRACT;
      case '*' -> MULTIPLY;
      case '/' -> DIVIDE;
      default -> throw new IllegalArgumentException("Unknown math operator: " + c);
    };
  }

  public static boolean isOperator(char c) {
    return c == '+' || c == '-' || c == '*' || c == '/';
  }
}
