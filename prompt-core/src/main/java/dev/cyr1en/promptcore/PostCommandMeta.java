package dev.cyr1en.promptcore;

import java.util.Objects;

/**
 * A post-command meta (PCM) that dispatches a command after all prompts complete.
 *
 * <p>PCMs are extracted from tags like {@code <!tempban {0} 7d>}. The {@code {0}} references are
 * resolved against the collected answers when the session finishes.
 *
 * <p>A PCM can also be a <b>preset reference</b> of the form {@code <!@id>} (or {@code <!!@id>},
 * {@code <!:N@id>}). For preset PCMs, {@code command} holds the id and {@link #isPreset()} returns
 * {@code true}. The actual command and placeholders come from the {@code PresetRegistry} at
 * dispatch time.
 *
 * @param command the command template with unresolved {@code {N}} references. For preset
 *     references, this holds the preset id
 * @param answerIndices the indices of prompt answers referenced by this PCM
 * @param delayTicks ticks to wait before dispatching (0 = immediate)
 * @param onCancel whether this PCM runs on cancel ({@code <!!...>}) or completion ({@code <!...>})
 * @param dispatchTarget where to dispatch (passthrough/console/player)
 * @param preset whether this PCM is a JSON preset reference (e.g. {@code <!@log_reason>})
 */
public record PostCommandMeta(
    String command,
    int[] answerIndices,
    int delayTicks,
    boolean onCancel,
    DispatchTarget dispatchTarget,
    boolean preset) {

  public PostCommandMeta {
    Objects.requireNonNull(command);
    Objects.requireNonNull(dispatchTarget);
  }

  /**
   * Whether this PCM is a JSON preset reference of the form {@code <!@id>}. Preset PCMs have their
   * full definition (command, placeholders, delay, target) loaded from the {@code PresetRegistry}
   * at dispatch time.
   */
  public boolean isPreset() {
    return preset;
  }
}
