package dev.cyr1en.promptpaper.engine;

import dev.cyr1en.promptcore.*;
import dev.cyr1en.promptcore.parser.CommandLineParser;
import dev.cyr1en.promptcore.session.PromptSession;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.PromptDefinition;
import dev.cyr1en.promptpaper.util.MiniMessageTagFilter;
import dev.cyr1en.promptpaper.util.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

/**
 * Manages the lifecycle of interactive prompt sessions for players.
 * Parses command lines for prompt tags, tracks per-player sessions,
 * collects answers, and dispatches post-completion/cancellation commands.
 * All session state is held in a {@link ConcurrentHashMap} keyed by player UUID.
 */
public class PromptEngine {

    /** Immutable dispatch context captured before ScreenManager clears its per-player maps. */
    public record DispatchContext(
            ExecuteAs executeAs,
            String permissionKey,
            boolean attachmentRequired,
            List<String> permissionSnapshot) {
        public DispatchContext {
            if (executeAs == null) executeAs = ExecuteAs.PLAYER;
            permissionSnapshot = permissionSnapshot == null
                    ? List.of()
                    : List.copyOf(permissionSnapshot);
        }

        public DispatchContext(ExecuteAs executeAs, String permissionKey, boolean attachmentRequired) {
            this(executeAs, permissionKey, attachmentRequired, List.of());
        }

        public DispatchContext(ExecuteAs executeAs, String permissionKey) {
            this(executeAs, permissionKey, permissionKey != null, List.of());
        }

        public static DispatchContext player() {
            return new DispatchContext(ExecuteAs.PLAYER, null, false, List.of());
        }
    }

    private final CommandPrompter plugin;
    private volatile CommandLineParser parser;
    private final Map<UUID, PromptSession> sessions;
    private final Map<SessionResult, List<PostCommandMeta>> dispatchPcmSnapshots;
    private final Scheduler scheduler;
    private final Object sessionLifecycleMonitor;
    private final AtomicBoolean reloadInProgress;

    public PromptEngine(CommandPrompter plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.parser = buildParser(plugin);
        this.sessions = new ConcurrentHashMap<>();
        this.dispatchPcmSnapshots = java.util.Collections.synchronizedMap(
                new java.util.IdentityHashMap<>());
        this.scheduler = scheduler;
        this.sessionLifecycleMonitor = new Object();
        this.reloadInProgress = new AtomicBoolean();
    }

    /**
     * Creates the command-line parser, optionally with a MiniMessage tag filter.
     *
     * <p>When {@code Ignore-MiniMessage} is enabled in the config and the prompt delimiters are
     * angle brackets, a {@link MiniMessageTagFilter} is attached so that MiniMessage formatting
     * tags (e.g. {@code <red>}, {@code </red>}) are not treated as prompts.
     */
    private CommandLineParser buildParser(CommandPrompter plugin) {
        var config = plugin.getConfigLoader().getConfig();
        if (config == null) {
            return new CommandLineParser();
        }
        var regex = config.argumentRegex();
        if (regex == null || regex.isBlank()) {
            regex = "<.*?>";
        }
        ParserConfig parserConfig;
        try {
            parserConfig = ParserConfig.fromArgumentRegex(regex);
        } catch (IllegalArgumentException e) {
            plugin.getPluginLogger().err("Failed to parse argument regex '" + regex + "': " + e.getMessage() + ". Falling back to angle brackets.");
            parserConfig = ParserConfig.ANGLE_BRACKETS;
        }

        boolean useFilter = config.ignoreMiniMessage() && "<".equals(parserConfig.opening()) && ">".equals(parserConfig.closing());
        var filter = useFilter ? new MiniMessageTagFilter() : null;
        return new CommandLineParser(parserConfig, filter);
    }

    public CommandLineParser getParser() {
        return this.parser;
    }

    public void reloadParser() {
        this.parser = buildParser(plugin);
    }

