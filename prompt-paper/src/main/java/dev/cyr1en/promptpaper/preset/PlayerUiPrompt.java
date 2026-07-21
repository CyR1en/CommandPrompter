package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import dev.cyr1en.promptcore.TitleConfig;
import java.util.Objects;

/**
 * Player-UI prompt definition: the player is shown a paginated grid of online players to pick from.
 *
 * <p>{@code filter}, {@code cancelButton}, {@code previousButton}, and {@code nextButton} are all
 * optional in the JSON schema, so the record components use boxed / nullable types and resolve to
 * defaults where needed.
 *
 * @param type the discriminator value, always {@code "player_ui"}
 * @param id the unique identifier
 * @param promptText the inventory title
 * @param filter optional filter expression; {@code null} if not provided
 * @param cancelButton optional cancel-button layout; {@code null} hides the button
 * @param previousButton optional previous-page button layout; {@code null} hides the button
 * @param nextButton optional next-page button layout; {@code null} hides the button
 * @param sanitize whether to strip color codes from the player's input
 * @param titleDisplay optional title-wrapper config; {@code null} when not requested
 */
public record PlayerUiPrompt(
    String type,
    String id,
    @SerializedName("prompt_text") String promptText,
    String filter,
    @SerializedName("cancel_button") UIButton cancelButton,
    @SerializedName("previous_button") UIButton previousButton,
    @SerializedName("next_button") UIButton nextButton,
    boolean sanitize,
    @SerializedName("title_display") TitleConfig titleDisplay)
    implements PromptDefinition {

  /** Canonical constructor. */
  public PlayerUiPrompt {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(promptText, "prompt_text must not be null");
    if (!"player_ui".equals(type)) {
      throw new IllegalArgumentException("PlayerUiPrompt.type must be \"player_ui\", got: " + type);
    }
  }

  /**
   * Backward-compatible convenience constructor without the title-wrapper field. Delegates to the
   * canonical constructor with {@code titleDisplay = null}.
   */
  public PlayerUiPrompt(
      String type,
      String id,
      String promptText,
      String filter,
      UIButton cancelButton,
      UIButton previousButton,
      UIButton nextButton,
      boolean sanitize) {
    this(type, id, promptText, filter, cancelButton, previousButton, nextButton, sanitize, null);
  }
}

