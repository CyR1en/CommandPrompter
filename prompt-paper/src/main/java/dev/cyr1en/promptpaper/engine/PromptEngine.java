package dev.cyr1en.promptpaper.engine;

import dev.cyr1en.promptcore.*;
import dev.cyr1en.promptcore.parser.CommandLineParser;
import dev.cyr1en.promptcore.session.PromptSession;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.util.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Manages the lifecycle of interactive prompt sessions for players.
 * Parses command lines for prompt tags, tracks per-player sessions,
 * collects answers, and dispatches post-completion/cancellation commands.
 * All session state is held in a {@link ConcurrentHashMap} keyed by player UUID.
 */
public class PromptEngine {

    private final CommandPrompter plugin;
    private final CommandLineParser parser;
    private final Map<UUID, PromptSession> sessions;
    private final Scheduler scheduler;

    public PromptEngine(CommandPrompter plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.parser = new CommandLineParser();
        this.sessions = new ConcurrentHashMap<>();
        this.scheduler = scheduler;
    }

    /**
     * Parses a command line and, if it contains prompt tags, starts a new
     * session for the player and returns the parsed result.
     *
     * <p>When {@code config.enablePermission()} is {@code true}, this method
     * returns empty (and starts no session) for any player that lacks
     * {@code promptpaper.use}. This is the per-player control gate for the
     * prompting feature, distinct from the per-command permissions checked
     * by the command system.
     *
     * <h2>Fail-fast on missing presets</h2>
     *
     * <p>If the parsed command references any preset prompts ({@code <@id>}) or
     * preset post-commands ({@code <!@id>}), this method queries the plugin's
     * {@code PresetRegistry} for each id. If any id is unknown, the session is
     * <b>not</b> created; a localized error message is sent to the player and a
     * severe warning is logged with the full list of missing ids and the original
     * command line. This is the spec-mandated fail-fast behavior — under no
     * circumstance is the literal tag passed to the underlying command.
     *
     * <p>The fail-fast check runs <i>before</i> the {@code hasPrompts()} check so a
     * command that contains only missing-preset post-commands (no prompt tags) is
     * still rejected.
     *
     * @return the parsed command with prompts, or empty if no prompts were found
     *     (or the command was rejected by the fail-fast check)
     */
    public Optional<ParsedCommand> intercept(Player player, String commandLine) {
        var config = plugin.getConfigLoader().getConfig();
        if (config.enablePermission() && !player.hasPermission("promptpaper.use")) {
            plugin.getPluginLogger().debug("Player " + player.getName()
                    + " lacks promptpaper.use, skipping prompt intercept");
            return Optional.empty();
        }
        var parsed = parser.parse(commandLine);

        // Fail-fast: any unresolved preset id aborts the entire command flow,
        // even if the command has no prompt tags (e.g. only <!@missing>).
        // The listener cancels the PlayerCommandPreprocessEvent when the
        // command had tag form and the engine returned empty for a permission
        // / fail-fast reason.
        var missingPrompts = findMissingPromptPresets(parsed);
        var missingPostCmds = findMissingPostCommandPresets(parsed);
        if (!missingPrompts.isEmpty() || !missingPostCmds.isEmpty()) {
            failFastMissingPresets(player, commandLine, missingPrompts, missingPostCmds);
            return Optional.empty();
        }

        if (!parsed.hasPrompts()) {
            plugin.getPluginLogger().debug("No prompts in command from " + player.getName());
            return Optional.empty();
        }

        if (hasActiveSession(player)) {
            plugin.getPluginLogger().debug("Player " + player.getName() + " already has an active session, aborting new session");
            player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.error.session_active"));
            return Optional.empty();
        }

        if (hasActiveSession(player)) {
            plugin.getPluginLogger().debug("Player " + player.getName() + " already has an active session, aborting new session");
            player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.error.session_active"));
            return Optional.empty();
        }

        sessions.put(player.getUniqueId(), PromptSession.start(player.getUniqueId().toString(), parsed));
        plugin.getPluginLogger().debug("Intercepted " + parsed.promptTags().size()
                + " prompts for " + player.getName());
        return Optional.of(parsed);
    }

    /**
     * Whether the given command line contains at least one tag (prompt or PCM). Useful for
     * callers that need to distinguish "no tag form at all" from "had tag form but
     * parsing returned empty for some reason" — for example, the fail-fast path that
     * must cancel the underlying command dispatch.
     */
    public boolean commandHasTagForm(String commandLine) {
        return parser.hasTagForm(commandLine);
    }

