package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptcore.logic.condition.ConditionBindings;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.custom.ActiveScreenHandle;
import dev.cyr1en.promptpaper.custom.CustomScreenAdapter;
import dev.cyr1en.promptpaper.custom.CustomScreenHandle;
import dev.cyr1en.promptpaper.custom.CustomScreenRegistry;
import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import dev.cyr1en.promptpaper.custom.ProviderLifecycleCoordinator;
import dev.cyr1en.promptpaper.custom.ScreenResolution;
import dev.cyr1en.promptpaper.custom.SessionVerificationSnapshot;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.engine.SessionInceptionArtifacts;
import dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator;
import dev.cyr1en.promptpaper.execution.dispatch.PaperImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PaperPrimaryCommandDispatcher;
import dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry;
import dev.cyr1en.promptpaper.execution.runtime.InputCompletion;
import dev.cyr1en.promptui.ComponentUtil;
import dev.cyr1en.promptui.DialogScreen;
import dev.cyr1en.promptpaper.preset.ActionsSource;
import dev.cyr1en.promptpaper.preset.DialogPrompt;
import dev.cyr1en.promptpaper.screen.dialog.AnswerEncoding;
import dev.cyr1en.promptpaper.screen.dialog.DialogCompletionContext;
import dev.cyr1en.promptpaper.screen.dialog.DialogInputKind;
import dev.cyr1en.promptpaper.screen.playerui.PlayerUIScreen;
import dev.cyr1en.promptpaper.util.CancellableTask;
import dev.cyr1en.promptpaper.util.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import dev.cyr1en.promptcore.i18n.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Orchestrates prompt sessions by routing tags to the appropriate screen
 * type, collecting answers, and dispatching the assembled command.
 */
public class ScreenManager {

    @FunctionalInterface
    public interface ScreenCreator {
        InputScreen create(Player player, PromptTag tag, DialogCompletionContext context);
    }

    public enum DispatchMode {
        NORMAL,
        CONSOLE,
        ATTACHMENT
    }

    private final CommandPrompter plugin;
    private final PromptEngine engine;
    private final PromptFactory factory;
    private final Scheduler scheduler;
    private final ScreenCreator screenCreator;
    private final Function<Player, PlayerExecutor> playerExecutorFactory;
    private final Map<UUID, ActiveScreenHandle> activeScreenHandles;
    private final Map<Long, Set<UUID>> providerToActivePlayers;
    private final Map<UUID, InputScreen> activeScreens;
    private final Map<UUID, Long> activeScreenAttempts;
    private final AtomicLong screenAttemptSequence;
    private final Map<UUID, CancellableTask> timeoutTasks;
    private final Map<UUID, Long> timeoutTokens;
    private final AtomicLong timeoutSequence;
    private final Map<UUID, DispatchMode> dispatchModes;
    private final Map<UUID, String> attachmentKeys;
    private final Set<UUID> teardownInProgress;
    private final ProviderLifecycleCoordinator providerLifecycleCoordinator;
    private volatile ExecutionCoordinator executionCoordinator;
    private static final java.util.regex.Pattern C0_CONTROLS = java.util.regex.Pattern.compile("[\\u0000-\\u001F\\u007F]");

    public ScreenManager(CommandPrompter plugin, PromptEngine engine, PromptFactory factory, Scheduler scheduler) {
        this(plugin, engine, factory, scheduler, factory != null ? factory::createFromTag : null, null);
    }

    public ScreenManager(
            CommandPrompter plugin,
            PromptEngine engine,
            PromptFactory factory,
            Scheduler scheduler,
            ScreenCreator screenCreator) {
        this(plugin, engine, factory, scheduler, screenCreator, null);
    }

    public ScreenManager(
            CommandPrompter plugin,
            PromptEngine engine,
            PromptFactory factory,
            Scheduler scheduler,
            ScreenCreator screenCreator,
            Function<Player, PlayerExecutor> playerExecutorFactory) {
        this(plugin, engine, factory, scheduler, screenCreator, playerExecutorFactory, null);
    }

    public ScreenManager(
            CommandPrompter plugin,
            PromptEngine engine,
            PromptFactory factory,
            Scheduler scheduler,
            ScreenCreator screenCreator,
            Function<Player, PlayerExecutor> playerExecutorFactory,
            dev.cyr1en.promptpaper.custom.CustomScreenAuditLogger auditLogger) {
        this(plugin, engine, factory, scheduler, screenCreator, playerExecutorFactory, auditLogger, null);
    }

    public ScreenManager(
            CommandPrompter plugin,
            PromptEngine engine,
            PromptFactory factory,
            Scheduler scheduler,
            ScreenCreator screenCreator,
            Function<Player, PlayerExecutor> playerExecutorFactory,
            dev.cyr1en.promptpaper.custom.CustomScreenAuditLogger auditLogger,
            ExecutionCoordinator executionCoordinator) {
        this.plugin = plugin;
        this.engine = engine;
        this.factory = factory;
        this.scheduler = scheduler;
        this.screenCreator = screenCreator != null ? screenCreator : (factory != null ? factory::createFromTag : null);
        this.playerExecutorFactory = playerExecutorFactory != null
                ? playerExecutorFactory
                : p -> PlayerExecutor.forPlayer(plugin, p);
        this.activeScreenHandles = new ConcurrentHashMap<>();
        this.providerToActivePlayers = new ConcurrentHashMap<>();
        this.activeScreens = new ConcurrentHashMap<>();
        this.activeScreenAttempts = new ConcurrentHashMap<>();
        this.screenAttemptSequence = new AtomicLong();
        this.timeoutTasks = new ConcurrentHashMap<>();
        this.timeoutTokens = new ConcurrentHashMap<>();
        this.timeoutSequence = new AtomicLong();
        this.dispatchModes = new ConcurrentHashMap<>();
        this.attachmentKeys = new ConcurrentHashMap<>();
        this.teardownInProgress = ConcurrentHashMap.newKeySet();
        dev.cyr1en.promptpaper.custom.CustomScreenAuditLogger effectiveAuditLogger = auditLogger != null
                ? auditLogger
                : (plugin != null && plugin.getCustomScreenAuditLogger() != null
                        ? plugin.getCustomScreenAuditLogger()
                        : dev.cyr1en.promptpaper.custom.CustomScreenAuditLogger.noop());
        this.providerLifecycleCoordinator = new ProviderLifecycleCoordinator(
                engine != null && engine.getScreenKeyResolver() != null
                        ? engine.getScreenKeyResolver().customRegistry()
                        : new CustomScreenRegistry(),
                this,
                effectiveAuditLogger,
                this.playerExecutorFactory
        );
        if (executionCoordinator != null) {
            this.executionCoordinator = executionCoordinator;
        } else {
            var reg = new ExecutionRegistry();
            var primaryDisp = new PaperPrimaryCommandDispatcher(plugin, scheduler);
            var actionDisp = new PaperImmediateActionDispatcher(plugin, scheduler);
            this.executionCoordinator = new ExecutionCoordinator(
                    plugin, engine, reg, primaryDisp, actionDisp, this.playerExecutorFactory);
        }
        if (this.engine != null) {
            this.engine.setExecutionCoordinator(this.executionCoordinator);
        }
    }

    public ExecutionCoordinator getExecutionCoordinator() {
        return executionCoordinator;
    }

    public void setExecutionCoordinator(ExecutionCoordinator executionCoordinator) {
        if (executionCoordinator != null) {
            this.executionCoordinator = executionCoordinator;
            if (this.engine != null) {
                this.engine.setExecutionCoordinator(executionCoordinator);
            }
        }
    }

