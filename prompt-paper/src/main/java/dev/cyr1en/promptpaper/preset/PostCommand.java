package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * Post-command definition: a side-effect command that fires when a prompt flow reaches the
 * configured lifecycle state.
 *
 * <p>Post-commands are referenced from command strings via {@code <!@id>}. The {@code command}
 * template supports session-scoped placeholders ({@code {player}}, {@code {input}}, {@code
 * {input:N}}, plus PAPI {@code %...%}) which are substituted at execution time (Scope 5).
 *
 * @param id the unique identifier referenced by {@code <!@id>} tags
 * @param command the raw command template (without the leading slash)
 * @param executionPolicy when the command should fire
 * @param executeAs the execution context (console or player)
 * @param delayTicks optional delay (in server ticks) before dispatch; defaults to {@code 0}
 */
public record PostCommand(
    String id,
    String command,
    @SerializedName("execution_policy") ExecutionPolicy executionPolicy,
    @SerializedName("execute_as") ExecuteAs executeAs,
    @SerializedName("delay_ticks") int delayTicks) {

  /** Canonical constructor. */
  public PostCommand {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(executionPolicy, "execution_policy must not be null");
    Objects.requireNonNull(executeAs, "execute_as must not be null");
    if (delayTicks < 0) {
      throw new IllegalArgumentException("delay_ticks must be >= 0, got: " + delayTicks);
    }
  }
}
