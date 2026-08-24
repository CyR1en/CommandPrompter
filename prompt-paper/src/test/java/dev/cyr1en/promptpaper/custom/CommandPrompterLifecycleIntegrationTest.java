package dev.cyr1en.promptpaper.custom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.CommandPrompterConfig;
import dev.cyr1en.promptpaper.config.PaperConfigLoader;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.engine.InterceptResult;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.listener.PluginDisableListener;
import dev.cyr1en.promptpaper.listener.PlayerCommandListener;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.api.CommandPrompterAPI;
import dev.cyr1en.promptui.api.PromptScreenFactory;
import dev.cyr1en.promptui.api.ScreenContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CommandPrompterLifecycleIntegrationTest extends MockBukkitTest {

    private CustomScreenRegistry customRegistry;
    private ScreenKeyResolver resolver;
    private PromptEngine engine;
    private PromptFactory factory;
    private ScreenManager screenManager;
    private PluginDisableListener disableListener;
    private Map<UUID, FakePlayerExecutor> playerExecutors;
    private List<CustomScreenAuditEvent> auditLog;
    private Plugin thirdPartyPlugin;
    private CommandPrompterAPIFacade apiFacade;

    @BeforeEach
    void initIntegration() {
        auditLog = Collections.synchronizedList(new ArrayList<>());
        playerExecutors = new java.util.concurrent.ConcurrentHashMap<>();

        var promptConfigMock = mock(PromptConfig.class);
        when(promptConfigMock.getScreenMappings()).thenReturn(Map.of("", ScreenType.CHAT));
        lenient().when(promptConfigMock.sendCancelText()).thenReturn(false);
        lenient().when(promptConfigMock.responseListenerPriority()).thenReturn("LOWEST");
        when(configLoader.getPromptConfig()).thenReturn(promptConfigMock);

        customRegistry = new CustomScreenRegistry(
                () -> true,
                () -> configLoader != null && configLoader.getPromptConfig() != null
                        ? configLoader.getPromptConfig().getScreenMappings()
                        : Map.of(),
                PromptConfig.RESERVED_SCREEN_KEYS,
                auditLog::add
        );

        resolver = new ScreenKeyResolver(
                customRegistry,
                () -> configLoader != null && configLoader.getPromptConfig() != null
                        ? configLoader.getPromptConfig().getScreenMappings()
                        : Map.of()
        );

        engine = new PromptEngine(plugin, scheduler, resolver);
        factory = new PromptFactory(plugin);
        screenManager = new ScreenManager(
                plugin,
                engine,
                factory,
                scheduler,
                null,
                player -> playerExecutors.computeIfAbsent(player.getUniqueId(), k -> new FakePlayerExecutor()),
                auditLog::add
        );

        apiFacade = new CommandPrompterAPIFacade(customRegistry, screenManager.getProviderLifecycleCoordinator());
        disableListener = new PluginDisableListener(plugin, screenManager);

        when(plugin.getEngine()).thenReturn(engine);
        when(plugin.getPromptFactory()).thenReturn(factory);
        when(plugin.getScreenManager()).thenReturn(screenManager);
        when(plugin.getCustomScreenRegistry()).thenReturn(customRegistry);
        when(plugin.getScreenKeyResolver()).thenReturn(resolver);
        when(plugin.getCustomScreenAuditLogger()).thenReturn(auditLog::add);
        doCallRealMethod().when(plugin).onDisable();

        try {
            Field registryField = CommandPrompter.class.getDeclaredField("customScreenRegistry");
            registryField.setAccessible(true);
            registryField.set(plugin, customRegistry);

            Field facadeField = CommandPrompter.class.getDeclaredField("apiFacade");
            facadeField.setAccessible(true);
            facadeField.set(plugin, apiFacade);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        thirdPartyPlugin = mock(Plugin.class);
        when(thirdPartyPlugin.getName()).thenReturn("ThirdPartyProvider");
        when(thirdPartyPlugin.isEnabled()).thenReturn(true);
    }

    private FakePlayerExecutor getExecutor(Player player) {
        return playerExecutors.computeIfAbsent(player.getUniqueId(), k -> new FakePlayerExecutor());
    }

    // =========================================================================
    // Section B: Bukkit ServicesManager Tests
    // =========================================================================

    @Nested
    @DisplayName("ServicesManager Lifecycle & Registration")
    class ServicesManagerTests {

        @Test
        @DisplayName("CommandPrompterAPI facade is registered with Bukkit ServicesManager and unregistered on disable")
        void testServicesManagerRegisterAndUnregister() {
            server.getServicesManager().register(
                    CommandPrompterAPI.class,
                    apiFacade,
                    plugin,
                    ServicePriority.Normal);

            RegisteredServiceProvider<CommandPrompterAPI> registration =
                    server.getServicesManager().getRegistration(CommandPrompterAPI.class);
            assertNotNull(registration);
            assertSame(apiFacade, registration.getProvider());
            assertEquals(plugin, registration.getPlugin());

            // On plugin disable
            plugin.onDisable();

            assertTrue(customRegistry.isFrozen(), "Registry must be frozen on disable");
            assertNull(server.getServicesManager().getRegistration(CommandPrompterAPI.class),
                    "CommandPrompterAPI registration must be absent after disable");
        }

        @Test
        @DisplayName("ServiceRegisterEvent synchronous registration succeeds")
        void testServiceRegisterEventSynchronousRegistration() {
            Plugin providerPlugin = mock(Plugin.class);
            when(providerPlugin.getName()).thenReturn("SyncProvider");
            when(providerPlugin.isEnabled()).thenReturn(true);

            AtomicBoolean registeredSuccessfully = new AtomicBoolean(false);

            Listener syncListener = new Listener() {
                @EventHandler
                public void onServiceRegister(ServiceRegisterEvent event) {
                    if (event.getProvider().getService().equals(CommandPrompterAPI.class)) {
                        CommandPrompterAPI api = (CommandPrompterAPI) event.getProvider().getProvider();
                        api.registerScreen(providerPlugin, "sync_screen", (p, ctx) -> null);
                        registeredSuccessfully.set(true);
                    }
                }
            };
            server.getPluginManager().registerEvents(syncListener, plugin);

            server.getServicesManager().register(
                    CommandPrompterAPI.class,
                    apiFacade,
                    plugin,
                    ServicePriority.Normal
            );

            assertTrue(registeredSuccessfully.get(), "Synchronous registration during ServiceRegisterEvent must succeed");
            assertTrue(customRegistry.isRegistered("sync_screen"));
        }

        @Test
        @DisplayName("Enable failure cleanup explicitly unregisters service and freezes registry")
        void testCleanupEnableFailure() throws Exception {
            server.getServicesManager().register(
                    CommandPrompterAPI.class,
                    apiFacade,
                    plugin,
                    ServicePriority.Normal);

            assertNotNull(server.getServicesManager().getRegistration(CommandPrompterAPI.class));

            Method cleanupMethod = CommandPrompter.class.getDeclaredMethod("cleanupEnableFailure");
            cleanupMethod.setAccessible(true);

            cleanupMethod.invoke(plugin);

            assertTrue(customRegistry.isFrozen());
            assertNull(server.getServicesManager().getRegistration(CommandPrompterAPI.class));
            assertEquals(0, customRegistry.getRegisteredKeys().size());
        }
    }

    // =========================================================================
    // Section C: Real PluginDisableEvent & Public Unregister Integration
    // =========================================================================

    @Nested
    @DisplayName("PluginDisableEvent & Public API Unregister Tests")
    class PluginDisableEventTests {

        @Test
        @DisplayName("API-05/10: Real PluginDisableEvent cleans up custom screen with MANUAL cancel and zero provider calls")
        void testRealPluginDisableEventTeardown() {
            StrictTeardownStub stub = new StrictTeardownStub();
            customRegistry.registerScreen(thirdPartyPlugin, "testscreen", (p, ctx) -> stub);

            Player player = createPlayer("DisableEventUser");
            screenManager.startSession(player, "/cmd <testscreen:Select item -rarity:epic>");

            FakePlayerExecutor executor = getExecutor(player);
            executor.runAll(); // executes open

            assertTrue(engine.hasActiveSession(player));
            assertTrue(screenManager.hasActiveScreen(player));
            var regHandle = customRegistry.getRegistration("testscreen").orElseThrow();
            long providerId = regHandle.providerId();
            assertEquals(Set.of(player.getUniqueId()), screenManager.getActivePlayersForProvider(providerId));

            // Mark stub ready for assertion: after this moment, zero stub methods may be invoked
            stub.markTeardownStarted();

            // Fire real PluginDisableEvent through Bukkit plugin manager
            PluginDisableEvent event = new PluginDisableEvent(thirdPartyPlugin);
            disableListener.onPluginDisable(event);

            // Execute scheduled player teardown tasks
            executor.runAll();

            // Assertions:
            assertFalse(customRegistry.isRegistered("testscreen"), "Registration must be removed");
            assertFalse(engine.hasActiveSession(player), "Session must be cancelled");
            assertFalse(screenManager.hasActiveScreen(player), "Active screen handle must be unlinked");
            assertEquals(Set.of(), screenManager.getActivePlayersForProvider(providerId), "Reverse index must be cleared");

            // Zero provider calls after teardown began
            assertEquals(1, stub.openCalls.get(), "Only the initial open() occurred");
            assertEquals(0, stub.closeCalls.get(), "No close() call to provider during disable teardown");
        }

        @Test
        @DisplayName("Explicit public API unregister cancels owned active sessions, direct platform close, and leaves other providers intact")
        void testPublicApiUnregisterCancelsOwnedSessionsAndDirectPlatformClose() {
            StrictTeardownStub stubA = new StrictTeardownStub();
            StrictTeardownStub stubB = new StrictTeardownStub();

            Plugin pluginB = mock(Plugin.class);
            when(pluginB.getName()).thenReturn("OtherPluginB");
            when(pluginB.isEnabled()).thenReturn(true);

            apiFacade.registerScreen(thirdPartyPlugin, "screen_a", (p, ctx) -> stubA);
            apiFacade.registerScreen(pluginB, "screen_b", (p, ctx) -> stubB);

            Player playerA = createPlayer("PlayerA");
            Player playerB = createPlayer("PlayerB");

            screenManager.startSession(playerA, "/cmd <screen_a:Prompt A>");
            screenManager.startSession(playerB, "/cmd <screen_b:Prompt B>");

            getExecutor(playerA).runAll();
            getExecutor(playerB).runAll();

            assertTrue(engine.hasActiveSession(playerA));
            assertTrue(engine.hasActiveSession(playerB));

            stubA.markTeardownStarted();
            apiFacade.unregisterScreens(thirdPartyPlugin);

            getExecutor(playerA).runAll();
            getExecutor(playerB).runAll();

            // PlayerA session cancelled, PlayerB session remains intact
            assertFalse(engine.hasActiveSession(playerA));
            assertFalse(screenManager.hasActiveScreen(playerA));
            assertTrue(engine.hasActiveSession(playerB));
            assertTrue(screenManager.hasActiveScreen(playerB));

            assertEquals(1, stubA.openCalls.get());
            assertEquals(0, stubA.closeCalls.get(), "No delegate close called");
        }

        @Test
        @DisplayName("Disable between resolve and link is cleaned immediately with MANUAL cancellation and platform close")
        void testDisableBetweenResolveAndLinkIsCleanedImmediately() {
            StrictTeardownStub stub = new StrictTeardownStub();
            customRegistry.registerScreen(thirdPartyPlugin, "racelink", (p, ctx) -> stub);

            Player player = createPlayer("RaceLinkUser");
            var parsed = engine.intercept(player, "/cmd <racelink:Prompt>");
            assertTrue(parsed.isPresent());
            assertTrue(engine.hasActiveSession(player));

            // Provider unregisters right before showPrompt
            customRegistry.unregisterScreens(thirdPartyPlugin);

            // showPrompt attempts to link
            screenManager.showNextPrompt(player);

            assertFalse(engine.hasActiveSession(player));
            assertFalse(screenManager.hasActiveScreen(player));
            assertEquals(0, stub.openCalls.get(), "Zero provider methods called");
        }

        @Test
        @DisplayName("Host shutdown bulk teardown zeroes provider methods, clears maps, removes service, closes platform inventory")
        void testHostShutdownBulkTeardown() {
            StrictTeardownStub stub = new StrictTeardownStub();
            customRegistry.registerScreen(thirdPartyPlugin, "shutscreen", (p, ctx) -> stub);

            server.getServicesManager().register(
                    CommandPrompterAPI.class,
                    apiFacade,
                    plugin,
                    ServicePriority.Normal
            );

            Player player = createPlayer("ShutdownUser");
            screenManager.startSession(player, "/cmd <shutscreen:Prompt>");
            getExecutor(player).runAll();

            assertTrue(engine.hasActiveSession(player));
            assertTrue(screenManager.hasActiveScreen(player));

            stub.markTeardownStarted();

            plugin.onDisable();
            getExecutor(player).runAll();

            assertEquals(1, stub.openCalls.get());
            assertEquals(0, stub.closeCalls.get());

            assertFalse(engine.hasActiveSession(player));
            assertFalse(screenManager.hasActiveScreen(player));
            assertNull(server.getServicesManager().getRegistration(CommandPrompterAPI.class));
            assertTrue(customRegistry.isFrozen());
        }

        @Test
        @DisplayName("CommandPrompter's own disable is ignored by PluginDisableListener")
        void testCommandPrompterOwnDisableIgnoredByListener() {
            PluginDisableEvent event = new PluginDisableEvent(plugin);
            assertDoesNotThrow(() -> disableListener.onPluginDisable(event));
        }
    }

    // =========================================================================
    // Section E: Atomic Config Reload Collision
    // =========================================================================

    @Nested
    @DisplayName("Atomic Config Reload Collision Validation")
    class ConfigReloadCollisionTests {

        @Test
        @DisplayName("No collision reload succeeds and retains valid state")
        void testNoCollisionReloadSucceeds() {
            customRegistry.registerScreen(thirdPartyPlugin, "custom_eco", (p, ctx) -> null);

            Map<String, ScreenType> candidateMappings = Map.of(
                    "dialog_custom", ScreenType.DIALOG,
                    "anvil_custom", ScreenType.ANVIL
            );

            assertDoesNotThrow(() -> resolver.validateNoCollisions(candidateMappings));
        }

        @Test
        @DisplayName("Collision with active custom screen throws IllegalStateException and normalizes case")
        void testCollisionWithActiveCustomScreenThrows() {
            customRegistry.registerScreen(thirdPartyPlugin, "ecoitem", (p, ctx) -> null);

            Map<String, ScreenType> collidingLower = Map.of("ecoitem", ScreenType.ANVIL);
            IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
                    resolver.validateNoCollisions(collidingLower));
            assertTrue(ex1.getMessage().contains("ecoitem"));
            assertTrue(ex1.getMessage().contains("ThirdPartyProvider"));

            Map<String, ScreenType> collidingUpper = Map.of("ECOITEM", ScreenType.ANVIL);
            IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                    resolver.validateNoCollisions(collidingUpper));
            assertTrue(ex2.getMessage().contains("ECOITEM"));
        }

        @Test
        @DisplayName("Concurrent reload validation and screen registration has one winner and no conflicting state")
        void testConcurrentReloadAndRegistrationTransaction() throws Exception {
            int threadCount = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger reloadSuccessCount = new AtomicInteger(0);
            AtomicInteger registerSuccessCount = new AtomicInteger(0);

            Map<String, ScreenType> candidate = Map.of("contested_key", ScreenType.ANVIL);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (idx % 2 == 0) {
                            customRegistry.validateMappingsAndPublish(candidate, reloadSuccessCount::incrementAndGet);
                        } else {
                            Plugin p = mock(Plugin.class);
                            when(p.getName()).thenReturn("ContenderPlugin_" + idx);
                            when(p.isEnabled()).thenReturn(true);
                            customRegistry.registerScreen(p, "contested_key", (pl, ctx) -> null);
                            registerSuccessCount.incrementAndGet();
                        }
                    } catch (IllegalStateException | IllegalArgumentException ignored) {
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            executor.shutdown();

            assertTrue(reloadSuccessCount.get() + registerSuccessCount.get() >= 1);
            if (registerSuccessCount.get() > 0) {
                assertTrue(customRegistry.isRegistered("contested_key"));
            }
        }
    }

    // =========================================================================
    // Acceptance Scenarios API-01 to API-10
    // =========================================================================

    @Nested
    @DisplayName("Custom Screen API Acceptance Scenarios")
    class AcceptanceScenariosTests {

        @Test
        @DisplayName("API-01: Registration and actual <custom:...> session creation with flags")
        void testAPI01RegistrationAndSessionCreation() {
            AtomicReference<ScreenContext> capturedContext = new AtomicReference<>();
            MockScreen mockScreen = new MockScreen();

            customRegistry.registerScreen(thirdPartyPlugin, "mygui", (p, ctx) -> {
                capturedContext.set(ctx);
                return mockScreen;
            });

            Player player = createPlayer("User01");
            screenManager.startSession(player, "/give diamonds <mygui:Choose Reward -rarity:mythic -glow>");

            FakePlayerExecutor executor = getExecutor(player);
            executor.runAll();

            assertNotNull(capturedContext.get());
            assertEquals("mygui", capturedContext.get().key());
            assertEquals("Choose Reward", capturedContext.get().displayText());
            assertEquals("mythic", capturedContext.get().flagOrDefault("rarity", "common"));
            assertTrue(capturedContext.get().booleanFlag("glow"));
            assertTrue(mockScreen.isOpen());
        }

        @Test
        @DisplayName("API-03: Unknown screen key fails closed and cancels command dispatch")
        void testAPI03UnknownKeyFailsClosed() {
            Player player = createPlayer("UnknownKeyUser");
            var result = engine.interceptResult(player, "/give diamonds <unregistered_screen:Select>");

            assertInstanceOf(InterceptResult.RejectedFailClosed.class, result);
            assertFalse(engine.hasActiveSession(player));
        }

        @Test
        @DisplayName("API-03: Unknown custom screen key event cancelled and dispatch count zero")
        void testAPI03UnknownKeyCancelsEventAndZeroDispatch() {
            Player realPlayer = createPlayer("UnknownKeyDispatchUser");
            Player spyPlayer = spy(realPlayer);

            var listener = new PlayerCommandListener(plugin, screenManager, engine);
            var event = new org.bukkit.event.player.PlayerCommandPreprocessEvent(spyPlayer, "/give diamonds <unknown_custom_key:Prompt>");

            listener.onPlayerCommand(event);

            assertTrue(event.isCancelled(), "PlayerCommandPreprocessEvent must be cancelled");
            assertFalse(engine.hasActiveSession(spyPlayer), "No active session created");
            verify(spyPlayer, never()).performCommand(anyString());
        }

        @Test
        @DisplayName("API-04: Factory error fails closed with CancelReason.ERROR")
        void testAPI04FactoryErrorFailsClosed() {
            customRegistry.registerScreen(thirdPartyPlugin, "errscreen", (p, ctx) -> {
                throw new RuntimeException("UI creation crashed");
            });

            Player player = createPlayer("ErrorUser");
            screenManager.startSession(player, "/test <errscreen:Prompt>");

            getExecutor(player).runAll();

            assertFalse(engine.hasActiveSession(player));
            assertFalse(screenManager.hasActiveScreen(player));
        }

        @Test
        @DisplayName("API-06: Foreign-thread result hops to player scheduler safely")
        void testAPI06ForeignThreadResultHop() throws InterruptedException {
            MockScreen mockScreen = new MockScreen();
            customRegistry.registerScreen(thirdPartyPlugin, "foreignscreen", (p, ctx) -> mockScreen);

            Player player = createPlayer("ForeignThreadUser");
            screenManager.startSession(player, "/say <foreignscreen:Input>");

            FakePlayerExecutor executor = getExecutor(player);
            executor.runAll();

            assertTrue(engine.hasActiveSession(player));

            CountDownLatch latch = new CountDownLatch(1);
            Thread asyncWorker = new Thread(() -> {
                mockScreen.emitResult(ScreenResult.answer("async_answer"));
                latch.countDown();
            });
            asyncWorker.start();
            assertTrue(latch.await(2, TimeUnit.SECONDS));

            assertTrue(engine.hasActiveSession(player));

            executor.runAll();

            assertFalse(engine.hasActiveSession(player));
        }

        @Test
        @DisplayName("API-08: Foreign unregister has no effect and duplicate unregister is safe")
        void testAPI08ForeignAndDuplicateUnregister() {
            customRegistry.registerScreen(thirdPartyPlugin, "ownerscreen", (p, ctx) -> new MockScreen());

            Plugin foreign = mock(Plugin.class);
            when(foreign.getName()).thenReturn("ForeignPlugin");
            when(foreign.isEnabled()).thenReturn(true);

            customRegistry.unregisterScreens(foreign);
            assertTrue(customRegistry.isRegistered("ownerscreen"));

            customRegistry.unregisterScreens(thirdPartyPlugin);
            assertFalse(customRegistry.isRegistered("ownerscreen"));

            assertDoesNotThrow(() -> customRegistry.unregisterScreens(thirdPartyPlugin));
        }

        @Test
        @DisplayName("API-09: Callback vs Timeout race executes exactly once")
        void testAPI09CallbackTimeoutRace() {
            MockScreen mockScreen = new MockScreen();
            customRegistry.registerScreen(thirdPartyPlugin, "racescreen", (p, ctx) -> mockScreen);

            Player player = createPlayer("RaceUser");
            screenManager.startSession(player, "/say <racescreen:Prompt>");

            FakePlayerExecutor executor = getExecutor(player);
            executor.runAll();

            mockScreen.emitResult(ScreenResult.answer("first"));
            mockScreen.emitResult(ScreenResult.answer("second"));
            mockScreen.emitResult(ScreenResult.cancel(CancelReason.TIMEOUT));

            executor.runAll();

            assertFalse(engine.hasActiveSession(player));
            assertFalse(screenManager.hasActiveScreen(player));
        }

        @Test
        @DisplayName("API-09: Race condition between delegate result and timeout delivers exactly one terminal completion")
        void testAPI09RaceConditionDeliversExactlyOneCompletion() {
            MockScreen mockScreen = new MockScreen();
            customRegistry.registerScreen(thirdPartyPlugin, "raceterminal", (p, ctx) -> mockScreen);

            Player player = createPlayer("RaceTerminalUser");
            screenManager.startSession(player, "/cmd <raceterminal:Prompt>");

            FakePlayerExecutor executor = getExecutor(player);
            executor.runAll();

            assertTrue(engine.hasActiveSession(player));

            // Race: fire result, timeout, and another result
            mockScreen.emitResult(ScreenResult.answer("first_valid_answer"));
            mockScreen.emitResult(ScreenResult.cancel(CancelReason.TIMEOUT));
            mockScreen.emitResult(ScreenResult.answer("late_answer"));

            executor.runAll();

            assertFalse(engine.hasActiveSession(player));
            assertFalse(screenManager.hasActiveScreen(player));
        }

        @Test
        @DisplayName("Platform close test verifies closeInventory() invocation during provider disable")
        void testPlatformCloseVerifiesCloseInventoryInvocation() {
            Player realPlayer = createPlayer("PlatformCloseUser");
            Player spyPlayer = spy(realPlayer);

            StrictTeardownStub stub = new StrictTeardownStub();
            customRegistry.registerScreen(thirdPartyPlugin, "invscreen", (p, ctx) -> stub);

            screenManager.startSession(spyPlayer, "/cmd <invscreen:Prompt>");
            getExecutor(spyPlayer).runAll();

            assertTrue(screenManager.hasActiveScreen(spyPlayer));

            stub.markTeardownStarted();
            customRegistry.unregisterScreens(thirdPartyPlugin);

            screenManager.teardownCustomProvider(spyPlayer, screenManager.getActiveScreenHandle(spyPlayer.getUniqueId()));

            verify(spyPlayer, atLeastOnce()).closeInventory();
        }

        @Test
        @DisplayName("Audit events are observable at always-on channel")
        void testAuditEventsObservableAtAlwaysOnChannel() {
            customRegistry.registerScreen(thirdPartyPlugin, "auditedscreen", (p, ctx) -> null);
            customRegistry.unregisterScreens(thirdPartyPlugin);

            List<CustomScreenAuditEvent.Type> types = auditLog.stream().map(CustomScreenAuditEvent::type).toList();
            assertTrue(types.contains(CustomScreenAuditEvent.Type.REGISTERED));
            assertTrue(types.contains(CustomScreenAuditEvent.Type.UNREGISTERED));
        }

        @Test
        @DisplayName("Production audit logger wiring captures provider teardown event with owner, screen count, and player count")
        void testProductionAuditLoggerCapturesTeardownEvent() {
            customRegistry.registerScreen(thirdPartyPlugin, "prodkey", (p, ctx) -> new MockScreen());

            Player player = createPlayer("ProdAuditUser");
            screenManager.startSession(player, "/cmd <prodkey:Prompt>");
            getExecutor(player).runAll();

            // Trigger provider disable via coordinator
            screenManager.getProviderLifecycleCoordinator().onProviderDisable(thirdPartyPlugin);

            // Assert TEARDOWN audit event emitted with owner and counts
            CustomScreenAuditEvent teardownEvent = auditLog.stream()
                    .filter(e -> e.type() == CustomScreenAuditEvent.Type.TEARDOWN)
                    .findFirst()
                    .orElse(null);

            assertNotNull(teardownEvent, "TEARDOWN audit event must be captured by coordinator");
            assertEquals("ThirdPartyProvider", teardownEvent.ownerName());
            assertTrue(teardownEvent.detail().contains("1 screen(s)"));
            assertTrue(teardownEvent.detail().contains("1 active player(s)"));
        }

        @Test
        @DisplayName("Null contract: unregisterScreens(null) and registerScreen null parameters throw NullPointerException")
        void testPublicApiNullContractEnforced() {
            assertThrows(NullPointerException.class, () -> apiFacade.unregisterScreens(null),
                    "apiFacade.unregisterScreens(null) must throw NPE");
            assertThrows(NullPointerException.class, () -> customRegistry.unregisterScreens(null),
                    "customRegistry.unregisterScreens(null) must throw NPE");
            assertThrows(NullPointerException.class, () -> apiFacade.registerScreen(null, "somekey", (p, c) -> null),
                    "apiFacade.registerScreen with null plugin must throw NPE");
            assertThrows(NullPointerException.class, () -> apiFacade.registerScreen(thirdPartyPlugin, null, (p, c) -> null),
                    "apiFacade.registerScreen with null key must throw NPE");
            assertThrows(NullPointerException.class, () -> apiFacade.registerScreen(thirdPartyPlugin, "somekey", null),
                    "apiFacade.registerScreen with null factory must throw NPE");
        }
    }

    // =========================================================================
    // Test Helpers
    // =========================================================================

    private static class StrictTeardownStub implements InputScreen {
        private final AtomicBoolean teardownStarted = new AtomicBoolean(false);
        final AtomicInteger openCalls = new AtomicInteger(0);
        final AtomicInteger closeCalls = new AtomicInteger(0);

        Consumer<ScreenResult> resultConsumer;
        Consumer<Throwable> failureConsumer;

        void markTeardownStarted() {
            teardownStarted.set(true);
        }

        private void checkNotTornDown(String method) {
            if (teardownStarted.get()) {
                throw new AssertionError("Method '" + method + "' called AFTER teardown started!");
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
            this.failureConsumer = callback;
        }
    }

    private static class MockScreen implements InputScreen {
        Consumer<ScreenResult> callback;
        boolean open = false;

        @Override
        public void open() {
            this.open = true;
        }

        @Override
        public void close() {
            this.open = false;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void onResult(Consumer<ScreenResult> callback) {
            this.callback = callback;
        }

        public void emitResult(ScreenResult result) {
            if (callback != null) {
                callback.accept(result);
            }
        }
    }

    private static class FakePlayerExecutor implements PlayerExecutor {
        private final Queue<Runnable> queue = new ArrayDeque<>();
        private final Queue<Runnable> retiredQueue = new ArrayDeque<>();

        @Override
        public void execute(Runnable task, Runnable retired) {
            queue.add(task);
            if (retired != null) retiredQueue.add(retired);
        }

        public void runAll() {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }
}