    /**
     * Acquires the reload barrier before any player teardown is scheduled.
     *
     * <p>The monitor makes the gate transition atomic with the session-creation path. A prompt
     * that is already in the small hand-off between parsing and screen creation either completes
     * that hand-off before the barrier is acquired or is rejected before it can create screen
     * state.
     *
     * @return {@code true} when this caller owns the barrier, or {@code false} when another reload
     *     is already in progress
     */
    public boolean beginReload() {
        synchronized (sessionLifecycleMonitor) {
            return reloadInProgress.compareAndSet(false, true);
        }
    }

    /** Releases the reload barrier after the complete reload attempt has finished. */
    public void endReload() {
        synchronized (sessionLifecycleMonitor) {
            reloadInProgress.set(false);
        }
    }

    /** Whether new prompt sessions are currently blocked by a configuration reload. */
    public boolean isReloadInProgress() {
        return reloadInProgress.get();
    }

    /**
     * Sends the reload-gate feedback when a new player session is rejected.
     *
     * <p>This uses an existing localized reload failure key because the message catalog is owned
     * by the resource/configuration lane. The raw tagged command is still rejected by the caller.
     */
    public boolean rejectIfReloading(Player player) {
        if (!reloadInProgress.get()) return false;
        try {
            player.sendMessage(plugin.getConfigLoader().getI18n().get(
                    "command.reload.failed",
                    player,
                    Placeholder.of("error", "a configuration reload is in progress")));
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Unable to send reload-gate feedback: " + e.getMessage());
        }
        return true;
    }

    /**
     * Runs the final session-to-screen hand-off while holding the same monitor used by the reload
     * barrier. This prevents a reload from being acquired between session acceptance and creation
     * of screen/timeout state.
     */
    public boolean runIfReloadNotInProgress(Runnable action) {
        synchronized (sessionLifecycleMonitor) {
            if (reloadInProgress.get()) return false;
            action.run();
            return true;
        }
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
        if (rejectIfReloading(player)) return Optional.empty();
        var config = plugin.getConfigLoader().getConfig();
        if (config.enablePermission() && !player.hasPermission("promptpaper.use")) {
            plugin.getPluginLogger().debug("Player " + player.getName()
                    + " lacks promptpaper.use, skipping prompt intercept");
            return Optional.empty();
        }
        var parsed = getParser().parse(commandLine);

        // Fail-fast: any unresolved preset ID or validator alias aborts the command flow.
        var missingPrompts = findMissingPromptPresets(parsed);
        var missingPostCmds = findMissingPostCommandPresets(parsed);
        var missingValidators = findMissingValidators(parsed);
        if (!missingPrompts.isEmpty() || !missingPostCmds.isEmpty() || !missingValidators.isEmpty()) {
            failFastMissing(player, commandLine, missingPrompts, missingPostCmds, missingValidators);
            return Optional.empty();
        }

        if (!parsed.hasPrompts()) {
            plugin.getPluginLogger().debug("No prompts in command from " + player.getName());
            return Optional.empty();
        }

        if (hasActiveSession(player)) {
            plugin.getPluginLogger().debug("Player " + player.getName() + " already has an active session, aborting new session");
            player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.error.session_active", player));
            return Optional.empty();
        }

        var effectiveParsed = applyPresetSanitize(parsed);

        var accepted = new AtomicBoolean();
        boolean rejectedByReload;
        synchronized (sessionLifecycleMonitor) {
            rejectedByReload = reloadInProgress.get();
            if (!rejectedByReload) {
                sessions.compute(player.getUniqueId(), (uuid, existing) -> {
                    if (existing != null && existing.isActive()) return existing;
                    accepted.set(true);
                    return PromptSession.start(uuid.toString(), effectiveParsed);
                });
            }
        }
        if (rejectedByReload) {
            rejectIfReloading(player);
            return Optional.empty();
        }
        if (!accepted.get()) {
            plugin.getPluginLogger().debug("Player " + player.getName()
                    + " already has an active session, aborting new session");
            player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.error.session_active", player));
            return Optional.empty();
        }
        plugin.getPluginLogger().debug("Intercepted " + effectiveParsed.promptTags().size()
                + " prompts for " + player.getName());
        return Optional.of(effectiveParsed);
    }