    public ProviderLifecycleCoordinator getProviderLifecycleCoordinator() {
        return providerLifecycleCoordinator;
    }

    public ActiveScreenHandle getActiveScreenHandle(UUID uuid) {
        if (uuid == null) return null;
        return activeScreenHandles.get(uuid);
    }

    public Set<UUID> getActivePlayersForProvider(long providerId) {
        var set = providerToActivePlayers.get(providerId);
        return set != null ? Set.copyOf(set) : Set.of();
    }

    private boolean linkActiveScreen(
            UUID uuid,
            InputScreen screen,
            long attemptToken,
            long incarnation,
            long generation,
            int promptIndex,
            CustomScreenHandle customHandle) {
        if (customHandle != null && !customHandle.isActive()) {
            return false;
        }
        unlinkActiveScreen(uuid);
        var handle = new ActiveScreenHandle(
                uuid, screen, attemptToken, incarnation, generation, promptIndex, customHandle);
        activeScreenHandles.put(uuid, handle);
        activeScreens.put(uuid, screen);
        activeScreenAttempts.put(uuid, attemptToken);
        if (customHandle != null) {
            providerToActivePlayers
                    .computeIfAbsent(customHandle.providerId(), k -> ConcurrentHashMap.newKeySet())
                    .add(uuid);
            if (!customHandle.isActive()) {
                unlinkActiveScreen(uuid);
                return false;
            }
        }
        return true;
    }

    private InputScreen unlinkActiveScreen(UUID uuid) {
        if (uuid == null) return null;
        activeScreenAttempts.remove(uuid);
        var removedScreen = activeScreens.remove(uuid);
        var removedHandle = activeScreenHandles.remove(uuid);
        if (removedHandle != null) {
            if (removedHandle.providerId() != null) {
                var players = providerToActivePlayers.get(removedHandle.providerId());
                if (players != null) {
                    players.remove(uuid);
                    if (players.isEmpty()) {
                        providerToActivePlayers.remove(removedHandle.providerId(), players);
                    }
                }
            }
            if (removedScreen == null) {
                removedScreen = removedHandle.screen();
            }
        }
        return removedScreen;
    }

    public boolean verifyAttempt(SessionVerificationSnapshot snapshot) {
        if (snapshot == null) return false;
        var player = Bukkit.getPlayer(snapshot.playerUuid());
        if (player == null) return false;
        var sessionOpt = engine.getSession(player);
        if (sessionOpt.isEmpty()) return false;
        var session = sessionOpt.get();
        if (!session.isActive()) return false;
        if (session.incarnation() != snapshot.expectedIncarnation()) return false;
        if (session.generation() != snapshot.expectedGeneration()) return false;
        if (snapshot.promptIndex() >= 0 && session.currentIndex() != snapshot.promptIndex()) return false;
        var activeHandle = activeScreenHandles.get(snapshot.playerUuid());
        if (activeHandle == null || activeHandle.attemptToken() != snapshot.attemptToken()) return false;
        return true;
    }

    /**
     * Intercepts the command line for prompt tags and begins showing
     * the first prompt to the player.
     */
    public void startSession(Player player, String commandLine) {
        var parsed = engine.intercept(player, commandLine);
        if (parsed.isEmpty()) {
            plugin.getPluginLogger().debug("No prompts to show for " + player.getName());
            return;
        }
        plugin.getPluginLogger().debug("Starting session for " + player.getName()
                + " with " + parsed.get().promptTags().size() + " prompts");
        var started = engine.runIfReloadNotInProgress(() -> showNextPrompt(player));
        if (!started) {
            engine.discard(player.getUniqueId());
            engine.rejectIfReloading(player);
            return;
        }
    }

    /**
     * Like {@link #startSession} but with a dispatch mode and optional
     * permission key for post-session command execution.
     */
    public void startDelegatedSession(Player target, String commandLine, DispatchMode mode, String permissionKey) {
        var uuid = target.getUniqueId();
        try {
            var task = target.getScheduler().run(
                    plugin,
                    scheduledTask -> startDelegatedSessionOnPlayer(
                            target, commandLine, mode, permissionKey),
                    () -> discardState(uuid));
            if (task == null) discardState(uuid);
        } catch (Throwable t) {
            plugin.getPluginLogger().err("Unable to schedule delegated session for " + uuid
                    + ": " + t.getMessage());
            discardState(uuid);
        }
    }

    private void startDelegatedSessionOnPlayer(
            Player target, String commandLine, DispatchMode mode, String permissionKey) {
        var uuid = target.getUniqueId();
        try {
            plugin.getPluginLogger().debug("Delegated session: target=" + target.getName()
                    + " mode=" + mode + " permKey=" + permissionKey);
            var normalized = commandLine.startsWith("/")
                    ? commandLine.substring(1)
                    : commandLine;
            normalized = normalized.replace("%target_player%", target.getName());
            var parsed = engine.intercept(target, normalized);
            if (parsed.isEmpty()) {
                if (!engine.commandHasTagForm(normalized)) {
                    plugin.getPluginLogger().debug("No prompts, dispatching directly");
                    dispatchDirect(target, normalized, mode, permissionKey);
                } else {
                    plugin.getPluginLogger().debug("Command had tag form but intercept rejected it "
                            + "(missing preset, no permission, or active session); not dispatching");
                }
                return;
            }
            var started = engine.runIfReloadNotInProgress(() -> {
                if (mode != DispatchMode.NORMAL) {
                    dispatchModes.put(uuid, mode);
                    if (permissionKey != null) attachmentKeys.put(uuid, permissionKey);
                }
                showNextPrompt(target);
            });
            if (!started) {
                engine.discard(uuid);
                engine.rejectIfReloading(target);
            }
        } catch (Throwable t) {
            plugin.getPluginLogger().err("Delegated session failed for " + uuid
                    + ": " + t.getMessage());
            discardState(uuid);
        }
    }

    private void dispatchDirect(Player target, String commandLine, DispatchMode mode, String permissionKey) {
        var uuid = target.getUniqueId();
        var permissionSnapshot = mode == DispatchMode.ATTACHMENT
                ? capturePermissionSnapshot(permissionKey)
                : List.<String>of();
        var dispatchSnapshot = switch (mode) {
            case CONSOLE -> DispatchContextSnapshot.console();
            case ATTACHMENT -> new DispatchContextSnapshot(
                    dev.cyr1en.promptpaper.preset.ExecuteAs.PLAYER,
                    permissionKey,
                    true,
                    permissionSnapshot);
            default -> DispatchContextSnapshot.player();
        };
        var snapshot = plugin != null && plugin.getPresetRegistry() != null && plugin.getPresetRegistry().getSnapshot() != null
                ? plugin.getPresetRegistry().getSnapshot()
                : dev.cyr1en.promptpaper.preset.PresetSnapshot.empty();
        var parsed = new dev.cyr1en.promptcore.ParsedCommand(
                commandLine, List.of(), List.of(), dev.cyr1en.promptcore.ParserConfig.ANGLE_BRACKETS);
        var templateSyntax = plugin != null && plugin.getConfigLoader() != null && plugin.getConfigLoader().getConfig() != null
                ? plugin.getConfigLoader().getConfig().templateSyntax()
                : dev.cyr1en.promptcore.logic.transform.TemplateSyntax.DEFAULT;
        var plan = dev.cyr1en.promptcore.plan.ExecutionPlanAdapter.fromParsedCommand(parsed, templateSyntax);
        var sessionResult = new dev.cyr1en.promptcore.SessionResult(
                commandLine, List.of(), List.of(), List.of());
        var completion = InputCompletion.of(
                uuid,
                1L,
                0L,
                sessionResult,
                plan,
                snapshot,
                dispatchSnapshot);
        executionCoordinator.coordinate(target, completion, sessionResult);
    }

