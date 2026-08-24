package dev.cyr1en.promptcore;

import java.util.Locale;

/** Output format for item selector prompt (<i:...> / <item:...>). */
public enum ItemOutputFormat {
  KEY,
  MATERIAL,
  SLOT,
  AMOUNT;

  /**
   * Resolves an {@link ItemOutputFormat} from a flag alias string (case-insensitive).
   *
   * @param alias the alias string (e.g. "key", "material", "slot", "amount")
   * @return the resolved {@link ItemOutputFormat}
   * @throws IllegalArgumentException if alias is null, blank, or unknown
   */
  public static ItemOutputFormat fromAlias(String alias) {
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("Item output format alias cannot be null or blank");
    }
    return switch (alias.trim().toLowerCase(Locale.ROOT)) {
      case "key" -> KEY;
      case "material" -> MATERIAL;
      case "slot" -> SLOT;
      case "amount" -> AMOUNT;
      default -> throw new IllegalArgumentException("Unknown item output format: " + alias);
    };
  }

  /**
   * Convenience alias for {@link #fromAlias(String)}.
   *
   * @param str the alias string
   * @return the resolved {@link ItemOutputFormat}
   */
  public static ItemOutputFormat fromString(String str) {
    return fromAlias(str);
  }

  /**
   * Validates a token string against this output format's whitelist specification.
   *
   * @param token the token string to validate
   * @return the validated token
   * @throws IllegalArgumentException if the token violates the format whitelist
   */
  public String validate(String token) {
    return ItemTokenFormatter.validateToken(this, token);
  }

  /**
   * Checks whether a token string matches this output format's whitelist specification.
   *
   * @param token the token string to test
   * @return true if valid, false otherwise
   */
  public boolean isValid(String token) {
    return ItemTokenFormatter.isValidToken(this, token);
  }
}
