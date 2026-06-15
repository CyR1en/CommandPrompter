package dev.cyr1en.promptcore;

import java.util.List;
import java.util.Objects;

/**
 * The assembled output of a completed {@link PromptSession}.
 *
 * <p>Contains the final command string (with prompt tags replaced by answers) and any post-command
 * metas that should be dispatched. Cancelled sessions produce onCancelCmds; completed sessions
 * produce onCompleteCmds.
 *
 * @param assembledCommand the final command string with answers substituted in
 * @param onCompleteCmds PCMs that run after successful completion (empty if cancelled)
 * @param onCancelCmds PCMs that run on cancel (empty if completed)
 */
public record SessionResult(
    String assembledCommand,
    List<PostCommandMeta> onCompleteCmds,
    List<PostCommandMeta> onCancelCmds) {

  /** Compact constructor that validates non-null components. */
  public SessionResult {
    Objects.requireNonNull(assembledCommand);
    Objects.requireNonNull(onCompleteCmds);
    Objects.requireNonNull(onCancelCmds);
  }

  /** Whether any PCMs should be dispatched after the main command. */
  public boolean hasPostCommands() {
    return !onCompleteCmds.isEmpty() || !onCancelCmds.isEmpty();
  }
}
