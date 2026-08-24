package dev.cyr1en.promptpaper.custom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.api.PromptScreenFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderLifecycleCoordinatorTest extends MockBukkitTest {

    private CustomScreenRegistry registry;
    private ScreenKeyResolver resolver;
    private PromptEngine engine;
    private PromptFactory factory;
    private ScreenManager screenManager;
    private Map<UUID, FakePlayerExecutor> playerExecutors;
    private List<CustomScreenAuditEvent> auditEvents;
    private ProviderLifecycleCoordinator coordinator;

    private Plugin pluginA;
    private Plugin pluginB;

    @BeforeEach
    void setUpCoordinator() {
        var promptConfig = mock(PromptConfig.class);
        when(promptConfig.getScreenMappings()).thenReturn(Map.of("", ScreenType.CHAT));
        lenient().when(promptConfig.sendCancelText()).thenReturn(false);
        lenient().when(promptConfig.responseListenerPriority()).thenReturn("LOWEST");
        lenient().when(configLoader.getPromptConfig()).thenReturn(promptConfig);

        auditEvents = Collections.synchronizedList(new ArrayList<>());
        registry = new CustomScreenRegistry(() -> true, Map::of, PromptConfig.RESERVED_SCREEN_KEYS, auditEvents::add);
        resolver = new ScreenKeyResolver(registry, () -> Map.of("", ScreenType.CHAT));
        engine = new PromptEngine(plugin, scheduler, resolver);
        factory = new PromptFactory(plugin);

        playerExecutors = new java.util.concurrent.ConcurrentHashMap<>();
        screenManager = new ScreenManager(
                plugin,
                engine,
                factory,
                scheduler,
                null,
                player -> playerExecutors.computeIfAbsent(player.getUniqueId(), k -> new FakePlayerExecutor())
        );

        coordinator = new ProviderLifecycleCoordinator(
                registry,
                screenManager,
                auditEvents::add,
                player -> playerExecutors.computeIfAbsent(player.getUniqueId(), k -> new FakePlayerExecutor())
        );

        pluginA = mock(Plugin.class);
        when(pluginA.getName()).thenReturn("PluginA");
        when(pluginA.isEnabled()).thenReturn(true);

        pluginB = mock(Plugin.class);
        when(pluginB.getName()).thenReturn("PluginB");
        when(pluginB.isEnabled()).thenReturn(true);
    }

    private FakePlayerExecutor getExecutor(Player player) {
        return playerExecutors.computeIfAbsent(player.getUniqueId(), k -> new FakePlayerExecutor());
    }

    @Test
    @DisplayName("API-09/10: Provider teardown cancels only owned active sessions; other providers and built-ins remain unchanged")
    void testProviderTeardownCancelsOnlyOwnedActiveSessions() {
        StrictProviderStub stubA = new StrictProviderStub();
        StrictProviderStub stubB = new StrictProviderStub();

        registry.registerScreen(pluginA, "screen_a", (p, ctx) -> stubA);
        registry.registerScreen(pluginB, "screen_b", (p, ctx) -> stubB);

        Player playerA = createPlayer("PlayerA");
        Player playerB = createPlayer("PlayerB");
        Player playerBuiltIn = createPlayer("PlayerBuiltIn");

        screenManager.startSession(playerA, "/cmd <screen_a:Prompt A>");
        screenManager.startSession(playerB, "/cmd <screen_b:Prompt B>");
        screenManager.startSession(playerBuiltIn, "/cmd <Built-in Chat>");

        getExecutor(playerA).runAll();
        getExecutor(playerB).runAll();

        assertTrue(engine.hasActiveSession(playerA));
        assertTrue(engine.hasActiveSession(playerB));
        assertTrue(engine.hasActiveSession(playerBuiltIn));

        // Disable PluginA
        stubA.markTeardownStarted();
        coordinator.onProviderDisable(pluginA);

        // Run player executor tasks
        getExecutor(playerA).runAll();
        getExecutor(playerB).runAll();

        // PlayerA's session must be cancelled
        assertFalse(engine.hasActiveSession(playerA), "PlayerA session must be cancelled on PluginA disable");
        assertFalse(screenManager.hasActiveScreen(playerA));

        // PlayerB and Built-in player sessions must be completely unaffected
        assertTrue(engine.hasActiveSession(playerB), "PlayerB session must remain intact");
        assertTrue(screenManager.hasActiveScreen(playerB));
        assertTrue(engine.hasActiveSession(playerBuiltIn), "Built-in session must remain intact");
        assertTrue(screenManager.hasActiveScreen(playerBuiltIn));

        // Registry for PluginA must be detached
        assertFalse(registry.isRegistered("screen_a"));
        assertTrue(registry.isRegistered("screen_b"));
    }

    @Test
    @DisplayName("SEC-13/API-10: Zero provider methods called after teardown starts; platform close is invoked")
    void testZeroProviderMethodsCalledAfterTeardownStarts() {
        StrictProviderStub stub = new StrictProviderStub();
        registry.registerScreen(pluginA, "strictscreen", (p, ctx) -> stub);

        Player player = createPlayer("StrictUser");
        screenManager.startSession(player, "/cmd <strictscreen:Prompt>");

        FakePlayerExecutor executor = getExecutor(player);
        executor.runAll(); // open screen

        assertTrue(screenManager.hasActiveScreen(player));

        // Provider starts teardown
        stub.markTeardownStarted();
        coordinator.onProviderDisable(pluginA);

        executor.runAll(); // run teardown task on player executor

        // Assert session cancelled and active screen removed
        assertFalse(engine.hasActiveSession(player));
        assertFalse(screenManager.hasActiveScreen(player));

        // Attempting to invoke any delegate method would throw AssertionError from StrictProviderStub.
        // The fact that this test completes without exception proves ZERO provider methods were called!
        assertEquals(1, stub.openCalls.get(), "Only the initial open() was called before teardown");
        assertEquals(0, stub.closeCalls.get(), "Delegate close() must NOT be called after teardown starts");
    }

    @Test
    @DisplayName("SEC-12/API-09: Retired scheduler path performs map-only discard and no provider calls")
    void testRetiredSchedulerPathPerformsMapOnlyDiscard() {
        StrictProviderStub stub = new StrictProviderStub();
        registry.registerScreen(pluginA, "retirescreen", (p, ctx) -> stub);

        Player player = createPlayer("RetireUser");
        screenManager.startSession(player, "/cmd <retirescreen:Prompt>");

        FakePlayerExecutor executor = getExecutor(player);
        executor.runAll();

        assertTrue(screenManager.hasActiveScreen(player));

        stub.markTeardownStarted();

        // Trigger teardown but retire scheduler instead of executing
        coordinator.onProviderDisable(pluginA);
        executor.retireAll();

        assertFalse(engine.hasActiveSession(player), "Session must be discarded");
        assertFalse(screenManager.hasActiveScreen(player), "Active screen handle must be cleared");
        assertEquals(0, stub.closeCalls.get(), "No provider close() call on retirement");
    }

    @Test
    @DisplayName("API-09/10: Coordinator is idempotent and logs audit events with accurate counts")
    void testCoordinatorIdempotencyAndAudit() {
        StrictProviderStub stub = new StrictProviderStub();
        registry.registerScreen(pluginA, "auditscreen", (p, ctx) -> stub);

        Player player = createPlayer("AuditUser");
        screenManager.startSession(player, "/cmd <auditscreen:Prompt>");
        getExecutor(player).runAll();

        // First disable
        stub.markTeardownStarted();
        coordinator.onProviderDisable(pluginA);
        getExecutor(player).runAll();

        assertFalse(engine.hasActiveSession(player));

        int auditCountFirst = auditEvents.size();
        assertTrue(auditCountFirst > 0, "Audit event must be emitted");

        // Second disable (idempotent no-op)
        coordinator.onProviderDisable(pluginA);
        assertEquals(auditCountFirst, auditEvents.size(), "No new audit events on duplicate disable");
    }

    @Test
    @DisplayName("SEC-12: Player quit / discard cleans state and reverse index to baseline")
    void testPlayerQuitCleansStateToBaseline() {
        StrictProviderStub stub = new StrictProviderStub();
        registry.registerScreen(pluginA, "quitkey", (p, ctx) -> stub);

        var regHandle = registry.getRegistration("quitkey").orElseThrow();
        long providerId = regHandle.providerId();

        Player player = createPlayer("QuitUser");
        screenManager.startSession(player, "/cmd <quitkey:Prompt>");
        getExecutor(player).runAll();

        assertEquals(Set.of(player.getUniqueId()), screenManager.getActivePlayersForProvider(providerId));

        // Player disconnects / discardState
        screenManager.discardState(player.getUniqueId());

        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));
        assertEquals(Set.of(), screenManager.getActivePlayersForProvider(providerId), "Reverse index must be baseline");
    }

    @Test
    @DisplayName("Player-affine checks like isOnline/identity happen only inside PlayerExecutor")
    void testPlayerAffineChecksInsidePlayerExecutorOnly() {
        StrictProviderStub stub = new StrictProviderStub();
        registry.registerScreen(pluginA, "affine_key", (p, ctx) -> stub);

        Player player = createPlayer("AffineUser");
        screenManager.startSession(player, "/cmd <affine_key:Prompt>");
        getExecutor(player).runAll();

        assertTrue(screenManager.hasActiveScreen(player));
        assertTrue(engine.hasActiveSession(player));

        stub.markTeardownStarted();

        AtomicBoolean taskExecuted = new AtomicBoolean(false);

        playerExecutors.put(player.getUniqueId(), new FakePlayerExecutor() {
            @Override
            public void execute(Runnable task, Runnable retired) {
                super.execute(() -> {
                    taskExecuted.set(true);
                    task.run();
                }, retired);
            }
        });

        // Initiate teardown
        coordinator.onProviderDisable(pluginA);

        // Before player executor runs, task has not executed and session is still active
        assertFalse(taskExecuted.get());
        assertTrue(engine.hasActiveSession(player), "Session must still be active before executor runs");

        // Run the scheduled executor task
        getExecutor(player).runAll();

        assertTrue(taskExecuted.get());
        assertFalse(engine.hasActiveSession(player), "Session must be cancelled after executor runs");
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    @DisplayName("Player offline when scheduler task executes performs map-only discard")
    void testPlayerOfflineOnSchedulerDoesMapOnlyDiscard() {
        StrictProviderStub stub = new StrictProviderStub();
        registry.registerScreen(pluginA, "offline_key", (p, ctx) -> stub);

        var player = createPlayer("OfflineUser");
        screenManager.startSession(player, "/cmd <offline_key:Prompt>");
        getExecutor(player).runAll();

        assertTrue(screenManager.hasActiveScreen(player));

        stub.markTeardownStarted();

        // Disable provider
        coordinator.onProviderDisable(pluginA);

        // Simulate player went offline before executor ran
        player.disconnect();
        assertFalse(player.isOnline());

        // Execute task
        getExecutor(player).runAll();

        assertFalse(engine.hasActiveSession(player), "Session must be discarded");
        assertFalse(screenManager.hasActiveScreen(player), "Screen must be unlinked");
        assertEquals(0, stub.closeCalls.get(), "Provider close must NOT be called");
    }

    @Test
    @DisplayName("Executor throwing exception performs map-only discard")
    void testPlayerExecutorThrowingDoesMapOnlyDiscard() {
        StrictProviderStub stub = new StrictProviderStub();
        registry.registerScreen(pluginA, "throw_key", (p, ctx) -> stub);

        Player player = createPlayer("ThrowExecUser");
        screenManager.startSession(player, "/cmd <throw_key:Prompt>");
        getExecutor(player).runAll();

        assertTrue(screenManager.hasActiveScreen(player));

        // Reconfigure executor to throw on execute
        playerExecutors.put(player.getUniqueId(), new FakePlayerExecutor() {
            @Override
            public void execute(Runnable task, Runnable retired) {
                throw new RuntimeException("Scheduler rejected execution");
            }
        });

        stub.markTeardownStarted();

        assertDoesNotThrow(() -> coordinator.onProviderDisable(pluginA));

        assertFalse(engine.hasActiveSession(player), "Session must be discarded");
        assertFalse(screenManager.hasActiveScreen(player), "Screen must be cleared");
        assertEquals(0, stub.closeCalls.get());
    }

    // =========================================================================
    // Test Provider Stub
    // =========================================================================

    /**
     * Strict provider stub that asserts NO methods are invoked after teardown has started.
     */
    private static class StrictProviderStub implements InputScreen {
        private final AtomicBoolean teardownStarted = new AtomicBoolean(false);
        final AtomicInteger openCalls = new AtomicInteger(0);
        final AtomicInteger closeCalls = new AtomicInteger(0);
        final AtomicInteger isOpenCalls = new AtomicInteger(0);

        Consumer<ScreenResult> resultConsumer;
        Consumer<Throwable> openFailureConsumer;

        void markTeardownStarted() {
            teardownStarted.set(true);
        }

        private void checkNotTornDown(String method) {
            if (teardownStarted.get()) {
                throw new AssertionError("Provider method '" + method + "' was called AFTER teardown started!");
            }
        }

        @Override
        public void open() {
            checkNotTornDown("open");
            openCalls.incrementAndGet();
        }

        @Override
        public void close() {
            checkNotTornDown("close");
            closeCalls.incrementAndGet();
        }

        @Override
        public boolean isOpen() {
            checkNotTornDown("isOpen");
            isOpenCalls.incrementAndGet();
            return true;
        }

        @Override
        public void onResult(Consumer<ScreenResult> callback) {
            checkNotTornDown("onResult");
            this.resultConsumer = callback;
        }

        @Override
        public void onOpenFailure(Consumer<Throwable> callback) {
            checkNotTornDown("onOpenFailure");
            this.openFailureConsumer = callback;
        }
    }

    private static class FakePlayerExecutor implements PlayerExecutor {
        private final Queue<Runnable> taskQueue = new ArrayDeque<>();
        private final Queue<Runnable> retiredQueue = new ArrayDeque<>();

        @Override
        public void execute(Runnable task, Runnable retired) {
            taskQueue.add(task);
            if (retired != null) {
                retiredQueue.add(retired);
            }
        }

        public void runAll() {
            while (!taskQueue.isEmpty()) {
                taskQueue.poll().run();
            }
        }

        public void retireAll() {
            taskQueue.clear();
            while (!retiredQueue.isEmpty()) {
                retiredQueue.poll().run();
            }
        }
    }
}
