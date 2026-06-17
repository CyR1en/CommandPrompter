package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Root model parsed from {@code presets.json}.
 *
 * <p>Wraps the two top-level arrays from the JSON schema. Either list may be absent in the
 * source file (the canonical constructor coerces {@code null} to an empty list).
 *
 * @param prompts the prompt definitions referenced by {@code <@id>} tags
 * @param postCommands the post-command definitions referenced by {@code <!@id>} tags
 */
public record PresetConfig(
    List<PromptDefinition> prompts,
    @SerializedName("post_commands") List<PostCommand> postCommands) {

  /**
   * Canonical constructor. Coerces {@code null} lists to empty immutable lists so downstream
   * code can iterate without null checks.
   */
  public PresetConfig {
    prompts = prompts == null ? List.of() : List.copyOf(prompts);
    postCommands = postCommands == null ? List.of() : List.copyOf(postCommands);
  }
}
