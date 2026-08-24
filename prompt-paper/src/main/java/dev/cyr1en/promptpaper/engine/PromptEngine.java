package dev.cyr1en.promptpaper.engine;

import dev.cyr1en.promptcore.*;
import dev.cyr1en.promptcore.parser.CommandLineParser;
import dev.cyr1en.promptcore.session.PromptSession;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.CommandPrompterConfig;
import dev.cyr1en.promptpaper.custom.CustomScreenRegistry;
import dev.cyr1en.promptpaper.custom.ScreenKeyResolver;
import dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.PromptDefinition;
import dev.cyr1en.promptpaper.util.MiniMessageTagFilter;
import dev.cyr1en.promptpaper.util.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

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

        public static DispatchContext console() {
            return new DispatchContext(ExecuteAs.CONSOLE, null, false, List.of());
        }

        public boolean isConsoleDelegated() {
            return executeAs == ExecuteAs.CONSOLE;
        }

        public boolean isDelegated() {
            return executeAs == ExecuteAs.CONSOLE || attachmentRequired;
        }

        public DispatchContextSnapshot toSnapshot() {
            return new DispatchContextSnapshot(executeAs, permissionKey, attachmentRequired, permissionSnapshot);
        }
    }

    private final CommandPrompter plugin;
    private volatile CommandLineParser parser;
    private final Map<UUID, PromptSession> sessions;
    private final Map<UUID, SessionInceptionArtifacts> sessionInceptionArtifacts;
    private final Scheduler scheduler;
    private final Object sessionLifecycleMonitor;
    private final AtomicBoolean reloadInProgress;
    private final java.util.concurrent.atomic.AtomicLong sessionIncarnationSequence;
    private final ScreenKeyResolver screenKeyResolver;
    private volatile dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry executionRegistry;
    private volatile dev.cyr1en.promptpaper.approval.PlayerInteractionLeaseRegistry leaseRegistry;
    private volatile dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator executionCoordinator;
    private final Map<UUID, InterceptResult> lastInterceptResults;
    private static final java.util.regex.Pattern C0_CONTROLS = java.util.regex.Pattern.compile("[\\u0000-\\u001F\\u007F]");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final int MAX_SUMMARY_LENGTH = 256;
    private static final int MAX_ID_LENGTH = 64;

    public PromptEngine(CommandPrompter plugin, Scheduler scheduler) {
        this(plugin, scheduler, defaultScreenKeyResolver(plugin), null, null);
    }

    public PromptEngine(CommandPrompter plugin, Scheduler scheduler, ScreenKeyResolver screenKeyResolver) {
        this(plugin, scheduler, screenKeyResolver, null, null);
    }

    public PromptEngine(
            CommandPrompter plugin,
            Scheduler scheduler,
            ScreenKeyResolver screenKeyResolver,
            dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry executionRegistry) {
        this(plugin, scheduler, screenKeyResolver, executionRegistry, null);
    }

    public PromptEngine(
            CommandPrompter plugin,
            Scheduler scheduler,
            ScreenKeyResolver screenKeyResolver,
            dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry executionRegistry,
            dev.cyr1en.promptpaper.approval.PlayerInteractionLeaseRegistry leaseRegistry) {
        this.plugin = plugin;
        this.parser = buildParser(plugin);
        this.sessions = new ConcurrentHashMap<>();
        this.sessionInceptionArtifacts = new ConcurrentHashMap<>();
        this.scheduler = scheduler;
        this.sessionLifecycleMonitor = new Object();
        this.reloadInProgress = new AtomicBoolean();
        this.sessionIncarnationSequence = new java.util.concurrent.atomic.AtomicLong();
        this.screenKeyResolver = screenKeyResolver != null
                ? screenKeyResolver
                : defaultScreenKeyResolver(plugin);
        this.executionRegistry = executionRegistry;
        this.leaseRegistry = leaseRegistry != null
                ? leaseRegistry
                : new dev.cyr1en.promptpaper.approval.PlayerInteractionLeaseRegistry();
        this.lastInterceptResults = new ConcurrentHashMap<>();
    }

    public void setExecutionRegistry(dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry executionRegistry) {
        this.executionRegistry = executionRegistry;
    }

    public void setLeaseRegistry(dev.cyr1en.promptpaper.approval.PlayerInteractionLeaseRegistry leaseRegistry) {
        this.leaseRegistry = leaseRegistry != null
                ? leaseRegistry
                : new dev.cyr1en.promptpaper.approval.PlayerInteractionLeaseRegistry();
    }

    public dev.cyr1en.promptpaper.approval.PlayerInteractionLeaseRegistry getEffectiveLeaseRegistry() {
        var reg = leaseRegistry;
        if (reg != null) return reg;
        if (plugin != null && plugin.getApprovalCoordinator() != null) {
            reg = plugin.getApprovalCoordinator().getLeaseRegistry();
            if (reg != null) return reg;
        }
        return new dev.cyr1en.promptpaper.approval.PlayerInteractionLeaseRegistry();
    }

    public dev.cyr1en.promptpaper.approval.PlayerInteractionLeaseRegistry getLeaseRegistry() {
        return getEffectiveLeaseRegistry();
    }

    private static ScreenKeyResolver defaultScreenKeyResolver(CommandPrompter plugin) {
        return new ScreenKeyResolver(
                new CustomScreenRegistry(),
                () -> {
                    if (plugin == null) return Map.of();
                    var loader = plugin.getConfigLoader();
                    if (loader == null) return Map.of();
                    var promptConfig = loader.getPromptConfig();
                    return promptConfig != null && promptConfig.getScreenMappings() != null
                            ? promptConfig.getScreenMappings()
                            : Map.of();
                });
    }

    /**
     * Creates the command-line parser, optionally with a MiniMessage tag filter.
     *
     * <p>When {@code Ignore-MiniMessage} is enabled in the config and the prompt delimiters are
     * angle brackets, a {@link MiniMessageTagFilter} is attached so that MiniMessage formatting
     * tags (e.g. {@code <red>}, {@code </red>}) are not treated as prompts.
     */
    private CommandLineParser buildParser(CommandPrompter plugin) {
        if (plugin == null || plugin.getConfigLoader() == null) {
            return this.parser != null ? this.parser : new CommandLineParser();
        }
        var config = plugin.getConfigLoader().getConfig();
        if (config == null) {
            return this.parser != null ? this.parser : new CommandLineParser();
        }
        try {
            return prepareParser(config);
        } catch (Exception e) {
            if (plugin.getPluginLogger() != null) {
                plugin.getPluginLogger().err("Failed to obtain valid parser configuration: " + e.getMessage());
            }
            return this.parser != null ? this.parser : new CommandLineParser();
        }
    }

    /** Builds and validates a parser for a staged configuration without publishing it. */
    public CommandLineParser prepareParser(CommandPrompterConfig config) {
        Objects.requireNonNull(config, "config");
        ParserConfig parserConfig = Objects.requireNonNull(config.parserConfig(), "parserConfig");
        boolean useFilter = config.ignoreMiniMessage() && "<".equals(parserConfig.opening()) && ">".equals(parserConfig.closing());
        var filter = useFilter ? new MiniMessageTagFilter() : null;
        return new CommandLineParser(parserConfig, filter);
    }

    /** Publishes a parser returned by {@link #prepareParser(CommandPrompterConfig)}. */
    public void publishParser(CommandLineParser prepared) {
        this.parser = Objects.requireNonNull(prepared, "prepared parser");
    }

    public CommandLineParser getParser() {
        return this.parser;
    }

    public ScreenKeyResolver getScreenKeyResolver() {
        return this.screenKeyResolver;
    }

    public Optional<InterceptResult> lastInterceptResult(Player player) {
        if (player == null) return Optional.empty();
        return Optional.ofNullable(lastInterceptResults.get(player.getUniqueId()));
    }

    public void reloadParser() {
        try {
            var newParser = buildParser(plugin);
            if (newParser != null) {
                publishParser(newParser);
            }
        } catch (Exception e) {
            if (plugin != null && plugin.getPluginLogger() != null) {
                plugin.getPluginLogger().err("Failed to reload parser; retaining current parser: " + e.getMessage());
            }
        }
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
     * <p>Compatibility wrapper around {@link #interceptResult(Player, String)}.
     *
     * @return the parsed command with prompts, or empty if no prompts were found
     *     (or the command was rejected)
     */
    public Optional<ParsedCommand> intercept(Player player, String commandLine) {
        return interceptResult(player, commandLine).toOptional();
    }

    /**
     * Authoritative interception method that parses the command line, validates all preset
     * references, validator aliases, and screen keys fail-closed, and begins a new prompt session
     * if valid prompts are present.
     *
     * @param player the command sender
     * @param commandLine the raw or normalized command string
     * @return the typed outcome of the interception attempt
     */
    public InterceptResult interceptResult(Player player, String commandLine) {
        if (rejectIfReloading(player) || (plugin != null && !plugin.isPluginActive())) {
            var res = new InterceptResult.RejectedFailClosed("Configuration reload or shutdown in progress");
            recordLastResult(player, res);
            return res;
        }
        var config = plugin != null && plugin.getConfigLoader() != null ? plugin.getConfigLoader().getConfig() : null;
        if (config != null && config.enablePermission() && !player.hasPermission("promptpaper.use")) {
            plugin.getPluginLogger().debug("Player " + player.getName()
                    + " lacks promptpaper.use, skipping prompt intercept");
            var res = InterceptResult.RejectedPermission.INSTANCE;
            recordLastResult(player, res);
            return res;
        }
        ParsedCommand parsed;
        try {
            parsed = getParser().parse(commandLine);
        } catch (IllegalArgumentException e) {
            var safeMsg = C0_CONTROLS.matcher(e.getMessage() != null ? e.getMessage() : "malformed tag").replaceAll("");
            if (safeMsg.length() > 128) safeMsg = safeMsg.substring(0, 128);
            plugin.getPluginLogger().warn("Structural command parse failed for " + player.getName() + ": " + safeMsg);
            var res = new InterceptResult.RejectedFailClosed("Structural command parse failed: " + safeMsg);
            recordLastResult(player, res);
            return res;
        }

        var presetSnapshot = plugin != null && plugin.getPresetRegistry() != null && plugin.getPresetRegistry().getSnapshot() != null
                ? plugin.getPresetRegistry().getSnapshot()
                : dev.cyr1en.promptpaper.preset.PresetSnapshot.empty();

        // Fail-fast: any unresolved preset ID or validator alias aborts the command flow.
        var missingPrompts = findMissingPromptPresets(parsed, presetSnapshot);
        var missingPostCmds = findMissingPostCommandPresets(parsed, presetSnapshot);
        var missingValidators = findMissingValidators(parsed);
        var missingGates = findMissingGatePresets(parsed, presetSnapshot);
        if (!missingPrompts.isEmpty() || !missingPostCmds.isEmpty() || !missingValidators.isEmpty() || !missingGates.isEmpty()) {
            failFastMissing(player, commandLine, missingPrompts, missingPostCmds, missingValidators, missingGates);
            var res = new InterceptResult.RejectedFailClosed("Missing presets or validators");
            recordLastResult(player, res);
            return res;
        }

        // Fail-fast: validate every prompt tag key before session creation.
        var unresolvedKey = findUnresolvedScreenKey(parsed);
        if (unresolvedKey.isPresent()) {
            failFastUnresolvedKey(player, unresolvedKey.get());
            var res = new InterceptResult.RejectedFailClosed("Unresolved screen key: " + sanitizeKey(unresolvedKey.get()));
            recordLastResult(player, res);
            return res;
        }

        if (!parsed.hasPrompts()) {
            if (parsed.hasGates()) {
                var playerUuid = player != null ? String.valueOf(player.getUniqueId()) : "unknown";
                plugin.getPluginLogger().warn("Fail-fast: command from " + playerUuid
                        + " contains approval gates with zero prompts; standalone gate execution is not supported");
                if (plugin.getConfigLoader() != null && plugin.getConfigLoader().getI18n() != null) {
                    player.sendMessage(plugin.getConfigLoader().getI18n().get("command.error.missing_preset", player));
                }
                var res = new InterceptResult.RejectedFailClosed("Commands with approval gates but zero prompts are rejected fail-closed");
                recordLastResult(player, res);
                return res;
            }
            plugin.getPluginLogger().debug("No prompts in command from " + player.getName());
            var res = InterceptResult.NoPrompts.INSTANCE;
            recordLastResult(player, res);
            return res;
        }

        if (hasActiveSession(player) || hasActiveExecution(player) || hasActiveApprovalLease(player)) {
            plugin.getPluginLogger().debug("Player " + player.getName() + " already has an active session or execution, aborting new session");
            if (plugin.getConfigLoader() != null && plugin.getConfigLoader().getI18n() != null) {
                player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.error.session_active", player));
            }
            var res = InterceptResult.RejectedActiveSession.INSTANCE;
            recordLastResult(player, res);
            return res;
        }

        var effectiveParsed = applyPresetSanitize(parsed, presetSnapshot);
        var templateSyntax = plugin != null && plugin.getConfigLoader() != null && plugin.getConfigLoader().getConfig() != null
                ? plugin.getConfigLoader().getConfig().templateSyntax()
                : dev.cyr1en.promptcore.logic.transform.TemplateSyntax.DEFAULT;
        var planDefinition = dev.cyr1en.promptcore.plan.ExecutionPlanAdapter.fromParsedCommand(effectiveParsed, templateSyntax);

        long candidateIncarnation = sessionIncarnationSequence.incrementAndGet();
        var claimOpt = getEffectiveLeaseRegistry().acquirePrompt(player.getUniqueId(), candidateIncarnation);
        if (claimOpt.isEmpty()) {
            plugin.getPluginLogger().debug("Player " + player.getName()
                    + " failed to acquire prompt interaction claim, aborting new session");
            if (plugin.getConfigLoader() != null && plugin.getConfigLoader().getI18n() != null) {
                player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.error.session_active", player));
            }
            var res = InterceptResult.RejectedActiveSession.INSTANCE;
            recordLastResult(player, res);
            return res;
        }

        var accepted = new AtomicBoolean();
        boolean rejectedByReload;
        synchronized (sessionLifecycleMonitor) {
            rejectedByReload = reloadInProgress.get();
            if (!rejectedByReload) {
                if (hasActiveExecution(player)) {
                    // Prevent concurrent inception while execution is active
                } else {
                    sessions.compute(player.getUniqueId(), (uuid, existing) -> {
                        if (existing != null && existing.isActive()) return existing;
                        accepted.set(true);
                        sessionInceptionArtifacts.put(
                                uuid,
                                new SessionInceptionArtifacts(
                                        candidateIncarnation,
                                        0L,
                                        planDefinition,
                                        presetSnapshot,
                                        List.copyOf(effectiveParsed.postCmds())));
                        return PromptSession.start(uuid.toString(), effectiveParsed, candidateIncarnation);
                    });
                }
            }
        }
        if (rejectedByReload) {
            getEffectiveLeaseRegistry().releasePromptIfExact(player.getUniqueId(), candidateIncarnation);
            rejectIfReloading(player);
            var res = new InterceptResult.RejectedFailClosed("Configuration reload in progress");
            recordLastResult(player, res);
            return res;
        }
        if (!accepted.get()) {
            getEffectiveLeaseRegistry().releasePromptIfExact(player.getUniqueId(), candidateIncarnation);
            plugin.getPluginLogger().debug("Player " + player.getName()
                    + " already has an active session, aborting new session");
            if (plugin.getConfigLoader() != null && plugin.getConfigLoader().getI18n() != null) {
                player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.error.session_active", player));
            }
            var res = InterceptResult.RejectedActiveSession.INSTANCE;
            recordLastResult(player, res);
            return res;
        }
        plugin.getPluginLogger().debug("Intercepted " + effectiveParsed.promptTags().size()
                + " prompts for " + player.getName());
        var res = new InterceptResult.Started(effectiveParsed);
        recordLastResult(player, res);
        return res;
    }

    private void recordLastResult(Player player, InterceptResult result) {
        if (player != null && result != null) {
            lastInterceptResults.put(player.getUniqueId(), result);
        }
    }

    /**
     * Overlays each preset prompt's configured {@code sanitize} flag onto its parsed tag before a
     * session starts. The parser defaults preset tags to {@code sanitize = true}, but the preset
     * definition is authoritative: a preset configured with {@code sanitize: false} must keep the
     * player's color codes intact. Commands without preset tags return the same parsed command
     * object unchanged.
     */
    ParsedCommand applyPresetSanitize(ParsedCommand parsed) {
        var snapshot = plugin != null && plugin.getPresetRegistry() != null
                ? plugin.getPresetRegistry().getSnapshot()
                : null;
        return applyPresetSanitize(parsed, snapshot);
    }

    ParsedCommand applyPresetSanitize(ParsedCommand parsed, dev.cyr1en.promptpaper.preset.PresetSnapshot snapshot) {
        if (parsed.promptTags().stream().noneMatch(PromptTag::isPreset)) return parsed;
        var registry = plugin != null ? plugin.getPresetRegistry() : null;
        var adjustedTags = parsed.promptTags().stream()
                .map(tag -> {
                    if (!tag.isPreset()) return tag;
                    // Fail-fast above already rejected unknown ids; fall back defensively.
                    var sanitize = (snapshot != null && snapshot.getPrompt(tag.displayText()).isPresent())
                            ? snapshot.getPrompt(tag.displayText()).map(PromptDefinition::sanitize).orElse(tag.sanitize())
                            : (registry != null
                                    ? registry.getPrompt(tag.displayText()).map(PromptDefinition::sanitize).orElse(tag.sanitize())
                                    : tag.sanitize());
                    return withSanitize(tag, sanitize);
                })
                .toList();
        return withPromptTags(parsed, adjustedTags);
    }

    private static PromptTag withSanitize(PromptTag tag, boolean sanitize) {
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
                tag.title(),
                tag.timeout(),
                tag.flags(),
                tag.breakIf());
    }

    private static ParsedCommand withPromptTags(ParsedCommand parsed, List<PromptTag> promptTags) {
        return new ParsedCommand(
                parsed.templateCommand(),
                promptTags,
                parsed.postCmds(),
                parsed.preDispatchGates(),
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
     * ({@code <@id>} or {@code <!@id>}) or approval gate. The listener uses this to decide whether
     * to cancel the {@link org.bukkit.event.player.PlayerCommandPreprocessEvent}
     * even when no prompt session was started — the literal preset or gate tag must never
     * reach the underlying command dispatcher.
     */
    public boolean hasPresetReferences(String commandLine) {
        if (!getParser().hasTagForm(commandLine)) return false;
        try {
            var parsed = getParser().parse(commandLine);
            return parsed.promptTags().stream().anyMatch(PromptTag::isPreset)
                    || parsed.postCmds().stream().anyMatch(PostCommandMeta::isPreset)
                    || parsed.hasGates();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Whether parsing the given command line produces a structural parser error
     * (e.g. invalid timeout, exceeded tag limits).
     */
    public boolean hasStructuralParseError(String commandLine) {
        if (!getParser().hasTagForm(commandLine)) return false;
        try {
            getParser().parse(commandLine);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
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
            long priorIncarnation = session.incarnation();
            long priorGeneration = session.generation();
            var next = session.submitAnswer(answer);
            if (next.isComplete()) {
                var result = next.finish();
                completed.set(result);
                getEffectiveLeaseRegistry().releasePromptIfExact(uuid, priorIncarnation);
                return null;
            }
            sessionInceptionArtifacts.computeIfPresent(
                    uuid,
                    (u, art) -> {
                        if (art.incarnation() == priorIncarnation && art.generation() == priorGeneration) {
                            return art.withGeneration(next.generation());
                        }
                        return art;
                    });
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
            long priorIncarnation = session.incarnation();
            long priorGeneration = session.generation();
            var next = session.submitAnswers(answers);
            if (next.isComplete()) {
                var result = next.finish();
                completed.set(result);
                getEffectiveLeaseRegistry().releasePromptIfExact(uuid, priorIncarnation);
                return null;
            }
            sessionInceptionArtifacts.computeIfPresent(
                    uuid,
                    (u, art) -> {
                        if (art.incarnation() == priorIncarnation && art.generation() == priorGeneration) {
                            return art.withGeneration(next.generation());
                        }
                        return art;
                    });
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
            long priorIncarnation = session.incarnation();
            long priorGeneration = session.generation();
            var next = session.submitAnswers(answers, expectedCount);
            if (next.isComplete()) {
                var result = next.finish();
                completed.set(result);
                getEffectiveLeaseRegistry().releasePromptIfExact(uuid, priorIncarnation);
                return null;
            }
            sessionInceptionArtifacts.computeIfPresent(
                    uuid,
                    (u, art) -> {
                        if (art.incarnation() == priorIncarnation && art.generation() == priorGeneration) {
                            return art.withGeneration(next.generation());
                        }
                        return art;
                    });
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

    public dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator getExecutionCoordinator() {
        if (executionCoordinator != null) return executionCoordinator;
        if (plugin != null && plugin.getExecutionCoordinator() != null) {
            return plugin.getExecutionCoordinator();
        }
        synchronized (this) {
            if (executionCoordinator == null) {
                var reg = executionRegistry != null
                        ? executionRegistry
                        : (plugin != null && plugin.getExecutionRegistry() != null
                                ? plugin.getExecutionRegistry()
                                : new dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry());
                var primaryDisp = new dev.cyr1en.promptpaper.execution.dispatch.PaperPrimaryCommandDispatcher(plugin, scheduler);
                var actionDisp = new dev.cyr1en.promptpaper.execution.dispatch.PaperImmediateActionDispatcher(plugin, scheduler);
                this.executionCoordinator = new dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator(
                        plugin, this, reg, primaryDisp, actionDisp);
            }
            return executionCoordinator;
        }
    }

    public void setExecutionCoordinator(dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator executionCoordinator) {
        this.executionCoordinator = executionCoordinator;
    }

    /**
     * Cancels the player's active session and dispatches on-cancel commands if present.
     */
    public void cancel(Player player, CancelReason reason) {
        cancel(player, reason, (DispatchContextSnapshot) null, CancellationMode.USER_ACTIONS);
    }

    /** Atomically cancels a session and dispatches its cancellation plan via ExecutionCoordinator. */
    public void cancel(Player player, CancelReason reason, DispatchContext dispatchContext) {
        cancel(player, reason, dispatchContext != null ? dispatchContext.toSnapshot() : null, CancellationMode.USER_ACTIONS);
    }

    /** Atomically cancels a session and dispatches its cancellation plan via ExecutionCoordinator. */
    public void cancel(
            Player player,
            CancelReason reason,
            dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot dispatchContextSnapshot) {
        cancel(player, reason, dispatchContextSnapshot, CancellationMode.USER_ACTIONS);
    }

    public void cancel(
            Player player,
            CancelReason reason,
            DispatchContext dispatchContext,
            CancellationMode mode) {
        cancel(player, reason, dispatchContext != null ? dispatchContext.toSnapshot() : null, mode);
    }

    public void cancel(
            Player player,
            CancelReason reason,
            dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot dispatchContextSnapshot,
            CancellationMode mode) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        if (mode == CancellationMode.DISCARD_ONLY || isReloadInProgress() || (plugin != null && !plugin.isPluginActive())) {
            var cancelledIncarnation = new java.util.concurrent.atomic.AtomicLong(-1L);
            sessions.compute(uuid, (key, existing) -> {
                if (existing != null) {
                    cancelledIncarnation.set(existing.incarnation());
                }
                return null;
            });
            sessionInceptionArtifacts.remove(uuid);
            lastInterceptResults.remove(uuid);
            if (cancelledIncarnation.get() >= 0) {
                getEffectiveLeaseRegistry().releasePromptIfExact(uuid, cancelledIncarnation.get());
            } else {
                getEffectiveLeaseRegistry().releaseAllForPlayer(uuid);
            }
            var coordinator = getExecutionCoordinator();
            if (coordinator != null) {
                coordinator.cancel(uuid);
            }
            return;
        }

        var cancelledResult = new AtomicReference<SessionResult>();
        var cancelledIncarnation = new java.util.concurrent.atomic.AtomicLong(-1L);
        var cancelledGeneration = new java.util.concurrent.atomic.AtomicLong(-1L);
        sessions.compute(player.getUniqueId(), (u, session) -> {
            if (session == null || !session.isActive()) return session;
            cancelledIncarnation.set(session.incarnation());
            cancelledGeneration.set(session.generation());
            var cancelled = session.cancel(reason);
            if (cancelled.isCancelled()) {
                var result = cancelled.finish();
                cancelledResult.set(result);
                getEffectiveLeaseRegistry().releasePromptIfExact(u, session.incarnation());
            }
            return null;
        });
        if (cancelledResult.get() == null) {
            plugin.getPluginLogger().debug("No session to cancel for " + player.getName());
            return;
        }
        plugin.getPluginLogger().debug("Session cancelled for " + player.getName() + " reason=" + reason);

        long inc = cancelledIncarnation.get();
        long gen = cancelledGeneration.get();
        var artifactsOpt = takeInceptionArtifacts(player.getUniqueId(), inc, gen);
        if (artifactsOpt.isEmpty()) {
            var safePlayer = player != null ? C0_CONTROLS.matcher(player.getName()).replaceAll("") : "unknown";
            if (safePlayer.length() > 64) safePlayer = safePlayer.substring(0, 64);
            plugin.getPluginLogger().warn("Missing or mismatched inception artifacts for cancelled session of "
                    + safePlayer + " (inc=" + inc + ", gen=" + gen + "); failing closed with no on-cancel PCM dispatch");
            return;
        }

        var artifacts = artifactsOpt.get();
        var snapshot = artifacts.presetSnapshot();
        List<PostCommandMeta> pcms = artifacts.originalPostCommands();

        var completion = dev.cyr1en.promptpaper.execution.runtime.InputCompletion.of(
                player.getUniqueId(),
                inc,
                gen,
                cancelledResult.get(),
                artifacts.planDefinition(),
                snapshot,
                dispatchContextSnapshot != null
                        ? dispatchContextSnapshot
                        : dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot.player(),
                pcms);

        var coordinator = getExecutionCoordinator();
        if (coordinator != null) {
            coordinator.coordinateCancellation(player, completion);
        }
    }

    /**
     * Cancels all active sessions (e.g. during plugin shutdown).
     */
    public void cancelAll() {
        cancelAll(CancellationMode.DISCARD_ONLY);
    }

    public void cancelAll(CancellationMode mode) {
        var snapshot = new java.util.ArrayList<>(sessions.keySet());
        for (var uuid : snapshot) {
            var player = plugin.getServer().getPlayer(uuid);
            if (player == null) {
                discard(uuid);
                continue;
            }
            try {
                if (mode == CancellationMode.DISCARD_ONLY) {
                    discard(uuid);
                } else {
                    var task = player.getScheduler().run(
                            plugin,
                            scheduledTask -> cancel(player, CancelReason.MANUAL, (dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot) null, mode),
                            () -> discard(uuid));
                    if (task == null) discard(uuid);
                }
            } catch (Throwable t) {
                plugin.getPluginLogger().debug("Unable to cancel session for retired player " + uuid);
                discard(uuid);
            }
        }
    }

    /** Drops session state without dispatching cancellation PCMs. */
    public void discard(UUID uuid) {
        if (uuid != null) {
            var s = sessions.remove(uuid);
            sessionInceptionArtifacts.remove(uuid);
            lastInterceptResults.remove(uuid);
            if (s != null) {
                getEffectiveLeaseRegistry().releasePromptIfExact(uuid, s.incarnation());
            } else {
                getEffectiveLeaseRegistry().releaseAllForPlayer(uuid);
            }
        }
    }

    /** Drops all session state without invoking player-affine work. */
    public void discardAll() {
        for (var entry : sessions.entrySet()) {
            getEffectiveLeaseRegistry().releasePromptIfExact(entry.getKey(), entry.getValue().incarnation());
        }
        sessions.clear();
        sessionInceptionArtifacts.clear();
        lastInterceptResults.clear();
    }

    /**
     * Atomically takes and removes the captured session inception artifacts for the given player UUID,
     * verifying that the stored artifacts match the expected incarnation.
     *
     * @param uuid player UUID
     * @param expectedIncarnation expected session incarnation
     * @return optional containing the inception artifacts, or empty if absent or mismatched
     */
    public Optional<SessionInceptionArtifacts> takeInceptionArtifacts(UUID uuid, long expectedIncarnation) {
        return takeInceptionArtifacts(uuid, expectedIncarnation, -1L);
    }

    /**
     * Atomically takes and removes the captured session inception artifacts for the given player UUID,
     * verifying that the stored artifacts match the expected incarnation and generation.
     *
     * @param uuid player UUID
     * @param expectedIncarnation expected session incarnation
     * @param expectedGeneration expected session generation (or negative to ignore generation)
     * @return optional containing the inception artifacts, or empty if absent or mismatched
     */
    public Optional<SessionInceptionArtifacts> takeInceptionArtifacts(
            UUID uuid, long expectedIncarnation, long expectedGeneration) {
        if (uuid == null) return Optional.empty();
        var taken = new AtomicReference<SessionInceptionArtifacts>();
        sessionInceptionArtifacts.compute(uuid, (key, existing) -> {
            if (existing != null
                    && existing.incarnation() == expectedIncarnation
                    && (expectedGeneration < 0 || existing.generation() == expectedGeneration)) {
                taken.set(existing);
                return null;
            }
            return existing;
        });
        return Optional.ofNullable(taken.get());
    }

    /**
     * Looks up the captured session inception artifacts for the given player UUID without removing.
     *
     * @param uuid player UUID
     * @return optional containing the inception artifacts, or empty if not found
     */
    public Optional<SessionInceptionArtifacts> getInceptionArtifacts(UUID uuid) {
        if (uuid == null) return Optional.empty();
        return Optional.ofNullable(sessionInceptionArtifacts.get(uuid));
    }

    /**
     * Looks up the captured session inception artifacts for the given player UUID and expected incarnation without removing.
     *
     * @param uuid player UUID
     * @param expectedIncarnation expected session incarnation
     * @return optional containing the inception artifacts, or empty if absent or mismatched
     */
    public Optional<SessionInceptionArtifacts> getInceptionArtifacts(UUID uuid, long expectedIncarnation) {
        return getInceptionArtifacts(uuid, expectedIncarnation, -1L);
    }

    /**
     * Looks up the captured session inception artifacts for the given player UUID, expected incarnation, and expected generation without removing.
     *
     * @param uuid player UUID
     * @param expectedIncarnation expected session incarnation
     * @param expectedGeneration expected session generation (or negative to ignore generation)
     * @return optional containing the inception artifacts, or empty if absent or mismatched
     */
    public Optional<SessionInceptionArtifacts> getInceptionArtifacts(
            UUID uuid, long expectedIncarnation, long expectedGeneration) {
        if (uuid == null) return Optional.empty();
        var artifacts = sessionInceptionArtifacts.get(uuid);
        if (artifacts != null
                && artifacts.incarnation() == expectedIncarnation
                && (expectedGeneration < 0 || artifacts.generation() == expectedGeneration)) {
            return Optional.of(artifacts);
        }
        return Optional.empty();
    }

    /**
     * Returns whether the player has an active (non-complete, non-cancelled) session.
     */
    public boolean hasActiveSession(Player player) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    /**
     * Returns whether an active execution exists in the execution registry for the player.
     */
    public boolean hasActiveExecution(Player player) {
        if (player == null) return false;
        return hasActiveExecution(player.getUniqueId());
    }

    /**
     * Returns whether an active execution exists in the execution registry for the UUID.
     */
    public boolean hasActiveExecution(UUID uuid) {
        if (uuid == null) return false;
        var reg = executionRegistry != null
                ? executionRegistry
                : (plugin != null ? plugin.getExecutionRegistry() : null);
        return reg != null && reg.hasActiveExecution(uuid);
    }

    /**
     * Returns whether an active approval interaction lease exists for the player.
     */
    public boolean hasActiveApprovalLease(Player player) {
        if (player == null) return false;
        return hasActiveApprovalLease(player.getUniqueId());
    }

    /**
     * Returns whether an active approval interaction lease exists for the UUID.
     */
    public boolean hasActiveApprovalLease(UUID uuid) {
        if (uuid == null) return false;
        var reg = leaseRegistry != null
                ? leaseRegistry
                : (plugin != null && plugin.getApprovalCoordinator() != null
                        ? plugin.getApprovalCoordinator().getLeaseRegistry()
                        : null);
        return reg != null && reg.isLeased(uuid);
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
        var snapshot = plugin != null && plugin.getPresetRegistry() != null ? plugin.getPresetRegistry().getSnapshot() : null;
        return findMissingPromptPresets(parsed, snapshot);
    }

    private List<String> findMissingPromptPresets(ParsedCommand parsed, dev.cyr1en.promptpaper.preset.PresetSnapshot snapshot) {
        var registry = plugin != null ? plugin.getPresetRegistry() : null;
        if (registry == null && snapshot == null) {
            return parsed.promptTags().stream()
                    .filter(PromptTag::isPreset)
                    .map(PromptTag::displayText)
                    .toList();
        }
        return parsed.promptTags().stream()
                .filter(PromptTag::isPreset)
                .map(PromptTag::displayText)
                .filter(id -> {
                    if (snapshot != null && snapshot.getPrompt(id).isPresent()) {
                        return false;
                    }
                    if (registry != null && registry.getPrompt(id).isPresent()) {
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    /**
     * Returns the list of preset post-command ids that appear in {@code parsed} but are
     * not registered in the plugin's {@code PresetRegistry}. Order matches the order of
     * occurrence in the parsed command.
     */
    private List<String> findMissingPostCommandPresets(ParsedCommand parsed) {
        var snapshot = plugin != null && plugin.getPresetRegistry() != null ? plugin.getPresetRegistry().getSnapshot() : null;
        return findMissingPostCommandPresets(parsed, snapshot);
    }

    private List<String> findMissingPostCommandPresets(ParsedCommand parsed, dev.cyr1en.promptpaper.preset.PresetSnapshot snapshot) {
        var registry = plugin != null ? plugin.getPresetRegistry() : null;
        if (registry == null && snapshot == null) {
            return parsed.postCmds().stream()
                    .filter(PostCommandMeta::isPreset)
                    .map(PostCommandMeta::command)
                    .toList();
        }
        return parsed.postCmds().stream()
                .filter(PostCommandMeta::isPreset)
                .map(PostCommandMeta::command)
                .filter(id -> {
                    if (snapshot != null && snapshot.getPostCommand(id).isPresent()) {
                        return false;
                    }
                    if (registry != null && registry.getPostCommand(id).isPresent()) {
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    private List<String> findMissingGatePresets(ParsedCommand parsed, dev.cyr1en.promptpaper.preset.PresetSnapshot snapshot) {
        if (parsed == null || parsed.preDispatchGates() == null || parsed.preDispatchGates().isEmpty()) {
            return List.of();
        }
        var missing = new java.util.ArrayList<String>();
        for (var gate : parsed.preDispatchGates()) {
            if (gate instanceof dev.cyr1en.promptcore.plan.PreDispatchGateSpec.Approval approval) {
                String id = approval.presetId();
                boolean found = snapshot != null && snapshot.getApprovalGate(id).isPresent();
                if (!found) {
                    missing.add(id);
                }
            }
        }
        return missing;
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
        failFastMissing(player, commandLine, missingPrompts, missingPostCmds, missingValidators, List.of());
    }

    private void failFastMissing(
            Player player,
            String commandLine,
            List<String> missingPrompts,
            List<String> missingPostCmds,
            List<String> missingValidators,
            List<String> missingGates) {
        var all = new java.util.ArrayList<String>();
        if (!missingPrompts.isEmpty()) {
            all.add("prompts=" + sanitizeLogIdentifiers(missingPrompts));
        }
        if (!missingPostCmds.isEmpty()) {
            all.add("post-commands=" + sanitizeLogIdentifiers(missingPostCmds));
        }
        if (!missingValidators.isEmpty()) {
            all.add("validators=" + sanitizeLogIdentifiers(missingValidators));
        }
        if (!missingGates.isEmpty()) {
            all.add("gates=" + sanitizeLogIdentifiers(missingGates));
        }
        var summary = String.join(", ", all);
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            summary = summary.substring(0, MAX_SUMMARY_LENGTH);
        }
        var playerUuid = player != null ? String.valueOf(player.getUniqueId()) : "unknown";
        plugin.getPluginLogger().err(
                "Fail-fast: command from " + playerUuid
                        + " references unknown element(s) [" + summary
                        + "] — command NOT executed.");
        var i18n = plugin.getConfigLoader().getI18n();
        if (!missingValidators.isEmpty() && missingPrompts.isEmpty() && missingPostCmds.isEmpty() && missingGates.isEmpty()) {
            player.sendMessage(i18n.get("command.error.missing_validator", player));
        } else {
            player.sendMessage(i18n.get("command.error.missing_preset", player));
        }
    }

    private static String sanitizeLogIdentifier(String id) {
        if (id == null) return "";
        var clean = C0_CONTROLS.matcher(id).replaceAll("");
        if (clean.length() > MAX_ID_LENGTH) {
            clean = clean.substring(0, MAX_ID_LENGTH);
        }
        return MINI_MESSAGE.escapeTags(clean);
    }

    private static List<String> sanitizeLogIdentifiers(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        var result = new java.util.ArrayList<String>(ids.size());
        for (var id : ids) {
            result.add(sanitizeLogIdentifier(id));
        }
        return result;
    }

    private Optional<String> findUnresolvedScreenKey(ParsedCommand parsed) {
        if (screenKeyResolver == null) return Optional.empty();
        for (var tag : parsed.promptTags()) {
            if (tag.isPreset()) continue;
            var resolution = screenKeyResolver.resolve(tag.key());
            if (resolution.isUnresolved()) {
                return Optional.of(tag.key());
            }
            if (tag.isCompound()) {
                for (var subTag : tag.subTags()) {
                    if (subTag.isPreset()) continue;
                    var subResolution = screenKeyResolver.resolve(subTag.key());
                    if (subResolution.isUnresolved()) {
                        return Optional.of(subTag.key());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private void failFastUnresolvedKey(Player player, String rawKey) {
        var safeKey = sanitizeKey(rawKey);
        var safePlayer = player != null ? C0_CONTROLS.matcher(player.getName()).replaceAll("") : "unknown";
        if (safePlayer.length() > 64) safePlayer = safePlayer.substring(0, 64);
        plugin.getPluginLogger().warn(
                "Fail-fast: command from " + safePlayer
                        + " references unknown screen key [" + safeKey
                        + "] — command NOT executed.");
        if (plugin.getConfigLoader() != null && plugin.getConfigLoader().getI18n() != null) {
            player.sendMessage(plugin.getConfigLoader().getI18n().get("command.error.missing_preset", player));
        }
    }

    private static String sanitizeKey(String key) {
        if (key == null) return "";
        var clean = C0_CONTROLS.matcher(key).replaceAll("");
        return clean.length() > MAX_ID_LENGTH ? clean.substring(0, MAX_ID_LENGTH) : clean;
    }
}
