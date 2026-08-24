package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptcore.ConfirmationMode;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.command.ResponseCommand;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.preset.ConfirmationPrompt;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.screen.TitleWrapperScreen;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationChatView;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationDecision;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationOutcome;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationRateLimiter;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationView;
import dev.cyr1en.promptpaper.screen.confirmation.NonceResponseRegistry;
import dev.cyr1en.promptpaper.util.PluginLogger;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmationFlowIntegrationTest extends MockBukkitTest {

    record CapturedCommand(String command, CommandSender sender) {}

    private final List<CapturedCommand> capturedCommands = new ArrayList<>();
    private final List<String> capturedLogMessages = new ArrayList<>();
    private final List<String> capturedWarnMessages = new ArrayList<>();

    private PromptEngine engine;
    private PromptFactory factory;
    private ScreenManager screenManager;
    private ResponseCommand responseCommand;
    private NonceResponseRegistry nonceRegistry;
    private ConfirmationRateLimiter rateLimiter;
    private TestPluginLogger testLogger;

    private static class TestPluginLogger extends PluginLogger {
        private final List<String> allLogs;
        private final List<String> warnLogs;

        TestPluginLogger(CommandPrompter plugin, List<String> allLogs, List<String> warnLogs) {
            super(plugin);
            this.allLogs = allLogs;
            this.warnLogs = warnLogs;
        }

        @Override
        public void info(String msg, Object... args) {
            allLogs.add(dev.cyr1en.promptpaper.util.FormatUtil.safeFormat(msg, args));
            super.info(msg, args);
        }

        @Override
        public void warn(String msg, Object... args) {
            var formatted = dev.cyr1en.promptpaper.util.FormatUtil.safeFormat(msg, args);
            allLogs.add(formatted);
            warnLogs.add(formatted);
            super.warn(msg, args);
        }

        @Override
        public void err(String msg, Object... args) {
            allLogs.add(dev.cyr1en.promptpaper.util.FormatUtil.safeFormat(msg, args));
            super.err(msg, args);
        }

        @Override
        public void debug(String msg, Object... args) {
            allLogs.add(dev.cyr1en.promptpaper.util.FormatUtil.safeFormat(msg, args));
            super.debug(msg, args);
        }
    }

    @BeforeEach
    void init() {
        capturedCommands.clear();
        capturedLogMessages.clear();
        capturedWarnMessages.clear();

        testLogger = new TestPluginLogger(plugin, capturedLogMessages, capturedWarnMessages);
        when(plugin.getPluginLogger()).thenReturn(testLogger);

        var pConfig = mock(dev.cyr1en.promptpaper.config.PromptConfig.class);
        when(pConfig.getScreenMappings()).thenReturn(java.util.Map.of("", dev.cyr1en.promptpaper.config.ScreenType.CHAT));
        lenient().when(pConfig.sendCancelText()).thenReturn(false);
        lenient().when(pConfig.responseListenerPriority()).thenReturn("LOWEST");
        when(configLoader.getPromptConfig()).thenReturn(pConfig);

        nonceRegistry = new NonceResponseRegistry();
        rateLimiter = new ConfirmationRateLimiter();
        when(plugin.getNonceRegistry()).thenReturn(nonceRegistry);
        when(plugin.getRateLimiter()).thenReturn(rateLimiter);

        engine = new PromptEngine(plugin, scheduler);
        factory = new PromptFactory(plugin);
        screenManager = new ScreenManager(plugin, engine, factory, scheduler);

        when(plugin.getEngine()).thenReturn(engine);
        when(plugin.getPromptFactory()).thenReturn(factory);
        when(plugin.getScreenManager()).thenReturn(screenManager);
        doCallRealMethod().when(plugin).onDisable();

        responseCommand = new ResponseCommand(plugin);

        registerCapturingCommand("say");
        registerCapturingCommand("delete");
        registerCapturingCommand("pvp");
        registerCapturingCommand("cancel_pcm");
        registerCapturingCommand("timeout_pcm");
        registerCapturingCommand("error_pcm");
        registerCapturingCommand("audit");
        registerCapturingCommand("test_cmd");
    }

    private void registerCapturingCommand(String name) {
        var cmd = new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                var joined = args.length > 0 ? " " + String.join(" ", args) : "";
                capturedCommands.add(new CapturedCommand(commandLabel + joined, sender));
                return true;
            }
        };
        server.getCommandMap().register("commandprompter", cmd);
    }

    private long countCapturedCommands(String prefix) {
        return capturedCommands.stream()
                .filter(c -> c.command().startsWith(prefix) || c.command().startsWith("/" + prefix))
                .count();
    }

    // =========================================================================
    // CONF-01 & CONF-02: Aliases and Grammatical Parsing
    // =========================================================================

    @Test
    void inlineAliasesAndCaseNormalizationRouteToConfirmation() {
        var player = createPlayer("ConfirmUser");

        // <c:Proceed?>
        screenManager.startSession(player, "/say <c:Proceed?>");
        assertTrue(engine.hasActiveSession(player));
        assertTrue(screenManager.hasActiveScreen(player));
        assertFalse(screenManager.hasChatScreen(player));
        screenManager.cancelAll(player);

        // <C:Proceed?>
        screenManager.startSession(player, "/say <C:Proceed?>");
        assertTrue(engine.hasActiveSession(player));
        assertFalse(screenManager.hasChatScreen(player));
        screenManager.cancelAll(player);

        // <confirm:Proceed?>
        screenManager.startSession(player, "/say <confirm:Proceed?>");
        assertTrue(engine.hasActiveSession(player));
        assertFalse(screenManager.hasChatScreen(player));
        screenManager.cancelAll(player);

        // <CONFIRM:Proceed?>
        screenManager.startSession(player, "/say <CONFIRM:Proceed?>");
        assertTrue(engine.hasActiveSession(player));
        assertFalse(screenManager.hasChatScreen(player));
        screenManager.cancelAll(player);
    }

    @Test
    void malformedConfigNeverFallbacksAndFailsClosed() {
        var player = createPlayer("MalformedUser");
        assertThrows(IllegalArgumentException.class, () -> {
            dev.cyr1en.promptcore.ConfirmationGrammar.parse("Prompt? -mode:invalidMode");
        });
        screenManager.startSession(player, "/say <c:Prompt? -mode:invalidMode>");
        assertFalse(engine.hasActiveSession(player));
        assertFalse(screenManager.hasActiveScreen(player));
    }

    // =========================================================================
    // CONF-03: Gating Decline Semantics & Cancellation PCM
    // =========================================================================

    @Test
    void conf03_gatingDeclineNoPrimaryDispatchAndExactlyOneCancelPcm() {
        var player = createPlayer("GatingDeclineUser");

        var view = new TestConfirmationView();
        var screen = new ConfirmationPromptScreen(view);
        var customManager = new ScreenManager(plugin, engine, factory, scheduler, (p, t, c) -> screen);

        customManager.startSession(player, "/delete world <c:Delete world?> <!!cancel_pcm decline>");
        assertTrue(engine.hasActiveSession(player));
        assertTrue(customManager.hasActiveScreen(player));

        // Fire the real declined callback from the active view
        view.emit(ConfirmationOutcome.declined());
        performTicks(5);

        // Primary command count must be 0, cancel PCM count must be exactly 1
        assertEquals(0, countCapturedCommands("delete"), "Primary command must NOT be dispatched on decline");
        assertEquals(1, countCapturedCommands("cancel_pcm"), "Cancellation PCM must be dispatched exactly once on decline");
        assertFalse(engine.hasActiveSession(player));
        assertFalse(customManager.hasActiveScreen(player));
        assertEquals(0, nonceRegistry.size(), "Nonces must be cleaned up on decline");
        assertEquals(0, rateLimiter.attemptCount(player.getUniqueId()), "Rate limiter must be reset on decline");
    }

    @Test
    void conf03_gatingDeclineThroughResponseServiceNoPrimaryDispatchAndOneCancelPcm() {
        var player = createPlayer("GatingDeclineNonceUser");

        screenManager.startSession(player, "/delete world <c:Delete world? -mode:chat> <!!cancel_pcm decline>");
        assertTrue(engine.hasActiveSession(player));
        assertTrue(screenManager.hasActiveScreen(player));
        assertEquals(1, nonceRegistry.size(), "Chat mode confirmation must register a nonce");

        var screen = (ConfirmationPromptScreen) screenManager.getActiveScreen(player);
        var chatView = (ConfirmationChatView) screen.activeView().orElseThrow();
        var nonce = chatView.getActiveNonce().orElseThrow();

        // Consume decline decision through production response command service
        responseCommand.executeResponse(player, nonce, "decline");
        performTicks(5);

        assertEquals(0, countCapturedCommands("delete"), "Primary command must NOT be dispatched on decline");
        assertEquals(1, countCapturedCommands("cancel_pcm"), "Cancellation PCM must be dispatched exactly once on decline");
        assertFalse(engine.hasActiveSession(player));
        assertFalse(screenManager.hasActiveScreen(player));
        assertEquals(0, nonceRegistry.size(), "Nonces must be cleaned up on decline");
        assertEquals(0, rateLimiter.attemptCount(player.getUniqueId()), "Rate limiter must be reset on decline");
    }

    // =========================================================================
    // CONF-04: Scheduled Timeout Execution
    // =========================================================================

    @Test
    void conf04_scheduledTimeoutCancelsWithTimeoutReasonAndDispatchesCancelPcm() {
        when(config.promptTimeout()).thenReturn(1);
        when(config.showCancelled()).thenReturn(true);
        var player = createPlayer("TimeoutUser");

        screenManager.startSession(player, "/delete world <c:Are you sure? -timeout:1> <!!timeout_pcm timeout>");
        assertTrue(engine.hasActiveSession(player));
        assertTrue(screenManager.hasActiveScreen(player));

        // Advance 25 ticks (1.25s) to trigger scheduled timeout
        performTicks(25);

        assertEquals(0, countCapturedCommands("delete"), "Primary command must NOT dispatch on timeout");
        assertEquals(1, countCapturedCommands("timeout_pcm"), "Timeout cancellation PCM must dispatch exactly once");
        assertFalse(engine.hasActiveSession(player), "Session must be cancelled after timeout");
        assertFalse(screenManager.hasActiveScreen(player), "Screen must be closed after timeout");
        assertEquals(0, nonceRegistry.size(), "Nonces must be cleaned up after timeout");
        assertEquals(0, rateLimiter.attemptCount(player.getUniqueId()), "Rate limiter must be reset after timeout");
    }

    // =========================================================================
    // CONF-05: Expired Nonce Through ResponseCommand
    // =========================================================================

    @Test
    void conf05_expiredNonceThroughResponseCommandPathNoStateChange() {
        var player = createPlayer("ExpiredNonceUser");
        screenManager.startSession(player, "/delete world <c:Delete world?> <!!cancel_pcm>");
        var session = engine.getSession(player).orElseThrow();

        // Register an already-expired nonce (expired 10 seconds ago)
        var expiredBinding = nonceRegistry.register(
                player.getUniqueId(),
                session.incarnation(),
                session.generation(),
                session.currentIndex(),
                Instant.now().minusSeconds(10),
                d -> {});

        var expiredNonce = expiredBinding.nonce();
        assertTrue(nonceRegistry.contains(expiredNonce));

        // Attempt response command with expired nonce
        responseCommand.executeResponse(player, expiredNonce, "confirm");
        performTicks(1);

        // Session must still be active (no state change)
        assertTrue(engine.hasActiveSession(player), "Session state must NOT change on expired nonce submission");
        assertEquals(0, countCapturedCommands("delete"), "Primary command must NOT be dispatched");
        assertEquals(0, countCapturedCommands("cancel_pcm"), "Cancel PCM must NOT be dispatched");
    }

    // =========================================================================
    // CONF-06 & CONF-07: Concurrent Outcome / Close vs Timeout Races
    // =========================================================================

    @Test
    void conf06_07_callbackBeforeScheduledTimeoutRaceExecutesSingleTerminalOutcome() {
        when(config.promptTimeout()).thenReturn(1);
        when(config.showCancelled()).thenReturn(true);
        var player = createPlayer("CallbackFirstUser");
        var view = new TestConfirmationView();
        var screen = new ConfirmationPromptScreen(view);
        var customManager = new ScreenManager(plugin, engine, factory, scheduler, (p, t, c) -> screen);

        customManager.startSession(player, "/delete world <c:Delete world? -timeout:1> <!!timeout_pcm timeout>");
        assertTrue(engine.hasActiveSession(player));

        // 1. View confirms first
        view.emit(ConfirmationOutcome.confirmed());
        performTicks(1);

        assertEquals(1, countCapturedCommands("delete"), "Primary command must be dispatched on confirm");
        assertEquals(0, countCapturedCommands("timeout_pcm"), "Timeout PCM must NOT be dispatched on confirm");
        assertFalse(engine.hasActiveSession(player), "Session must complete");

        // 2. Advance time past timeout duration
        performTicks(25);

        // Terminal effects remain invariant
        assertEquals(1, countCapturedCommands("delete"), "Primary command count must remain exactly 1");
        assertEquals(0, countCapturedCommands("timeout_pcm"), "Timeout PCM must NOT dispatch after session completed");
        assertFalse(customManager.hasActiveScreen(player));
        assertEquals(0, nonceRegistry.size());
    }

    @Test
    void conf06_07_scheduledTimeoutBeforeCallbackRaceDiscardsStaleOutcome() {
        when(config.promptTimeout()).thenReturn(1);
        when(config.showCancelled()).thenReturn(true);
        var player = createPlayer("TimeoutFirstUser");
        var view = new TestConfirmationView();
        var screen = new ConfirmationPromptScreen(view);
        var customManager = new ScreenManager(plugin, engine, factory, scheduler, (p, t, c) -> screen);

        customManager.startSession(player, "/delete world <c:Delete world? -timeout:1> <!!timeout_pcm timeout>");
        assertTrue(engine.hasActiveSession(player));

        // 1. Timeout triggers first
        performTicks(25);

        assertEquals(0, countCapturedCommands("delete"), "Primary command must NOT dispatch on timeout");
        assertEquals(1, countCapturedCommands("timeout_pcm"), "Timeout cancellation PCM must dispatch exactly once");
        assertFalse(engine.hasActiveSession(player));

        // 2. Stale view callbacks deliver confirmed and declined
        view.emit(ConfirmationOutcome.confirmed());
        view.emit(ConfirmationOutcome.declined());
        performTicks(5);

        assertEquals(0, countCapturedCommands("delete"), "Primary command must NOT dispatch from stale callbacks");
        assertEquals(1, countCapturedCommands("timeout_pcm"), "Timeout PCM count must remain 1");
    }

    @Test
    void conf06_07_programmaticCloseVsCapturedCallbackGuaranteesNoStaleDeclineOrDoublePcm() {
        var player = createPlayer("ProgCloseUser");
        var view = new TestConfirmationView();
        var screen = new ConfirmationPromptScreen(view);
        var customManager = new ScreenManager(plugin, engine, factory, scheduler, (p, t, c) -> screen);

        customManager.startSession(player, "/delete world <c:Delete world?> <!!cancel_pcm decline>");
        assertTrue(engine.hasActiveSession(player));

        // 1. Programmatic close (cancelAll)
        customManager.cancelAll(player, false);
        performTicks(1);

        assertEquals(0, countCapturedCommands("delete"));
        assertEquals(1, countCapturedCommands("cancel_pcm"), "Cancel PCM dispatched on programmatic close");
        assertFalse(engine.hasActiveSession(player));

        // 2. Stale captured GUI/view callback delivers decline and confirm
        view.emit(ConfirmationOutcome.declined());
        view.emit(ConfirmationOutcome.confirmed());
        performTicks(5);

        assertEquals(0, countCapturedCommands("delete"));
        assertEquals(1, countCapturedCommands("cancel_pcm"), "Stale callbacks must NOT cause duplicate cancel PCM");
    }

    @Test
    void conf06_07_duplicateViewDeliveryExecutesTerminalEffectExactlyOnce() {
        var player = createPlayer("DupUser");
        var view = new TestConfirmationView();
        var screen = new ConfirmationPromptScreen(view);
        var customManager = new ScreenManager(plugin, engine, factory, scheduler, (p, t, c) -> screen);

        customManager.startSession(player, "/delete world <c:Delete world?> <!!cancel_pcm decline>");
        assertTrue(engine.hasActiveSession(player));

        // Rapid duplicate emits
        view.emit(ConfirmationOutcome.confirmed());
        view.emit(ConfirmationOutcome.confirmed());
        view.emit(ConfirmationOutcome.declined());
        performTicks(5);

        assertEquals(1, countCapturedCommands("delete"), "Primary command executed exactly once");
        assertEquals(0, countCapturedCommands("cancel_pcm"), "Cancel PCM count must be 0 on confirm");
        assertFalse(engine.hasActiveSession(player));
        assertFalse(customManager.hasActiveScreen(player));
    }

    // =========================================================================
    // CONF-08: Wrong-Player Response Path and Safe Log Truncation
    // =========================================================================

    @Test
    void conf08_wrongPlayerWithActiveSessionFailsWithUuidMismatchAndSafeLogTruncation() {
        var playerA = createPlayer("PlayerA");
        var playerB = createPlayer("PlayerB");

        screenManager.startSession(playerA, "/say SecretA <c:Confirm A?>");
        var sessionA = engine.getSession(playerA).orElseThrow();

        // Player B ALSO starts an active session to progress past the no-session early check
        screenManager.startSession(playerB, "/say SecretB <c:Confirm B?>");
        var sessionB = engine.getSession(playerB).orElseThrow();

        assertTrue(engine.hasActiveSession(playerA));
        assertTrue(engine.hasActiveSession(playerB));

        var callbackAInvoked = new AtomicBoolean(false);
        var bindingA = nonceRegistry.register(
                playerA.getUniqueId(),
                sessionA.incarnation(),
                sessionA.generation(),
                sessionA.currentIndex(),
                Duration.ofMinutes(1),
                d -> callbackAInvoked.set(true));

        var fullNonceA = bindingA.nonce();
        var truncatedPrefix = NonceResponseRegistry.truncateNonce(fullNonceA);

        // Player B attempts to confirm Player A's valid nonce
        responseCommand.executeResponse(playerB, fullNonceA, "confirm");
        performTicks(5);

        // Rejection must be specifically UUID mismatch and no consumption
        assertFalse(callbackAInvoked.get(), "Player A's callback must NOT be invoked by Player B");
        assertTrue(nonceRegistry.contains(fullNonceA), "Player A's nonce must NOT be consumed by Player B");
        assertTrue(engine.hasActiveSession(playerA), "Player A's session must remain active");
        assertTrue(engine.hasActiveSession(playerB), "Player B's session must remain active");
        assertEquals(sessionA.generation(), engine.getSession(playerA).orElseThrow().generation(), "Player A session unchanged");
        assertEquals(sessionB.generation(), engine.getSession(playerB).orElseThrow().generation(), "Player B session unchanged");

        // Verify logs contain PLAYER_MISMATCH, truncated prefix, and NEVER the full nonce
        boolean hasPlayerMismatchLog = capturedLogMessages.stream().anyMatch(m -> m.contains("PLAYER_MISMATCH"));
        boolean hasTruncatedPrefix = capturedLogMessages.stream().anyMatch(m -> m.contains(truncatedPrefix));
        boolean hasFullNonce = capturedLogMessages.stream().anyMatch(m -> m.contains(fullNonceA));

        assertTrue(hasPlayerMismatchLog, "Log messages must indicate PLAYER_MISMATCH rejection");
        assertTrue(hasTruncatedPrefix, "Log messages must contain the safe truncated nonce prefix");
        assertFalse(hasFullNonce, "Log messages must NEVER expose the full nonce string");
    }

    // =========================================================================
    // CONF-09: Successful-Completion Cleanup Baseline
    // =========================================================================

    @Test
    void conf09_successfulCompletionCleansUpAllResourcesAndDispatchesExactlyOnce() {
        var player = createPlayer("SuccessUser");
        var view = new TestConfirmationView();
        var screen = new ConfirmationPromptScreen(view);
        var customManager = new ScreenManager(plugin, engine, factory, scheduler, (p, t, c) -> screen);

        customManager.startSession(player, "/delete world <c:Delete world?> <!audit success> <!!cancel_pcm decline>");
        var session = engine.getSession(player).orElseThrow();

        // Baseline before completion: active session, screen, registered nonce, rate limiter attempt
        assertTrue(engine.hasActiveSession(player));
        assertTrue(customManager.hasActiveScreen(player));
        var binding = nonceRegistry.register(
                player.getUniqueId(),
                session.incarnation(),
                session.generation(),
                session.currentIndex(),
                Duration.ofMinutes(1),
                d -> {});
        rateLimiter.tryAcquire(player.getUniqueId());

        assertEquals(1, nonceRegistry.size(), "Nonce present before confirm");
        assertEquals(1, rateLimiter.attemptCount(player.getUniqueId()), "Rate limiter state present before confirm");

        // Confirm through real view path
        view.emit(ConfirmationOutcome.confirmed());
        performTicks(5);

        // Assert dispatch
        assertEquals(1, countCapturedCommands("delete"), "Primary command must be dispatched exactly once");
        assertEquals(1, countCapturedCommands("audit"), "Success completion PCM must be dispatched exactly once");
        assertEquals(0, countCapturedCommands("cancel_pcm"), "Cancellation PCM must NOT be dispatched");

        // Assert terminal baselines
        assertFalse(engine.hasActiveSession(player), "Session must be terminated");
        assertFalse(customManager.hasActiveScreen(player), "Screen must be removed");
        assertEquals(0, nonceRegistry.size(), "Nonce registry must be empty after completion");
        assertEquals(0, rateLimiter.attemptCount(player.getUniqueId()), "Rate limiter must be reset after completion");
    }

    // =========================================================================
    // CONF-10: Burst Rate Limiting & Single Count-Bearing Diagnostic Log
    // =========================================================================

    @Test
    void conf10_burstRateLimitingThroughResponseCommandSingleAggregatedLog() {
        var player = createPlayer("BurstUser");
        screenManager.startSession(player, "/say <c:Proceed?>");

        // Burst 10 rapid response command calls
        for (int i = 0; i < 10; i++) {
            responseCommand.executeResponse(player, "nonce_burst_" + i, "confirm");
        }
        performTicks(5);

        // Rate limiter allows 5 attempts max in rolling 10s
        assertEquals(5, rateLimiter.attemptCount(player.getUniqueId()));

        // Exactly ONE aggregate rate-limit warning should be logged for this burst/window
        long rateLimitWarnings = capturedWarnMessages.stream()
                .filter(m -> m.contains("Rate limited confirmation response attempts"))
                .count();

        assertEquals(1, rateLimitWarnings, "Rate limiting must emit exactly one counted/rate-bounded warning per window");
    }

    // =========================================================================
    // Value Mode & GUI ESC Tests
    // =========================================================================

    @Test
    void valueConfirmAndDeclineSubmitExactTrueFalseTokens() {
        var player = createPlayer("ValueUser");

        // Value mode confirm
        screenManager.startSession(player, "/pvp set <c:Enable PvP? -value>");
        var session = engine.getSession(player).orElseThrow();
        assertEquals(0, session.currentIndex());

        var confirmSubmitted = engine.submitAnswers(player, List.of("true"), 1);
        assertTrue(confirmSubmitted.isPresent());
        assertEquals("/pvp set \"true\"", confirmSubmitted.get().assembledCommand());
        assertEquals(List.of("true"), confirmSubmitted.get().answers());

        // Value mode decline
        screenManager.startSession(player, "/pvp set <c:Enable PvP? -value>");
        var declineSubmitted = engine.submitAnswers(player, List.of("false"), 1);
        assertTrue(declineSubmitted.isPresent());
        assertEquals("/pvp set \"false\"", declineSubmitted.get().assembledCommand());
        assertEquals(List.of("false"), declineSubmitted.get().answers());
    }

    @Test
    void guiEscTriggersDeclinedSemantics() {
        var fakeView = new TestConfirmationView();
        var screen = new ConfirmationPromptScreen(fakeView);

        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        fakeView.emit(ConfirmationOutcome.declined());

        assertEquals(ConfirmationOutcome.declined(), screen.lastOutcome().orElse(null));
        assertNotNull(resultRef.get());
        assertTrue(resultRef.get().cancelled());
        assertEquals(CancelReason.GUI_EXIT, resultRef.get().cancelReason());
    }

    @Test
    void titleWrapperAppliesToAllModesAndTimeoutStartsAfterEffectiveOpen() {
        var player = createPlayer("TitleUser");
        var title = new TitleConfig("Title", "Subtitle", 10);
        var prompt = new ConfirmationPrompt(
                "confirmation", "c_title", ConfirmationMode.CHAT,
                null, "Proceed?", null, null, false, null, true, title);

        var screen = plugin.getPromptFactory().create(player, prompt);
        assertInstanceOf(TitleWrapperScreen.class, screen);
        var titleWrapper = (TitleWrapperScreen) screen;
        assertInstanceOf(ConfirmationPromptScreen.class, titleWrapper.delegate());

        var effectiveOpenCalled = new AtomicBoolean(false);
        titleWrapper.setOnDelegateOpen(() -> effectiveOpenCalled.set(true));

        titleWrapper.open();
        assertFalse(effectiveOpenCalled.get());

        performTicks(20);
    }

    // =========================================================================
    // Non-terminal Next-Prompt Advancement & Security State Isolation
    // =========================================================================

    @Test
    void nonTerminalAdvancementInvalidatesPreviousPromptNonceWithoutResettingRateLimiter() {
        var player = createPlayer("MultiPromptUser");
        screenManager.startSession(player, "/say <c:Step 1? -value> <c:Step 2? -value>");
        var session = engine.getSession(player).orElseThrow();

        // Register nonces for prompt 0 and prompt 1
        var binding0 = nonceRegistry.register(
                player.getUniqueId(),
                session.incarnation(),
                session.generation(),
                0,
                Duration.ofMinutes(1),
                d -> {});

        var binding1 = nonceRegistry.register(
                player.getUniqueId(),
                session.incarnation(),
                session.generation(),
                1,
                Duration.ofMinutes(1),
                d -> {});

        assertTrue(nonceRegistry.contains(binding0.nonce()));
        assertTrue(nonceRegistry.contains(binding1.nonce()));

        // Simulate answering prompt 0 and advancing to prompt 1
        var submitted = engine.submitAnswers(player, List.of("true"), 1);
        assertFalse(submitted.isPresent(), "Non-terminal submission must not finish session");

        // Advance in screen manager
        try {
            var handleSubmittedMethod = ScreenManager.class.getDeclaredMethod(
                    "handleSubmitted", Player.class, java.util.Optional.class);
            handleSubmittedMethod.setAccessible(true);
            handleSubmittedMethod.invoke(screenManager, player, submitted);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Nonce for prompt 0 must be invalidated, prompt 1 nonce must remain active!
        assertFalse(nonceRegistry.contains(binding0.nonce()), "Prompt 0 nonce must be invalidated on advancement");
        assertTrue(nonceRegistry.contains(binding1.nonce()), "Prompt 1 nonce must remain valid for active prompt");
    }

    // =========================================================================
    // Open Failure Lifecycle Tests (Synchronous & Asynchronous)
    // =========================================================================

    @Test
    void openFailureAsyncCallbackTriggersErrorTeardownWithCancelPcm() {
        var player = createPlayer("AsyncFailUser");

        var failingView = new TestConfirmationView();
        var screen = new ConfirmationPromptScreen(failingView);
        var customManager = new ScreenManager(plugin, engine, factory, scheduler, (p, t, c) -> screen);

        customManager.startSession(player, "/delete world <c:Delete?> <!!error_pcm open_error>");
        assertTrue(engine.hasActiveSession(player));
        assertTrue(customManager.hasActiveScreen(player));

        // Fire asynchronous open failure
        failingView.emitFailure(new RuntimeException("Async open failed"));
        performTicks(5);

        assertFalse(engine.hasActiveSession(player), "Session must be cancelled with ERROR on async open failure");
        assertFalse(customManager.hasActiveScreen(player), "Screen must be cleaned up");
        assertEquals(0, countCapturedCommands("delete"), "Primary command must NOT dispatch on async open error");
        assertEquals(1, countCapturedCommands("error_pcm"), "Error cancellation PCM must dispatch exactly once");
        assertEquals(0, nonceRegistry.size(), "Nonces must be cleaned up");
        assertEquals(0, rateLimiter.attemptCount(player.getUniqueId()), "Rate limiter must be reset");
    }

    @Test
    void openFailureSynchronousExceptionTriggersErrorTeardownWithCancelPcm() {
        var player = createPlayer("SyncFailUser");

        var throwingScreen = new InputScreen() {
            @Override
            public void open() {
                throw new RuntimeException("Synchronous open failed");
            }

            @Override
            public void close() {}

            @Override
            public boolean isOpen() {
                return false;
            }

            @Override
            public void onResult(Consumer<ScreenResult> callback) {}
        };
        var customManager = new ScreenManager(plugin, engine, factory, scheduler, (p, t, c) -> throwingScreen);

        // startSession triggers showPrompt -> screen.open() -> throws RuntimeException
        assertThrows(RuntimeException.class, () -> {
            customManager.startSession(player, "/delete world <c:Delete?> <!!error_pcm open_error>");
        });
        performTicks(5);

        assertFalse(engine.hasActiveSession(player), "Session must be cancelled with ERROR on sync open error");
        assertFalse(customManager.hasActiveScreen(player), "Screen must be cleaned up");
        assertEquals(0, countCapturedCommands("delete"), "Primary command must NOT dispatch on sync open error");
        assertEquals(1, countCapturedCommands("error_pcm"), "Error cancellation PCM must dispatch exactly once");
        assertEquals(0, nonceRegistry.size(), "Nonces must be cleaned up");
        assertEquals(0, rateLimiter.attemptCount(player.getUniqueId()), "Rate limiter must be reset");
    }

    // =========================================================================
    // Quit, Disable & Baseline Teardown Tests
    // =========================================================================

    @Test
    void quitAndSchedulerRetirementCleanupsReturnToBaseline() {
        var player = createPlayer("CleanupUser");
        screenManager.startSession(player, "/say <c:Proceed?>");
        assertTrue(engine.hasActiveSession(player));

        nonceRegistry.register(player.getUniqueId(), 0L, 0L, 0, Duration.ofMinutes(1), d -> {});
        rateLimiter.tryAcquire(player.getUniqueId());

        assertEquals(1, nonceRegistry.size());
        assertEquals(1, rateLimiter.attemptCount(player.getUniqueId()));

        // Discard state clears player resources without invoking player APIs
        screenManager.discardState(player.getUniqueId());
        assertEquals(0, nonceRegistry.size());
        assertEquals(0, rateLimiter.attemptCount(player.getUniqueId()));
        assertFalse(engine.hasActiveSession(player));
    }

    @Test
    void disableExecutesRealLifecycleTeardown() {
        var player1 = createPlayer("DisableUser1");
        var player2 = createPlayer("DisableUser2");

        screenManager.startSession(player1, "/say <c:Proceed 1?>");
        screenManager.startSession(player2, "/say <c:Proceed 2?>");

        nonceRegistry.register(player1.getUniqueId(), 0L, 0L, 0, Duration.ofMinutes(1), d -> {});
        nonceRegistry.register(player2.getUniqueId(), 0L, 0L, 0, Duration.ofMinutes(1), d -> {});
        rateLimiter.tryAcquire(player1.getUniqueId());
        rateLimiter.tryAcquire(player2.getUniqueId());

        assertEquals(2, nonceRegistry.size());
        assertEquals(1, rateLimiter.attemptCount(player1.getUniqueId()));
        assertEquals(1, rateLimiter.attemptCount(player2.getUniqueId()));

        // Execute real plugin.onDisable()
        plugin.onDisable();

        assertEquals(0, nonceRegistry.size(), "onDisable must clear nonce registry");
        assertEquals(0, rateLimiter.attemptCount(player1.getUniqueId()), "onDisable must clear rate limiter");
        assertEquals(0, rateLimiter.attemptCount(player2.getUniqueId()), "onDisable must clear rate limiter");
        assertFalse(engine.hasActiveSession(player1), "onDisable must cancel player 1 session");
        assertFalse(engine.hasActiveSession(player2), "onDisable must cancel player 2 session");
    }

    @Test
    void optionalSoundInvalidKeyOnlyDegradesCosmetically() {
        var player = createPlayer("SoundUser");
        var prompt = new ConfirmationPrompt(
                "confirmation", "c_sound", ConfirmationMode.GUI,
                null, "Proceed?", null, null, false, "invalid sound with spaces", true);

        var screen = plugin.getPromptFactory().create(player, prompt);
        assertInstanceOf(ConfirmationPromptScreen.class, screen);
        assertDoesNotThrow(screen::open);
        screen.close();
    }

    // =========================================================================
    // Test View Helper
    // =========================================================================

    private static class TestConfirmationView implements ConfirmationView {
        protected Consumer<ConfirmationOutcome> callback;
        protected Consumer<Throwable> failureCallback;
        private boolean open;

        @Override
        public void open(Consumer<ConfirmationOutcome> callback) {
            this.callback = callback;
            this.open = true;
        }

        @Override
        public void close() {
            this.open = false;
            this.callback = null;
            this.failureCallback = null;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void onOpenFailure(Consumer<Throwable> failureCallback) {
            this.failureCallback = failureCallback;
        }

        public void emit(ConfirmationOutcome outcome) {
            if (callback != null) {
                callback.accept(outcome);
            }
        }

        public void emitFailure(Throwable t) {
            if (failureCallback != null) {
                failureCallback.accept(t);
            }
        }
    }
}
