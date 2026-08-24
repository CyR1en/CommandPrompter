package dev.cyr1en.promptcore;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ItemTokenFormatterTest {

  // ====================================================================
  // Key Whitelist Tests: ^[a-z0-9_.:\-]+$
  // ====================================================================

  @ParameterizedTest
  @ValueSource(
      strings = {
        "minecraft:diamond_sword",
        "minecraft:stone",
        "custom_plugin:special_item-123.v2",
        "diamond",
        "iron_ingot",
        "mod.name:item-sub_type:extra.1"
      })
  @DisplayName("Valid key tokens pass validation")
  void validKeyTokensPass(String key) {
    assertEquals(key, ItemTokenFormatter.formatKey(key));
    assertTrue(ItemTokenFormatter.isValidKey(key));
    assertEquals(key, ItemTokenFormatter.validateToken(ItemOutputFormat.KEY, key));
    assertTrue(ItemOutputFormat.KEY.isValid(key));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "Minecraft:diamond_sword",
        "minecraft:DIAMOND_SWORD",
        "minecraft:diamond sword", // space
        "minecraft:diamond;give", // command injection semicolon
        "minecraft:diamond<c:test>", // tag injection
        "minecraft:diamond\"quote", // quote injection
        "minecraft:diamond\nnewline", // newline
        "minecraft:diamond\r", // CR
        "minecraft:diamond\u0000", // NUL
        "minecraft:diamond$price", // symbol
        "minecraft:diamond@target", // symbol
        "minecraft:diamond{0}" // transformer ref
      })
  @DisplayName("Invalid key tokens fail closed")
  void invalidKeyTokensFailClosed(String key) {
    assertThrows(IllegalArgumentException.class, () -> ItemTokenFormatter.formatKey(key));
    assertFalse(ItemTokenFormatter.isValidKey(key));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemTokenFormatter.validateToken(ItemOutputFormat.KEY, key));
    assertFalse(ItemOutputFormat.KEY.isValid(key));
  }

  @Test
  @DisplayName("Null key fails closed")
  void nullKeyFailsClosed() {
    assertThrows(IllegalArgumentException.class, () -> ItemTokenFormatter.formatKey(null));
    assertFalse(ItemTokenFormatter.isValidKey(null));
  }

  // ====================================================================
  // Material Whitelist Tests: ^[A-Z0-9_]+$
  // ====================================================================

  @ParameterizedTest
  @ValueSource(
      strings = {
        "DIAMOND_SWORD",
        "STONE",
        "NETHERITE_UPGRADE_SMITHING_TEMPLATE",
        "DIRT_123",
        "RED_WOOL"
      })
  @DisplayName("Valid material tokens pass validation")
  void validMaterialTokensPass(String material) {
    assertEquals(material, ItemTokenFormatter.formatMaterial(material));
    assertTrue(ItemTokenFormatter.isValidMaterial(material));
    assertEquals(material, ItemTokenFormatter.validateToken(ItemOutputFormat.MATERIAL, material));
    assertTrue(ItemOutputFormat.MATERIAL.isValid(material));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "diamond_sword", // lowercase
        "Diamond_Sword", // mixed case
        "minecraft:DIAMOND_SWORD", // colon
        "DIAMOND SWORD", // space
        "DIAMOND-SWORD", // hyphen
        "DIAMOND;give", // semicolon
        "DIAMOND\"quote", // quote
        "DIAMOND\n", // newline
        "DIAMOND$1", // symbol
        "DIAMOND{0}" // transformer ref
      })
  @DisplayName("Invalid material tokens fail closed")
  void invalidMaterialTokensFailClosed(String material) {
    assertThrows(IllegalArgumentException.class, () -> ItemTokenFormatter.formatMaterial(material));
    assertFalse(ItemTokenFormatter.isValidMaterial(material));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemTokenFormatter.validateToken(ItemOutputFormat.MATERIAL, material));
    assertFalse(ItemOutputFormat.MATERIAL.isValid(material));
  }

  @Test
  @DisplayName("Null material fails closed")
  void nullMaterialFailsClosed() {
    assertThrows(IllegalArgumentException.class, () -> ItemTokenFormatter.formatMaterial(null));
    assertFalse(ItemTokenFormatter.isValidMaterial(null));
  }

  // ====================================================================
  // Slot Bounds Tests: integer 0–40
  // ====================================================================

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 8, 9, 26, 27, 35, 36, 39, 40})
  @DisplayName("Valid slot integer bounds [0, 40]")
  void validSlotIntBounds(int slot) {
    assertEquals(String.valueOf(slot), ItemTokenFormatter.formatSlot(slot));
    assertTrue(ItemTokenFormatter.isValidSlot(slot));
  }

  @ParameterizedTest
  @ValueSource(ints = {-10, -1, 41, 42, 100, 1024})
  @DisplayName("Out of bounds slot integers fail closed")
  void outOfBoundsSlotIntsFailClosed(int slot) {
    assertThrows(IllegalArgumentException.class, () -> ItemTokenFormatter.formatSlot(slot));
    assertFalse(ItemTokenFormatter.isValidSlot(slot));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "1", "8", "9", "26", "27", "35", "36", "39", "40"})
  @DisplayName("Valid slot string bounds [0, 40]")
  void validSlotStringBounds(String slot) {
    assertEquals(slot, ItemTokenFormatter.formatSlot(slot));
    assertTrue(ItemTokenFormatter.isValidSlot(slot));
    assertEquals(slot, ItemTokenFormatter.validateToken(ItemOutputFormat.SLOT, slot));
    assertTrue(ItemOutputFormat.SLOT.isValid(slot));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "-1", "+1", "41", "100", "0.0", "1a", " 1", "1 ", "1;give", "1\"quote", "1\n"})
  @DisplayName("Invalid slot strings fail closed")
  void invalidSlotStringsFailClosed(String slot) {
    assertThrows(IllegalArgumentException.class, () -> ItemTokenFormatter.formatSlot(slot));
    assertFalse(ItemTokenFormatter.isValidSlot(slot));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemTokenFormatter.validateToken(ItemOutputFormat.SLOT, slot));
    assertFalse(ItemOutputFormat.SLOT.isValid(slot));
  }

  // ====================================================================
  // Amount Bounds Tests: unsigned decimal integer 1–1024
  // ====================================================================

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 16, 32, 64, 100, 500, 1024})
  @DisplayName("Valid amount integer bounds [1, 1024]")
  void validAmountIntBounds(int amount) {
    assertEquals(String.valueOf(amount), ItemTokenFormatter.formatAmount(amount));
    assertTrue(ItemTokenFormatter.isValidAmount(amount));
  }

  @ParameterizedTest
  @ValueSource(ints = {-10, -1, 0, 1025, 2000, 99999})
  @DisplayName("Out of bounds amount integers fail closed")
  void outOfBoundsAmountIntsFailClosed(int amount) {
    assertThrows(IllegalArgumentException.class, () -> ItemTokenFormatter.formatAmount(amount));
    assertFalse(ItemTokenFormatter.isValidAmount(amount));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "2", "16", "32", "64", "100", "500", "1024"})
  @DisplayName("Valid amount string bounds [1, 1024]")
  void validAmountStringBounds(String amount) {
    assertEquals(amount, ItemTokenFormatter.formatAmount(amount));
    assertTrue(ItemTokenFormatter.isValidAmount(amount));
    assertEquals(amount, ItemTokenFormatter.validateToken(ItemOutputFormat.AMOUNT, amount));
    assertTrue(ItemOutputFormat.AMOUNT.isValid(amount));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "0",
        "-1",
        "+1",
        "1025",
        "64.0",
        "64a",
        " 64",
        "64 ",
        "64;give",
        "64\"quote",
        "64\n"
      })
  @DisplayName("Invalid amount strings fail closed")
  void invalidAmountStringsFailClosed(String amount) {
    assertThrows(IllegalArgumentException.class, () -> ItemTokenFormatter.formatAmount(amount));
    assertFalse(ItemTokenFormatter.isValidAmount(amount));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemTokenFormatter.validateToken(ItemOutputFormat.AMOUNT, amount));
    assertFalse(ItemOutputFormat.AMOUNT.isValid(amount));
  }

  // ====================================================================
  // ITEM-10: Property-Style Test for Output Token Charset & Non-Coercion
  // ====================================================================

  @Test
  @DisplayName(
      "ITEM-10: Property test - All valid outputs match whitelist and reject invalid without coercion")
  void item10_propertyTestOutputTokenWhitelists() {
    var random = new Random(42);

    // 1. Valid Slot range property
    for (int slot = 0; slot <= 40; slot++) {
      String formatted = ItemTokenFormatter.formatSlot(slot);
      assertEquals(String.valueOf(slot), formatted);
      assertTrue(ItemTokenFormatter.KEY_PATTERN.matcher(formatted).matches());
      assertTrue(ItemTokenFormatter.DIGITS_PATTERN.matcher(formatted).matches());
    }

    // 2. Valid Amount range property
    for (int amount = 1; amount <= 1024; amount++) {
      String formatted = ItemTokenFormatter.formatAmount(amount);
      assertEquals(String.valueOf(amount), formatted);
      assertTrue(ItemTokenFormatter.DIGITS_PATTERN.matcher(formatted).matches());
    }

    // 3. Random valid Keys
    String allowedKeyChars = "abcdefghijklmnopqrstuvwxyz0123456789_.:-";
    for (int i = 0; i < 100; i++) {
      int len = 1 + random.nextInt(30);
      var sb = new StringBuilder();
      for (int j = 0; j < len; j++) {
        sb.append(allowedKeyChars.charAt(random.nextInt(allowedKeyChars.length())));
      }
      String validKey = sb.toString();
      assertTrue(ItemTokenFormatter.isValidKey(validKey));
      assertEquals(validKey, ItemTokenFormatter.formatKey(validKey));
    }

    // 4. Random valid Materials
    String allowedMatChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
    for (int i = 0; i < 100; i++) {
      int len = 1 + random.nextInt(30);
      var sb = new StringBuilder();
      for (int j = 0; j < len; j++) {
        sb.append(allowedMatChars.charAt(random.nextInt(allowedMatChars.length())));
      }
      String validMat = sb.toString();
      assertTrue(ItemTokenFormatter.isValidMaterial(validMat));
      assertEquals(validMat, ItemTokenFormatter.formatMaterial(validMat));
    }

    // 5. Injections & corrupt inputs are never coerced or truncated
    List<String> injectionPayloads =
        List.of(
            "minecraft:diamond;op attacker",
            "minecraft:diamond\nexecute as @a",
            "minecraft:diamond\" && pkill java",
            "minecraft:diamond<c:fake>",
            "minecraft:diamond{0:upper}",
            "DIAMOND_SWORD;op",
            "DIAMOND_SWORD\r\n",
            "41;rm -rf /",
            "1025;grant",
            "0",
            "-5",
            "\"true\"",
            "'material'");

    for (String payload : injectionPayloads) {
      assertFalse(
          ItemTokenFormatter.isValidToken(ItemOutputFormat.KEY, payload)
              && ItemTokenFormatter.isValidToken(ItemOutputFormat.MATERIAL, payload)
              && ItemTokenFormatter.isValidToken(ItemOutputFormat.SLOT, payload)
              && ItemTokenFormatter.isValidToken(ItemOutputFormat.AMOUNT, payload),
          "Injection payload must not be accepted across all formats: " + payload);
    }
  }
}
