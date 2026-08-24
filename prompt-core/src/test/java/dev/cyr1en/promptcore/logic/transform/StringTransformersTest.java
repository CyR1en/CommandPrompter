package dev.cyr1en.promptcore.logic.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class StringTransformersTest {

  @Test
  void upperTransformer_convertsToUppercase() {
    UpperTransformer upper = UpperTransformer.INSTANCE;

    assertEquals("HELLO WORLD", upper.transform("hello world", MathMode.LEGACY).value());
    assertEquals("ALREADY UPPER", upper.transform("ALREADY UPPER", MathMode.LEGACY).value());
    assertEquals("", upper.transform("", MathMode.LEGACY).value());
    assertEquals("", upper.transform(null, MathMode.LEGACY).value());
  }

  @Test
  void upperTransformer_localeIndependence() {
    Locale defaultLocale = Locale.getDefault();
    try {
      // In Turkish, lower 'i' upper-cases to 'İ' (U+0130) under tr-TR, but 'I' under ROOT
      Locale.setDefault(Locale.of("tr", "TR"));
      UpperTransformer upper = UpperTransformer.INSTANCE;
      assertEquals("TITLE", upper.transform("title", MathMode.LEGACY).value());
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }

  @Test
  void lowerTransformer_convertsToLowercase() {
    LowerTransformer lower = LowerTransformer.INSTANCE;

    assertEquals("hello world", lower.transform("HELLO WORLD", MathMode.LEGACY).value());
    assertEquals("already lower", lower.transform("already lower", MathMode.LEGACY).value());
    assertEquals("", lower.transform("", MathMode.LEGACY).value());
    assertEquals("", lower.transform(null, MathMode.LEGACY).value());
  }

  @Test
  void lowerTransformer_localeIndependence() {
    Locale defaultLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.of("tr", "TR"));
      LowerTransformer lower = LowerTransformer.INSTANCE;
      assertEquals("title", lower.transform("TITLE", MathMode.LEGACY).value());
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }

  @Test
  void capitalizeTransformer_capitalizesFirstCodePointAndLowercasesRest() {
    CapitalizeTransformer cap = CapitalizeTransformer.INSTANCE;

    assertEquals("Hello", cap.transform("hello", MathMode.LEGACY).value());
    assertEquals("Hello", cap.transform("HELLO", MathMode.LEGACY).value());
    assertEquals("Hello world", cap.transform("HELLO WORLD", MathMode.LEGACY).value());
    assertEquals("H", cap.transform("h", MathMode.LEGACY).value());
    assertEquals("H", cap.transform("H", MathMode.LEGACY).value());
    assertEquals("", cap.transform("", MathMode.LEGACY).value());
    assertEquals("", cap.transform(null, MathMode.LEGACY).value());
  }

  @Test
  void trimTransformer_stripsWhitespace() {
    TrimTransformer trim = TrimTransformer.INSTANCE;

    assertEquals("hello", trim.transform("  hello  ", MathMode.LEGACY).value());
    assertEquals("hello world", trim.transform("\t hello world \n", MathMode.LEGACY).value());
    assertEquals("", trim.transform("   ", MathMode.LEGACY).value());
    assertEquals("", trim.transform("", MathMode.LEGACY).value());
    assertEquals("", trim.transform(null, MathMode.LEGACY).value());
  }

  @Test
  void stripColorTransformer_stripsLegacyAndHexAndMiniMessage() {
    StripColorTransformer stripColor = StripColorTransformer.INSTANCE;

    // Legacy & and §
    assertEquals("Hello", stripColor.transform("&cHello", MathMode.LEGACY).value());
    assertEquals("World", stripColor.transform("§aWorld", MathMode.LEGACY).value());
    assertEquals("Bold", stripColor.transform("&l&cBold", MathMode.LEGACY).value());

    // Hex colors
    assertEquals("Hex", stripColor.transform("&x&f&f&0&0&0&0Hex", MathMode.LEGACY).value());
    assertEquals("Hex2", stripColor.transform("&#ff0000Hex2", MathMode.LEGACY).value());
    assertEquals("Hex3", stripColor.transform("§#abcdefHex3", MathMode.LEGACY).value());

    // MiniMessage tags
    assertEquals("Plain", stripColor.transform("<red>Plain</red>", MathMode.LEGACY).value());
    assertEquals(
        "Complex",
        stripColor
            .transform("<gradient:#ff0000:#00ff00>Complex</gradient>", MathMode.LEGACY)
            .value());
    assertEquals(
        "Nested",
        stripColor
            .transform(
                "<gold><b><click:run_command:/test>Nested</click></b></gold>", MathMode.LEGACY)
            .value());
  }

  @Test
  void defaultTransformer_substitutesWhenBlank() {
    DefaultTransformer def = new DefaultTransformer("fallback");

    assertEquals("provided", def.transform("provided", MathMode.LEGACY).value());
    assertEquals("fallback", def.transform("", MathMode.LEGACY).value());
    assertEquals("fallback", def.transform("   ", MathMode.LEGACY).value());
    assertEquals("fallback", def.transform(null, MathMode.LEGACY).value());
  }

  @Test
  void roundTransformer_roundsHalfUp() {
    RoundTransformer round = RoundTransformer.INSTANCE;

    assertEquals("20", round.transform("20.4", MathMode.LEGACY).value());
    assertEquals("21", round.transform("20.5", MathMode.LEGACY).value());
    assertEquals("21", round.transform("20.6", MathMode.LEGACY).value());
    assertEquals("-20", round.transform("-20.4", MathMode.LEGACY).value());
    assertEquals("-21", round.transform("-20.5", MathMode.LEGACY).value());
    assertEquals("0", round.transform("0.0", MathMode.LEGACY).value());
    assertEquals("100", round.transform("100", MathMode.LEGACY).value());
  }

  @Test
  void roundTransformer_failsClosedOnNonNumeric() {
    RoundTransformer round = RoundTransformer.INSTANCE;

    var result1 = round.transform("abc", MathMode.LEGACY);
    assertTrue(result1.isFailure());
    assertEquals(TransformErrorCode.NON_NUMERIC_INPUT, result1.error().code());

    var result2 = round.transform("12.34.56", MathMode.LEGACY);
    assertTrue(result2.isFailure());
    assertEquals(TransformErrorCode.NON_NUMERIC_INPUT, result2.error().code());

    var result3 = round.transform("", MathMode.LEGACY);
    assertTrue(result3.isFailure());
    assertEquals(TransformErrorCode.NON_NUMERIC_INPUT, result3.error().code());

    var result4 = round.transform(null, MathMode.LEGACY);
    assertTrue(result4.isFailure());
    assertEquals(TransformErrorCode.NON_NUMERIC_INPUT, result4.error().code());
  }
}