    /**
     * Advances the session to the next prompt tag and displays it,
     * or completes the session if all prompts have been answered.
     */
    public void showNextPrompt(Player player) {
        var session = engine.getSession(player);
        if (session.isEmpty() || !session.get().isActive()) return;
        var current = session.get().currentPrompt();
        if (current.isEmpty()) return;
        var tag = current.get();
        var kind = DialogInputKind.parse(tag.filter());
        if (!tag.isCompound() && (kind == DialogInputKind.TITLE || kind == DialogInputKind.BODY)) {
            plugin.getPluginLogger().warn("Player " + player.getName()
                    + " initiated a prompt containing a non-compound tag with a layout filter: "
                    + tag.rawTag());
            player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.error.invalid_title_filter", player));
            cancelAll(player);
            return;
        }
        showPrompt(player, tag);
    }

    /**
     * Builds the completion context, creates the screen via the factory (the single
     * presentation-materialization boundary), and opens it for the player.
     */
    private void showPrompt(Player player, PromptTag tag) {
        InputScreen screen = null;
        var uuid = player.getUniqueId();
        var sessionOpt = engine.getSession(player);
        if (sessionOpt.isEmpty() || !sessionOpt.get().isActive()) return;
        var session = sessionOpt.get();
        long expectedIncarnation = session.incarnation();
        long expectedGeneration = session.generation();
        int currentPromptIndex = session.currentIndex();
        long attemptToken = screenAttemptSequence.incrementAndGet();

        // Re-resolve the screen key immediately before creation
        var resolution = engine.getScreenKeyResolver().resolve(tag.key());
        if (resolution instanceof ScreenResolution.Unresolved) {
            plugin.getPluginLogger().warn("Failed to resolve screen key '" + tag.key()
                    + "' for player " + player.getName() + "; failing closed");
            teardown(player, CancelReason.ERROR, false, false);
            return;
        }

        CustomScreenHandle customHandle = null;
        try {
            if (resolution instanceof ScreenResolution.Custom customRes) {
                customHandle = customRes.handle();
                if (!customHandle.isActive()) {
                    plugin.getPluginLogger().warn("Custom screen provider '" + customHandle.key()
                            + "' is no longer active for player " + player.getName() + "; failing closed");
                    teardown(player, CancelReason.ERROR, false, false);
                    return;
                }

                var screenContext = new dev.cyr1en.promptui.api.ScreenContext(
                        customHandle.key(),
                        tag.displayText(),
                        tag.flags(),
                        tag.sanitize()
                );

                var snapshot = new SessionVerificationSnapshot(
                        uuid,
                        expectedIncarnation,
                        expectedGeneration,
                        currentPromptIndex,
                        attemptToken
                );

                var playerExecutor = playerExecutorFactory.apply(player);
                var customHandleRef = customHandle;
                var adapter = CustomScreenAdapter.lazy(
                        customHandleRef,
                        () -> customHandleRef.invokeFactory(promptFactory ->
                                promptFactory.createScreen(player, screenContext)
                        ).orElse(null),
                        playerExecutor,
                        snapshot,
                        this::verifyAttempt,
                        () -> discardState(uuid)
                );

                if (tag.title() != null) {
                    screen = new TitleWrapperScreen(adapter, tag.title(), player, scheduler, plugin);
                } else {
                    screen = adapter;
                }
            } else {
                var context = buildCompletionContext(player, tag);
                screen = screenCreator != null
                        ? screenCreator.create(player, tag, context)
                        : factory.createFromTag(player, tag, context);
            }

            if (screen == null) {
                throw new IllegalStateException("Screen creation returned null");
            }

            plugin.getPluginLogger().debug("Showing prompt for " + player.getName()
                    + " key=" + tag.key() + " screen=" + screen.getClass().getSimpleName()
                    + " inc=" + expectedIncarnation + " gen=" + expectedGeneration + " attempt=" + attemptToken);

            boolean linked = linkActiveScreen(
                    uuid,
                    screen,
                    attemptToken,
                    expectedIncarnation,
                    expectedGeneration,
                    currentPromptIndex,
                    customHandle);

            if (!linked) {
                plugin.getPluginLogger().warn("Custom screen provider '" + (customHandle != null ? customHandle.key() : tag.key())
                        + "' became inactive during screen linking for player " + player.getName() + "; failing closed");
                if (screen instanceof TitleWrapperScreen wrapper) {
                    wrapper.invalidateCallbacks();
                    if (wrapper.delegate() instanceof CustomScreenAdapter customAdapter) {
                        customAdapter.teardownDetach();
                    }
                } else if (screen instanceof CustomScreenAdapter customAdapter) {
                    customAdapter.teardownDetach();
                }
                var dispatchContext = takeDispatchContext(uuid);
                engine.cancel(player, CancelReason.MANUAL, dispatchContext);
                try {
                    player.closeInventory();
                } catch (Throwable ignored) {}
                discardState(uuid);
                return;
            }

            screen.onResult(result -> handleResult(player, result, expectedIncarnation, expectedGeneration, attemptToken));
            screen.onOpenFailure(error -> handleOpenFailure(player, error, expectedIncarnation, expectedGeneration, attemptToken));

            if (screen instanceof TitleWrapperScreen titleWrapper) {
                titleWrapper.setOnDelegateOpen(() -> scheduleTimeout(player, tag));
                screen.open();
            } else {
                screen.open();
                scheduleTimeout(player, tag);
            }
        } catch (Throwable e) {
            cancelTimeout(uuid);
            unlinkActiveScreen(uuid);
            if (screen instanceof TitleWrapperScreen wrapper) {
                wrapper.invalidateCallbacks();
                if (wrapper.delegate() instanceof CustomScreenAdapter customAdapter) {
                    customAdapter.teardownDetach();
                }
            } else if (screen instanceof PlayerUIScreen playerUIScreen) {
                playerUIScreen.invalidateCallbacks();
            } else if (screen instanceof CustomScreenAdapter customAdapter) {
                customAdapter.teardownDetach();
            }
            if (plugin.getNonceRegistry() != null) {
                plugin.getNonceRegistry().invalidatePlayer(uuid);
            }
            if (plugin.getRateLimiter() != null) {
                plugin.getRateLimiter().reset(uuid);
            }
            var dispatchContext = takeDispatchContext(uuid);
            engine.cancel(player, CancelReason.ERROR, dispatchContext);
            if (screen != null) {
                try {
                    screen.close();
                } catch (Exception ignored) {
                    // The owning player may already be retired after an open failure.
                }
            }
            discardState(uuid);
            var safeMsg = e.getMessage() != null
                    ? C0_CONTROLS.matcher(e.getMessage()).replaceAll("")
                    : e.getClass().getSimpleName();
            plugin.getPluginLogger().err("Unable to open prompt screen for " + uuid
                    + ": " + safeMsg);
            if (e instanceof RuntimeException runtimeException) throw runtimeException;
            if (e instanceof Error error) throw error;
            throw new IllegalStateException("Prompt screen open failed", e);
        }
    }

