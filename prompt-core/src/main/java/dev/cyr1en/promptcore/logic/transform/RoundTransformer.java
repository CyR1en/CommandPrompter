package dev.cyr1en.promptcore.logic.transform;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Transformer that rounds a bound numeric value to the nearest integer (scale 0, HALF_UP). */
public record RoundTransformer() implements Transformer {

  public static final RoundTransformer INSTANCE = new RoundTransformer();
  private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

  @Override
  public SingleTransformResult transform(String input, MathMode mathMode) {
    if (input == null) {
      return SingleTransformResult.failure(
          TransformErrorCode.NON_NUMERIC_INPUT, "Input to round must not be null");
    }
    String stripped = input.strip();
    if (!NUMERIC_PATTERN.matcher(stripped).matches()) {
      return SingleTransformResult.failure(
          TransformErrorCode.NON_NUMERIC_INPUT,
          "Input to round must be a valid signed decimal: " + input);
    }
    BigDecimal decimal;
    try {
      decimal = new BigDecimal(stripped);
    } catch (NumberFormatException e) {
      return SingleTransformResult.failure(
          TransformErrorCode.NON_NUMERIC_INPUT, "Invalid decimal format: " + input);
    }
    if (decimal.abs().compareTo(TransformLimits.MAX_MAGNITUDE) > 0) {
      return SingleTransformResult.failure(
          TransformErrorCode.MAGNITUDE_EXCEEDED,
          "Input magnitude exceeds " + TransformLimits.MAX_MAGNITUDE + ": " + input);
    }
    BigDecimal rounded = decimal.setScale(0, RoundingMode.HALF_UP);
    return SingleTransformResult.success(rounded.toPlainString());
  }

  @Override
  public String name() {
    return "round";
  }
}
