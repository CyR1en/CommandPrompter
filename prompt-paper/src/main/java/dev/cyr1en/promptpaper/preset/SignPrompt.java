package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import dev.cyr1en.promptcore.TitleConfig;
import java.util.List;
import java.util.Objects;

/**
 * Sign-prompt definition: the player types on a sign GUI.
 *
 * <p>{@code defaultLines} is an optional list of up to four pre-filled sign lines. The JSON schema
 * caps it at four entries; the record does not enforce that cap (it would require a custom
 * deserializer) and trusts the configuration file.
 *
 * @param type the discriminator value, always {@code "sign"}
 * @param id the unique identifier
 * @param promptText the message sent to the player explaining what to write
 * @param defaultLines optional list of pre-filled sign lines (max 4); may be {@code null}
 * @param sanitize whether to strip color codes from the player's input
 * @param titleDisplay optional title-wrapper config; {@code null} when not requested
 */
public record SignPrompt(
    String type,
    String id,
    @SerializedName("prompt_text") String promptText,
    @SerializedName("default_lines") List<String> defaultLines,
    boolean sanitize,
    @SerializedName("title_display") TitleConfig titleDisplay)
    implements PromptDefinition {

  /** Canonical constructor; coerces {@code null} {@code defaultLines} to an empty list. */
  public SignPrompt {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(promptText, "prompt_text must not be null");
    if (!"sign".equals(type)) {
      throw new IllegalArgumentException("SignPrompt.type must be \"sign\", got: " + type);
    }
    defaultLines = defaultLines == null ? List.of() : List.copyOf(defaultLines);
  }

  /**
   * Backward-compatible convenience constructor without the title-wrapper field. Delegates to the
   * canonical constructor with {@code titleDisplay = null}.
   */
  public SignPrompt(
      String type,
      String id,
      String promptText,
      List<String> defaultLines,
      boolean sanitize) {
    this(type, id, promptText, defaultLines, sanitize, null);
  }
}

