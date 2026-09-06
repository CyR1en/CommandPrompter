package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Root model parsed from {@code presets.json}.
 *
 * <p>Wraps the top-level arrays from the JSON schema. Any list may be absent in the source file
 * (the canonical constructor coerces {@code null} to an empty list).
 *
 * @param prompts the prompt definitions referenced by {@code <@id>} tags
 * @param postCommands the post-command definitions referenced by {@code <!@id>} tags
 * @param approvalGates the approval gate definitions referenced by {@code <#@id>} tags
 * @param conditionalPostCommands the conditional post-command definitions
 */
public record PresetConfig(
    List<PromptDefinition> prompts,
    @SerializedName("post_commands") List<PostCommand> postCommands,
    @SerializedName("approval_gates") List<ApprovalGateDefinition> approvalGates,
    @SerializedName("conditional_post_commands")
        List<ConditionalPostCommandDefinition> conditionalPostCommands) {

  /**
   * Backward-compatible 2-argument constructor for existing callers and tests.
   *
   * @param prompts the prompt definitions
   * @param postCommands the post-command definitions
   */
  public PresetConfig(List<PromptDefinition> prompts, List<PostCommand> postCommands) {
    this(prompts, postCommands, List.of(), List.of());
  }

  /**
   * Canonical constructor. Coerces {@code null} lists to empty immutable lists so downstream code
   * can iterate without null checks.
   */
  public PresetConfig {
    prompts = prompts == null ? List.of() : List.copyOf(prompts);
    postCommands = postCommands == null ? List.of() : List.copyOf(postCommands);
    approvalGates = approvalGates == null ? List.of() : List.copyOf(approvalGates);
    conditionalPostCommands =
        conditionalPostCommands == null ? List.of() : List.copyOf(conditionalPostCommands);
  }
}
