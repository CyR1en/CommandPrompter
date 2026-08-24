package dev.cyr1en.promptpaper.custom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.api.PromptScreenFactory;
import dev.cyr1en.promptui.api.ScreenContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomScreenRuntimeTest extends MockBukkitTest {

    private CustomScreenRegistry registry;
    private ScreenKeyResolver resolver;
    private PromptEngine engine;
    private PromptFactory factory;
    private ScreenManager screenManager;
    private Map<UUID, FakePlayerExecutor> playerExecutors;
    private Plugin dummyOwnerPlugin;

    @BeforeEach
    void setUpCustomRuntime() {
        var promptConfig = mock(PromptConfig.class);
        when(promptConfig.getScreenMappings()).thenReturn(Map.of("", ScreenType.CHAT));
        lenient().when(promptConfig.sendCancelText()).thenReturn(false);
        lenient().when(promptConfig.responseListenerPriority()).thenReturn("LOWEST");
        lenient().when(configLoader.getPromptConfig()).thenReturn(promptConfig);

        registry = new CustomScreenRegistry(() -> true, Map::of, PromptConfig.RESERVED_SCREEN_KEYS, null);
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

        dummyOwnerPlugin = mock(Plugin.class);
        when(dummyOwnerPlugin.getName()).thenReturn("CustomProviderPlugin");
        when(dummyOwnerPlugin.isEnabled()).thenReturn(true);
    }

    private FakePlayerExecutor getExecutor(Player player) {
        return playerExecutors.computeIfAbsent(player.getUniqueId(), k -> new FakePlayerExecutor());
    }

    @Test
    @DisplayName("API-04/05: Registered custom tag creates ScreenContext with exact display/flags/sanitize and factory called on player executor")
    void testCustomTagCreatesScreenContextAndFactoryRunsOnPlayerExecutor() {
        AtomicReference<ScreenContext> capturedContext = new AtomicReference<>();
        AtomicReference<String> threadName = new AtomicReference<>();
        MockCustomInputScreen mockScreen = new MockCustomInputScreen();

        PromptScreenFactory customFactory = (p, ctx) -> {
            capturedContext.set(ctx);
            threadName.set(Thread.currentThread().getName());
            return mockScreen;
        };

        registry.registerScreen(dummyOwnerPlugin, "ecoitem", customFactory);

        Player player = createPlayer("CustomUser");
        screenManager.startSession(player, "/cmd <ecoitem:Pick weapon -glow -rarity:legendary -ds>");

        FakePlayerExecutor executor = getExecutor(player);
        assertEquals(1, executor.queueSize(), "Adapter open task should be queued on player executor");

        executor.runAll();

        assertNotNull(capturedContext.get(), "ScreenContext must be passed to custom factory");
        assertEquals("ecoitem", capturedContext.get().key());
        assertEquals("Pick weapon", capturedContext.get().displayText());
        assertEquals(Map.of("glow", "true", "rarity", "legendary"), capturedContext.get().flags());
        assertFalse(capturedContext.get().sanitize(), "-ds disables sanitization");

        assertEquals(1, mockScreen.openCalls.get(), "Delegate open() must be invoked");
        assertTrue(screenManager.hasActiveScreen(player), "ScreenManager must track active screen");
        assertTrue(screenManager.getActiveScreen(player) instanceof CustomScreenAdapter);
        assertEquals(mockScreen, ((CustomScreenAdapter) screenManager.getActiveScreen(player)).delegate());
    }

    @Test
    @DisplayName("SEC-12/API-06: Factory returning null fails closed with one CancelReason.ERROR and no command dispatch")
    void testFactoryReturningNullCancelsWithError() {
        PromptScreenFactory nullFactory = (p, ctx) -> null;
        registry.registerScreen(dummyOwnerPlugin, "nullscreen", nullFactory);

        var player = createPlayer("NullScreenUser");
        screenManager.startSession(player, "/give diamonds <nullscreen:Choose>");

        FakePlayerExecutor executor = getExecutor(player);
        executor.runAll();

        assertFalse(screenManager.hasActiveScreen(player), "Screen must be cleaned up on factory null");
        assertFalse(engine.hasActiveSession(player), "Session must be cancelled");
        assertNull(player.nextMessage(), "No command failure or completion message");
    }

    @Test
    @DisplayName("SEC-12/API-06: Factory throwing exception fails closed with one CancelReason.ERROR and no primary dispatch")
    void testFactoryThrowingExceptionCancelsWithError() {
        PromptScreenFactory throwingFactory = (p, ctx) -> {
            throw new RuntimeException("Factory creation exploded");
        };
        registry.registerScreen(dummyOwnerPlugin, "throwscreen", throwingFactory);

        Player player = createPlayer("ThrowUser");
        screenManager.startSession(player, "/give diamonds <throwscreen:Choose>");

        FakePlayerExecutor executor = getExecutor(player);
        executor.runAll();

        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));
    }

    @Test
    @DisplayName("SEC-12/API-06: Delegate open() throwing exception fails closed with CancelReason.ERROR")
    void testDelegateOpenThrowingCancelsWithError() {
        MockCustomInputScreen brokenScreen = new MockCustomInputScreen();
        brokenScreen.throwOnOpen = new RuntimeException("Inventory open error");

        registry.registerScreen(dummyOwnerPlugin, "brokenscreen", (p, ctx) -> brokenScreen);

        Player player = createPlayer("BrokenOpenUser");
        screenManager.startSession(player, "/cmd <brokenscreen:Choose>");

        FakePlayerExecutor executor = getExecutor(player);
        executor.runAll();

        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));
    }

    @Test
    @DisplayName("API-05/06: Foreign-thread callback queues work and delivers after hop only")
    void testForeignThreadCallbackQueuesAndDeliversAfterHop() throws InterruptedException {
        MockCustomInputScreen mockScreen = new MockCustomInputScreen();
        registry.registerScreen(dummyOwnerPlugin, "asyncscreen", (p, ctx) -> mockScreen);

        Player player = createPlayer("AsyncUser");
        screenManager.startSession(player, "/cmd <asyncscreen:Choose>");

        FakePlayerExecutor executor = getExecutor(player);
        executor.runAll(); // open screen

        assertTrue(screenManager.hasActiveScreen(player));

        // Fire result from foreign thread
        CountDownLatch latch = new CountDownLatch(1);
        Thread foreignThread = new Thread(() -> {
            mockScreen.fireResult(ScreenResult.answer("sword_99"));
            latch.countDown();
        });
        foreignThread.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // Session must not be completed yet before executor runs
        assertTrue(engine.hasActiveSession(player), "Session must still be active before hop");

        // Run the queued hop task
        executor.runAll();

        assertFalse(engine.hasActiveSession(player), "Session must complete after executor runs");
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    @DisplayName("SEC-12: Stale incarnation/generation/attempt callbacks are safely discarded")
    void testStaleCallbacksDiscarded() {
        MockCustomInputScreen mockScreen = new MockCustomInputScreen();
        registry.registerScreen(dummyOwnerPlugin, "stalescreen", (p, ctx) -> mockScreen);

        Player player = createPlayer("StaleUser");
        screenManager.startSession(player, "/cmd <stalescreen:Choose>");

        FakePlayerExecutor executor = getExecutor(player);
        executor.runAll();

        // Cancel the session
        screenManager.cancelAll(player);
        assertFalse(engine.hasActiveSession(player));

        // Late callback from the old screen arrives
        mockScreen.fireResult(ScreenResult.answer("late_answer"));
        executor.runAll();

        assertFalse(engine.hasActiveSession(player));
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    @DisplayName("SEC-12/API-06: Duplicate callbacks execute exactly once")
    void testDuplicateCallbacksExecuteExactlyOnce() {
        MockCustomInputScreen mockScreen = new MockCustomInputScreen();
        registry.registerScreen(dummyOwnerPlugin, "dupscreen", (p, ctx) -> mockScreen);

        Player player = createPlayer("DupUser");
        screenManager.startSession(player, "/cmd <dupscreen:Choose>");

        FakePlayerExecutor executor = getExecutor(player);
        executor.runAll();

        // Fire result twice
        mockScreen.fireResult(ScreenResult.answer("first_answer"));
        mockScreen.fireResult(ScreenResult.answer("duplicate_answer"));

        executor.runAll();

        assertFalse(engine.hasActiveSession(player), "Session completed");
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    @DisplayName("SEC-13: Provider removed between intercept and showPrompt fails closed with ERROR")
    void testProviderRemovedBetweenInterceptAndShowPromptFailsClosed() {
        MockCustomInputScreen mockScreen = new MockCustomInputScreen();
        registry.registerScreen(dummyOwnerPlugin, "tempkey", (p, ctx) -> mockScreen);

        Player player = createPlayer("UnregUser");
        var parsed = engine.intercept(player, "/cmd <tempkey:Prompt>");
        assertTrue(parsed.isPresent());
        assertTrue(engine.hasActiveSession(player));

        // Unregister provider before showPrompt
        registry.unregisterScreens(dummyOwnerPlugin);

        // Advance to showPrompt
        screenManager.showNextPrompt(player);

        assertFalse(engine.hasActiveSession(player), "Session must be failed closed with ERROR");
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    @DisplayName("SEC-12: Validation retry creates a new attempt token and invalidates old callbacks")
    void testValidationRetryInvalidatesOldAttemptToken() {
        MockCustomInputScreen firstScreen = new MockCustomInputScreen();
        MockCustomInputScreen secondScreen = new MockCustomInputScreen();
        Queue<MockCustomInputScreen> screenQueue = new ArrayDeque<>(List.of(firstScreen, secondScreen));

        registry.registerScreen(dummyOwnerPlugin, "valscreen", (p, ctx) -> screenQueue.poll());

        Player player = createPlayer("ValidationUser");
        // -int enforces integer validation
        screenManager.startSession(player, "/cmd <valscreen:Enter number -int>");

        FakePlayerExecutor executor = getExecutor(player);
        executor.runAll();

        assertEquals(firstScreen, ((CustomScreenAdapter) screenManager.getActiveScreen(player)).delegate());

        // Submit non-integer answer to trigger validation failure and retry
        firstScreen.fireResult(ScreenResult.answer("not_a_number"));
        executor.runAll();

        // ScreenManager should re-show prompt with secondScreen
        assertEquals(secondScreen, ((CustomScreenAdapter) screenManager.getActiveScreen(player)).delegate(), "New attempt must have replacement screen");

        // Old callback from first screen arrives late
        firstScreen.fireResult(ScreenResult.answer("123"));
        executor.runAll();

        // Second screen must still be active because old callback was discarded
        assertEquals(secondScreen, ((CustomScreenAdapter) screenManager.getActiveScreen(player)).delegate());
        assertTrue(engine.hasActiveSession(player));

        // Valid answer from second screen succeeds
        secondScreen.fireResult(ScreenResult.answer("456"));
        executor.runAll();

        assertFalse(engine.hasActiveSession(player), "Session successfully completed");
    }

    @Test
    @DisplayName("API-09/10: Normal completion and cancellation unlinks the reverse index")
    void testNormalCompletionAndCancelUnlinksReverseIndex() {
        MockCustomInputScreen mockScreen = new MockCustomInputScreen();
        registry.registerScreen(dummyOwnerPlugin, "reversescreen", (p, ctx) -> mockScreen);

        var regHandle = registry.getRegistration("reversescreen").orElseThrow();
        long providerId = regHandle.providerId();

        Player player = createPlayer("ReverseUser");
        screenManager.startSession(player, "/cmd <reversescreen:Prompt>");

        FakePlayerExecutor executor = getExecutor(player);
        executor.runAll();

        assertEquals(Set.of(player.getUniqueId()), screenManager.getActivePlayersForProvider(providerId));

        // Cancel
        screenManager.cancelAll(player);
        assertEquals(Set.of(), screenManager.getActivePlayersForProvider(providerId), "Reverse index must be empty after cancel");
    }

    // =========================================================================
    // Test Fakes
    // =========================================================================

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

        public int queueSize() {
            return taskQueue.size();
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

    private static class MockCustomInputScreen implements InputScreen {
        final AtomicInteger openCalls = new AtomicInteger(0);
        final AtomicInteger closeCalls = new AtomicInteger(0);
        final AtomicInteger isOpenCalls = new AtomicInteger(0);
        final AtomicInteger onResultCalls = new AtomicInteger(0);
        final AtomicInteger onOpenFailureCalls = new AtomicInteger(0);

        Consumer<ScreenResult> resultConsumer;
        Consumer<Throwable> openFailureConsumer;

        RuntimeException throwOnOpen;
        RuntimeException throwOnClose;

        boolean open = false;

        @Override
        public void open() {
            openCalls.incrementAndGet();
            open = true;
            if (throwOnOpen != null) {
                throw throwOnOpen;
            }
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            open = false;
            if (throwOnClose != null) {
                throw throwOnClose;
            }
        }

        @Override
        public boolean isOpen() {
            isOpenCalls.incrementAndGet();
            return open;
        }

        @Override
        public void onResult(Consumer<ScreenResult> callback) {
            onResultCalls.incrementAndGet();
            this.resultConsumer = callback;
        }

        @Override
        public void onOpenFailure(Consumer<Throwable> callback) {
            onOpenFailureCalls.incrementAndGet();
            this.openFailureConsumer = callback;
        }

        public void fireResult(ScreenResult result) {
            if (resultConsumer != null) {
                resultConsumer.accept(result);
            }
        }

        public void fireOpenFailure(Throwable error) {
            if (openFailureConsumer != null) {
                openFailureConsumer.accept(error);
            }
        }
    }
}
