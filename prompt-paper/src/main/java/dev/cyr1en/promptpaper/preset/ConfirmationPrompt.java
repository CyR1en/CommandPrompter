package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import dev.cyr1en.promptcore.ConfirmationMode;
import dev.cyr1en.promptcore.TitleConfig;
import java.util.Objects;

/**
 * Confirmation-prompt definition: displays a confirmation prompt (GUI, chat, or dialog).
 *
 * @param type the discriminator value, always {@code "confirmation"}
 * @param id the unique identifier
 * @param mode presentation mode override (gui, chat, dialog); may be {@code null}
 * @param title inventory or window title; may be {@code null}
 * @param promptText the question or prompt shown to the player
 * @param confirmText custom confirmation button label; may be {@code null}
 * @param cancelText custom cancellation button label; may be {@code null}
 * @param valueMode whether value mode is enabled (returns true/false instead of gating)
 * @param sound sound to play on open; may be {@code null}
 * @param sanitize whether to strip color codes from the player's input
 * @param titleDisplay optional title-wrapper config; {@code null} when not requested
 * @param timeout optional timeout in seconds; {@code null} when not specified
 */
public record ConfirmationPrompt(
    String type,
    String id,
    ConfirmationMode mode,
    String title,
    @SerializedName("prompt_text") String promptText,
    @SerializedName("confirm_text") String confirmText,
    @SerializedName("cancel_text") String cancelText,
    @SerializedName("value_mode") boolean valueMode,
    String sound,
    boolean sanitize,
    @SerializedName("title_display") TitleConfig titleDisplay,
    Integer timeout)
    implements PromptDefinition {

  /**
   * Canonical constructor. Enforces {@code type == "confirmation"}, non-null required fields,
   * and timeout bounds [1, 3600].
   */
  public ConfirmationPrompt {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(promptText, "prompt_text must not be null");
    if (!"confirmation".equals(type)) {
      throw new IllegalArgumentException(
          "ConfirmationPrompt.type must be \"confirmation\", got: " + type);
    }
    if (timeout != null && (timeout < 1 || timeout > 3600)) {
      throw new IllegalArgumentException(
          "ConfirmationPrompt.timeout must be between 1 and 3600, got: " + timeout);
    }
  }

  /**
   * Backward-compatible convenience constructor without the timeout field. Delegates to the
   * canonical constructor with {@code timeout = null}.
   */
  public ConfirmationPrompt(
      String type,
      String id,
      ConfirmationMode mode,
      String title,
      String promptText,
      String confirmText,
      String cancelText,
      boolean valueMode,
      String sound,
      boolean sanitize,
      TitleConfig titleDisplay) {
    this(
        type,
        id,
        mode,
        title,
        promptText,
        confirmText,
        cancelText,
        valueMode,
        sound,
        sanitize,
        titleDisplay,
        null);
  }

  /**
   * Backward-compatible convenience constructor without the title-wrapper field. Delegates to the
   * canonical constructor with {@code titleDisplay = null} and {@code timeout = null}.
   */
  public ConfirmationPrompt(
      String type,
      String id,
      ConfirmationMode mode,
      String title,
      String promptText,
      String confirmText,
      String cancelText,
      boolean valueMode,
      String sound,
      boolean sanitize) {
    this(
        type,
        id,
        mode,
        title,
        promptText,
        confirmText,
        cancelText,
        valueMode,
        sound,
        sanitize,
        null,
        null);
  }
}
