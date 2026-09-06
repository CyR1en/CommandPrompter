package dev.cyr1en.promptpaper.config;

import dev.cyr1en.promptcore.BuiltInPromptType;

/**
 * Identifies the screen implementation used for a prompt.
 *
 * <p>Mapped from the {@code screen-mappings} section of {@code prompt-config.yml}.
 */
public enum ScreenType {
  CHAT,
  ANVIL,
  SIGN,
  DIALOG,
  PLAYER,
  CONFIRMATION,
  ITEM;

  /** Resolves a canonical built-in prompt key, or returns {@code null} for a custom key. */
  public static ScreenType fromBuiltInKey(String key) {
    return BuiltInPromptType.resolve(key).map(type -> ScreenType.valueOf(type.name())).orElse(null);
  }
}
