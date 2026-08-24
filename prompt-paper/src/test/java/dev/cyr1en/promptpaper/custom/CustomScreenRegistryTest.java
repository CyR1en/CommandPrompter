package dev.cyr1en.promptpaper.custom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.ScreenType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CustomScreenRegistryTest {

    private CustomScreenRegistry registry;
    private List<CustomScreenAuditEvent> auditLog;
    private Plugin testPlugin;
    private CustomScreenFactory dummyFactory;

    @BeforeEach
    void setUp() {
        auditLog = Collections.synchronizedList(new ArrayList<>());
        registry = new CustomScreenRegistry(
                () -> true,
                Map::of,
                PromptConfig.RESERVED_SCREEN_KEYS,
                auditLog::add
        );

        testPlugin = mock(Plugin.class);
        when(testPlugin.getName()).thenReturn("TestProviderPlugin");
        when(testPlugin.isEnabled()).thenReturn(true);

        dummyFactory = (player, tag) -> null;
    }

    @Nested
    @DisplayName("Valid Registration and Metadata")
    class ValidRegistrationTests {

        @Test
        @DisplayName("Valid key registers successfully with correct metadata and provider token")
        void testValidRegistration() {
            registry.registerScreen(testPlugin, "my_custom_screen", dummyFactory);

            assertTrue(registry.isRegistered("my_custom_screen"));
            var handleOpt = registry.getRegistration("my_custom_screen");
            assertTrue(handleOpt.isPresent());

            CustomScreenHandle handle = handleOpt.get();
            assertEquals("my_custom_screen", handle.key());
            assertEquals("TestProviderPlugin", handle.ownerName());
            assertEquals(ProviderState.ACTIVE, handle.state());
            assertTrue(handle.isActive());
            assertSame(dummyFactory, handle.factory());
            assertTrue(handle.providerId() > 0);

            // Audit event emitted
            assertEquals(1, auditLog.size());
            CustomScreenAuditEvent event = auditLog.getFirst();
            assertEquals(CustomScreenAuditEvent.Type.REGISTERED, event.type());
            assertEquals("my_custom_screen", event.key());
            assertEquals("TestProviderPlugin", event.ownerName());
        }

        @Test
        @DisplayName("Monotonic provider ID increments across multiple registrations")
        void testMonotonicProviderId() {
            registry.registerScreen(testPlugin, "screen_one", dummyFactory);
            registry.registerScreen(testPlugin, "screen_two", dummyFactory);

            var h1 = registry.getRegistration("screen_one").orElseThrow();
            var h2 = registry.getRegistration("screen_two").orElseThrow();

            assertTrue(h2.providerId() > h1.providerId());
        }
    }

    @Nested
    @DisplayName("Null Validation")
    class NullValidationTests {

        @Test
        @DisplayName("Null plugin throws NullPointerException")
        void testNullPlugin() {
            assertThrows(NullPointerException.class, () ->
                    registry.registerScreen(null, "valid_key", dummyFactory));
        }

        @Test
        @DisplayName("Null key throws NullPointerException")
        void testNullKey() {
            assertThrows(NullPointerException.class, () ->
                    registry.registerScreen(testPlugin, null, dummyFactory));
        }

        @Test
        @DisplayName("Null factory throws NullPointerException")
        void testNullFactory() {
            assertThrows(NullPointerException.class, () ->
                    registry.registerScreen(testPlugin, "valid_key", null));
        }
    }

    @Nested
    @DisplayName("Lifecycle & Plugin State Validation")
    class LifecycleValidationTests {

        @Test
        @DisplayName("Disabled owner plugin throws IllegalStateException")
        void testDisabledOwnerPlugin() {
            Plugin disabledPlugin = mock(Plugin.class);
            when(disabledPlugin.getName()).thenReturn("DisabledPlugin");
            when(disabledPlugin.isEnabled()).thenReturn(false);

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    registry.registerScreen(disabledPlugin, "valid_key", dummyFactory));
            assertTrue(ex.getMessage().contains("not enabled"));
        }

        @Test
        @DisplayName("Inactive CommandPrompter lifecycle throws IllegalStateException")
        void testInactiveLifecycle() {
            CustomScreenRegistry inactiveRegistry = new CustomScreenRegistry(
                    () -> false,
                    Map::of,
                    PromptConfig.RESERVED_SCREEN_KEYS,
                    auditLog::add
            );

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    inactiveRegistry.registerScreen(testPlugin, "valid_key", dummyFactory));
            assertTrue(ex.getMessage().contains("not active"));
        }

        @Test
        @DisplayName("Frozen registry rejects new registrations")
        void testFrozenRegistry() {
            registry.freeze();
            assertTrue(registry.isFrozen());
            assertFalse(registry.isLifecycleActive());

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    registry.registerScreen(testPlugin, "valid_key", dummyFactory));
            assertTrue(ex.getMessage().contains("frozen"));
        }
    }

    @Nested
    @DisplayName("Key Grammar Validation")
    class KeyGrammarTests {

        @Test
        @DisplayName("Uppercase keys are rejected by grammar")
        void testUppercaseKeys() {
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "MyScreen", dummyFactory));
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "ANVIL", dummyFactory));
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "screenA", dummyFactory));
        }

        @Test
        @DisplayName("Keys starting with digit are rejected")
        void testDigitStartKey() {
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "1screen", dummyFactory));
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "0abc", dummyFactory));
        }

        @Test
        @DisplayName("Keys with whitespace are rejected")
        void testWhitespaceKey() {
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "my screen", dummyFactory));
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, " screen", dummyFactory));
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "screen ", dummyFactory));
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, " ", dummyFactory));
        }

        @Test
        @DisplayName("Keys with symbols or invalid characters are rejected")
        void testSymbolKeys() {
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "my-screen", dummyFactory));
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "my.screen", dummyFactory));
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "my$screen", dummyFactory));
        }

        @Test
        @DisplayName("Keys starting with @ are rejected")
        void testPresetNamespaceKey() {
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "@meta", dummyFactory));
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "@preset", dummyFactory));
        }

        @Test
        @DisplayName("Oversized keys exceeding 32 characters are rejected")
        void testOversizedKey() {
            String oversized = "a".repeat(33);
            assertEquals(33, oversized.length());
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, oversized, dummyFactory));

            // 32 characters is allowed
            String maxAllowed = "a".repeat(32);
            assertDoesNotThrow(() ->
                    registry.registerScreen(testPlugin, maxAllowed, dummyFactory));
        }

        @Test
        @DisplayName("Empty key is rejected")
        void testEmptyKey() {
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "", dummyFactory));
        }
    }

    @Nested
    @DisplayName("Reserved Keys Validation")
    class ReservedKeyTests {

        @Test
        @DisplayName("All standard reserved keys are explicitly rejected")
        void testStandardReservedKeys() {
            List<String> reserved = List.of(
                    "a", "anvil",
                    "s", "sign",
                    "p", "player",
                    "d", "dialog",
                    "c", "confirm", "confirmation",
                    "i", "item"
            );

            for (String key : reserved) {
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                        registry.registerScreen(testPlugin, key, dummyFactory), "Expected " + key + " to be rejected");
                assertTrue(ex.getMessage().contains("reserved") || ex.getMessage().contains("match"),
                        "Message for " + key + ": " + ex.getMessage());
            }
        }

        @Test
        @DisplayName("Case variants of reserved keys are rejected with descriptive messages")
        void testCaseVariantReservedKeys() {
            List<String> variants = List.of("Anvil", "ANVIL", "Sign", "SIGN", "Player", "PLAYER", "Dialog", "DIALOG", "Confirm", "CONFIRM");
            for (String variant : variants) {
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                        registry.registerScreen(testPlugin, variant, dummyFactory));
                assertNotNull(ex.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Configured Key Collision")
    class ConfiguredCollisionTests {

        @Test
        @DisplayName("Registration colliding with configured screen mapping is rejected")
        void testConfiguredCollision() {
            CustomScreenRegistry regWithConfig = new CustomScreenRegistry(
                    () -> true,
                    () -> Map.of("custom_mapped", ScreenType.ANVIL),
                    PromptConfig.RESERVED_SCREEN_KEYS,
                    auditLog::add
            );

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    regWithConfig.registerScreen(testPlugin, "custom_mapped", dummyFactory));
            assertTrue(ex.getMessage().contains("collides with configured screen mapping"));
        }
    }

    @Nested
    @DisplayName("Duplicate Registrations & Atomicity")
    class DuplicateRegistrationTests {

        @Test
        @DisplayName("Duplicate registration from same owner throws and leaves original intact")
        void testSameOwnerDuplicate() {
            registry.registerScreen(testPlugin, "screen_a", dummyFactory);

            CustomScreenFactory secondFactory = (player, tag) -> null;
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "screen_a", secondFactory));
            assertTrue(ex.getMessage().contains("already registered"));

            // First registration remains intact
            var handle = registry.getRegistration("screen_a").orElseThrow();
            assertSame(dummyFactory, handle.factory());
        }

        @Test
        @DisplayName("Duplicate registration from different owner throws and leaves original intact")
        void testDifferentOwnerDuplicate() {
            registry.registerScreen(testPlugin, "screen_a", dummyFactory);

            Plugin plugin2 = mock(Plugin.class);
            when(plugin2.getName()).thenReturn("OtherPlugin");
            when(plugin2.isEnabled()).thenReturn(true);

            CustomScreenFactory otherFactory = (player, tag) -> null;
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(plugin2, "screen_a", otherFactory));
            assertTrue(ex.getMessage().contains("already registered"));

            var handle = registry.getRegistration("screen_a").orElseThrow();
            assertEquals("TestProviderPlugin", handle.ownerName());
            assertSame(dummyFactory, handle.factory());
        }

        @Test
        @DisplayName("Concurrent duplicate registration yields exactly one winner")
        void testConcurrentRegistration() throws InterruptedException {
            int threadCount = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    Plugin p = mock(Plugin.class);
                    when(p.getName()).thenReturn("Plugin_" + idx);
                    when(p.isEnabled()).thenReturn(true);

                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        registry.registerScreen(p, "contested_screen", (player, tag) -> null);
                        successCount.incrementAndGet();
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        failureCount.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                });
            }

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startLatch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(1, successCount.get());
            assertEquals(threadCount - 1, failureCount.get());
            assertTrue(registry.isRegistered("contested_screen"));
        }
    }

    @Nested
    @DisplayName("Unregistration & Factory Detachment")
    class UnregistrationTests {

        @Test
        @DisplayName("Owner unregister removes screens, detaches factories, and marks state inactive")
        void testOwnerUnregister() {
            registry.registerScreen(testPlugin, "screen_one", dummyFactory);
            registry.registerScreen(testPlugin, "screen_two", dummyFactory);

            CustomScreenHandle handleBefore = registry.getRegistration("screen_one").orElseThrow();
            assertTrue(handleBefore.isActive());
            assertSame(dummyFactory, handleBefore.factory());

            registry.unregisterScreens(testPlugin);

            assertFalse(registry.isRegistered("screen_one"));
            assertFalse(registry.isRegistered("screen_two"));
            assertTrue(registry.getRegistration("screen_one").isEmpty());
            assertTrue(registry.getRegistrationsSnapshot().isEmpty());
            assertTrue(registry.getRegisteredKeys().isEmpty());

            // EXACT SAME previously obtained handle must now report inactive and factory null
            assertFalse(handleBefore.isActive());
            assertNull(handleBefore.factory(), "Factory reference must be nulled on previously obtained handle");
            assertEquals(ProviderState.INACTIVE, handleBefore.state());
        }

        @Test
        @DisplayName("Previously obtained handle becomes inactive and factory null after unregister")
        void testPreviouslyObtainedHandleBecomesInactiveAndFactoryNull() {
            registry.registerScreen(testPlugin, "live_handle_key", dummyFactory);

            CustomScreenHandle handle = registry.getRegistration("live_handle_key").orElseThrow();
            assertTrue(handle.isActive());
            assertSame(dummyFactory, handle.factory());
            assertEquals(ProviderState.ACTIVE, handle.state());

            registry.unregisterScreens(testPlugin);

            // EXACT SAME handle instance must immediately reflect detached state
            assertFalse(handle.isActive());
            assertNull(handle.factory(), "Factory reference must be nulled on previously obtained handle");
            assertEquals(ProviderState.INACTIVE, handle.state());
        }

        @Test
        @DisplayName("Foreign unregister call is a no-op and leaves owner registration intact")
        void testForeignUnregisterNoOp() {
            registry.registerScreen(testPlugin, "screen_one", dummyFactory);

            Plugin foreignPlugin = mock(Plugin.class);
            when(foreignPlugin.getName()).thenReturn("ForeignPlugin");
            when(foreignPlugin.isEnabled()).thenReturn(true);

            registry.unregisterScreens(foreignPlugin);

            assertTrue(registry.isRegistered("screen_one"));
            var handle = registry.getRegistration("screen_one").orElseThrow();
            assertEquals("TestProviderPlugin", handle.ownerName());
        }

        @Test
        @DisplayName("Teardown and unregisterAll clears indexes and detaches factory references")
        void testUnregisterAll() {
            registry.registerScreen(testPlugin, "screen_a", dummyFactory);
            registry.registerScreen(testPlugin, "screen_b", dummyFactory);

            registry.unregisterAll();

            assertFalse(registry.isRegistered("screen_a"));
            assertFalse(registry.isRegistered("screen_b"));
            assertEquals(0, registry.getRegistrationsSnapshot().size());
            assertEquals(0, registry.getRegisteredKeys().size());
        }
    }

    @Nested
    @DisplayName("Audit Logging & Sanitization")
    class AuditLoggingTests {

        @Test
        @DisplayName("Audit events record sanitized owner name")
        void testOwnerNameSanitization() {
            Plugin weirdNamePlugin = mock(Plugin.class);
            when(weirdNamePlugin.getName()).thenReturn("Bad\nName\t<color:red>!!$$");
            when(weirdNamePlugin.isEnabled()).thenReturn(true);

            registry.registerScreen(weirdNamePlugin, "sanitized_screen", dummyFactory);

            var handle = registry.getRegistration("sanitized_screen").orElseThrow();
            assertFalse(handle.ownerName().contains("\n"));
            assertFalse(handle.ownerName().contains("<"));
            assertFalse(handle.ownerName().contains("$"));
        }

        @Test
        @DisplayName("Audit events emitted on registration, unregistration, rejection, and freeze")
        void testAuditEvents() {
            registry.registerScreen(testPlugin, "screen_x", dummyFactory);
            registry.unregisterScreens(testPlugin);
            assertThrows(IllegalArgumentException.class, () ->
                    registry.registerScreen(testPlugin, "INVALID_UPPER", dummyFactory));
            registry.freeze();

            List<CustomScreenAuditEvent.Type> eventTypes = auditLog.stream().map(CustomScreenAuditEvent::type).toList();
            assertTrue(eventTypes.contains(CustomScreenAuditEvent.Type.REGISTERED));
            assertTrue(eventTypes.contains(CustomScreenAuditEvent.Type.UNREGISTERED));
            assertTrue(eventTypes.contains(CustomScreenAuditEvent.Type.REJECTED));
            assertTrue(eventTypes.contains(CustomScreenAuditEvent.Type.FROZEN));
        }
    }
}