    /**
     * Builds a {@link DialogCompletionContext} for TAB prompts (both inline {@code d:tab}
     * and preset dialogs with {@code actions_source: "tab_completion"}) by
     * reconstructing the partial command from the session's parsed
     * command and current answers. Returns null for non-TAB prompts.
     */
    private DialogCompletionContext buildCompletionContext(Player player, PromptTag tag) {
        if (tag.isPreset()) {
            var registry = plugin.getPresetRegistry();
            if (registry != null) {
                var optDef = registry.getPrompt(tag.displayText());
                if (optDef.isPresent() && optDef.get() instanceof DialogPrompt dialogPrompt) {
                    var dt = dialogPrompt.dialogType();
                    if (dt != null && dt.actionsSource() == ActionsSource.TAB_COMPLETION) {
                        var session = engine.getSession(player).orElse(null);
                        if (session == null) return null;
                        var partial = session.buildPartialCommand();
                        return new DialogCompletionContext(player, partial);
                    }
                }
            }
            return null;
        }
        if (!"d".equals(tag.key())) return null;
        if (DialogInputKind.parse(tag.filter()) != DialogInputKind.TAB) {
            return null;
        }
        var session = engine.getSession(player).orElse(null);
        if (session == null) return null;
        var partial = session.buildPartialCommand();
        return new DialogCompletionContext(player, partial);
    }

    /**
     * Routes a raw chat message to the active {@link ChatPromptScreen} for the player.
     */
    public void handleChatInput(Player player, String input) {
        var screen = activeScreens.get(player.getUniqueId());
        if (screen instanceof TitleWrapperScreen wrapper) {
            screen = wrapper.delegate();
        }
        if (!(screen instanceof ChatPromptScreen chatScreen)) return;
        cancelTimeout(player);
        chatScreen.handleInput(input);
    }

    /**
     * Processes a screen result: validates the answer, handles compound
     * payloads, and either advances the session or dispatches the command.
     *
     * <p>Dialog screens (inline compound, inline single, or JSON preset) are
     * detected and unwrapped before they are removed from the active-screens
     * map and their result is routed through one arity-aware batch handler,
     * regardless of whether the parsed tag is compound.
     */
    private void handleResult(Player player, ScreenResult result) {
        var session = engine.getSession(player).orElse(null);
        var inc = session != null ? session.incarnation() : -1L;
        var gen = session != null ? session.generation() : -1L;
        var attempt = activeScreenAttempts.getOrDefault(player.getUniqueId(), -1L);
        handleResult(player, result, inc, gen, attempt);
    }

    private void handleResult(Player player, ScreenResult result, long expectedGeneration) {
        var session = engine.getSession(player).orElse(null);
        var inc = session != null ? session.incarnation() : -1L;
        var attempt = activeScreenAttempts.getOrDefault(player.getUniqueId(), -1L);
        handleResult(player, result, inc, expectedGeneration, attempt);
    }

    private void handleResult(
            Player player,
            ScreenResult result,
            long expectedIncarnation,
            long expectedGeneration,
            long attemptToken) {
        try {
            var executor = playerExecutorFactory.apply(player);
            executor.execute(
                    () -> handleResultOnPlayerScheduler(
                            player, result, expectedIncarnation, expectedGeneration, attemptToken),
                    () -> discardState(player.getUniqueId()));
        } catch (Error error) {
            throw error;
        } catch (Throwable t) {
            plugin.getPluginLogger().debug("Unable to schedule handleResult for " + player.getUniqueId() + ": " + t.getMessage());
            discardState(player.getUniqueId());
        }
    }

    private void handleOpenFailure(
            Player player,
            Throwable error,
            long expectedIncarnation,
            long expectedGeneration,
            long attemptToken) {
        var uuid = player.getUniqueId();
        try {
            var executor = playerExecutorFactory.apply(player);
            executor.execute(
                    () -> handleOpenFailureOnPlayerScheduler(
                            player, error, expectedIncarnation, expectedGeneration, attemptToken),
                    () -> discardState(uuid));
        } catch (Error err) {
            throw err;
        } catch (Throwable t) {
            var safeMsg = t.getMessage() != null
                    ? C0_CONTROLS.matcher(t.getMessage()).replaceAll("")
                    : t.getClass().getSimpleName();
            plugin.getPluginLogger().debug("Unable to schedule handleOpenFailure for " + uuid + ": " + safeMsg);
            discardState(uuid);
        }
    }

    private void handleOpenFailureOnPlayerScheduler(
            Player player,
            Throwable error,
            long expectedIncarnation,
            long expectedGeneration,
            long attemptToken) {
        var sessionOpt = engine.getSession(player);
        if (sessionOpt.isEmpty()) {
            plugin.getPluginLogger().debug("No session for " + player.getName() + " on open failure");
            return;
        }
        var session = sessionOpt.get();
        if (!session.isActive()
                || (expectedIncarnation >= 0 && session.incarnation() != expectedIncarnation)
                || session.generation() != expectedGeneration) {
            plugin.getPluginLogger().debug("Discarding stale open failure for " + player.getName()
                    + " (expected inc=" + expectedIncarnation + ", gen=" + expectedGeneration
                    + "; current inc=" + session.incarnation() + ", gen=" + session.generation()
                    + ", state=" + session.state() + ")");
            return;
        }
        var currentAttempt = activeScreenAttempts.get(player.getUniqueId());
        if (attemptToken > 0 && (currentAttempt == null || currentAttempt.longValue() != attemptToken)) {
            plugin.getPluginLogger().debug("Discarding stale screen attempt on open failure for " + player.getName()
                    + " (expected attempt=" + attemptToken + ", current attempt=" + currentAttempt + ")");
            return;
        }

        var errorDetail = "";
        if (error != null) {
            var msg = error.getMessage();
            errorDetail = ": " + (msg != null ? C0_CONTROLS.matcher(msg).replaceAll("") : error.getClass().getSimpleName());
        }

        plugin.getPluginLogger().debug("Screen open failure for " + player.getName()
                + " inc=" + expectedIncarnation + " gen=" + expectedGeneration + " attempt=" + attemptToken
                + errorDetail);

        teardown(player, CancelReason.ERROR, true, false);
    }

