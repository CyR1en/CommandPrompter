package dev.cyr1en.promptcore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Canonical built-in prompt types and their accepted case-insensitive tag-key aliases. */
public enum BuiltInPromptType {
  CHAT(""),
  ANVIL("a", "anvil"),
  SIGN("s", "sign"),
  PLAYER("p", "player"),
  DIALOG("d", "dialog"),
  CONFIRMATION("c", "confirm", "confirmation"),
  ITEM("i", "item");

  private static final Map<String, BuiltInPromptType> BY_ALIAS;

  static {
    var aliases = new LinkedHashMap<String, BuiltInPromptType>();
    for (var type : values()) {
      for (var alias : type.aliases) aliases.put(alias, type);
    }
    BY_ALIAS = Map.copyOf(aliases);
  }

  private final Set<String> aliases;

  BuiltInPromptType(String... aliases) {
    this.aliases = Set.of(aliases);
  }

  public Set<String> aliases() {
    return aliases;
  }

  public static Set<String> allAliases() {
    return BY_ALIAS.keySet();
  }

  public static Optional<BuiltInPromptType> resolve(String key) {
    if (key == null) return Optional.empty();
    return Optional.ofNullable(BY_ALIAS.get(key.trim().toLowerCase(java.util.Locale.ROOT)));
  }
}