    /**
     * Overlays each preset prompt's configured {@code sanitize} flag onto its parsed tag before a
     * session starts. The parser defaults preset tags to {@code sanitize = true}, but the preset
     * definition is authoritative: a preset configured with {@code sanitize: false} must keep the
     * player's color codes intact. Commands without preset tags return the same parsed command
     * object unchanged.
     */
    private ParsedCommand applyPresetSanitize(ParsedCommand parsed) {
        if (parsed.promptTags().stream().noneMatch(PromptTag::isPreset)) return parsed;
        var registry = plugin.getPresetRegistry();
        var adjustedTags = parsed.promptTags().stream()
                .map(tag -> {
                    if (!tag.isPreset() || registry == null) return tag;
                    // Fail-fast above already rejected unknown ids; fall back defensively.
                    var sanitize = registry.getPrompt(tag.displayText())
                            .map(PromptDefinition::sanitize)
                            .orElse(tag.sanitize());
                    return new PromptTag(
                            tag.rawTag(),
                            tag.key(),
                            tag.filter(),
                            tag.displayText(),
                            sanitize,
                            tag.validatorAlias(),
                            tag.type(),
                            tag.subTags(),
                            tag.preset(),
                            tag.title());
                })
                .toList();
        return new ParsedCommand(
                parsed.templateCommand(),
                adjustedTags,
                parsed.postCmds(),
                parsed.parserConfig(),
                parsed.rawTemplateCommand(),
                parsed.templateSpans());
    }

    /**
     * Whether the given command line contains at least one tag (prompt or PCM). Useful for
     * callers that need to distinguish "no tag form at all" from "had tag form but
     * parsing returned empty for some reason" — for example, the fail-fast path that
     * must cancel the underlying command dispatch.
     */
    public boolean commandHasTagForm(String commandLine) {
        return getParser().hasTagForm(commandLine);
    }

    /**
     * Whether the given command line contains at least one <b>preset reference</b>
     * ({@code <@id>} or {@code <!@id>}). The listener uses this to decide whether
     * to cancel the {@link org.bukkit.event.player.PlayerCommandPreprocessEvent}
     * even when no prompt session was started — the literal preset tag must never
     * reach the underlying command dispatcher.
     */
    public boolean hasPresetReferences(String commandLine) {
        if (!getParser().hasTagForm(commandLine)) return false;
        var parsed = getParser().parse(commandLine);
        return parsed.promptTags().stream().anyMatch(PromptTag::isPreset)
                || parsed.postCmds().stream().anyMatch(PostCommandMeta::isPreset);
    }

    /**
     * Submits a single answer for the player's current prompt session.
     *
     * @return the completed session result if all prompts are now answered, or empty
     */
    public Optional<SessionResult> submit(Player player, String answer) {
        var found = new AtomicBoolean();
        var completed = new AtomicReference<SessionResult>();
        sessions.compute(player.getUniqueId(), (uuid, session) -> {
            if (session == null || !session.isActive()) return session;
            found.set(true);
            var next = session.submitAnswer(answer);
            if (next.isComplete()) {
                var result = next.finish();
                rememberAllPCMs(next, result);
                completed.set(result);
                return null;
            }
            return next;
        });
        if (!found.get()) {
            plugin.getPluginLogger().debug("No active session for " + player.getName() + " on submit");
            return Optional.empty();
        }
        if (completed.get() != null) {
            plugin.getPluginLogger().debug("Session complete for " + player.getName());
            return Optional.of(completed.get());
        }
        var session = sessions.get(player.getUniqueId());
        plugin.getPluginLogger().debug("Answer accepted for " + player.getName()
                + ", " + (session != null ? session.remainingCount() : 0) + " remaining");
        return Optional.empty();
    }