    private void handleResultOnPlayerScheduler(
            Player player,
            ScreenResult result,
            long expectedIncarnation,
            long expectedGeneration,
            long attemptToken) {
        var sessionOpt = engine.getSession(player);
        if (sessionOpt.isEmpty()) {
            plugin.getPluginLogger().debug("No session for " + player.getName() + " on result");
            return;
        }
        var session = sessionOpt.get();
        if (!session.isActive()
                || (expectedIncarnation >= 0 && session.incarnation() != expectedIncarnation)
                || session.generation() != expectedGeneration) {
            plugin.getPluginLogger().debug("Discarding stale callback for " + player.getName()
                    + " (expected inc=" + expectedIncarnation + ", gen=" + expectedGeneration
                    + "; current inc=" + session.incarnation() + ", gen=" + session.generation()
                    + ", state=" + session.state() + ")");
            return;
        }
        var currentAttempt = activeScreenAttempts.get(player.getUniqueId());
        if (attemptToken > 0 && (currentAttempt == null || currentAttempt.longValue() != attemptToken)) {
            plugin.getPluginLogger().debug("Discarding stale screen attempt for " + player.getName()
                    + " (expected attempt=" + attemptToken + ", current attempt=" + currentAttempt + ")");
            return;
        }

        plugin.getPluginLogger().debug("Screen result for " + player.getName()
                + " cancelled=" + result.cancelled() + " reason=" + result.cancelReason()
                + " inc=" + expectedIncarnation + " gen=" + expectedGeneration + " attempt=" + attemptToken);

        cancelTimeout(player);
        var screen = unlinkActiveScreen(player.getUniqueId());
        var confirmationScreen = unwrapConfirmationScreen(screen);
        var dialogScreen = unwrapDialogScreen(screen);

        if (confirmationScreen != null) {
            handleConfirmationResult(player, confirmationScreen, result, expectedIncarnation, expectedGeneration);
            return;
        }

        if (result.cancelled()) {
            var reason = result.cancelReason() != null ? result.cancelReason() : CancelReason.GUI_EXIT;
            teardown(player, reason, false, true);
            return;
        }

        var tagOpt = session.currentPrompt();
        if (tagOpt.isEmpty()) return;
        var tag = tagOpt.get();

        var cancelKeyword = plugin.getConfigLoader().getConfig().cancelKeyword();
        boolean isCancelKeyword = false;
        if (result.answer() != null && cancelKeyword != null && !cancelKeyword.isBlank()) {
            if (dialogScreen != null) {
                // Dialog payloads are decoded with the screen's effective arity
                // (which may be 0, 1, or N and is not the tag's compound shape).
                var decoded = decodeAnswers(result.answer(), dialogScreen.effectiveAnswerCount());
                if (decoded != null) {
                    for (var ans : decoded) {
                        var cleanAns = C0_CONTROLS.matcher(ans).replaceAll("");
                        if (ComponentUtil.stripColor(cleanAns).trim().equalsIgnoreCase(cancelKeyword)) {
                            isCancelKeyword = true;
                            break;
                        }
                    }
                }
            } else if (tag.isCompound()) {
                var answerTags = answerBearingTags(tag);
                var decoded = decodeAnswers(result.answer(), answerTags.size());
                if (decoded != null) {
                    for (var ans : decoded) {
                        var cleanAns = C0_CONTROLS.matcher(ans).replaceAll("");
                        if (ComponentUtil.stripColor(cleanAns).trim().equalsIgnoreCase(cancelKeyword)) {
                            isCancelKeyword = true;
                            break;
                        }
                    }
                }
            } else {
                var cleanAns = C0_CONTROLS.matcher(result.answer()).replaceAll("");
                if (ComponentUtil.stripColor(cleanAns).trim().equalsIgnoreCase(cancelKeyword)) {
                    isCancelKeyword = true;
                }
            }
        }

        if (isCancelKeyword) {
            teardown(player, CancelReason.MANUAL, false, true);
            return;
        }

        if (dialogScreen != null) {
            handleDialogResult(player, tag, result, dialogScreen, expectedIncarnation, expectedGeneration);
            return;
        }

        // Compound dialogs encode multiple sub-answers with control characters (RS/US).
        if (tag.isCompound()) {
            handleCompoundResult(player, tag, result.answer(), expectedIncarnation, expectedGeneration);
            return;
        }

        var cleanAnswer = C0_CONTROLS.matcher(result.answer() != null ? result.answer() : "").replaceAll("");
        if (!validateAnswer(player, cleanAnswer, tag)) {
            plugin.getPluginLogger().debug("Validation failed for " + player.getName());
            showPrompt(player, tag);
            return;
        }

        if (checkBreakIf(player, tag, List.of(cleanAnswer))) {
            return;
        }

        var submitted = engine.submit(player, cleanAnswer);
        handleSubmitted(player, submitted, expectedIncarnation, expectedGeneration);
    }

    public static dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen unwrapConfirmationScreen(InputScreen screen) {
        if (screen instanceof TitleWrapperScreen wrapper) {
            screen = wrapper.delegate();
        }
        if (screen instanceof CustomScreenAdapter adapter) {
            screen = adapter.delegate();
        }
        return screen instanceof dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen confirmation ? confirmation : null;
    }

    private void handleConfirmationResult(
            Player player,
            dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen confirmationScreen,
            ScreenResult result,
            long expectedIncarnation,
            long expectedGeneration) {
        var outcome = confirmationScreen.lastOutcome().orElseGet(() -> {
            if (result.cancelled()) {
                return dev.cyr1en.promptpaper.screen.confirmation.ConfirmationOutcome.cancelled(
                        result.cancelReason() != null ? result.cancelReason() : CancelReason.GUI_EXIT);
            }
            return dev.cyr1en.promptpaper.screen.confirmation.ConfirmationOutcome.declined();
        });

        plugin.getPluginLogger().debug("Confirmation outcome for " + player.getName()
                + ": " + outcome.getClass().getSimpleName()
                + " (valueMode=" + confirmationScreen.isValueMode() + ")");

        var tagOpt = engine.getSession(player).flatMap(dev.cyr1en.promptcore.session.PromptSession::currentPrompt);
        var tag = tagOpt.orElse(null);

        switch (outcome) {
            case dev.cyr1en.promptpaper.screen.confirmation.ConfirmationOutcome.Confirmed confirmed -> {
                if (confirmationScreen.isValueMode()) {
                    var candidate = List.of("true");
                    if (checkBreakIf(player, tag, candidate)) return;
                    var submitted = engine.submitAnswers(player, candidate, 1);
                    handleSubmitted(player, submitted, expectedIncarnation, expectedGeneration);
                } else {
                    var candidate = List.<String>of();
                    if (checkBreakIf(player, tag, candidate)) return;
                    var submitted = engine.submitAnswers(player, candidate, 0);
                    handleSubmitted(player, submitted, expectedIncarnation, expectedGeneration);
                }
            }
            case dev.cyr1en.promptpaper.screen.confirmation.ConfirmationOutcome.Declined declined -> {
                if (confirmationScreen.isValueMode()) {
                    var candidate = List.of("false");
                    if (checkBreakIf(player, tag, candidate)) return;
                    var submitted = engine.submitAnswers(player, candidate, 1);
                    handleSubmitted(player, submitted, expectedIncarnation, expectedGeneration);
                } else {
                    teardown(player, CancelReason.MANUAL, false, true);
                }
            }
            case dev.cyr1en.promptpaper.screen.confirmation.ConfirmationOutcome.Cancelled cancelled -> {
                teardown(player, cancelled.reason(), false, true);
            }
        }
    }

    private void handleSubmitted(
            Player player, java.util.Optional<dev.cyr1en.promptcore.SessionResult> submitted) {
        var session = engine.getSession(player).orElse(null);
        long inc = session != null ? session.incarnation() : -1L;
        long gen = session != null ? session.generation() : -1L;
        handleSubmitted(player, submitted, inc, gen);
    }

