package dev.cyr1en.promptcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ParserConfigTest {

  @Test
  void testFromArgumentRegexValid() {
    var config1 = ParserConfig.fromArgumentRegex("<.*?>");
    assertEquals("<", config1.opening());
    assertEquals(">", config1.closing());
    assertEquals("\\", config1.escape());

    var config2 = ParserConfig.fromArgumentRegex("{.*?}");
    assertEquals("{", config2.opening());
    assertEquals("}", config2.closing());
    assertEquals("\\", config2.escape());

    var config3 = ParserConfig.fromArgumentRegex("[...]");
    assertEquals("[", config3.opening());
    assertEquals("]", config3.closing());
    assertEquals("\\", config3.escape());

    var config4 = ParserConfig.fromArgumentRegex("(.*?)");
    assertEquals("(", config4.opening());
    assertEquals(")", config4.closing());
    assertEquals("\\", config4.escape());
  }

  @Test
  void testFromArgumentRegexInvalid() {
    assertThrows(IllegalArgumentException.class, () -> ParserConfig.fromArgumentRegex(null));
    assertThrows(IllegalArgumentException.class, () -> ParserConfig.fromArgumentRegex(""));
    assertThrows(IllegalArgumentException.class, () -> ParserConfig.fromArgumentRegex("  "));
    assertThrows(
        IllegalArgumentException.class, () -> ParserConfig.fromArgumentRegex("<>")); // length 2
    assertThrows(
        IllegalArgumentException.class, () -> ParserConfig.fromArgumentRegex("a")); // length 1
  }

  @Test
  void testFromArgumentRegexRoundTrip() {
    var config = ParserConfig.fromArgumentRegex("<.*?>");
    assertEquals(ParserConfig.ANGLE_BRACKETS, config);
  }

  @Test
  void testMultiCharacterDelimiters() {
    var config = new ParserConfig("{{", "}}", "%%");
    assertEquals("{{", config.opening());
    assertEquals("}}", config.closing());
    assertEquals("%%", config.escape());
  }

  @Test
  void testConstructorValidation() {
    assertThrows(NullPointerException.class, () -> new ParserConfig(null, ">", "\\"));
    assertThrows(NullPointerException.class, () -> new ParserConfig("<", null, "\\"));
    assertThrows(NullPointerException.class, () -> new ParserConfig("<", ">", null));
    assertThrows(IllegalArgumentException.class, () -> new ParserConfig("", ">", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new ParserConfig("<", "", "\\"));
    assertThrows(IllegalArgumentException.class, () -> new ParserConfig("<", ">", ""));
  }
}