    /**
     * Submit a batch of answers to the current prompt. The current prompt
     * must be a compound tag with the matching number of sub-answers. See
     * {@link dev.cyr1en.promptcore.session.PromptSession#submitAnswers(List)}
     * for the size-validation rules.
     */
    public Optional<SessionResult> submitAnswers(Player player, java.util.List<String> answers) {
        var found = new AtomicBoolean();
        var completed = new AtomicReference<SessionResult>();
        sessions.compute(player.getUniqueId(), (uuid, session) -> {
            if (session == null || !session.isActive()) return session;
            found.set(true);
            var next = session.submitAnswers(answers);
            if (next.isComplete()) {
                var result = next.finish();
                rememberAllPCMs(next, result);
                completed.set(result);
                return null;
            }
            return next;
        });
        if (!found.get()) {
            plugin.getPluginLogger().debug("No active session for " + player.getName() + " on submitAnswers");
            return Optional.empty();
        }
        if (completed.get() != null) {
            plugin.getPluginLogger().debug("Session complete for " + player.getName());
            return Optional.of(completed.get());
        }
        var session = sessions.get(player.getUniqueId());
        plugin.getPluginLogger().debug("Compound answers accepted for " + player.getName()
                + ", " + (session != null ? session.remainingCount() : 0) + " remaining");
        return Optional.empty();
    }

    /**
     * Submit a batch of answers to the current prompt with an explicit expected
     * answer count. This is the arity-aware entry point for dialog flows whose
     * effective answer count is not derivable from the parsed tag shape — JSON
     * dialog presets may submit 0, 1, or N answers. Completion/finish behavior
     * mirrors {@link #submit(Player, String)}. See
     * {@link dev.cyr1en.promptcore.session.PromptSession#submitAnswers(List, int)}
     * for the validation rules.
     *
     * @return the completed session result if all prompts are now answered, or empty
     */
    public Optional<SessionResult> submitAnswers(
            Player player, java.util.List<String> answers, int expectedCount) {
        var found = new AtomicBoolean();
        var completed = new AtomicReference<SessionResult>();
        sessions.compute(player.getUniqueId(), (uuid, session) -> {
            if (session == null || !session.isActive()) return session;
            found.set(true);
            var next = session.submitAnswers(answers, expectedCount);
            if (next.isComplete()) {
                var result = next.finish();
                rememberAllPCMs(next, result);
                completed.set(result);
                return null;
            }
            return next;
        });
        if (!found.get()) {
            plugin.getPluginLogger().debug("No active session for " + player.getName()
                    + " on submitAnswers(expected=" + expectedCount + ")");
            return Optional.empty();
        }
        if (completed.get() != null) {
            plugin.getPluginLogger().debug("Session complete for " + player.getName());
            return Optional.of(completed.get());
        }
        var session = sessions.get(player.getUniqueId());
        plugin.getPluginLogger().debug("Dialog answers accepted for " + player.getName()
                + ", " + (session != null ? session.remainingCount() : 0) + " remaining");
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
        cancel(player, reason, DispatchContext.player());
    }

    /** Atomically cancels a session and dispatches its PCMs with the captured context. */
    public void cancel(Player player, CancelReason reason, DispatchContext dispatchContext) {
        var cancelledResult = new AtomicReference<SessionResult>();
        sessions.compute(player.getUniqueId(), (uuid, session) -> {
            if (session == null || !session.isActive()) return session;
            var cancelled = session.cancel(reason);
            if (cancelled.isCancelled()) {
                var result = cancelled.finish();
                rememberAllPCMs(cancelled, result);
                cancelledResult.set(result);
            }
            return null;
        });
        if (cancelledResult.get() == null) {
            plugin.getPluginLogger().debug("No session to cancel for " + player.getName());
            return;
        }
        plugin.getPluginLogger().debug("Session cancelled for " + player.getName() + " reason=" + reason);
        dispatchPCMs(player, cancelledResult.get(), true, dispatchContext);
    }

