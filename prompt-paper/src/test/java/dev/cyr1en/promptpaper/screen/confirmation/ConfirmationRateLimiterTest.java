package dev.cyr1en.promptpaper.screen.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmationRateLimiterTest {

  private MutableTestClock testClock;
  private ConfirmationRateLimiter rateLimiter;
  private UUID player1;
  private UUID player2;

  @BeforeEach
  void setUp() {
    testClock = new MutableTestClock(Instant.parse("2026-08-17T12:00:00Z"));
    rateLimiter = new ConfirmationRateLimiter(testClock, 5, Duration.ofSeconds(10));
    player1 = UUID.randomUUID();
    player2 = UUID.randomUUID();
  }

  @Test
  void allowsUpToMaxAttemptsInWindow() {
    for (int i = 0; i < 5; i++) {
      assertTrue(rateLimiter.tryAcquire(player1), "Attempt " + (i + 1) + " should be allowed");
    }
    assertEquals(5, rateLimiter.attemptCount(player1));

    // 6th attempt in the same window is denied
    assertFalse(rateLimiter.tryAcquire(player1), "6th attempt should be rejected");
  }

  @Test
  void slidingWindowRollsOffExpiredAttempts() {
    // 5 attempts at T=0
    for (int i = 0; i < 5; i++) {
      assertTrue(rateLimiter.tryAcquire(player1));
    }
    assertFalse(rateLimiter.tryAcquire(player1));

    // Advance 5 seconds - still in 10-second window
    testClock.set(Instant.parse("2026-08-17T12:00:05Z"));
    assertFalse(rateLimiter.tryAcquire(player1));

    // Advance past 10 seconds (T = 10.001s)
    testClock.set(Instant.parse("2026-08-17T12:00:10.001Z"));
    assertTrue(
        rateLimiter.tryAcquire(player1),
        "New attempt should succeed after previous window expired");
  }

  @Test
  void perPlayerIsolation() {
    for (int i = 0; i < 5; i++) {
      assertTrue(rateLimiter.tryAcquire(player1));
    }
    assertFalse(rateLimiter.tryAcquire(player1));

    // Player 2 is unaffected
    for (int i = 0; i < 5; i++) {
      assertTrue(rateLimiter.tryAcquire(player2));
    }
    assertFalse(rateLimiter.tryAcquire(player2));
  }

  @Test
  void resetClearsPlayerRateLimit() {
    for (int i = 0; i < 5; i++) {
      assertTrue(rateLimiter.tryAcquire(player1));
    }
    assertFalse(rateLimiter.tryAcquire(player1));

    rateLimiter.reset(player1);
    assertEquals(0, rateLimiter.attemptCount(player1));
    assertTrue(rateLimiter.tryAcquire(player1));
  }

  @Test
  void purgeExpiredRemovesEmptyPlayerEntries() {
    rateLimiter.tryAcquire(player1);
    rateLimiter.tryAcquire(player2);

    // Advance past window
    testClock.set(Instant.parse("2026-08-17T12:00:11Z"));

    int purged = rateLimiter.purgeExpired();
    assertEquals(2, purged);
  }

  @Test
  void clearResetsAllState() {
    rateLimiter.tryAcquire(player1);
    rateLimiter.tryAcquire(player2);

    rateLimiter.clear();
    assertEquals(0, rateLimiter.attemptCount(player1));
    assertEquals(0, rateLimiter.attemptCount(player2));
  }

  @Test
  void separateSuppressionChannelsAreIndependent() {
    // Rejection log consumed
    assertTrue(rateLimiter.shouldLogRejection(player1), "First rejection log should be allowed");
    assertFalse(
        rateLimiter.shouldLogRejection(player1),
        "Second rejection log in window should be suppressed");

    // Rate limit log is on a separate channel and still allowed
    assertTrue(
        rateLimiter.shouldLogRateLimit(player1),
        "Rate limit log should still be allowed after prior rejection");
    assertFalse(
        rateLimiter.shouldLogRateLimit(player1),
        "Second rate limit log in window should be suppressed");

    // Player 2 is unaffected on both channels
    assertTrue(rateLimiter.shouldLogRejection(player2));
    assertTrue(rateLimiter.shouldLogRateLimit(player2));
  }

  @Test
  void resetClearsAllSuppressionChannelsForPlayer() {
    assertTrue(rateLimiter.shouldLogRejection(player1));
    assertTrue(rateLimiter.shouldLogRateLimit(player1));
    assertFalse(rateLimiter.shouldLogRejection(player1));
    assertFalse(rateLimiter.shouldLogRateLimit(player1));

    rateLimiter.reset(player1);

    assertTrue(
        rateLimiter.shouldLogRejection(player1), "Rejection log should be allowed after reset");
    assertTrue(
        rateLimiter.shouldLogRateLimit(player1), "Rate limit log should be allowed after reset");
  }

  @Test
  void purgeExpiredClearsChannelSuppression() {
    assertTrue(rateLimiter.shouldLogRejection(player1));
    assertTrue(rateLimiter.shouldLogRateLimit(player1));

    // Advance past window
    testClock.set(Instant.parse("2026-08-17T12:00:11Z"));
    rateLimiter.purgeExpired();

    assertTrue(
        rateLimiter.shouldLogRejection(player1),
        "Rejection log should be allowed after window expires");
    assertTrue(
        rateLimiter.shouldLogRateLimit(player1),
        "Rate limit log should be allowed after window expires");
  }

  @Test
  void clearResetsAllSuppressionChannels() {
    assertTrue(rateLimiter.shouldLogRejection(player1));
    assertTrue(rateLimiter.shouldLogRateLimit(player1));

    rateLimiter.clear();

    assertTrue(
        rateLimiter.shouldLogRejection(player1), "Rejection log should be allowed after clear");
    assertTrue(
        rateLimiter.shouldLogRateLimit(player1), "Rate limit log should be allowed after clear");
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
