package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import dev.cyr1en.promptcore.ItemOutputFormat;
import dev.cyr1en.promptcore.ItemSource;
import dev.cyr1en.promptcore.TitleConfig;
import java.util.Objects;

/**
 * Item-selector prompt definition: displays an item selection interface (inventory, hand, armor, or
 * catalog).
 *
 * @param type the discriminator value, always {@code "item"}
 * @param id the unique identifier
 * @param promptText the main question or prompt shown to the player
 * @param source sourcing location for items (default {@link ItemSource#INVENTORY})
 * @param output token output format (default {@link ItemOutputFormat#KEY})
 * @param category category filter for catalog mode ("all" by default if catalog, null otherwise)
 * @param sound sound to play on open; may be {@code null}
 * @param sanitize whether to strip color codes from the player's input
 * @param titleDisplay optional title-wrapper config; {@code null} when not requested
 * @param timeout optional timeout in seconds; {@code null} when not specified
 */
public record ItemPrompt(
    String type,
    String id,
    @SerializedName("prompt_text") String promptText,
    ItemSource source,
    @SerializedName("output") ItemOutputFormat output,
    String category,
    String sound,
    boolean sanitize,
    @SerializedName("title_display") TitleConfig titleDisplay,
    Integer timeout)
    implements PromptDefinition {

  /**
   * Canonical constructor. Enforces {@code type == "item"}, non-null required fields, timeout
   * bounds [1, 3600], and core-compatible defaults and source/output/category invariants.
   */
  public ItemPrompt {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(promptText, "prompt_text must not be null");
    if (!"item".equals(type)) {
      throw new IllegalArgumentException("ItemPrompt.type must be \"item\", got: " + type);
    }
    if (timeout != null && (timeout < 1 || timeout > 3600)) {
      throw new IllegalArgumentException(
          "ItemPrompt.timeout must be between 1 and 3600, got: " + timeout);
    }
    if (sound != null) {
      dev.cyr1en.promptcore.ItemGrammar.validateSoundKey(sound);
    }
    if (source == null) {
      source = ItemSource.INVENTORY;
    }
    if (output == null) {
      output = ItemOutputFormat.KEY;
    }
    if (source == ItemSource.CATALOG) {
      if (category == null || category.isBlank()) {
        category = "all";
      } else {
        dev.cyr1en.promptcore.ItemGrammar.validateCategory(category);
      }
      if (output == ItemOutputFormat.SLOT) {
        throw new IllegalArgumentException(
            "Output format 'slot' is not supported for catalog source");
      }
    } else {
      if (category != null) {
        throw new IllegalArgumentException(
            "Category filter is only valid for catalog source, but source was: " + source);
      }
    }
  }

  /** Convenience constructor without timeout. */
  public ItemPrompt(
      String type,
      String id,
      String promptText,
      ItemSource source,
      ItemOutputFormat output,
      String category,
      String sound,
      boolean sanitize,
      TitleConfig titleDisplay) {
    this(type, id, promptText, source, output, category, sound, sanitize, titleDisplay, null);
  }

  /** Convenience constructor without title-wrapper and timeout. */
  public ItemPrompt(
      String type,
      String id,
      String promptText,
      ItemSource source,
      ItemOutputFormat output,
      String category,
      String sound,
      boolean sanitize) {
    this(type, id, promptText, source, output, category, sound, sanitize, null, null);
  }

  public ItemOutputFormat outputFormat() {
    return output;
  }

  public ItemOutputFormat format() {
    return output;
  }

  public String soundKey() {
    return sound;
  }
}
