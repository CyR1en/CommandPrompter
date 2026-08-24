package dev.cyr1en.promptpaper.screen.confirmation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player sliding-window rate limiter for confirmation prompt responses.
 *
 * <p>By default, allows at most 5 response attempts in any 10-second window.
 */
public final class ConfirmationRateLimiter {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final Duration DEFAULT_WINDOW = Duration.ofSeconds(10);

    /** Named suppression channels for rate-limit and rejection diagnostic logging. */
    public enum LogChannel {
        RATE_LIMIT,
        REJECTION
    }

    private final Clock clock;
    private final int maxAttempts;
    private final Duration window;
    private final Map<UUID, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Map<LogChannel, Map<UUID, Instant>> lastLogTimestampsByChannel =
            new ConcurrentHashMap<>();

    public ConfirmationRateLimiter() {
        this(Clock.systemUTC(), DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW);
    }

    public ConfirmationRateLimiter(Clock clock) {
        this(clock, DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW);
    }

    public ConfirmationRateLimiter(Clock clock, int maxAttempts, Duration window) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        this.maxAttempts = maxAttempts;
        this.window = Objects.requireNonNull(window, "window must not be null");
    }

    /**
     * Attempts to acquire a rate-limit slot for the given player.
     *
     * @param playerUuid player UUID
     * @return true if permitted, false if rate limited
     */
    public synchronized boolean tryAcquire(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid must not be null");
        var now = clock.instant();
        var cutoff = now.minus(window);
        var queue = attempts.computeIfAbsent(playerUuid, k -> new ArrayDeque<>());
        while (!queue.isEmpty() && !queue.peekFirst().isAfter(cutoff)) {
            queue.pollFirst();
        }
        if (queue.size() < maxAttempts) {
            queue.addLast(now);
            return true;
        }
        return false;
    }

    /**
     * Checks whether a log warning on the default {@link LogChannel#RATE_LIMIT} channel
     * should be emitted for the player.
     * Guarantees at most one log emission per player per rate-limit window for this channel.
     *
     * @param playerUuid player UUID
     * @return true if diagnostic should be emitted, false if suppressed
     */
    public synchronized boolean shouldLog(UUID playerUuid) {
        return shouldLog(playerUuid, LogChannel.RATE_LIMIT);
    }

    /**
     * Checks whether a rate-limit warning should be logged for the player.
     *
     * @param playerUuid player UUID
     * @return true if rate limit warning should be emitted, false if suppressed
     */
    public synchronized boolean shouldLogRateLimit(UUID playerUuid) {
        return shouldLog(playerUuid, LogChannel.RATE_LIMIT);
    }

    /**
     * Checks whether a rejection warning should be logged for the player.
     *
     * @param playerUuid player UUID
     * @return true if rejection warning should be emitted, false if suppressed
     */
    public synchronized boolean shouldLogRejection(UUID playerUuid) {
        return shouldLog(playerUuid, LogChannel.REJECTION);
    }

    /**
     * Checks whether a diagnostic log warning should be emitted for the player on a specific channel.
     * Guarantees at most one log emission per player per rate-limit window per channel.
     *
     * @param playerUuid player UUID
     * @param channel suppression channel
     * @return true if diagnostic should be emitted, false if suppressed
     */
    public synchronized boolean shouldLog(UUID playerUuid, LogChannel channel) {
        if (playerUuid == null || channel == null) {
            return false;
        }
        var now = clock.instant();
        var cutoff = now.minus(window);
        var channelMap =
                lastLogTimestampsByChannel.computeIfAbsent(channel, k -> new ConcurrentHashMap<>());
        var lastLog = channelMap.get(playerUuid);
        if (lastLog == null || !lastLog.isAfter(cutoff)) {
            channelMap.put(playerUuid, now);
            return true;
        }
        return false;
    }

    /**
     * Resets rate limiter tracking state for a specific player (e.g. on quit or completion).
     *
     * @param playerUuid player UUID
     */
    public synchronized void reset(UUID playerUuid) {
        if (playerUuid != null) {
            attempts.remove(playerUuid);
            for (var channelMap : lastLogTimestampsByChannel.values()) {
                channelMap.remove(playerUuid);
            }
        }
    }

    /**
     * Purges expired timestamp entries across all tracked players and suppression channels.
     *
     * @return count of player entries removed because they had no active attempts
     */
    public synchronized int purgeExpired() {
        var now = clock.instant();
        var cutoff = now.minus(window);
        int purged = 0;
        var it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var queue = entry.getValue();
            while (!queue.isEmpty() && !queue.peekFirst().isAfter(cutoff)) {
                queue.pollFirst();
            }
            if (queue.isEmpty()) {
                it.remove();
                purged++;
            }
        }
        for (var channelMap : lastLogTimestampsByChannel.values()) {
            channelMap.entrySet().removeIf(entry -> !entry.getValue().isAfter(cutoff));
        }
        return purged;
    }

    /**
     * Clears all rate limiting and log suppression state across all channels.
     */
    public synchronized void clear() {
        attempts.clear();
        for (var channelMap : lastLogTimestampsByChannel.values()) {
            channelMap.clear();
        }
        lastLogTimestampsByChannel.clear();
    }

    /**
     * Returns the active attempt count in the current window for the player.
     *
     * @param playerUuid player UUID
     * @return attempt count in active window
     */
    public synchronized int attemptCount(UUID playerUuid) {
        if (playerUuid == null) {
            return 0;
        }
        var queue = attempts.get(playerUuid);
        if (queue == null) {
            return 0;
        }
        var cutoff = clock.instant().minus(window);
        return (int) queue.stream().filter(t -> t.isAfter(cutoff)).count();
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration window() {
        return window;
    }
}
