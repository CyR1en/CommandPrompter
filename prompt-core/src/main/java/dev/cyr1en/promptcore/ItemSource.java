package dev.cyr1en.promptcore;

import java.util.Locale;

/** Sourcing location for item selector prompt (<i:...> / <item:...>). */
public enum ItemSource {
  INVENTORY,
  HAND,
  ARMOR,
  CATALOG;

  /**
   * Resolves an {@link ItemSource} from a flag alias string (case-insensitive).
   *
   * @param alias the alias string (e.g. "inv", "inventory", "hand", "mainhand", "armor", "catalog")
   * @return the resolved {@link ItemSource}
   * @throws IllegalArgumentException if alias is null, blank, or unknown
   */
  public static ItemSource fromAlias(String alias) {
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("Item source alias cannot be null or blank");
    }
    return switch (alias.trim().toLowerCase(Locale.ROOT)) {
      case "inv", "inventory" -> INVENTORY;
      case "hand", "mainhand" -> HAND;
      case "armor" -> ARMOR;
      case "catalog" -> CATALOG;
      default -> throw new IllegalArgumentException("Unknown item source: " + alias);
    };
  }

  /**
   * Convenience alias for {@link #fromAlias(String)}.
   *
   * @param str the alias string
   * @return the resolved {@link ItemSource}
   */
  public static ItemSource fromString(String str) {
    return fromAlias(str);
  }
}