    /**
     * Cancels all active sessions (e.g. during plugin shutdown).
     */
    public void cancelAll() {
        var snapshot = new java.util.ArrayList<>(sessions.keySet());
        for (var uuid : snapshot) {
            var player = plugin.getServer().getPlayer(uuid);
            if (player == null) {
                sessions.remove(uuid);
                continue;
            }
            try {
                var task = player.getScheduler().run(
                        plugin,
                        scheduledTask -> cancel(player, CancelReason.MANUAL),
                        () -> discard(uuid));
                if (task == null) discard(uuid);
            } catch (Throwable t) {
                plugin.getPluginLogger().debug("Unable to cancel session for retired player " + uuid);
                discard(uuid);
            }
        }
    }

    /** Drops session state without dispatching cancellation PCMs. */
    public void discard(UUID uuid) {
        if (uuid != null) sessions.remove(uuid);
    }

    /** Drops all session state without invoking player-affine work. */
    public void discardAll() {
        sessions.clear();
        dispatchPcmSnapshots.clear();
    }

    /**
     * Returns whether the player has an active (non-complete, non-cancelled) session.
     */
    public boolean hasActiveSession(Player player) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    /**
     * Remembers the complete parsed PCM source list for a finished result.
     *
     * <p>Prompt-core intentionally puts only the marker-matching list in a normal
     * {@link SessionResult}. The runtime dispatcher must also see the opposite-marker preset
     * references so their configured execution policy can be authoritative. Legacy references are
     * re-resolved here and are still filtered by {@link PostCommandResolver} using their marker.
     */
    private void rememberAllPCMs(PromptSession finishedSession, SessionResult result) {
        var all = finishedSession.parsedCommand().postCmds().stream()
                .map(pcm -> resolvePCMReferences(pcm, result.answers()))
                .toList();
        dispatchPcmSnapshots.put(result, all);
    }