    private void handleSubmitted(
            Player player,
            java.util.Optional<dev.cyr1en.promptcore.SessionResult> submitted,
            long expectedIncarnation,
            long expectedGeneration) {
        var uuid = player.getUniqueId();
        if (submitted.isPresent()) {
            var sessionResult = submitted.get();
            if (plugin.getNonceRegistry() != null) {
                plugin.getNonceRegistry().invalidatePlayer(uuid);
            }
            if (plugin.getRateLimiter() != null) {
                plugin.getRateLimiter().reset(uuid);
            }
            cancelTimeout(uuid);
            unlinkActiveScreen(uuid);

            plugin.getPluginLogger().debug("Session complete for " + player.getName());
            var dispatchSnapshot = takeDispatchContextSnapshot(uuid);
            var artifactsOpt = engine.takeInceptionArtifacts(uuid, expectedIncarnation, expectedGeneration);
            if (artifactsOpt.isEmpty()) {
                var safeName = player.getName() != null
                        ? C0_CONTROLS.matcher(player.getName()).replaceAll("")
                        : "unknown";
                if (safeName.length() > 64) safeName = safeName.substring(0, 64);
                plugin.getPluginLogger().err("Missing or mismatched inception artifacts for session completion of "
                        + safeName + " (expected inc=" + expectedIncarnation + ", gen=" + expectedGeneration + "); failing closed");
                if (plugin.getConfigLoader() != null && plugin.getConfigLoader().getI18n() != null) {
                    try {
                        player.sendMessage(plugin.getConfigLoader().getI18n().get(
                                "prompt.error.command_failed", player));
                    } catch (Throwable ignored) {
                    }
                }
                return;
            }

            var artifacts = artifactsOpt.get();
            var completion = InputCompletion.of(
                    uuid,
                    artifacts.incarnation(),
                    artifacts.generation(),
                    sessionResult,
                    artifacts.planDefinition(),
                    artifacts.presetSnapshot(),
                    dispatchSnapshot,
                    artifacts.originalPostCommands());

            executionCoordinator.coordinate(player, completion, sessionResult);
        } else {
            if (plugin.getNonceRegistry() != null) {
                var session = engine.getSession(player).orElse(null);
                if (session != null) {
                    int prevIndex = session.currentIndex() - 1;
                    if (prevIndex >= 0) {
                        plugin.getNonceRegistry().invalidatePrompt(uuid, session.incarnation(), prevIndex);
                    }
                }
            }
            plugin.getPluginLogger().debug("Answers accepted, showing next prompt");
            showNextPrompt(player);
        }
    }

    /**
     * Unwraps a {@link TitleWrapperScreen} (if any) and returns the underlying
     * screen when it is a {@link DialogScreen}; otherwise {@code null}. Works
     * through the loadable UI-API marker so the Paper-bound dialog screen class
     * is never referenced here.
     */
    private static DialogScreen unwrapDialogScreen(InputScreen screen) {
        if (screen instanceof TitleWrapperScreen wrapper) {
            screen = wrapper.delegate();
        }
        if (screen instanceof CustomScreenAdapter adapter) {
            screen = adapter.delegate();
        }
        return screen instanceof DialogScreen dialog ? dialog : null;
    }

    /**
     * Routes a {@link DialogScreen} result through the arity-aware batch path.
     * The payload is decoded with the screen's effective answer count (cached
     * at open time — never recomputed from tab completion here), each answer is
     * validated against the block-level constraints, and the batch is submitted
     * with that expected count.
     */
    private void handleDialogResult(
            Player player,
            PromptTag tag,
            ScreenResult result,
            DialogScreen dialogScreen,
            long expectedIncarnation,
            long expectedGeneration) {
        int expected = dialogScreen.effectiveAnswerCount();
        var rawAnswers = decodeAnswers(result.answer(), expected);
        if (rawAnswers == null) {
            // Defensive fallback: re-show prompt if the dialog payload is malformed.
            plugin.getPluginLogger().warn("Malformed dialog payload received from dialog for "
                    + player.getName());
            showPrompt(player, tag);
            return;
        }
        var answers = rawAnswers.stream()
                .map(a -> a == null ? "" : C0_CONTROLS.matcher(a).replaceAll(""))
                .toList();
        for (var i = 0; i < answers.size(); i++) {
            if (!validateAnswer(player, answers.get(i), tag)) {
                plugin.getPluginLogger().debug("Validation failed for answer " + i
                        + " of dialog prompt for " + player.getName());
                showPrompt(player, tag);
                return;
            }
        }
        if (checkBreakIf(player, tag, answers)) {
            return;
        }
        var submitted = engine.submitAnswers(player, answers, expected);
        handleSubmitted(player, submitted, expectedIncarnation, expectedGeneration);
    }

    /**
     * Decodes a compound RS/US payload into sub-answers, validates each
     * against the block-level constraints, and submits all at once. Used only
     * as a defensive fallback for compound tags that did not produce a
     * {@link DialogPromptScreen}; dialog screens route through
     * {@link #handleDialogResult}. TITLE/BODY layout rows never validate or
     * submit — only answer-bearing sub-tags occupy answer positions.
     */
    private void handleCompoundResult(
            Player player,
            PromptTag tag,
            String rawPayload,
            long expectedIncarnation,
            long expectedGeneration) {
        var answerTags = answerBearingTags(tag);
        var rawAnswers = decodeAnswers(rawPayload, answerTags.size());
        if (rawAnswers == null) {
            // Defensive fallback: re-show prompt if compound payload is malformed.
            plugin.getPluginLogger().warn("Malformed compound payload received from dialog for "
                    + player.getName());
            showPrompt(player, tag);
            return;
        }
        var answers = rawAnswers.stream()
                .map(a -> a == null ? "" : C0_CONTROLS.matcher(a).replaceAll(""))
                .toList();
        for (var i = 0; i < answers.size(); i++) {
            if (!validateAnswer(player, answers.get(i), tag)) {
                plugin.getPluginLogger().debug("Validation failed for sub-answer " + i
                        + " of compound prompt for " + player.getName());
                showPrompt(player, tag);
                return;
            }
        }
        if (checkBreakIf(player, tag, answers)) {
            return;
        }
        var submitted = engine.submitAnswers(player, answers, answerTags.size());
        handleSubmitted(player, submitted, expectedIncarnation, expectedGeneration);
    }

    /**
     * Evaluates the current prompt tag's breakIf condition against the combined accepted and candidate
     * answers before submission. If the condition is met (true), cancels the session with MANUAL reason.
     * If an evaluation error occurs, fails closed with ERROR reason.
     *
     * @param player the command sender
     * @param tag the current prompt tag
     * @param candidateAnswers candidate answers ready for submission
     * @return true if the flow was broken/terminated, false to continue normal submission
     */
    private boolean checkBreakIf(Player player, PromptTag tag, List<String> candidateAnswers) {
        if (tag == null || tag.breakIf() == null) {
            return false;
        }
        var session = engine.getSession(player).orElse(null);
        var existingAnswers = session != null ? session.answers() : List.<String>of();
        var combinedAnswers = new java.util.ArrayList<String>(existingAnswers.size() + candidateAnswers.size());
        combinedAnswers.addAll(existingAnswers);
        combinedAnswers.addAll(candidateAnswers);
        var immutableCombined = java.util.Collections.unmodifiableList(combinedAnswers);
        var bindings = ConditionBindings.ofAnswers(immutableCombined);
        try {
            boolean shouldBreak = tag.breakIf().evaluate(bindings);
            if (shouldBreak) {
                plugin.getPluginLogger().debug("BreakIf condition met for " + player.getName()
                        + ": " + tag.breakIf().source());
                teardown(player, CancelReason.MANUAL, false, true);
                return true;
            }
            return false;
        } catch (Throwable t) {
            var msg = t.getMessage() != null
                    ? C0_CONTROLS.matcher(t.getMessage()).replaceAll("")
                    : t.getClass().getSimpleName();
            if (msg.length() > 128) {
                msg = msg.substring(0, 128);
            }
            plugin.getPluginLogger().warn("BreakIf evaluation failed for " + player.getName()
                    + " (" + tag.breakIf().source() + "): " + msg);
            teardown(player, CancelReason.ERROR, false, true);
            return true;
        }
    }

