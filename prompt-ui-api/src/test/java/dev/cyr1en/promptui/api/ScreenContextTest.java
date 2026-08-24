package dev.cyr1en.promptui.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScreenContextTest {

  @Test
  @DisplayName("Canonicalizes key to lowercase")
  void canonicalizesKeyToLowercase() {
    var context = new ScreenContext("EcoItem", "Pick weapon");
    assertEquals("ecoitem", context.key());
    assertEquals("Pick weapon", context.displayText());
    assertTrue(context.sanitize());
    assertTrue(context.flags().isEmpty());
  }

  @Test
  @DisplayName("Null key throws NullPointerException")
  void nullKeyThrowsNpe() {
    assertThrows(NullPointerException.class, () -> new ScreenContext(null, "Display"));
  }

  @Test
  @DisplayName("Null displayText throws NullPointerException")
  void nullDisplayTextThrowsNpe() {
    assertThrows(NullPointerException.class, () -> new ScreenContext("ecoitem", null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "123abc",
        "_item",
        "eco-item",
        "eco item",
        "eco@item",
        "this_is_a_very_long_key_exceeding_the_thirty_two_char_limit"
      })
  @DisplayName("Invalid keys throw IllegalArgumentException")
  void invalidKeysThrowException(String invalidKey) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ScreenContext(invalidKey, "Display"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "a",
        "ecoitem",
        "eco_item",
        "custom1",
        "region_selector_v2",
        "abcdefghijklmnopqrstuvwxyz012345"
      })
  @DisplayName("Valid keys matching ^[a-z][a-z0-9_]{0,31}$ succeed")
  void validKeysSucceed(String validKey) {
    var context = new ScreenContext(validKey, "Display");
    assertEquals(validKey.toLowerCase(), context.key());
  }

  @Test
  @DisplayName("Null flags map normalizes to empty immutable map")
  void nullFlagsNormalizesToEmptyMap() {
    var context = new ScreenContext("ecoitem", "Display", null, true);
    assertNotNull(context.flags());
    assertTrue(context.flags().isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> context.flags().put("k", "v"));
  }

  @Test
  @DisplayName("Flags map is defensively copied and immutable")
  void flagsMapIsDefensivelyCopied() {
    var mutableMap = new HashMap<String, String>();
    mutableMap.put("glow", "true");
    mutableMap.put("tier", "epic");

    var context = new ScreenContext("ecoitem", "Display", mutableMap, false);

    // Modify original map after construction
    mutableMap.put("glow", "false");
    mutableMap.put("new_flag", "foo");

    assertEquals("true", context.flags().get("glow"));
    assertFalse(context.flags().containsKey("new_flag"));
    assertFalse(context.sanitize());

    assertThrows(
        UnsupportedOperationException.class,
        () -> context.flags().put("k", "v"));
  }

  @Test
  @DisplayName("Flag helper methods work case-insensitively")
  void flagHelperMethods() {
    var flags = Map.of("glow", "true", "rarity", "legendary", "count", "5");
    var context = new ScreenContext("ecoitem", "Display", flags, true);

    // flag()
    assertTrue(context.flag("glow").isPresent());
    assertEquals("true", context.flag("glow").get());
    assertEquals("true", context.flag("GLOW").get());
    assertEquals("legendary", context.flag("Rarity").get());
    assertTrue(context.flag("missing").isEmpty());
    assertTrue(context.flag(null).isEmpty());

    // booleanFlag()
    assertTrue(context.booleanFlag("glow"));
    assertTrue(context.booleanFlag("GLOW"));
    assertFalse(context.booleanFlag("rarity"));
    assertFalse(context.booleanFlag("missing"));
    assertFalse(context.booleanFlag(null));

    // hasFlag()
    assertTrue(context.hasFlag("count"));
    assertTrue(context.hasFlag("COUNT"));
    assertFalse(context.hasFlag("missing"));
    assertFalse(context.hasFlag(null));

    // flagOrDefault()
    assertEquals("5", context.flagOrDefault("count", "1"));
    assertEquals("common", context.flagOrDefault("missing", "common"));
    assertEquals("common", context.flagOrDefault(null, "common"));
  }

  @Test
  @DisplayName("Convenience constructors set sensible defaults")
  void convenienceConstructors() {
    var c1 = new ScreenContext("ecoitem", "Hello");
    assertEquals("ecoitem", c1.key());
    assertEquals("Hello", c1.displayText());
    assertTrue(c1.flags().isEmpty());
    assertTrue(c1.sanitize());

    var c2 = new ScreenContext("ecoitem", "Hello", Map.of("k", "v"));
    assertEquals("ecoitem", c2.key());
    assertEquals("Hello", c2.displayText());
    assertEquals("v", c2.flag("k").orElseThrow());
    assertTrue(c2.sanitize());
  }
}
