package dev.cyr1en.promptcore.logic.transform;

import java.math.BigDecimal;

/** Bounded constants and resource caps for template compilation and transformation. */
public final class TransformLimits {

  public static final int MAX_TEMPLATE_LENGTH = 1024;
  public static final int MAX_OUTPUT_LENGTH = 1024;
  public static final int MAX_INPUT_LENGTH = 1024;
  public static final int MAX_MATH_EXPR_LENGTH = 64;
  public static final int MAX_MATH_OPERATIONS = 8;
  public static final int MATH_SCALE = 4;
  public static final BigDecimal MAX_MAGNITUDE = new BigDecimal("1000000000000"); // 10^12

  private TransformLimits() {}
}
