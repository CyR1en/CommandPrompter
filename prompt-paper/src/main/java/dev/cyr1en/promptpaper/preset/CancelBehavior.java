package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * Cancel-behavior block for chat prompts.
 *
 * <p>When a player types the cancellation keyword in a chat prompt, the four fields below control
 * what the plugin does in response.
 *
 * @param send whether to send the {@code message} back to the player
 * @param message the (localizable) cancel message
 * @param clickable whether the sent message is rendered as a clickable chat component
 * @param hoverMessage the hover tooltip text shown when {@code clickable} is true
 */
public record CancelBehavior(
    boolean send,
    String message,
    boolean clickable,
    @SerializedName("hover_message") String hoverMessage) {

  /** Canonical constructor with null-checks; Gson supplies defaults for absent fields. */
  public CancelBehavior {
    Objects.requireNonNull(message, "cancel.message must not be null");
    Objects.requireNonNull(hoverMessage, "cancel.hover_message must not be null");
  }
}
