package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * Pagination / control button used by player-UI prompts.
 *
 * <p>Like {@link AnvilButton}, {@code buttonIcon} is stored as a raw string and resolved to a
 * Bukkit {@code Material} later in the paper module.
 *
 * @param show whether the button is rendered
 * @param slot the inventory slot index (0–53)
 * @param buttonText the display name of the item
 * @param buttonIcon the Bukkit {@code Material} name
 * @param buttonHoverText the lore line(s) shown on hover
 * @param customModelData the integer custom-model-data tag applied to the item stack
 */
public record UIButton(
    boolean show,
    int slot,
    @SerializedName("button_text") String buttonText,
    @SerializedName("button_icon") String buttonIcon,
    @SerializedName("button_hover_text") String buttonHoverText,
    @SerializedName("custom_model_data") int customModelData) {

  /** Canonical constructor with null-checks. */
  public UIButton {
    Objects.requireNonNull(buttonText, "button_text must not be null");
    Objects.requireNonNull(buttonIcon, "button_icon must not be null");
    Objects.requireNonNull(buttonHoverText, "button_hover_text must not be null");
  }
}
