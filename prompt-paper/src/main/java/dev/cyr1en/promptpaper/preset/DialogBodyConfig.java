package dev.cyr1en.promptpaper.preset;

import java.util.Objects;

/**
 * Represents one element of a {@link DialogPrompt}'s body — either a plain message or an
 * item icon — mapping to a single entry in the JSON {@code base.body} array.
 *
 * <p>Field applicability depends on {@link #type()}:
 *
 * <ul>
 *   <li>{@link DialogBodyType#PLAIN_MESSAGE}: {@code content} is meaningful; {@code material}
 *       and {@code amount} are ignored.
 *   <li>{@link DialogBodyType#ITEM}: {@code material} and {@code amount} are meaningful;
 *       {@code content} is ignored.
 * </ul>
 *
 * @param type the kind of body element
 * @param content the text for {@link DialogBodyType#PLAIN_MESSAGE}, otherwise {@code null}
 * @param material the Bukkit {@code Material} name for {@link DialogBodyType#ITEM}, otherwise
 *     {@code null}
 * @param amount the stack size for {@link DialogBodyType#ITEM}; coerced to {@code 1} when null
 */
public record DialogBodyConfig(
    DialogBodyType type, String content, String material, Integer amount) {

  /** Canonical constructor. Enforces non-null {@code type} and {@code amount >= 1}. */
  public DialogBodyConfig {
    Objects.requireNonNull(type, "type must not be null");
    if (amount == null) {
      amount = 1;
    }
    if (amount < 1) {
      throw new IllegalArgumentException("amount must be >= 1, got: " + amount);
    }
  }
}
