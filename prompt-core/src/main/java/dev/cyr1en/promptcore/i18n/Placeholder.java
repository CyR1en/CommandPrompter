package dev.cyr1en.promptcore.i18n;

/**
 * A simple key-value pair used for {@code %key%} substitution in i18n messages.
 *
 * <p>Usage: {@code Placeholder.of("version", "3.0.0")} produces a placeholder that replaces {@code
 * %version%} with {@code 3.0.0} in any message string.
 */
public record Placeholder(String key, String value) {

  /**
   * Creates a new {@link Placeholder} for the given key and value.
   *
   * @param key the placeholder key (without the surrounding {@code %} delimiters)
   * @param value the replacement value
   * @return a new {@link Placeholder} instance
   */
  public static Placeholder of(String key, String value) {
    return new Placeholder(key, value);
  }
}
