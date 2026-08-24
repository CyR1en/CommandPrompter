package dev.cyr1en.promptcore.logic.transform;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MathTransformerTest {

  @Test
  void flow01_mathScalingMultiplication() {
    MathTransformer transformer = MathTransformer.parse("*1.5");
    SingleTransformResult result = transformer.transform("20", MathMode.LEGACY);

    assertTrue(result.isSuccess());
    assertEquals("30.00", result.value());
  }

  @Test
  void flow02_legacyDivisionByZero_substitutesZeroWithNotice() {
    MathTransformer transformer = MathTransformer.parse("/0");
    SingleTransformResult result = transformer.transform("100", MathMode.LEGACY);

    assertTrue(result.isSuccess());
    assertEquals("0", result.value());
    assertEquals(1, result.notices().size());
    assertEquals(TransformNotice.DIVISION_BY_ZERO_SUBSTITUTED, result.notices().get(0));
  }

  @Test
  void flow02_strictDivisionByZero_failsClosed() {
    MathTransformer transformer = MathTransformer.parse("/0");
    SingleTransformResult result = transformer.transform("100", MathMode.STRICT);

    assertTrue(result.isFailure());
    assertEquals(TransformErrorCode.DIVISION_BY_ZERO, result.error().code());
  }

  @Test
  void flow09_nonNumericInput_failsClosed() {
    MathTransformer transformer = MathTransformer.parse("+10");

    SingleTransformResult r1 = transformer.transform("abc", MathMode.LEGACY);
    assertTrue(r1.isFailure());
    assertEquals(TransformErrorCode.NON_NUMERIC_INPUT, r1.error().code());

    SingleTransformResult r2 = transformer.transform("10.20.30", MathMode.LEGACY);
    assertTrue(r2.isFailure());
    assertEquals(TransformErrorCode.NON_NUMERIC_INPUT, r2.error().code());

    SingleTransformResult r3 = transformer.transform("", MathMode.LEGACY);
    assertTrue(r3.isFailure());
    assertEquals(TransformErrorCode.NON_NUMERIC_INPUT, r3.error().code());

    SingleTransformResult r4 = transformer.transform(null, MathMode.LEGACY);
    assertTrue(r4.isFailure());
    assertEquals(TransformErrorCode.NON_NUMERIC_INPUT, r4.error().code());
  }

  @Test
  void flow09_magnitudeExceeded_failsClosed() {
    MathTransformer transformer = MathTransformer.parse("+1");

    SingleTransformResult r1 = transformer.transform("1000000000001", MathMode.LEGACY);
    assertTrue(r1.isFailure());
    assertEquals(TransformErrorCode.MAGNITUDE_EXCEEDED, r1.error().code());

    MathTransformer mulTransformer = MathTransformer.parse("*1000000");
    SingleTransformResult r2 = mulTransformer.transform("10000000", MathMode.LEGACY);
    assertTrue(r2.isFailure());
    assertEquals(TransformErrorCode.MAGNITUDE_EXCEEDED, r2.error().code());
  }

  @Test
  void flow09_expressionTooLong_rejectedAtParse() {
    String longExpr = "+1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1 +1";
    assertTrue(longExpr.length() > 64);

    TransformException ex =
        assertThrows(TransformException.class, () -> MathTransformer.parse(longExpr));
    assertEquals(TransformErrorCode.MATH_EXPRESSION_TOO_LONG, ex.code());
  }

  @Test
  void flow09_tooManyOperations_rejectedAtParse() {
    // 9 operations within 64 chars: "+1 +1 +1 +1 +1 +1 +1 +1 +1" (26 chars)
    String nineOps = "+1 +1 +1 +1 +1 +1 +1 +1 +1";
    assertTrue(nineOps.length() <= 64);

    TransformException ex =
        assertThrows(TransformException.class, () -> MathTransformer.parse(nineOps));
    assertEquals(TransformErrorCode.TOO_MANY_OPERATIONS, ex.code());
  }

  @Test
  void leftToRightPrecedence() {
    // (10 + 5) * 2 = 30.00
    MathTransformer transformer = MathTransformer.parse("+5 *2");
    SingleTransformResult result = transformer.transform("10", MathMode.LEGACY);

    assertTrue(result.isSuccess());
    assertEquals("30.00", result.value());
  }

  @Test
  void scaleAndFormattingRules() {
    // Exact integers pad to min scale 2
    assertEquals("50.00", MathTransformer.parse("+30").transform("20", MathMode.LEGACY).value());
    assertEquals("0.00", MathTransformer.parse("-20").transform("20", MathMode.LEGACY).value());
    assertEquals("-5.00", MathTransformer.parse("-10").transform("5", MathMode.LEGACY).value());

    // Single decimal digit pads to min scale 2
    assertEquals("30.50", MathTransformer.parse("+10.5").transform("20", MathMode.LEGACY).value());

    // Scale up to 4 decimals preserved
    assertEquals("3.3333", MathTransformer.parse("/3").transform("10", MathMode.LEGACY).value());
    assertEquals("1.25", MathTransformer.parse("/8").transform("10", MathMode.LEGACY).value());
    assertEquals("0.0625", MathTransformer.parse("/16").transform("1", MathMode.LEGACY).value());
  }

  @Test
  void negativeAndDecimalOperands() {
    MathTransformer transformer = MathTransformer.parse("--5.5 +-2.0");
    SingleTransformResult result = transformer.transform("10", MathMode.LEGACY);

    assertTrue(result.isSuccess());
    // 10 - (-5.5) + (-2.0) = 15.5 - 2.0 = 13.50
    assertEquals("13.50", result.value());
  }

  @Test
  void malformedMathSyntax_rejected() {
    assertThrows(TransformException.class, () -> MathTransformer.parse(""));
    assertThrows(TransformException.class, () -> MathTransformer.parse("   "));
    assertThrows(TransformException.class, () -> MathTransformer.parse("+"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("-"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("*"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("/"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("+-"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("--"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("+abc"));
    assertThrows(
        TransformException.class,
        () -> MathTransformer.parse("+10-2")); // Missing whitespace between ops
    assertThrows(
        TransformException.class,
        () -> MathTransformer.parse("+10+2")); // Missing whitespace between ops
    assertThrows(
        TransformException.class,
        () -> MathTransformer.parse("+10*2")); // Missing whitespace between ops
    assertThrows(
        TransformException.class,
        () -> MathTransformer.parse("+10/2")); // Missing whitespace between ops
    assertThrows(
        TransformException.class,
        () -> MathTransformer.parse("-5.5-2.0")); // Missing whitespace between ops
    assertThrows(TransformException.class, () -> MathTransformer.parse("(1 + 2)"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("+1."));
    assertThrows(TransformException.class, () -> MathTransformer.parse("+.5"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("++5"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("+--5"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("+1.2.3"));
  }

  @Test
  void strictGrammar_whitespaceRules() {
    // Leading whitespace rejected
    assertThrows(TransformException.class, () -> MathTransformer.parse(" +5"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("  *2"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("   -1"));

    // Trailing whitespace rejected
    assertThrows(TransformException.class, () -> MathTransformer.parse("+5 "));
    assertThrows(TransformException.class, () -> MathTransformer.parse("+5 *2 "));
    assertThrows(TransformException.class, () -> MathTransformer.parse("+5 *2   "));
    assertThrows(TransformException.class, () -> MathTransformer.parse("-10.5 "));

    // Whitespace between operator and operand rejected
    assertThrows(TransformException.class, () -> MathTransformer.parse("* 1.5"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("- -5.5"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("+ 10"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("/ 2"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("+ -2.0"));
    assertThrows(TransformException.class, () -> MathTransformer.parse("--5.5 + -2.0"));

    // C0 control characters rejected with CONTROL_CHARACTER_DETECTED
    TransformException exTab =
        assertThrows(TransformException.class, () -> MathTransformer.parse("+5\t*2"));
    assertEquals(TransformErrorCode.CONTROL_CHARACTER_DETECTED, exTab.code());

    TransformException exNewline =
        assertThrows(TransformException.class, () -> MathTransformer.parse("+5\n*2"));
    assertEquals(TransformErrorCode.CONTROL_CHARACTER_DETECTED, exNewline.code());

    TransformException exCr =
        assertThrows(TransformException.class, () -> MathTransformer.parse("+5\r*2"));
    assertEquals(TransformErrorCode.CONTROL_CHARACTER_DETECTED, exCr.code());

    // Valid whitespace between complete operations (one or more spaces)
    assertDoesNotThrow(() -> MathTransformer.parse("+5 *2"));
    assertDoesNotThrow(() -> MathTransformer.parse("+5   *2"));
    assertDoesNotThrow(() -> MathTransformer.parse("--5.5 +-2.0"));
    assertDoesNotThrow(() -> MathTransformer.parse("+-5 *-2.5 /-4"));
  }
}
