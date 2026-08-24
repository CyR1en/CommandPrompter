package dev.cyr1en.promptcore.logic.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TransformPropertyTest {

  private static final Pattern CANONICAL_DECIMAL = Pattern.compile("^-?\\d+\\.\\d{2,4}$");

  @Test
  void localeDeterminismAcrossMultipleLocales() {
    Locale originalLocale = Locale.getDefault();
    List<Locale> testLocales =
        List.of(
            Locale.of("tr", "TR"),
            Locale.of("az", "AZ"),
            Locale.of("de", "DE"),
            Locale.of("fr", "FR"),
            Locale.of("ja", "JP"),
            Locale.of("ar", "SA"),
            Locale.US);

    try {
      for (Locale locale : testLocales) {
        Locale.setDefault(locale);

        // String transformations
        assertEquals(
            "TITLE", UpperTransformer.INSTANCE.transform("title", MathMode.LEGACY).value());
        assertEquals(
            "title", LowerTransformer.INSTANCE.transform("TITLE", MathMode.LEGACY).value());
        assertEquals(
            "Title", CapitalizeTransformer.INSTANCE.transform("tItLe", MathMode.LEGACY).value());

        // Math formatting with decimal points (should always use '.' not locale ',' comma)
        MathTransformer math = MathTransformer.parse("+1.5");
        SingleTransformResult mathResult = math.transform("10.5", MathMode.LEGACY);
        assertTrue(mathResult.isSuccess());
        assertEquals("12.00", mathResult.value());
      }
    } finally {
      Locale.setDefault(originalLocale);
    }
  }

  @Test
  void mathOutputsMatchCanonicalDecimalFormat() {
    Random random = new Random(42);

    for (int i = 0; i < 100; i++) {
      int initial = random.nextInt(10000) - 5000;
      int add = random.nextInt(1000) - 500;
      double mul = (random.nextInt(100) + 1) / 10.0;

      String expr = String.format(Locale.ROOT, "+%d *%.1f", add, mul);
      MathTransformer transformer = MathTransformer.parse(expr);

      SingleTransformResult result =
          transformer.transform(String.valueOf(initial), MathMode.LEGACY);
      assertTrue(result.isSuccess());

      String val = result.value();
      assertTrue(
          CANONICAL_DECIMAL.matcher(val).matches(),
          "Value '" + val + "' should match canonical decimal format");
      assertFalse(
          val.contains("e") || val.contains("E"), "Value must not contain exponent notation");
      assertFalse(val.contains("+"), "Value must not contain plus sign");
    }
  }

  @Test
  void mathLeftToRightOperations_respectScaleBounds() {
    // 1 / 3 = 0.3333, then * 3 = 0.9999
    MathTransformer transformer = MathTransformer.parse("/3 *3");
    SingleTransformResult result = transformer.transform("1", MathMode.LEGACY);

    assertTrue(result.isSuccess());
    assertEquals("0.9999", result.value());
  }

  @Test
  void roundProperty_exactHalvesRoundUp() {
    RoundTransformer round = RoundTransformer.INSTANCE;

    for (int i = -100; i <= 100; i++) {
      BigDecimal base = BigDecimal.valueOf(i);
      BigDecimal half = base.add(new BigDecimal("0.5"));

      SingleTransformResult res = round.transform(half.toPlainString(), MathMode.LEGACY);
      assertTrue(res.isSuccess());

      int expected = i >= 0 ? i + 1 : i; // HALF_UP rounds -2.5 to -3 (abs increases) -> wait!
      // In Java BigDecimal RoundingMode.HALF_UP:
      // "Rounds towards nearest neighbor unless both neighbors are equidistant, in which case round
      // up (away from zero)."
      // So -2.5 rounds away from zero to -3!
      int expectedHalfUp =
          BigDecimal.valueOf(i)
              .add(new BigDecimal("0.5"))
              .setScale(0, java.math.RoundingMode.HALF_UP)
              .intValue();
      assertEquals(String.valueOf(expectedHalfUp), res.value());
    }
  }
}
