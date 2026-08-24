package dev.cyr1en.promptcore;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validates and formats injection-safe output tokens for item prompts.
 *
 * <p>Enforces strict whitelists and bounds according to specification §2.3 and §7.1(4):
 *
 * <ul>
 *   <li>{@code key}: {@code ^[a-z0-9_.:\-]+$}
 *   <li>{@code material}: {@code ^[A-Z0-9_]+$}
 *   <li>{@code slot}: integer {@code 0}–{@code 40}
 *   <li>{@code amount}: unsigned decimal integer {@code 1}–{@code 1024} (digits only, no signs)
 * </ul>
 *
 * <p>Fails closed without truncation or coercion.
 */
public final class ItemTokenFormatter {

  public static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_.:\\-]+$");
  public static final Pattern MATERIAL_PATTERN = Pattern.compile("^[A-Z0-9_]+$");
  public static final Pattern DIGITS_PATTERN = Pattern.compile("^[0-9]+$");

  public static final int MIN_SLOT = 0;
  public static final int MAX_SLOT = 40;
  public static final int MIN_AMOUNT = 1;
  public static final int MAX_AMOUNT = 1024;

  private ItemTokenFormatter() {}

  /**
   * Validates and returns a namespaced item key token.
   *
   * @param key the key string (e.g. "minecraft:diamond_sword")
   * @return the valid key
   * @throws IllegalArgumentException if key is null or does not match the key whitelist
   */
  public static String formatKey(String key) {
    if (key == null || !KEY_PATTERN.matcher(key).matches()) {
      throw new IllegalArgumentException("Invalid item key token: " + key);
    }
    return key;
  }

  /**
   * Checks whether the key matches the item key whitelist.
   *
   * @param key the key string to test
   * @return true if valid, false otherwise
   */
  public static boolean isValidKey(String key) {
    return key != null && KEY_PATTERN.matcher(key).matches();
  }

  /**
   * Validates and returns a Bukkit Material name token.
   *
   * @param material the material string (e.g. "DIAMOND_SWORD")
   * @return the valid material
   * @throws IllegalArgumentException if material is null or does not match the material whitelist
   */
  public static String formatMaterial(String material) {
    if (material == null || !MATERIAL_PATTERN.matcher(material).matches()) {
      throw new IllegalArgumentException("Invalid item material token: " + material);
    }
    return material;
  }

  /**
   * Checks whether the material matches the material whitelist.
   *
   * @param material the material string to test
   * @return true if valid, false otherwise
   */
  public static boolean isValidMaterial(String material) {
    return material != null && MATERIAL_PATTERN.matcher(material).matches();
  }

  /**
   * Validates and formats a logical player slot index into its canonical string representation.
   *
   * @param slot the slot index (0..40)
   * @return the string token
   * @throws IllegalArgumentException if slot is out of bounds [0, 40]
   */
  public static String formatSlot(int slot) {
    if (slot < MIN_SLOT || slot > MAX_SLOT) {
      throw new IllegalArgumentException(
          "Slot index out of bounds [" + MIN_SLOT + ", " + MAX_SLOT + "]: " + slot);
    }
    return String.valueOf(slot);
  }

  /**
   * Validates a slot token string against digits-only and bounds [0, 40].
   *
   * @param slot the slot string
   * @return the valid slot token
   * @throws IllegalArgumentException if slot is invalid or out of bounds
   */
  public static String formatSlot(String slot) {
    if (slot == null || !DIGITS_PATTERN.matcher(slot).matches()) {
      throw new IllegalArgumentException("Invalid slot token: " + slot);
    }
    try {
      int val = Integer.parseInt(slot);
      if (val < MIN_SLOT || val > MAX_SLOT) {
        throw new IllegalArgumentException(
            "Slot index out of bounds [" + MIN_SLOT + ", " + MAX_SLOT + "]: " + val);
      }
      return slot;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Slot index out of bounds: " + slot);
    }
  }

  /**
   * Checks whether a slot index is within bounds [0, 40].
   *
   * @param slot the slot index
   * @return true if valid, false otherwise
   */
  public static boolean isValidSlot(int slot) {
    return slot >= MIN_SLOT && slot <= MAX_SLOT;
  }

  /**
   * Checks whether a slot string is digits-only and within bounds [0, 40].
   *
   * @param slot the slot string
   * @return true if valid, false otherwise
   */
  public static boolean isValidSlot(String slot) {
    if (slot == null || !DIGITS_PATTERN.matcher(slot).matches()) return false;
    try {
      int val = Integer.parseInt(slot);
      return val >= MIN_SLOT && val <= MAX_SLOT;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Validates and formats a stack amount into its canonical string representation.
   *
   * @param amount the stack amount (1..1024)
   * @return the string token
   * @throws IllegalArgumentException if amount is out of bounds [1, 1024]
   */
  public static String formatAmount(int amount) {
    if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
      throw new IllegalArgumentException(
          "Item amount out of bounds [" + MIN_AMOUNT + ", " + MAX_AMOUNT + "]: " + amount);
    }
    return String.valueOf(amount);
  }

  /**
   * Validates an amount token string against digits-only and bounds [1, 1024].
   *
   * @param amount the amount string
   * @return the valid amount token
   * @throws IllegalArgumentException if amount is invalid or out of bounds
   */
  public static String formatAmount(String amount) {
    if (amount == null || !DIGITS_PATTERN.matcher(amount).matches()) {
      throw new IllegalArgumentException("Invalid item amount token: " + amount);
    }
    try {
      int val = Integer.parseInt(amount);
      if (val < MIN_AMOUNT || val > MAX_AMOUNT) {
        throw new IllegalArgumentException(
            "Item amount out of bounds [" + MIN_AMOUNT + ", " + MAX_AMOUNT + "]: " + val);
      }
      return amount;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Item amount out of bounds: " + amount);
    }
  }

  /**
   * Checks whether an amount is within bounds [1, 1024].
   *
   * @param amount the stack amount
   * @return true if valid, false otherwise
   */
  public static boolean isValidAmount(int amount) {
    return amount >= MIN_AMOUNT && amount <= MAX_AMOUNT;
  }

  /**
   * Checks whether an amount string is digits-only and within bounds [1, 1024].
   *
   * @param amount the amount string
   * @return true if valid, false otherwise
   */
  public static boolean isValidAmount(String amount) {
    if (amount == null || !DIGITS_PATTERN.matcher(amount).matches()) return false;
    try {
      int val = Integer.parseInt(amount);
      return val >= MIN_AMOUNT && val <= MAX_AMOUNT;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Validates a token according to the specified {@link ItemOutputFormat}.
   *
   * @param format the expected output format
   * @param token the token string to validate
   * @return the token if valid
   * @throws NullPointerException if format or token is null
   * @throws IllegalArgumentException if token does not match the whitelist for format
   */
  public static String validateToken(ItemOutputFormat format, String token) {
    Objects.requireNonNull(format, "format cannot be null");
    Objects.requireNonNull(token, "token cannot be null");
    return switch (format) {
      case KEY -> formatKey(token);
      case MATERIAL -> formatMaterial(token);
      case SLOT -> formatSlot(token);
      case AMOUNT -> formatAmount(token);
    };
  }

  /**
   * Checks whether a token is valid according to the specified {@link ItemOutputFormat}.
   *
   * @param format the output format
   * @param token the token to test
   * @return true if valid, false otherwise
   */
  public static boolean isValidToken(ItemOutputFormat format, String token) {
    if (format == null || token == null) return false;
    return switch (format) {
      case KEY -> isValidKey(token);
      case MATERIAL -> isValidMaterial(token);
      case SLOT -> isValidSlot(token);
      case AMOUNT -> isValidAmount(token);
    };
  }
}
