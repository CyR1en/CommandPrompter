package dev.cyr1en.promptui.api;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Immutable prompt context passed to a {@link PromptScreenFactory} when creating an {@link dev.cyr1en.promptui.InputScreen}.
 *
 * <p>Contains the canonical lowercase prompt key, the display text (with flags stripped), any parsed
 * arbitrary custom flags, and the answer sanitization toggle.
 *
 * <h2>Custom Flags</h2>
 *
 * <p>Custom prompt tags can specify arbitrary key-value flags at the end of the tag:
 * <pre>{@code <ecoitem:Pick weapon -glow -rarity:legendary -desc:"Super sword">}</pre>
 *
 * <p>These flags are parsed into the immutable {@link #flags()} map and can be accessed with helper
 * methods like {@link #flag(String)}, {@link #booleanFlag(String)}, or {@link #flagOrDefault(String, String)}.
 *
 * @param key the canonical lowercase prompt key (e.g., {@code "ecoitem"}); must match {@code ^[a-z][a-z0-9_]{0,31}$}
 * @param displayText the prompt display text shown to the user (non-null, may be empty)
 * @param flags immutable map of custom flag names to string values (non-null)
 * @param sanitize whether input answers should be sanitized (stripping color codes and special characters)
 * @since 3.3.0
 */
public record ScreenContext(
    String key,
    String displayText,
    Map<String, String> flags,
    boolean sanitize) {

  private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,31}$");

  /**
   * Compact constructor that validates non-null components, canonicalizes the key, and makes an
   * unmodifiable defensive copy of the flags map.
   *
   * @throws NullPointerException if {@code key} or {@code displayText} is null
   * @throws IllegalArgumentException if {@code key} does not match {@code ^[a-z][a-z0-9_]{0,31}$}
   */
  public ScreenContext {
    Objects.requireNonNull(key, "key cannot be null");
    Objects.requireNonNull(displayText, "displayText cannot be null");
    key = key.toLowerCase(Locale.ROOT);
    if (!KEY_PATTERN.matcher(key).matches()) {
      throw new IllegalArgumentException(
          "Invalid screen key '" + key + "': must match ^[a-z][a-z0-9_]{0,31}$");
    }
    flags = flags == null ? Map.of() : Map.copyOf(flags);
  }

  /**
   * Convenience constructor with default {@code sanitize = true} and empty flags.
   *
   * @param key the canonical screen key
   * @param displayText the prompt display text
   */
  public ScreenContext(String key, String displayText) {
    this(key, displayText, Map.of(), true);
  }

  /**
   * Convenience constructor with default {@code sanitize = true}.
   *
   * @param key the canonical screen key
   * @param displayText the prompt display text
   * @param flags custom prompt flags
   */
  public ScreenContext(String key, String displayText, Map<String, String> flags) {
    this(key, displayText, flags, true);
  }

  /**
   * Retrieves an optional custom flag value by case-insensitive name.
   *
   * @param name the flag name
   * @return an {@link Optional} containing the flag value if present, or {@link Optional#empty()}
   */
  public Optional<String> flag(String name) {
    if (name == null) return Optional.empty();
    return Optional.ofNullable(flags.get(name.toLowerCase(Locale.ROOT)));
  }

  /**
   * Retrieves a boolean flag value, returning {@code true} if present and equal to {@code "true"}
   * (case-insensitive).
   *
   * @param name the flag name
   * @return {@code true} if the flag is present and equals {@code "true"}, {@code false} otherwise
   */
  public boolean booleanFlag(String name) {
    return flag(name).map(Boolean::parseBoolean).orElse(false);
  }

  /**
   * Checks whether a custom flag is present by case-insensitive name.
   *
   * @param name the flag name
   * @return {@code true} if present, {@code false} otherwise
   */
  public boolean hasFlag(String name) {
    if (name == null) return false;
    return flags.containsKey(name.toLowerCase(Locale.ROOT));
  }

  /**
   * Retrieves a custom flag value or returns a default fallback if the flag is not set.
   *
   * @param name the flag name
   * @param defaultValue the fallback value if the flag is absent
   * @return the flag value if present, or {@code defaultValue}
   */
  public String flagOrDefault(String name, String defaultValue) {
    return flag(name).orElse(defaultValue);
  }
}