    /**
     * Returns the tags whose answers are actually submitted for a prompt.
     *
     * <p>For compound tags this is the sub-tag list filtered to answer-bearing
     * kinds (everything except {@link DialogInputKind#TITLE} and
     * {@link DialogInputKind#BODY} — layout rows never validate or enter
     * answer history). Non-compound tags (including JSON preset references)
     * yield the tag itself, so validation falls back to the block-level
     * constraints exactly as before.
     */
    public static List<PromptTag> answerBearingTags(PromptTag tag) {
        if (!tag.isCompound()) return List.of(tag);
        return tag.subTags().stream()
                .filter(sub -> DialogInputKind.parse(sub.filter()).isAnswerBearing())
                .toList();
    }

    /**
     * Decodes a compound payload via {@link AnswerEncoding#decode}.
     * Returns null if the payload is malformed.
     */
    static java.util.List<String> decodeAnswers(String payload, int expected) {
        return dev.cyr1en.promptpaper.screen.dialog.AnswerEncoding.decode(payload, expected);
    }

    /**
     * Validates a single answer against the tag's type constraint
     * and custom validator alias.
     */
    private boolean validateAnswer(Player player, String answer, PromptTag tag) {
        var i18n = plugin.getConfigLoader().getI18n();
        int maxLen = plugin != null && plugin.getConfigLoader() != null && plugin.getConfigLoader().getConfig() != null
                ? plugin.getConfigLoader().getConfig().maxAnswerLength()
                : 256;
        if (answer.length() > maxLen) {
            plugin.getPluginLogger().debug("Answer length (" + answer.length() + ") exceeds maximum ("
                    + maxLen + ") for " + player.getName());
            player.sendMessage(i18n.get("validation.answer_too_long", player, Placeholder.of("max", String.valueOf(maxLen))));
            return false;
        }
        switch (tag.type()) {
            case INTEGER -> {
                try {
                    Integer.parseInt(answer);
                } catch (NumberFormatException e) {
                    plugin.getPluginLogger().debug("Integer validation failed for "
                            + player.getName());
                    player.sendMessage(i18n.get("validation.invalid_integer", player));
                    return false;
                }
            }
            case STRING -> {
                if (answer.isBlank()) {
                    plugin.getPluginLogger().debug("String validation failed (blank) for "
                            + player.getName());
                    player.sendMessage(i18n.get("validation.invalid_string", player));
                    return false;
                }
            }
            case NONE -> {}
        }

        if (tag.validatorAlias() != null && !tag.validatorAlias().isBlank()) {
            var config = plugin.getConfigLoader().getPromptConfig();
            var validator = config.getInputValidator(tag.validatorAlias(), player, plugin);
            var valid = validator.validate(answer);
            plugin.getPluginLogger().debug("Validator " + tag.validatorAlias()
                    + " for " + player.getName() + ": valid=" + valid);
            if (!valid) {
                var msg = validator.messageOnFail();
                if (!msg.isBlank()) {
                    player.sendMessage(ComponentUtil.mini(msg));
                }
                return false;
            }
        }
        return true;
    }

    public boolean hasActiveScreen(Player player) {
        return activeScreenHandles.containsKey(player.getUniqueId())
                || activeScreens.containsKey(player.getUniqueId());
    }

    public InputScreen getActiveScreen(Player player) {
        var handle = activeScreenHandles.get(player.getUniqueId());
        if (handle != null) return handle.screen();
        return activeScreens.get(player.getUniqueId());
    }

    public boolean hasChatScreen(Player player) {
        var screen = getActiveScreen(player);
        if (screen instanceof TitleWrapperScreen wrapper) {
            screen = wrapper.delegate();
        }
        return screen instanceof ChatPromptScreen;
    }

    /**
     * Cancels the active screen, timeout, and session for the player.
     */
    public void cancelAll(Player player) {
        cancelAll(player, dev.cyr1en.promptpaper.engine.CancellationMode.USER_ACTIONS, false);
    }

    /**
     * Cancels the active screen, timeout, and session for the player, optionally notifying
     * players whose active prompt was cancelled.
     */
    public void cancelAll(Player player, boolean notifyCancelled) {
        cancelAll(player, dev.cyr1en.promptpaper.engine.CancellationMode.USER_ACTIONS, notifyCancelled);
    }

    /**
     * Cancels the active screen, timeout, and session for the player with the specified cancellation mode.
     */
    public void cancelAll(Player player, dev.cyr1en.promptpaper.engine.CancellationMode mode) {
        cancelAll(player, mode, false);
    }

    /**
     * Cancels the active screen, timeout, and session for the player with the specified cancellation mode and notification flag.
     */
    public void cancelAll(Player player, dev.cyr1en.promptpaper.engine.CancellationMode mode, boolean notifyCancelled) {
        var hadActiveSession = engine.hasActiveSession(player);
        teardown(player, CancelReason.MANUAL, true, notifyCancelled && hadActiveSession, mode);
        plugin.getPluginLogger().debug("Cancelled all for " + player.getName() + " (mode=" + mode + ")");
    }

    /** Clears state after a player scheduler retires without invoking player APIs. */
    public void discardState(UUID uuid) {
        if (uuid == null) return;
        unlinkAndInvalidateScreen(uuid, true);
        takeDispatchContext(uuid);
        releaseDerivedResources(uuid);
        engine.discard(uuid);
    }

    private InputScreen unlinkAndInvalidateScreen(UUID uuid, boolean detachCustomProvider) {
        cancelTimeout(uuid);
        var screen = unlinkActiveScreen(uuid);
        if (screen instanceof TitleWrapperScreen wrapper) {
            wrapper.invalidateCallbacks();
            if (detachCustomProvider && wrapper.delegate() instanceof CustomScreenAdapter adapter) {
                adapter.teardownDetach();
            }
        } else if (detachCustomProvider && screen instanceof CustomScreenAdapter adapter) {
            adapter.teardownDetach();
        } else if (screen instanceof PlayerUIScreen playerUIScreen) {
            playerUIScreen.invalidateCallbacks();
        }
        return screen;
    }

    private void releaseDerivedResources(UUID uuid) {
        if (plugin.getNonceRegistry() != null) {
            plugin.getNonceRegistry().invalidatePlayer(uuid);
        }
        if (plugin.getRateLimiter() != null) {
            plugin.getRateLimiter().reset(uuid);
        }
        if (executionCoordinator != null) {
            executionCoordinator.cancel(uuid);
        }
    }

