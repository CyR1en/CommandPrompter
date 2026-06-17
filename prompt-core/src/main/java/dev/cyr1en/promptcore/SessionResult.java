package dev.cyr1en.promptcore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The assembled output of a completed {@link dev.cyr1en.promptcore.session.PromptSession}.
 *
 * <p>Contains the final command string (with prompt tags replaced by answers), the raw collected
 * answers, and any post-command metas that should be dispatched. Cancelled sessions produce
 * onCancelCmds; completed sessions produce onCompleteCmds.
 *
 * @param assembledCommand the final command string with answers substituted in
 * @param answers the raw collected answers, in prompt order. Used by the dispatcher for
 *     post-command placeholder resolution (Scope 5)
 * @param onCompleteCmds PCMs that run after successful completion (empty if cancelled)
 * @param onCancelCmds PCMs that run on cancel (empty if completed)
 */
public record SessionResult(
    String assembledCommand,
    List<String> answers,
    List<PostCommandMeta> onCompleteCmds,
    List<PostCommandMeta> onCancelCmds) {

  /** Compact constructor that validates non-null components. */
  public SessionResult {
    Objects.requireNonNull(assembledCommand);
    Objects.requireNonNull(answers);
    Objects.requireNonNull(onCompleteCmds);
    Objects.requireNonNull(onCancelCmds);
    answers = List.copyOf(answers);
    onCompleteCmds = List.copyOf(onCompleteCmds);
    onCancelCmds = List.copyOf(onCancelCmds);
  }

  /** Whether any PCMs should be dispatched after the main command. */
  public boolean hasPostCommands() {
    return !onCompleteCmds.isEmpty() || !onCancelCmds.isEmpty();
  }

  /**
   * Returns the union of {@link #onCompleteCmds()} and {@link #onCancelCmds()}. The dispatcher
   * iterates this list and applies its own policy / registry checks rather than relying on the
   * pre-filtered lists (which were filtered by the parser's {@code onCancel} hint, not the preset's
   * {@code execution_policy}).
   */
  public List<PostCommandMeta> allPCMs() {
    var all = new ArrayList<PostCommandMeta>(onCompleteCmds.size() + onCancelCmds.size());
    all.addAll(onCompleteCmds);
    all.addAll(onCancelCmds);
    return all;
  }
}