    /**
     * Whether the given command line contains at least one <b>preset reference</b>
     * ({@code <@id>} or {@code <!@id>}). The listener uses this to decide whether
     * to cancel the {@link org.bukkit.event.player.PlayerCommandPreprocessEvent}
     * even when no prompt session was started — the literal preset tag must never
     * reach the underlying command dispatcher.
     */
    public boolean hasPresetReferences(String commandLine) {
        if (!parser.hasTagForm(commandLine)) return false;
        var parsed = parser.parse(commandLine);
        return parsed.promptTags().stream().anyMatch(PromptTag::isPreset)
                || parsed.postCmds().stream().anyMatch(PostCommandMeta::isPreset);
    }

    /**
     * Submits a single answer for the player's current prompt session.
     *
     * @return the completed session result if all prompts are now answered, or empty
     */
    public Optional<SessionResult> submit(Player player, String answer) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || !session.isActive()) {
            plugin.getPluginLogger().debug("No active session for " + player.getName() + " on submit");
            return Optional.empty();
        }
        session = session.submitAnswer(answer);
        sessions.put(player.getUniqueId(), session);
        if (session.isComplete()) {
            sessions.remove(player.getUniqueId());
            plugin.getPluginLogger().debug("Session complete for " + player.getName());
            return Optional.of(session.finish());
        }
        plugin.getPluginLogger().debug("Answer accepted for " + player.getName()
                + ", " + session.remainingCount() + " remaining");
        return Optional.empty();
    }

    /**
     * Submit a batch of answers to the current prompt. The current prompt
     * must be a compound tag with the matching number of sub-answers. See
     * {@link dev.cyr1en.promptcore.session.PromptSession#submitAnswers} for
     * the size-validation rules.
     */
    public Optional<SessionResult> submitAnswers(Player player, java.util.List<String> answers) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || !session.isActive()) {
            plugin.getPluginLogger().debug("No active session for " + player.getName() + " on submitAnswers");
            return Optional.empty();
        }
        session = session.submitAnswers(answers);
        sessions.put(player.getUniqueId(), session);
        if (session.isComplete()) {
            sessions.remove(player.getUniqueId());
            plugin.getPluginLogger().debug("Session complete for " + player.getName());
            return Optional.of(session.finish());
        }
        plugin.getPluginLogger().debug("Compound answers accepted for " + player.getName()
                + ", " + session.remainingCount() + " remaining");
        return Optional.empty();
    }

    /**
     * Returns the active session for a player, if one exists.
     */
    public Optional<PromptSession> getSession(Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    /**
     * Cancels the player's active session and dispatches on-cancel commands if present.
     */
    public void cancel(Player player, CancelReason reason) {
        var session = sessions.remove(player.getUniqueId());
        if (session == null) {
            plugin.getPluginLogger().debug("No session to cancel for " + player.getName());
            return;
        }
        plugin.getPluginLogger().debug("Session cancelled for " + player.getName() + " reason=" + reason);
        var cancelled = session.cancel(reason);
        if (cancelled.isCancelled()) {
            dispatchPCMs(player, cancelled.finish(), true);
        }
    }

    /**
     * Cancels all active sessions (e.g. during plugin shutdown).
     */
    public void cancelAll() {
        var snapshot = new java.util.ArrayList<>(sessions.keySet());
        for (var uuid : snapshot) {
            var player = plugin.getServer().getPlayer(uuid);
            if (player != null) cancel(player, CancelReason.MANUAL);
        }
        sessions.clear();
    }

    /**
     * Returns whether the player has an active (non-complete, non-cancelled) session.
     */
    public boolean hasActiveSession(Player player) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    /**
     * Dispatches post-completion or on-cancel commands (PCMs) from a session result.
     *
     * <p>Each PCM is routed through {@link PostCommandResolver}, which:
     *
     * <ul>
     *   <li>Resolves preset references ({@code <!@id>}) against the
     *       {@code PresetRegistry}.
     *   <li>Filters by the preset's {@code executionPolicy} (or, for legacy
     *       PCMs, by the parser's {@code onCancel} hint, which is already
     *       aligned with the session state via the pre-filtered
     *       {@code onCompleteCmds} / {@code onCancelCmds} lists).
     *   <li>Resolves session-scoped placeholders ({@code {player}},
     *       {@code {input}}, {@code {input:N}}, PAPI {@code %…%}).
     *   <li>Schedules the dispatch with the preset's / legacy delay via the
     *       Folia-safe {@link Scheduler}.
     * </ul>
     *
     * <p>Commands with a positive delay are scheduled; others run synchronously.
     */
    public void dispatchPCMs(Player player, SessionResult result, boolean wasCancelled) {
        var pcms = wasCancelled ? result.onCancelCmds() : result.onCompleteCmds();
        plugin.getPluginLogger().debug("Dispatching " + pcms.size() + " PCMs for "
                + player.getName() + " (cancelled=" + wasCancelled + ")");
        var resolver = new PostCommandResolver(plugin);
        for (var pcm : pcms) {
            var resolved = resolver.resolve(player, pcm, wasCancelled, result.answers());
            if (resolved.isEmpty()) {
                plugin.getPluginLogger().debug(
                        "PCM filtered out: raw=" + pcm.command()
                                + " preset=" + pcm.isPreset()
                                + " onCancel=" + pcm.onCancel());
                continue;
            }
            schedule(player, resolved.get());
        }
    }

    /** Schedules a single resolved post-command for execution. */
    private void schedule(Player player, PostCommandResolver.Resolved resolved) {
        Runnable task = () -> executeResolved(player, resolved);
        if (resolved.delayTicks() > 0) {
            plugin.getPluginLogger().debug(
                    "Scheduling PCM: source=" + resolved.sourceId()
                            + " preset=" + resolved.preset()
                            + " delay=" + resolved.delayTicks() + "t"
                            + " target=" + resolved.executeAs()
                            + " cmd=" + resolved.command());
            scheduler.runLater(task, resolved.delayTicks());
        } else {
            plugin.getPluginLogger().debug(
                    "Dispatching PCM: source=" + resolved.sourceId()
                            + " preset=" + resolved.preset()
                            + " target=" + resolved.executeAs()
                            + " cmd=" + resolved.command());
            scheduler.runSync(task);
        }
    }

    /**
     * Dispatches a resolved post-command to the appropriate command sender.
     * {@link dev.cyr1en.promptpaper.preset.ExecuteAs#CONSOLE} routes through
     * the server console; {@link dev.cyr1en.promptpaper.preset.ExecuteAs#PLAYER}
     * uses the player.
     */
    private void executeResolved(Player player, PostCommandResolver.Resolved resolved) {
        var sender = switch (resolved.executeAs()) {
            case CONSOLE -> plugin.getServer().getConsoleSender();
            case PLAYER -> player;
        };
        plugin.getServer().dispatchCommand(sender, resolved.command());
    }

    // ------------------------------------------------------------------
    // Preset validation (fail-fast)
    // ------------------------------------------------------------------

    /**
     * Returns the list of preset prompt ids that appear in {@code parsed} but are not
     * registered in the plugin's {@code PresetRegistry}. Order matches the order of
     * occurrence in the parsed command.
     */
    private List<String> findMissingPromptPresets(ParsedCommand parsed) {
        var registry = plugin.getPresetRegistry();
        if (registry == null) {
            // No registry wired (e.g. very early startup). Treat all preset refs as
            // missing so the player sees a clear error instead of a silent pass.
            return parsed.promptTags().stream()
                    .filter(PromptTag::isPreset)
                    .map(PromptTag::displayText)
                    .toList();
        }
        return parsed.promptTags().stream()
                .filter(PromptTag::isPreset)
                .map(PromptTag::displayText)
                .filter(id -> registry.getPrompt(id).isEmpty())
                .toList();
    }

    /**
     * Returns the list of preset post-command ids that appear in {@code parsed} but are
     * not registered in the plugin's {@code PresetRegistry}. Order matches the order of
     * occurrence in the parsed command.
     */
    private List<String> findMissingPostCommandPresets(ParsedCommand parsed) {
        var registry = plugin.getPresetRegistry();
        if (registry == null) {
            return parsed.postCmds().stream()
                    .filter(PostCommandMeta::isPreset)
                    .map(PostCommandMeta::command)
                    .toList();
        }
        return parsed.postCmds().stream()
                .filter(PostCommandMeta::isPreset)
                .map(PostCommandMeta::command)
                .filter(id -> registry.getPostCommand(id).isEmpty())
                .toList();
    }

    /**
     * Logs a severe warning to the console and sends a localized error message to the
     * player when a command references one or more unknown preset ids. Per the spec, the
     * command must not be executed and the player must be told why.
     */
    private void failFastMissingPresets(
            Player player,
            String commandLine,
            List<String> missingPrompts,
            List<String> missingPostCmds) {
        var all = new java.util.ArrayList<String>();
        if (!missingPrompts.isEmpty()) {
            all.add("prompts=" + missingPrompts);
        }
        if (!missingPostCmds.isEmpty()) {
            all.add("post-commands=" + missingPostCmds);
        }
        var summary = String.join(", ", all);
        plugin.getPluginLogger().err(
                "Fail-fast: command from " + player.getName()
                        + " references unknown preset(s) [" + summary
                        + "] — command NOT executed. Raw: " + commandLine);
        var i18n = plugin.getConfigLoader().getI18n();
        player.sendMessage(i18n.get("command.error.missing_preset"));
    }
}
