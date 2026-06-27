package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import dev.cyr1en.promptcore.TitleConfig;
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
 * @param titleDisplay optional title-wrapper config; {@code null} when not requested
 */
public record AnvilPrompt(
    String type,
    String id,
    String title,
    @SerializedName("prompt_text") String promptText,
    @SerializedName("left_button") AnvilButton leftButton,
    @SerializedName("right_button") AnvilButton rightButton,
    boolean sanitize,
    @SerializedName("title_display") TitleConfig titleDisplay)
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

  /**
   * Backward-compatible convenience constructor without the title-wrapper field. Delegates to the
   * canonical constructor with {@code titleDisplay = null}.
   */
  public AnvilPrompt(
      String type,
      String id,
      String title,
      String promptText,
      AnvilButton leftButton,
      AnvilButton rightButton,
      boolean sanitize) {
    this(type, id, title, promptText, leftButton, rightButton, sanitize, null);
  }
}