    /** Mirrors the core session's one-pass substitution for the PCMs added from the opposite list. */
    private PostCommandMeta resolvePCMReferences(PostCommandMeta pcm, List<String> answers) {
        var matcher = java.util.regex.Pattern.compile("\\{(\\d+)}").matcher(pcm.command());
        var resolved = new StringBuffer();
        while (matcher.find()) {
            int index;
            try {
                index = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                index = -1;
            }
            var replacement = index >= 0 && index < answers.size() ? answers.get(index) : "";
            matcher.appendReplacement(
                    resolved, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        var command = resolved.toString().replaceAll("\\s+", " ").trim();
        return new PostCommandMeta(
                command,
                pcm.answerIndices(),
                pcm.delayTicks(),
                pcm.onCancel(),
                pcm.dispatchTarget(),
                pcm.preset());
    }

    /**
     * Dispatches post-completion or on-cancel commands (PCMs) from a session result.
     *
     * <p>Each PCM is routed through {@link PostCommandResolver}, which:
     *
     * <ul>
     *   <li>Resolves preset references ({@code <!@id>}) against the
     *       {@code PresetRegistry}.
     *   <li>Filters by the preset's {@code executionPolicy}; legacy PCMs retain
     *       the parser's {@code onCancel} lifecycle hint.
     *   <li>Resolves session-scoped placeholders ({@code {player}},
     *       {@code {input}}, {@code {input:N}}, PAPI {@code %…%}).
     *   <li>Schedules the dispatch with the preset's / legacy delay via the
     *       Folia-safe {@link Scheduler}.
     * </ul>
     *
     * <p>Commands with a positive delay are scheduled; others run synchronously.
     */
    public void dispatchPCMs(Player player, SessionResult result, boolean wasCancelled) {
        dispatchPCMs(player, result, wasCancelled, DispatchContext.player());
    }

    /** Dispatches PCMs using an immutable snapshot of the original dispatch context. */
    public void dispatchPCMs(
            Player player,
            SessionResult result,
            boolean wasCancelled,
            DispatchContext dispatchContext) {
        if (dispatchContext == null) dispatchContext = DispatchContext.player();
        // SessionResult's public lifecycle lists are parser-prefiltered. The engine keeps an
        // identity-bound snapshot of the complete parsed PCM source list for results it creates;
        // use that snapshot so a preset whose source marker disagrees with its configured policy
        // is still considered. The resolver applies the authoritative preset policy below.
        List<PostCommandMeta> pcms;
        synchronized (dispatchPcmSnapshots) {
            pcms = dispatchPcmSnapshots.remove(result);
        }
        if (pcms == null) {
            pcms = wasCancelled ? result.onCancelCmds() : result.onCompleteCmds();
        }
        plugin.getPluginLogger().debug("Dispatching " + pcms.size() + " PCMs for "
                + player.getName() + " (cancelled=" + wasCancelled + ")");
        var resolver = new PostCommandResolver(plugin);
        var dispatchedPresetIds = new java.util.HashSet<String>();
        for (var pcm : pcms) {
            var resolved = resolver.resolve(player, pcm, wasCancelled, result.answers());
            if (resolved.isEmpty()) {
                plugin.getPluginLogger().debug(
                        "PCM filtered out: raw=" + pcm.command()
                                + " preset=" + pcm.isPreset()
                                + " onCancel=" + pcm.onCancel());
                continue;
            }
            if (resolved.get().preset()
                    && !dispatchedPresetIds.add(resolved.get().sourceId())) {
                plugin.getPluginLogger().debug(
                        "Skipping duplicate preset PCM: " + resolved.get().sourceId());
                continue;
            }
            schedule(player, resolved.get(), dispatchContext);
        }
    }

    /** Schedules a single resolved post-command for execution. */
    private void schedule(
            Player player,
            PostCommandResolver.Resolved resolved,
            DispatchContext dispatchContext) {
        var effectiveExecuteAs = resolved.inheritDispatch()
                ? dispatchContext.executeAs()
                : resolved.executeAs();
        if (effectiveExecuteAs == ExecuteAs.PLAYER) {
            if (resolved.delayTicks() > 0) {
                plugin.getPluginLogger().debug(
                        "Scheduling PCM: source=" + resolved.sourceId()
                                + " preset=" + resolved.preset()
                                + " delay=" + resolved.delayTicks() + "t"
                                + " target=" + effectiveExecuteAs
                                + " cmd=" + resolved.command());
                try {
                    player.getScheduler().runDelayed(
                            plugin,
                            scheduledTask -> executeResolved(player, resolved, dispatchContext),
                            null,
                            resolved.delayTicks());
                } catch (Throwable t) {
                    plugin.getPluginLogger().debug("Player PCM task retired before scheduling: "
                            + t.getMessage());
                }
            } else {
                plugin.getPluginLogger().debug(
                        "Dispatching PCM: source=" + resolved.sourceId()
                                + " preset=" + resolved.preset()
                                + " target=" + effectiveExecuteAs
                                + " cmd=" + resolved.command());
                try {
                    player.getScheduler().run(
                            plugin,
                            scheduledTask -> executeResolved(player, resolved, dispatchContext),
                            null);
                } catch (Throwable t) {
                    plugin.getPluginLogger().debug("Player PCM task retired before scheduling: "
                            + t.getMessage());
                }
            }
            return;
        }

        Runnable task = () -> executeResolved(player, resolved, dispatchContext);
        if (resolved.delayTicks() > 0) {
            plugin.getPluginLogger().debug(
                    "Scheduling PCM: source=" + resolved.sourceId()
                            + " preset=" + resolved.preset()
                            + " delay=" + resolved.delayTicks() + "t"
                            + " target=" + effectiveExecuteAs
                            + " cmd=" + resolved.command());
            scheduler.runLater(task, resolved.delayTicks());
        } else {
            plugin.getPluginLogger().debug(
                    "Dispatching PCM: source=" + resolved.sourceId()
                            + " preset=" + resolved.preset()
                            + " target=" + effectiveExecuteAs
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
    private void executeResolved(
            Player player,
            PostCommandResolver.Resolved resolved,
            DispatchContext dispatchContext) {
        var executeAs = resolved.inheritDispatch()
                ? dispatchContext.executeAs()
                : resolved.executeAs();
        if (resolved.inheritDispatch()
                && executeAs == ExecuteAs.PLAYER
                && dispatchContext.attachmentRequired()) {
            executeWithAttachment(
                    player,
                    resolved.command(),
                    dispatchContext.permissionKey(),
                    dispatchContext.permissionSnapshot(),
                    resolved.delayTicks() > 0);
            return;
        }
        var sender = switch (executeAs) {
            case CONSOLE -> plugin.getServer().getConsoleSender();
            case PLAYER -> player;
        };
        try {
            if (!plugin.getServer().dispatchCommand(sender, resolved.command())) {
                reportCommandFailure(player, resolved.command(), "dispatch returned false");
            }
        } catch (Exception e) {
            reportCommandFailure(player, resolved.command(), e.getMessage());
        }
    }

    private void executeWithAttachment(
            Player player,
            String command,
            String permissionKey,
            List<String> capturedPermissions,
            boolean delayed) {
        if (permissionKey == null || permissionKey.isBlank()
                || capturedPermissions == null || capturedPermissions.isEmpty()) {
            plugin.getPluginLogger().err(
                    "Refusing post-command attachment dispatch for invalid captured key/snapshot: "
                            + permissionKey);
            reportCommandFailure(player, command, "invalid permission attachment");
            return;
        }

        if (delayed) {
            var currentPermissions = readPermissionSnapshot(permissionKey);
            if (currentPermissions.isEmpty()) {
                plugin.getPluginLogger().err(
                        "Skipping delayed attachment PCM '" + command
                                + "': permission key was removed or is unavailable: "
                                + permissionKey);
                reportCommandFailure(player, command, "permission attachment changed after scheduling");
                return;
            }
            if (!currentPermissions.get().equals(capturedPermissions)) {
                plugin.getPluginLogger().err(
                        "Skipping delayed attachment PCM '" + command
                                + "': permissions for key " + permissionKey
                                + " changed from " + capturedPermissions + " to "
                                + currentPermissions.get());
                reportCommandFailure(player, command, "permission attachment changed after scheduling");
                return;
            }
        }

        var permissions = capturedPermissions.toArray(new String[0]);
        var config = plugin.getConfigLoader().getConfig();

        var attachment = new AtomicReference<PermissionAttachment>();
        var removed = new AtomicBoolean();
        Runnable remove = () -> {
            var current = attachment.get();
            if (current != null && removed.compareAndSet(false, true)) {
                try {
                    player.removeAttachment(current);
                } catch (Exception e) {
                    plugin.getPluginLogger().debug("Unable to remove permission attachment");
                }
            }
        };
        boolean removalScheduled = false;
        boolean failed = false;
        try {
            attachment.set(player.addAttachment(plugin));
            if (attachment.get() == null) {
                reportCommandFailure(player, command, "unable to create permission attachment");
                return;
            }
            for (var permission : permissions) attachment.get().setPermission(permission, true);
            attachment.get().getPermissible().recalculatePermissions();
            if (!plugin.getServer().dispatchCommand(player, command)) {
                failed = true;
                reportCommandFailure(player, command, "dispatch returned false");
            }
        } catch (Exception e) {
            failed = true;
            reportCommandFailure(player, command, e.getMessage());
        }
        if (!failed && !removed.get() && config != null && config.permissionAttachmentTicks() > 0) {
            try {
                var task = player.getScheduler().runDelayed(
                        plugin,
                        scheduledTask -> remove.run(),
                        () -> {},
                        config.permissionAttachmentTicks());
                removalScheduled = task != null;
            } catch (Exception e) {
                plugin.getPluginLogger().debug("Attachment removal scheduling failed: "
                        + e.getMessage());
            }
        }
        if (!removalScheduled) remove.run();
    }

    /** Reads the current attachment definition only for delayed fail-closed validation. */
    private Optional<List<String>> readPermissionSnapshot(String permissionKey) {
        if (permissionKey == null || permissionKey.isBlank()) return Optional.empty();
        try {
            var config = plugin.getConfigLoader().getConfig();
            var permissions = config == null ? null : config.getPermissionAttachment(permissionKey);
            if (permissions == null || permissions.length == 0) return Optional.empty();
            return Optional.of(List.copyOf(java.util.Arrays.asList(permissions)));
        } catch (Exception e) {
            plugin.getPluginLogger().debug(
                    "Unable to revalidate permission attachment key " + permissionKey + ": "
                            + e.getMessage());
            return Optional.empty();
        }
    }

    private void reportCommandFailure(Player player, String command, String detail) {
        var message = detail != null ? detail : "unknown error";
        plugin.getPluginLogger().info("Command dispatch failed for '" + command + "': " + message);
        try {
            var task = player.getScheduler().run(
                    plugin,
                    scheduledTask -> player.sendMessage(plugin.getConfigLoader().getI18n().get(
                            "prompt.error.command_failed",
                            player,
                            Placeholder.of("message", message))),
                    null);
            if (task == null) {
                plugin.getPluginLogger().debug("Unable to send command failure to retired player");
            }
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Unable to send command failure feedback: " + e.getMessage());
        }
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
            // If no registry is wired, treat all preset references as missing.
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
     * Returns the list of validator aliases that appear in {@code parsed} but are
     * not configured in {@code PromptConfig}. Order matches occurrence order.
     */
    private List<String> findMissingValidators(ParsedCommand parsed) {
        var configLoader = plugin.getConfigLoader();
        if (configLoader == null) return List.of();
        var promptConfig = configLoader.getPromptConfig();
        if (promptConfig == null) return List.of();
        var missing = new java.util.ArrayList<String>();
        for (var tag : parsed.promptTags()) {
            checkMissingValidator(tag.validatorAlias(), promptConfig, missing);
            if (tag.subTags() != null) {
                for (var subTag : tag.subTags()) {
                    checkMissingValidator(subTag.validatorAlias(), promptConfig, missing);
                }
            }
        }
        return missing;
    }

    private void checkMissingValidator(
            String alias, dev.cyr1en.promptpaper.config.PromptConfig config, List<String> missing) {
        if (alias != null && !alias.isBlank() && !config.hasValidator(alias)) {
            missing.add(alias);
        }
    }

    /**
     * Logs a severe warning to the console and sends a localized error message to the
     * player when a command references one or more unknown preset ids or validator aliases.
     * Per the spec, the command must not be executed and the player must be told why.
     */
    private void failFastMissing(
            Player player,
            String commandLine,
            List<String> missingPrompts,
            List<String> missingPostCmds,
            List<String> missingValidators) {
        var all = new java.util.ArrayList<String>();
        if (!missingPrompts.isEmpty()) {
            all.add("prompts=" + missingPrompts);
        }
        if (!missingPostCmds.isEmpty()) {
            all.add("post-commands=" + missingPostCmds);
        }
        if (!missingValidators.isEmpty()) {
            all.add("validators=" + missingValidators);
        }
        var summary = String.join(", ", all);
        plugin.getPluginLogger().err(
                "Fail-fast: command from " + player.getName()
                        + " references unknown element(s) [" + summary
                        + "] — command NOT executed. Raw: " + commandLine);
        var i18n = plugin.getConfigLoader().getI18n();
        if (!missingValidators.isEmpty() && missingPrompts.isEmpty() && missingPostCmds.isEmpty()) {
            player.sendMessage(i18n.get("command.error.missing_validator", player));
        } else {
            player.sendMessage(i18n.get("command.error.missing_preset", player));
        }
    }
}
