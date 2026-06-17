package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * Anvil-prompt definition: the player edits text inside an anvil inventory.
 *
 * @param type the discriminator value, always {@code "anvil"}
 * @param id the unique identifier
 * @param title the anvil inventory title
 * @param promptText the default text pre-filled in the anvil text field
 * @param leftButton the item placed in the left slot
 * @param rightButton the item placed in the right slot
 * @param sanitize whether to strip color codes from the player's input
 */
public record AnvilPrompt(
    String type,
    String id,
    String title,
    @SerializedName("prompt_text") String promptText,
    @SerializedName("left_button") AnvilButton leftButton,
    @SerializedName("right_button") AnvilButton rightButton,
    boolean sanitize)
    implements PromptDefinition {

  /** Canonical constructor. */
  public AnvilPrompt {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(promptText, "prompt_text must not be null");
    Objects.requireNonNull(leftButton, "left_button must not be null");
    Objects.requireNonNull(rightButton, "right_button must not be null");
    if (!"anvil".equals(type)) {
      throw new IllegalArgumentException("AnvilPrompt.type must be \"anvil\", got: " + type);
    }
  }
}