    /**
     * Performs bulk teardown of all active custom screen sessions during host CommandPrompter shutdown.
     * Detaches all registrations, cancels sessions, detaches adapter delegates, discards state, and attempts
     * direct platform inventory closure without calling any custom provider delegate methods.
     */
    public void bulkTeardownCustomScreens() {
        if (engine != null && engine.getScreenKeyResolver() != null
                && engine.getScreenKeyResolver().customRegistry() != null) {
            engine.getScreenKeyResolver().customRegistry().unregisterAllAndGet();
        }

        List<ActiveScreenHandle> customHandles = new java.util.ArrayList<>();
        for (ActiveScreenHandle handle : activeScreenHandles.values()) {
            if (handle.customHandle() != null) {
                customHandles.add(handle);
            }
        }

        for (ActiveScreenHandle handle : customHandles) {
            UUID uuid = handle.playerUuid();
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                discardState(uuid);
                continue;
            }

            try {
                var executor = playerExecutorFactory.apply(player);
                executor.execute(() -> {
                    if (!player.isOnline() || !uuid.equals(player.getUniqueId())) {
                        discardState(uuid);
                        return;
                    }
                    teardownCustomProvider(player, handle);
                }, () -> discardState(uuid));
            } catch (Throwable t) {
                discardState(uuid);
            }
        }
    }

    /**
     * Tears down any remaining built-in active screens across online players during host shutdown.
     */
    public void teardownBuiltInScreens() {
        for (var player : Bukkit.getOnlinePlayers()) {
            var uuid = player.getUniqueId();
            if (hasActiveScreen(player)) {
                try {
                    var task = player.getScheduler().run(
                            plugin,
                            scheduledTask -> cancelAll(player, dev.cyr1en.promptpaper.engine.CancellationMode.DISCARD_ONLY, false),
                            () -> discardState(uuid));
                    if (task == null) discardState(uuid);
                } catch (Throwable t) {
                    discardState(uuid);
                }
            }
        }
    }

    /**
     * Gracefully tears down a custom screen session when its provider plugin is disabled.
     *
     * <p>Enforces cancel-before-close ordering, detaches adapter delegate references,
     * directly closes player platform UI, and never invokes provider delegate methods.</p>
     *
     * @param player the target player
     * @param activeHandle the active screen handle belonging to the disabled provider
     */
    public void teardownCustomProvider(Player player, ActiveScreenHandle activeHandle) {
        var uuid = player.getUniqueId();
        if (!teardownInProgress.add(uuid)) return;
        try {
            unlinkAndInvalidateScreen(uuid, true);
            var dispatchContext = takeDispatchContext(uuid);
            releaseDerivedResources(uuid);
            engine.cancel(player, CancelReason.MANUAL, dispatchContext, dev.cyr1en.promptpaper.engine.CancellationMode.DISCARD_ONLY);
            if (plugin.getConfigLoader() != null
                    && plugin.getConfigLoader().getConfig() != null
                    && plugin.getConfigLoader().getConfig().showCancelled()) {
                player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.cancelled", player));
            }
            try {
                player.closeInventory();
            } catch (Throwable ignored) {
            }
        } finally {
            teardownInProgress.remove(uuid);
        }
    }

    private void teardown(
            Player player, CancelReason reason, boolean closeScreen, boolean notifyCancelled) {
        teardown(player, reason, closeScreen, notifyCancelled, dev.cyr1en.promptpaper.engine.CancellationMode.USER_ACTIONS);
    }

    private void teardown(
            Player player,
            CancelReason reason,
            boolean closeScreen,
            boolean notifyCancelled,
            dev.cyr1en.promptpaper.engine.CancellationMode mode) {
        var uuid = player.getUniqueId();
        if (!teardownInProgress.add(uuid)) return;
        try {
            var screen = unlinkAndInvalidateScreen(uuid, false);
            var dispatchContext = takeDispatchContext(uuid);
            releaseDerivedResources(uuid);
            engine.cancel(player, reason, dispatchContext, mode);
            if (notifyCancelled && plugin.getConfigLoader() != null
                    && plugin.getConfigLoader().getConfig() != null
                    && plugin.getConfigLoader().getConfig().showCancelled()) {
                player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.cancelled", player));
            }
            if (closeScreen && screen != null) {
                try {
                    screen.close();
                } catch (Exception e) {
                    plugin.getPluginLogger().debug("Unable to close screen for " + uuid + ": "
                            + e.getMessage());
                }
            }
        } finally {
            teardownInProgress.remove(uuid);
        }
    }

    private PromptEngine.DispatchContext takeDispatchContext(UUID uuid) {
        var mode = dispatchModes.remove(uuid);
        var key = attachmentKeys.remove(uuid);
        if (mode == DispatchMode.CONSOLE) {
            return new PromptEngine.DispatchContext(
                    dev.cyr1en.promptpaper.preset.ExecuteAs.CONSOLE, null, false);
        }
        if (mode == DispatchMode.ATTACHMENT) {
            var permissionSnapshot = capturePermissionSnapshot(key);
            return new PromptEngine.DispatchContext(
                    dev.cyr1en.promptpaper.preset.ExecuteAs.PLAYER,
                    key,
                    true,
                    permissionSnapshot);
        }
        return PromptEngine.DispatchContext.player();
    }

    private DispatchContextSnapshot takeDispatchContextSnapshot(UUID uuid) {
        var mode = dispatchModes.remove(uuid);
        var key = attachmentKeys.remove(uuid);
        if (mode == DispatchMode.CONSOLE) {
            return DispatchContextSnapshot.console();
        }
        if (mode == DispatchMode.ATTACHMENT) {
            var permissionSnapshot = capturePermissionSnapshot(key);
            return new DispatchContextSnapshot(
                    dev.cyr1en.promptpaper.preset.ExecuteAs.PLAYER,
                    key,
                    true,
                    permissionSnapshot);
        }
        return DispatchContextSnapshot.player();
    }

    /** Captures the exact attachment list at the session completion/cancellation boundary. */
    private List<String> capturePermissionSnapshot(String permissionKey) {
        if (permissionKey == null || permissionKey.isBlank()) return List.of();
        try {
            var config = plugin.getConfigLoader().getConfig();
            var permissions = config == null ? null : config.getPermissionAttachment(permissionKey);
            if (permissions == null || permissions.length == 0) return List.of();
            return List.copyOf(java.util.Arrays.asList(permissions));
        } catch (Exception e) {
            plugin.getPluginLogger().debug(
                    "Unable to capture permission attachment key " + permissionKey + ": "
                            + e.getMessage());
            return List.of();
        }
    }

    /**
     * Schedules a timeout that auto-cancels the session if the player
     * does not respond within the configured duration.
     */
    private void scheduleTimeout(Player player, PromptTag tag) {
        cancelTimeout(player);
        var timeoutSecs = tag.timeout() != null
                ? tag.timeout()
                : plugin.getConfigLoader().getConfig().promptTimeout();
        if (timeoutSecs <= 0) return;
        var uuid = player.getUniqueId();
        var token = timeoutSequence.incrementAndGet();
        timeoutTokens.put(uuid, token);
        plugin.getPluginLogger().debug("Scheduling timeout for " + player.getName()
                + " in " + timeoutSecs + "s (tag override: " + (tag.timeout() != null) + ")");
        try {
            var task = player.getScheduler().runDelayed(
                    plugin,
                    scheduledTask -> {
                        if (!timeoutTokens.remove(uuid, token)) return;
                        timeoutTasks.remove(uuid);
                        var session = engine.getSession(player);
                        if (session.isPresent() && session.get().isActive()) {
                            plugin.getPluginLogger().debug("Timeout triggered for " + player.getName());
                            teardown(player, CancelReason.TIMEOUT, true, false);
                            if (plugin.getConfigLoader().getConfig().showCancelled()) {
                                player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.timed_out", player));
                            }
                        }
                    },
                    () -> discardTimeoutState(uuid, token),
                    timeoutSecs * 20L);
            if (task == null) {
                discardTimeoutState(uuid, token);
            } else if (timeoutTokens.get(uuid) == token) {
                timeoutTasks.put(uuid, task::cancel);
            } else {
                task.cancel();
            }
        } catch (Throwable t) {
            discardTimeoutState(uuid, token);
        }
    }

    private void cancelTimeout(Player player) {
        cancelTimeout(player.getUniqueId());
    }

    private void cancelTimeout(UUID uuid) {
        timeoutTokens.remove(uuid);
        var task = timeoutTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private void discardTimeoutState(UUID uuid, long token) {
        if (timeoutTokens.remove(uuid, token)) {
            discardState(uuid);
        }
    }
}
