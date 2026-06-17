package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * Anvil GUI button layout: the item placed in either slot of the anvil interface.
 *
 * <p>{@code buttonIcon} is stored as a raw string and is resolved to a Bukkit {@code Material}
 * later (in the paper module) with a fallback to {@code PAPER} on invalid input.
 *
 * @param show whether the button slot is rendered at all
 * @param buttonText the display name of the item
 * @param buttonIcon the Bukkit {@code Material} name (case-insensitive, may include the {@code
 *     minecraft:} prefix)
 * @param buttonHoverText the lore line(s) shown on hover
 * @param customModelData the integer custom-model-data tag applied to the item stack
 */
public record AnvilButton(
    boolean show,
    @SerializedName("button_text") String buttonText,
    @SerializedName("button_icon") String buttonIcon,
    @SerializedName("button_hover_text") String buttonHoverText,
    @SerializedName("custom_model_data") int customModelData) {

  /** Canonical constructor with null-checks; Gson supplies defaults for absent fields. */
  public AnvilButton {
    Objects.requireNonNull(buttonText, "button_text must not be null");
    Objects.requireNonNull(buttonIcon, "button_icon must not be null");
    Objects.requireNonNull(buttonHoverText, "button_hover_text must not be null");
  }
}
