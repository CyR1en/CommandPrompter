package dev.cyr1en.promptpaper.screen.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NonceResponseRegistryTest {

    private MutableTestClock testClock;
    private NonceResponseRegistry registry;
    private UUID player1;
    private UUID player2;

    @BeforeEach
    void setUp() {
        testClock = new MutableTestClock(Instant.parse("2026-08-17T12:00:00Z"));
        registry = new NonceResponseRegistry(testClock);
        player1 = UUID.randomUUID();
        player2 = UUID.randomUUID();
    }

    @Test
    void entropyAndShape() {
        var nonces = new HashSet<String>();
        var pattern = "^[A-Za-z0-9_-]{22,}$";

        for (int i = 0; i < 200; i++) {
            var nonce = registry.generateNonce();
            assertTrue(nonce.matches(pattern), "Nonce should match URL-safe base64 pattern: " + nonce);
            assertTrue(nonce.length() >= 22, "128 bits in Base64 unpadded is 22 characters");
            assertTrue(nonces.add(nonce), "All generated nonces must be cryptographically unique");
        }
    }

    @Test
    void successfulConsumeConfirm() {
        var decisionRef = new AtomicReference<ConfirmationDecision>();
        var binding = registry.register(player1, 1L, 0L, 0, Duration.ofSeconds(30), decisionRef::set);

        assertNotNull(binding.nonce());
        assertEquals(1, registry.size());
        assertTrue(registry.contains(binding.nonce()));

        var result = registry.consume(binding.nonce(), player1, 1L, 0L, 0, "confirm");
        assertTrue(result.isSuccess());
        assertTrue(result.optBinding().isPresent());
        assertEquals(ConfirmationDecision.CONFIRM, result.optDecision().orElse(null));
        assertTrue(result.optRejectionReason().isEmpty());

        // Consumed nonce is removed from registry
        assertEquals(0, registry.size());
        assertFalse(registry.contains(binding.nonce()));
        assertEquals(0, registry.playerIndexSize());
        assertEquals(0, registry.sessionIndexSize());
    }

    @Test
    void successfulConsumeDecline() {
        var binding = registry.register(player1, 2L, 1L, 3, Duration.ofSeconds(30), d -> {});

        var result = registry.consume(binding.nonce(), player1, 2L, 1L, 3, "decline");
        assertTrue(result.isSuccess());
        assertEquals(ConfirmationDecision.DECLINE, result.optDecision().orElse(null));
        assertEquals(0, registry.size());
    }

    @Test
    void replayAttemptRejected() {
        var binding = registry.register(player1, 1L, 0L, 0, Duration.ofSeconds(30), d -> {});

        var first = registry.consume(binding.nonce(), player1, 1L, 0L, 0, "confirm");
        assertTrue(first.isSuccess());

        var second = registry.consume(binding.nonce(), player1, 1L, 0L, 0, "confirm");
        assertFalse(second.isSuccess());
        assertEquals(RejectionReason.NONCE_NOT_FOUND, second.optRejectionReason().orElse(null));
    }

    @Test
    void wrongPlayerUuidRejected() {
        var binding = registry.register(player1, 1L, 0L, 0, Duration.ofSeconds(30), d -> {});

        var result = registry.consume(binding.nonce(), player2, 1L, 0L, 0, "confirm");
        assertFalse(result.isSuccess());
        assertEquals(RejectionReason.PLAYER_MISMATCH, result.optRejectionReason().orElse(null));

        // Registry state is unchanged (binding not consumed)
        assertEquals(1, registry.size());
        assertTrue(registry.contains(binding.nonce()));
    }

    @Test
    void staleIncarnationRejected() {
        var binding = registry.register(player1, 5L, 0L, 0, Duration.ofSeconds(30), d -> {});

        var result = registry.consume(binding.nonce(), player1, 4L, 0L, 0, "confirm");
        assertFalse(result.isSuccess());
        assertEquals(RejectionReason.INCARNATION_MISMATCH, result.optRejectionReason().orElse(null));
        assertEquals(1, registry.size());
    }

    @Test
    void staleGenerationRejected() {
        var binding = registry.register(player1, 1L, 2L, 0, Duration.ofSeconds(30), d -> {});

        var result = registry.consume(binding.nonce(), player1, 1L, 1L, 0, "confirm");
        assertFalse(result.isSuccess());
        assertEquals(RejectionReason.GENERATION_MISMATCH, result.optRejectionReason().orElse(null));
        assertEquals(1, registry.size());
    }

    @Test
    void stalePromptIndexRejected() {
        var binding = registry.register(player1, 1L, 0L, 2, Duration.ofSeconds(30), d -> {});

        var result = registry.consume(binding.nonce(), player1, 1L, 0L, 1, "confirm");
        assertFalse(result.isSuccess());
        assertEquals(RejectionReason.PROMPT_INDEX_MISMATCH, result.optRejectionReason().orElse(null));
        assertEquals(1, registry.size());
    }

    @Test
    void expiryBoundary() {
        var binding = registry.register(player1, 1L, 0L, 0, Duration.ofSeconds(10), d -> {});

        // Advance to 9.999s (before expiry)
        testClock.set(Instant.parse("2026-08-17T12:00:09.999Z"));
        assertFalse(binding.isExpired(testClock.instant()));

        // Advance to 10.001s (after expiry)
        testClock.set(Instant.parse("2026-08-17T12:00:10.001Z"));
        assertTrue(binding.isExpired(testClock.instant()));

        var result = registry.consume(binding.nonce(), player1, 1L, 0L, 0, "confirm");
        assertFalse(result.isSuccess());
        assertEquals(RejectionReason.EXPIRED, result.optRejectionReason().orElse(null));
        // Expiry consume failure does not delete token from registry until purge
        assertEquals(1, registry.size());
    }

    @Test
    void invalidDecisionRejected() {
        var binding = registry.register(player1, 1L, 0L, 0, Duration.ofSeconds(30), d -> {});

        var result1 = registry.consume(binding.nonce(), player1, 1L, 0L, 0, "invalid");
        assertFalse(result1.isSuccess());
        assertEquals(RejectionReason.INVALID_DECISION, result1.optRejectionReason().orElse(null));

        var result2 = registry.consume(binding.nonce(), player1, 1L, 0L, 0, (String) null);
        assertFalse(result2.isSuccess());
        assertEquals(RejectionReason.INVALID_DECISION, result2.optRejectionReason().orElse(null));

        var result3 = registry.consume(binding.nonce(), player1, 1L, 0L, 0, (ConfirmationDecision) null);
        assertFalse(result3.isSuccess());
        assertEquals(RejectionReason.INVALID_DECISION, result3.optRejectionReason().orElse(null));

        // Registry state was not modified
        assertEquals(1, registry.size());
    }

    @Test
    void caseInsensitiveDecision() {
        var b1 = registry.register(player1, 1L, 0L, 0, Duration.ofSeconds(30), d -> {});
        var res1 = registry.consume(b1.nonce(), player1, 1L, 0L, 0, "CONFIRM");
        assertTrue(res1.isSuccess());
        assertEquals(ConfirmationDecision.CONFIRM, res1.optDecision().orElse(null));

        var b2 = registry.register(player1, 1L, 0L, 0, Duration.ofSeconds(30), d -> {});
        var res2 = registry.consume(b2.nonce(), player1, 1L, 0L, 0, "DeClInE");
        assertTrue(res2.isSuccess());
        assertEquals(ConfirmationDecision.DECLINE, res2.optDecision().orElse(null));
    }

    @Test
    void purgeExpiredCleansUpConsistently() {
        var b1 = registry.register(player1, 1L, 0L, 0, Duration.ofSeconds(5), d -> {});
        var b2 = registry.register(player1, 1L, 0L, 1, Duration.ofSeconds(15), d -> {});
        var b3 = registry.register(player2, 1L, 0L, 0, Duration.ofSeconds(5), d -> {});

        assertEquals(3, registry.size());
        assertEquals(2, registry.playerIndexSize());
        assertEquals(2, registry.sessionIndexSize());

        // Advance past 5s
        testClock.set(Instant.parse("2026-08-17T12:00:06Z"));
        int purged = registry.purgeExpired();

        assertEquals(2, purged);
        assertEquals(1, registry.size());
        assertTrue(registry.contains(b2.nonce()));
        assertFalse(registry.contains(b1.nonce()));
        assertFalse(registry.contains(b3.nonce()));

        // player2 index should be completely cleaned up
        assertEquals(1, registry.playerIndexSize());
        assertEquals(1, registry.sessionIndexSize());
    }

    @Test
    void invalidateSessionRemovesOnlyTargetSession() {
        var b1 = registry.register(player1, 1L, 0L, 0, Duration.ofSeconds(30), d -> {});
        var b2 = registry.register(player1, 1L, 1L, 1, Duration.ofSeconds(30), d -> {});
        var b3 = registry.register(player1, 2L, 0L, 0, Duration.ofSeconds(30), d -> {});
        var b4 = registry.register(player2, 1L, 0L, 0, Duration.ofSeconds(30), d -> {});

        assertEquals(4, registry.size());
        assertEquals(2, registry.playerIndexSize());
        assertEquals(3, registry.sessionIndexSize());

        int invalidated = registry.invalidateSession(player1, 1L);
        assertEquals(2, invalidated);
        assertEquals(2, registry.size());

        assertFalse(registry.contains(b1.nonce()));
        assertFalse(registry.contains(b2.nonce()));
        assertTrue(registry.contains(b3.nonce()));
        assertTrue(registry.contains(b4.nonce()));
    }

    @Test
    void invalidatePlayerRemovesAllSessionsForPlayer() {
        registry.register(player1, 1L, 0L, 0, Duration.ofSeconds(30), d -> {});
        registry.register(player1, 2L, 0L, 0, Duration.ofSeconds(30), d -> {});
        var bOther = registry.register(player2, 1L, 0L, 0, Duration.ofSeconds(30), d -> {});

        int invalidated = registry.invalidatePlayer(player1);
        assertEquals(2, invalidated);
        assertEquals(1, registry.size());
        assertTrue(registry.contains(bOther.nonce()));
        assertEquals(1, registry.playerIndexSize());
        assertEquals(1, registry.sessionIndexSize());
    }

    @Test
    void clearResetsAllIndexes() {
        registry.register(player1, 1L, 0L, 0, Duration.ofSeconds(30), d -> {});
        registry.register(player2, 1L, 0L, 0, Duration.ofSeconds(30), d -> {});

        registry.clear();
        assertEquals(0, registry.size());
        assertEquals(0, registry.playerIndexSize());
        assertEquals(0, registry.sessionIndexSize());
    }

    @Test
    void invalidateNonceSingle() {
        var b1 = registry.register(player1, 1L, 0L, 0, Duration.ofSeconds(30), d -> {});
        var b2 = registry.register(player1, 1L, 0L, 1, Duration.ofSeconds(30), d -> {});

        assertTrue(registry.invalidateNonce(b1.nonce()));
        assertFalse(registry.invalidateNonce("non-existent"));
        assertEquals(1, registry.size());
        assertTrue(registry.contains(b2.nonce()));
    }

    @Test
    void truncateNonceHelper() {
        assertEquals("null", NonceResponseRegistry.truncateNonce(null));
        assertEquals("abc", NonceResponseRegistry.truncateNonce("abc"));
        assertEquals("123456", NonceResponseRegistry.truncateNonce("123456"));
        assertEquals("123456...", NonceResponseRegistry.truncateNonce("1234567890abcdef"));
    }

    private static class MutableTestClock extends Clock {
        private Instant current;

        MutableTestClock(Instant initial) {
            this.current = initial;
        }

        void set(Instant instant) {
            this.current = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
