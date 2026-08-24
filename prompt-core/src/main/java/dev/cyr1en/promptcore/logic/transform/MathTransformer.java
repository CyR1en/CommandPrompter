package dev.cyr1en.promptcore.logic.transform;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Transformer that executes a bounded, deterministic list of arithmetic operations left-to-right on
 * a bound numeric input.
 *
 * <p>Add, subtract, and multiply use exact {@link BigDecimal} arithmetic, rounding to scale 4
 * HALF_UP when scale exceeds 4. Division uses scale 4 HALF_UP. Result magnitude is capped at 10^12.
 */
public record MathTransformer(String rawExpression, List<MathOperation> operations)
    implements Transformer {

  private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

  public MathTransformer {
    Objects.requireNonNull(rawExpression, "rawExpression must not be null");
    Objects.requireNonNull(operations, "operations must not be null");
    if (operations.isEmpty()) {
      throw new IllegalArgumentException("operations must not be empty");
    }
    if (operations.size() > TransformLimits.MAX_MATH_OPERATIONS) {
      throw new IllegalArgumentException(
          "operations count exceeds "
              + TransformLimits.MAX_MATH_OPERATIONS
              + ": "
              + operations.size());
    }
    operations = List.copyOf(operations);
  }

  public static MathTransformer parse(String rawExpr) {
    if (rawExpr == null) {
      throw new TransformException(
          TransformErrorCode.MATH_SYNTAX_ERROR, "Math expression must not be null");
    }
    if (rawExpr.length() > TransformLimits.MAX_MATH_EXPR_LENGTH) {
      throw new TransformException(
          TransformErrorCode.MATH_EXPRESSION_TOO_LONG,
          "Math expression length exceeds limit of "
              + TransformLimits.MAX_MATH_EXPR_LENGTH
              + ": "
              + rawExpr.length());
    }
    for (int idx = 0; idx < rawExpr.length(); idx++) {
      char c = rawExpr.charAt(idx);
      if (c < 0x20 || c == 0x7F) {
        throw new TransformException(
            TransformErrorCode.CONTROL_CHARACTER_DETECTED,
            "Control character detected in math expression at index " + idx);
      }
    }
    if (rawExpr.isEmpty()) {
      throw new TransformException(
          TransformErrorCode.MATH_SYNTAX_ERROR, "Math expression must not be empty");
    }

    List<MathOperation> ops = new ArrayList<>();
    int i = 0;
    int n = rawExpr.length();

    while (i < n) {
      char opChar = rawExpr.charAt(i);
      if (!MathOperator.isOperator(opChar)) {
        throw new TransformException(
            TransformErrorCode.MATH_SYNTAX_ERROR,
            "Expected math operator (+, -, *, /) at index " + i + " in: " + rawExpr);
      }
      MathOperator op = MathOperator.fromChar(opChar);
      i++;

      if (i >= n) {
        throw new TransformException(
            TransformErrorCode.MATH_SYNTAX_ERROR,
            "Operator " + op.symbol() + " missing operand at end of expression: " + rawExpr);
      }

      int operandStart = i;
      if (rawExpr.charAt(i) == '-') {
        i++;
        if (i >= n || !isDigit(rawExpr.charAt(i))) {
          throw new TransformException(
              TransformErrorCode.MATH_SYNTAX_ERROR,
              "Invalid negative operand at index " + operandStart + " in: " + rawExpr);
        }
      }

      if (i >= n || !isDigit(rawExpr.charAt(i))) {
        throw new TransformException(
            TransformErrorCode.MATH_SYNTAX_ERROR,
            "Expected digit at index " + i + " in: " + rawExpr);
      }

      while (i < n && isDigit(rawExpr.charAt(i))) {
        i++;
      }

      if (i < n && rawExpr.charAt(i) == '.') {
        i++;
        if (i >= n || !isDigit(rawExpr.charAt(i))) {
          throw new TransformException(
              TransformErrorCode.MATH_SYNTAX_ERROR,
              "Expected digit after decimal point at index " + (i - 1) + " in: " + rawExpr);
        }
        while (i < n && isDigit(rawExpr.charAt(i))) {
          i++;
        }
      }

      String operandStr = rawExpr.substring(operandStart, i);
      BigDecimal operand;
      try {
        operand = new BigDecimal(operandStr);
      } catch (NumberFormatException e) {
        throw new TransformException(
            TransformErrorCode.MATH_SYNTAX_ERROR, "Invalid operand: " + operandStr);
      }

      if (operand.abs().compareTo(TransformLimits.MAX_MAGNITUDE) > 0) {
        throw new TransformException(
            TransformErrorCode.MAGNITUDE_EXCEEDED,
            "Operand magnitude exceeds limit of "
                + TransformLimits.MAX_MAGNITUDE
                + ": "
                + operandStr);
      }

      ops.add(new MathOperation(op, operand));

      if (i < n) {
        if (rawExpr.charAt(i) != ' ') {
          throw new TransformException(
              TransformErrorCode.MATH_SYNTAX_ERROR,
              "Operations must be whitespace-separated at index " + i + " in: " + rawExpr);
        }
        while (i < n && rawExpr.charAt(i) == ' ') {
          i++;
        }
        if (i >= n) {
          throw new TransformException(
              TransformErrorCode.MATH_SYNTAX_ERROR,
              "Trailing whitespace is not allowed in: " + rawExpr);
        }
      }
    }

    if (ops.isEmpty()) {
      throw new TransformException(
          TransformErrorCode.MATH_SYNTAX_ERROR, "No operations found in: " + rawExpr);
    }

    if (ops.size() > TransformLimits.MAX_MATH_OPERATIONS) {
      throw new TransformException(
          TransformErrorCode.TOO_MANY_OPERATIONS,
          "Operation count exceeds limit of "
              + TransformLimits.MAX_MATH_OPERATIONS
              + ": "
              + ops.size());
    }

    return new MathTransformer(rawExpr, ops);
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  @Override
  public SingleTransformResult transform(String input, MathMode mathMode) {
    if (input == null) {
      return SingleTransformResult.failure(
          TransformErrorCode.NON_NUMERIC_INPUT, "Input to math must not be null");
    }
    String stripped = input.strip();
    if (!NUMERIC_PATTERN.matcher(stripped).matches()) {
      return SingleTransformResult.failure(
          TransformErrorCode.NON_NUMERIC_INPUT,
          "Input to math must be a valid signed decimal: " + input);
    }
    BigDecimal current;
    try {
      current = new BigDecimal(stripped);
    } catch (NumberFormatException e) {
      return SingleTransformResult.failure(
          TransformErrorCode.NON_NUMERIC_INPUT, "Invalid decimal format: " + input);
    }
    if (current.abs().compareTo(TransformLimits.MAX_MAGNITUDE) > 0) {
      return SingleTransformResult.failure(
          TransformErrorCode.MAGNITUDE_EXCEEDED,
          "Initial input magnitude exceeds " + TransformLimits.MAX_MAGNITUDE + ": " + input);
    }

    List<TransformNotice> notices = new ArrayList<>();

    for (MathOperation op : operations) {
      switch (op.operator()) {
        case ADD -> {
          current = current.add(op.operand());
          if (current.scale() > TransformLimits.MATH_SCALE) {
            current = current.setScale(TransformLimits.MATH_SCALE, RoundingMode.HALF_UP);
          }
          if (current.abs().compareTo(TransformLimits.MAX_MAGNITUDE) > 0) {
            return SingleTransformResult.failure(
                TransformErrorCode.MAGNITUDE_EXCEEDED,
                "Addition result magnitude exceeds " + TransformLimits.MAX_MAGNITUDE);
          }
        }
        case SUBTRACT -> {
          current = current.subtract(op.operand());
          if (current.scale() > TransformLimits.MATH_SCALE) {
            current = current.setScale(TransformLimits.MATH_SCALE, RoundingMode.HALF_UP);
          }
          if (current.abs().compareTo(TransformLimits.MAX_MAGNITUDE) > 0) {
            return SingleTransformResult.failure(
                TransformErrorCode.MAGNITUDE_EXCEEDED,
                "Subtraction result magnitude exceeds " + TransformLimits.MAX_MAGNITUDE);
          }
        }
        case MULTIPLY -> {
          current = current.multiply(op.operand());
          if (current.scale() > TransformLimits.MATH_SCALE) {
            current = current.setScale(TransformLimits.MATH_SCALE, RoundingMode.HALF_UP);
          }
          if (current.abs().compareTo(TransformLimits.MAX_MAGNITUDE) > 0) {
            return SingleTransformResult.failure(
                TransformErrorCode.MAGNITUDE_EXCEEDED,
                "Multiplication result magnitude exceeds " + TransformLimits.MAX_MAGNITUDE);
          }
        }
        case DIVIDE -> {
          if (op.operand().compareTo(BigDecimal.ZERO) == 0) {
            if (mathMode == MathMode.STRICT) {
              return SingleTransformResult.failure(
                  TransformErrorCode.DIVISION_BY_ZERO, "Division by zero in strict mode");
            } else {
              notices.add(TransformNotice.DIVISION_BY_ZERO_SUBSTITUTED);
              return SingleTransformResult.success("0", notices);
            }
          }
          current = current.divide(op.operand(), TransformLimits.MATH_SCALE, RoundingMode.HALF_UP);
          if (current.abs().compareTo(TransformLimits.MAX_MAGNITUDE) > 0) {
            return SingleTransformResult.failure(
                TransformErrorCode.MAGNITUDE_EXCEEDED,
                "Division result magnitude exceeds " + TransformLimits.MAX_MAGNITUDE);
          }
        }
      }
    }

    String formatted = formatMathResult(current);
    return SingleTransformResult.success(formatted, notices);
  }

  public static String formatMathResult(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    int targetScale = Math.max(2, stripped.scale());
    return stripped.setScale(targetScale, RoundingMode.UNNECESSARY).toPlainString();
  }

  @Override
  public String name() {
    return "math";
  }
}
