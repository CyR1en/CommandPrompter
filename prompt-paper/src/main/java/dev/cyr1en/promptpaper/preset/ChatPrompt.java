package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * Chat-prompt definition: the player is asked a question in the chat box and types their answer.
 *
 * <p>The {@code type} field is always {@code "chat"} and is included as a record component for
 * round-tripping / debugging convenience.
 *
 * @param type the discriminator value, always {@code "chat"}
 * @param id the unique identifier
 * @param promptText the question shown to the player
 * @param cancel cancel-behavior block
 * @param sanitize whether to strip color codes from the player's input
 */
public record ChatPrompt(
    String type,
    String id,
    @SerializedName("prompt_text") String promptText,
    CancelBehavior cancel,
    boolean sanitize)
    implements PromptDefinition {

  /** Canonical constructor. Enforces {@code type == "chat"} and non-null required fields. */
  public ChatPrompt {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(promptText, "prompt_text must not be null");
    Objects.requireNonNull(cancel, "cancel must not be null");
    if (!"chat".equals(type)) {
      throw new IllegalArgumentException("ChatPrompt.type must be \"chat\", got: " + type);
    }
  }
}
