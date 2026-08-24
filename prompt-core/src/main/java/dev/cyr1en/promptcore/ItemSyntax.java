package dev.cyr1en.promptcore;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable model representing parsed item prompt syntax (<i:...> / <item:...>).
 *
 * @param promptText the main prompt message to display
 * @param source the sourcing location for items (default INVENTORY)
 * @param outputFormat the output string token format (default KEY)
 * @param category the category filter for catalog mode ("all" by default if catalog, null
 *     otherwise)
 * @param soundKey optional namespaced sound key to play on open, or null for none
 */
public record ItemSyntax(
    String promptText,
    ItemSource source,
    ItemOutputFormat outputFormat,
    String category,
    String soundKey) {

  public ItemSyntax {
    Objects.requireNonNull(promptText, "promptText cannot be null");
    if (source == null) {
      source = ItemSource.INVENTORY;
    }
    if (outputFormat == null) {
      outputFormat = ItemOutputFormat.KEY;
    }
    if (soundKey != null) {
      ItemGrammar.validateSoundKey(soundKey);
    }
    if (source == ItemSource.CATALOG) {
      if (category == null || category.isBlank()) {
        category = "all";
      } else {
        ItemGrammar.validateCategory(category);
      }
      if (outputFormat == ItemOutputFormat.SLOT) {
        throw new IllegalArgumentException(
            "Output format 'slot' is not supported for catalog source");
      }
    } else {
      if (category != null) {
        throw new IllegalArgumentException(
            "Category filter '-cat:' is only valid for catalog source, but source was: " + source);
      }
    }
  }

  public Optional<String> optionalCategory() {
    return Optional.ofNullable(category);
  }

  public Optional<String> optionalSoundKey() {
    return Optional.ofNullable(soundKey);
  }

  public ItemOutputFormat output() {
    return outputFormat;
  }

  public ItemOutputFormat format() {
    return outputFormat;
  }
}
