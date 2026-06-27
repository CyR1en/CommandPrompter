package dev.cyr1en.promptpaper.engine;

import dev.cyr1en.promptcore.DispatchTarget;
import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.PostCommand;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * Resolves a {@link PostCommandMeta} (from the parser) into a fully prepared
 * command ready for dispatch: the preset lookup is done, placeholders are
 * substituted, and the execution policy is checked against the session state.
 *
 * <h2>Preset vs. legacy</h2>
 *
 * <ul>
 *   <li><b>Preset PCMs</b> ({@link PostCommandMeta#isPreset()} {@code == true},
 *       parsed from {@code <!@id>}, {@code <!!@id>}, {@code <!:N@id>}, etc.):
 *       the id is looked up in the {@code PresetRegistry} to obtain the full
 *       {@link PostCommand} definition. The preset's {@code executionPolicy},
 *       {@code executeAs}, and {@code delayTicks} override any parser hint.
 *   <li><b>Legacy PCMs</b> ({@code <!cmd>}, {@code <!!cmd>}, etc.): the {@code command}
 *       has already had its {@code {N}} placeholders substituted by
 *       {@code PromptSession.resolvePCMReferences}. The dispatch target
 *       ({@link DispatchTarget}) is mapped to {@link ExecuteAs}, and the session-level
 *       {@code onCancel} flag is the only execution-policy check.
 * </ul>
 *
 * <h2>Policy filter</h2>
 *
 * <p>For preset PCMs, the dispatcher's contract (Scope 5 spec) is that
 * {@link PostCommand#executionPolicy()} must match the session's completion state.
 * If the preset's policy disagrees with the parser's {@code onCancel} hint, the
 * preset wins and a warning is logged. This protects against a misconfigured
 * preset being executed on the wrong lifecycle event.
 */
public class PostCommandResolver {

  private final CommandPrompter plugin;
  private final PresetRegistry registry;
  private final PostCommandPlaceholderResolver placeholders;

  public PostCommandResolver(CommandPrompter plugin) {
    this.plugin = plugin;
    this.registry = plugin.getPresetRegistry();
    this.placeholders = new PostCommandPlaceholderResolver(plugin.getHookContainer());
  }

  /**
   * Resolves a single {@link PostCommandMeta} against the session state.
   *
   * @param player the player who owns the post-command
   * @param pcm the parsed post-command meta
   * @param wasCancelled true if the session was cancelled (vs. completed)
   * @param answers the raw collected answers from the session, in prompt order
   * @return a fully prepared {@link Resolved} command, or empty if the PCM should
   *     be skipped (preset not found, policy mismatch, or empty command)
   */
  public Optional<Resolved> resolve(
      Player player, PostCommandMeta pcm, boolean wasCancelled, List<String> answers) {
    if (pcm == null) return Optional.empty();
    if (pcm.isPreset()) {
      return resolvePreset(player, pcm, wasCancelled, answers);
    }
    return resolveLegacy(player, pcm, wasCancelled);
  }

  private Optional<Resolved> resolvePreset(
      Player player, PostCommandMeta pcm, boolean wasCancelled, List<String> answers) {
    if (registry == null) {
      plugin.getPluginLogger().err(
          "PresetRegistry is not wired; cannot resolve preset post-command '"
                  + pcm.command()
                  + "' — skipping");
      return Optional.empty();
    }
    var presetOpt = registry.getPostCommand(pcm.command());
    if (presetOpt.isEmpty()) {
      // Defensive check in case the registry was reloaded after the session started.
      plugin.getPluginLogger().err(
          "Preset post-command '" + pcm.command()
              + "' not found at dispatch time (registry may have been reloaded) — skipping");
      return Optional.empty();
    }
    var def = presetOpt.get();

    if (!policyMatches(def.executionPolicy(), wasCancelled)) {
      plugin.getPluginLogger().debug(
          "Preset post-command '" + def.id()
              + "' policy=" + def.executionPolicy()
              + " does not match session state (wasCancelled=" + wasCancelled
              + ") — skipping");
      return Optional.empty();
    }
    // Log a warning if the parser's hint disagrees with the preset policy.
    if (pcm.onCancel() != (def.executionPolicy() == ExecutionPolicy.ON_CANCEL)) {
      plugin.getPluginLogger().warn(
          "Preset post-command '" + def.id()
              + "' execution_policy=" + def.executionPolicy()
              + " disagrees with the parser's onCancel hint (onCancel=" + pcm.onCancel()
              + "). The preset's policy takes precedence.");
    }

    var command = placeholders.resolve(def.command(), player, answers);
    if (command.isEmpty()) {
      plugin.getPluginLogger().debug(
          "Preset post-command '" + def.id() + "' has empty command after placeholder resolution");
      return Optional.empty();
    }
    return Optional.of(new Resolved(command, def.executeAs(), def.delayTicks(), def.id(), true));
  }

  private Optional<Resolved> resolveLegacy(
      Player player, PostCommandMeta pcm, boolean wasCancelled) {
    // Legacy PCMs are pre-filtered by the session's completion state.
    if (pcm.command() == null || pcm.command().isEmpty()) {
      plugin.getPluginLogger().debug("Skipping empty legacy PCM");
      return Optional.empty();
    }
    // Suppress the unused warning on parameters we are intentionally not using here.
    @SuppressWarnings("unused")
    var unusedPlayer = player;
    @SuppressWarnings("unused")
    var unusedWasCancelled = wasCancelled;
    return Optional.of(
        new Resolved(
            pcm.command(),
            mapDispatchTarget(pcm.dispatchTarget()),
            pcm.delayTicks(),
            null,
            false));
  }

  private static boolean policyMatches(ExecutionPolicy policy, boolean wasCancelled) {
    return switch (policy) {
      case ON_COMPLETE -> !wasCancelled;
      case ON_CANCEL -> wasCancelled;
    };
  }

  private static ExecuteAs mapDispatchTarget(DispatchTarget target) {
    if (target == null) return ExecuteAs.PLAYER;
    return switch (target) {
      case CONSOLE -> ExecuteAs.CONSOLE;
      case PLAYER, PASSTHROUGH -> ExecuteAs.PLAYER;
    };
  }

  /**
   * A post-command fully prepared for dispatch.
   *
   * @param command the final command string with placeholders resolved
   * @param executeAs where to dispatch (console or player)
   * @param delayTicks how many ticks to wait before dispatching (0 = immediate)
   * @param sourceId the preset id, or {@code null} for a legacy PCM (useful for logging)
   * @param preset whether this command originated from a preset
   */
  public record Resolved(
      String command, ExecuteAs executeAs, int delayTicks, String sourceId, boolean preset) {}
}
