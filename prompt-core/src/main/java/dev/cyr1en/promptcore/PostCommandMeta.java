package dev.cyr1en.promptcore;

import java.util.Objects;

/**
 * A post-command meta (PCM) that dispatches a command after all prompts complete.
 *
 * <p>PCMs are extracted from tags like {@code <!tempban {0} 7d>}. The {@code {0}} references are
 * resolved against the collected answers when the session finishes.
 *
 * @param command the command template with unresolved {@code {N}} references
 * @param answerIndices the indices of prompt answers referenced by this PCM
 * @param delayTicks ticks to wait before dispatching (0 = immediate)
 * @param onCancel whether this PCM runs on cancel ({@code <!!...>}) or completion ({@code <!...>})
 * @param dispatchTarget where to dispatch (passthrough/console/player)
 */
public record PostCommandMeta(
    String command,
    int[] answerIndices,
    int delayTicks,
    boolean onCancel,
    DispatchTarget dispatchTarget) {

  public PostCommandMeta {
    Objects.requireNonNull(command);
    Objects.requireNonNull(dispatchTarget);
  }
}
