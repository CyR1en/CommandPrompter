package dev.cyr1en.promptpaper.screen.confirmation;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Thread-safe cryptographic nonce registry for chat confirmation prompts.
 *
 * <p>Generates command-safe nonces with at least 128 bits of entropy (16 bytes encoded as URL-safe
 * Base64 without padding). Tracks mappings by nonce, player UUID, and session incarnation to ensure
 * consistent cleanup across lifecycle transitions.
 */
public final class NonceResponseRegistry {

  private static final int NONCE_BYTE_LENGTH = 16; // 128 bits entropy

  private final SecureRandom secureRandom;
  private final Clock clock;

  private record SessionKey(UUID playerUuid, long incarnation) {}

  private final Map<String, ConfirmationBinding> byNonce = new HashMap<>();
  private final Map<UUID, Set<String>> byPlayer = new HashMap<>();
  private final Map<SessionKey, Set<String>> bySession = new HashMap<>();

  public NonceResponseRegistry() {
    this(new SecureRandom(), Clock.systemUTC());
  }

  public NonceResponseRegistry(Clock clock) {
    this(new SecureRandom(), clock);
  }

  public NonceResponseRegistry(SecureRandom secureRandom, Clock clock) {
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Generates a command-safe random nonce token with 128 bits of entropy.
   *
   * @return URL-safe Base64 encoded token without padding
   */
  public String generateNonce() {
    byte[] bytes = new byte[NONCE_BYTE_LENGTH];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Truncates a nonce to a safe prefix suitable for logging or diagnostics.
   *
   * @param nonce the raw nonce token
   * @return safe truncated representation
   */
  public static String truncateNonce(String nonce) {
    if (nonce == null) {
      return "null";
    }
    if (nonce.length() <= 6) {
      return nonce;
    }
    return nonce.substring(0, 6) + "...";
  }

  /** Registers a new confirmation prompt binding with a relative TTL. */
  public synchronized ConfirmationBinding register(
      UUID playerUuid,
      long incarnation,
      long generation,
      int promptIndex,
      Duration ttl,
      Consumer<ConfirmationDecision> callback) {
    Objects.requireNonNull(ttl, "ttl must not be null");
    var expiresAt = clock.instant().plus(ttl);
    return register(playerUuid, incarnation, generation, promptIndex, expiresAt, callback);
  }

  /** Registers a new confirmation prompt binding with an absolute expiration timestamp. */
  public synchronized ConfirmationBinding register(
      UUID playerUuid,
      long incarnation,
      long generation,
      int promptIndex,
      Instant expiresAt,
      Consumer<ConfirmationDecision> callback) {
    var nonce = generateNonce();
    var binding =
        new ConfirmationBinding(
            nonce, playerUuid, incarnation, generation, promptIndex, expiresAt, callback);
    putBinding(binding);
    return binding;
  }

  /** Registers a pre-constructed binding. */
  public synchronized void registerBinding(ConfirmationBinding binding) {
    Objects.requireNonNull(binding, "binding must not be null");
    putBinding(binding);
  }

  private void putBinding(ConfirmationBinding binding) {
    byNonce.put(binding.nonce(), binding);
    byPlayer.computeIfAbsent(binding.playerUuid(), k -> new HashSet<>()).add(binding.nonce());
    bySession
        .computeIfAbsent(
            new SessionKey(binding.playerUuid(), binding.incarnation()), k -> new HashSet<>())
        .add(binding.nonce());
  }

  /** Atomically validates and consumes a confirmation response using raw decision string. */
  public synchronized ConsumeResult consume(
      String nonce,
      UUID playerUuid,
      long expectedIncarnation,
      long expectedGeneration,
      int expectedPromptIndex,
      String rawDecision) {
    var parsed = ConfirmationDecision.parse(rawDecision);
    if (parsed.isEmpty()) {
      return ConsumeResult.rejected(RejectionReason.INVALID_DECISION);
    }
    return consume(
        nonce,
        playerUuid,
        expectedIncarnation,
        expectedGeneration,
        expectedPromptIndex,
        parsed.get());
  }

  /** Atomically validates and consumes a confirmation response using a strongly-typed decision. */
  public synchronized ConsumeResult consume(
      String nonce,
      UUID playerUuid,
      long expectedIncarnation,
      long expectedGeneration,
      int expectedPromptIndex,
      ConfirmationDecision decision) {
    if (decision == null) {
      return ConsumeResult.rejected(RejectionReason.INVALID_DECISION);
    }
    if (nonce == null || playerUuid == null) {
      return ConsumeResult.rejected(RejectionReason.NONCE_NOT_FOUND);
    }
    var binding = byNonce.get(nonce);
    if (binding == null) {
      return ConsumeResult.rejected(RejectionReason.NONCE_NOT_FOUND);
    }
    if (!binding.playerUuid().equals(playerUuid)) {
      return ConsumeResult.rejected(RejectionReason.PLAYER_MISMATCH);
    }
    if (binding.isExpired(clock.instant())) {
      return ConsumeResult.rejected(RejectionReason.EXPIRED);
    }
    if (binding.incarnation() != expectedIncarnation) {
      return ConsumeResult.rejected(RejectionReason.INCARNATION_MISMATCH);
    }
    if (binding.generation() != expectedGeneration) {
      return ConsumeResult.rejected(RejectionReason.GENERATION_MISMATCH);
    }
    if (binding.promptIndex() != expectedPromptIndex) {
      return ConsumeResult.rejected(RejectionReason.PROMPT_INDEX_MISMATCH);
    }

    // All checks passed -> atomic consume (removal) before returning
    removeBinding(binding);
    return ConsumeResult.success(binding, decision);
  }

  private void removeBinding(ConfirmationBinding binding) {
    byNonce.remove(binding.nonce());
    var playerSet = byPlayer.get(binding.playerUuid());
    if (playerSet != null) {
      playerSet.remove(binding.nonce());
      if (playerSet.isEmpty()) {
        byPlayer.remove(binding.playerUuid());
      }
    }
    var sessionKey = new SessionKey(binding.playerUuid(), binding.incarnation());
    var sessionSet = bySession.get(sessionKey);
    if (sessionSet != null) {
      sessionSet.remove(binding.nonce());
      if (sessionSet.isEmpty()) {
        bySession.remove(sessionKey);
      }
    }
  }

  /** Invalidates a specific nonce. */
  public synchronized boolean invalidateNonce(String nonce) {
    if (nonce == null) {
      return false;
    }
    var binding = byNonce.get(nonce);
    if (binding != null) {
      removeBinding(binding);
      return true;
    }
    return false;
  }

  /**
   * Invalidates all nonces belonging to the specified player, session incarnation, and prompt
   * index.
   *
   * @return count of nonces invalidated
   */
  public synchronized int invalidatePrompt(UUID playerUuid, long incarnation, int promptIndex) {
    if (playerUuid == null) {
      return 0;
    }
    var toRemove = new ArrayList<ConfirmationBinding>();
    for (var binding : byNonce.values()) {
      if (binding.playerUuid().equals(playerUuid)
          && binding.incarnation() == incarnation
          && binding.promptIndex() == promptIndex) {
        toRemove.add(binding);
      }
    }
    for (var binding : toRemove) {
      removeBinding(binding);
    }
    return toRemove.size();
  }

  /**
   * Invalidates all nonces belonging to the specified player and session incarnation.
   *
   * @return count of nonces invalidated
   */
  public synchronized int invalidateSession(UUID playerUuid, long incarnation) {
    if (playerUuid == null) {
      return 0;
    }
    var sessionKey = new SessionKey(playerUuid, incarnation);
    var nonces = bySession.remove(sessionKey);
    if (nonces == null || nonces.isEmpty()) {
      return 0;
    }
    int count = 0;
    var copy = new HashSet<>(nonces);
    for (var nonce : copy) {
      var binding = byNonce.remove(nonce);
      if (binding != null) {
        count++;
        var playerSet = byPlayer.get(playerUuid);
        if (playerSet != null) {
          playerSet.remove(nonce);
          if (playerSet.isEmpty()) {
            byPlayer.remove(playerUuid);
          }
        }
      }
    }
    return count;
  }

  /**
   * Invalidates all nonces belonging to the specified player across all sessions.
   *
   * @return count of nonces invalidated
   */
  public synchronized int invalidatePlayer(UUID playerUuid) {
    if (playerUuid == null) {
      return 0;
    }
    var nonces = byPlayer.remove(playerUuid);
    if (nonces == null || nonces.isEmpty()) {
      return 0;
    }
    int count = 0;
    var copy = new HashSet<>(nonces);
    for (var nonce : copy) {
      var binding = byNonce.remove(nonce);
      if (binding != null) {
        count++;
        var sessionKey = new SessionKey(playerUuid, binding.incarnation());
        var sessionSet = bySession.get(sessionKey);
        if (sessionSet != null) {
          sessionSet.remove(nonce);
          if (sessionSet.isEmpty()) {
            bySession.remove(sessionKey);
          }
        }
      }
    }
    return count;
  }

  /**
   * Purges expired entries using the registry's current clock.
   *
   * @return count of nonces purged
   */
  public synchronized int purgeExpired() {
    return purgeExpired(clock.instant());
  }

  /**
   * Purges expired entries relative to the given timestamp.
   *
   * @return count of nonces purged
   */
  public synchronized int purgeExpired(Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    var toRemove = new ArrayList<ConfirmationBinding>();
    for (var binding : byNonce.values()) {
      if (binding.isExpired(now)) {
        toRemove.add(binding);
      }
    }
    for (var binding : toRemove) {
      removeBinding(binding);
    }
    return toRemove.size();
  }

  /** Clears all state across nonces, player indexes, and session indexes. */
  public synchronized void clear() {
    byNonce.clear();
    byPlayer.clear();
    bySession.clear();
  }

  public synchronized int size() {
    return byNonce.size();
  }

  public synchronized boolean contains(String nonce) {
    return nonce != null && byNonce.containsKey(nonce);
  }

  public synchronized Optional<ConfirmationBinding> get(String nonce) {
    return nonce == null ? Optional.empty() : Optional.ofNullable(byNonce.get(nonce));
  }

  public synchronized int playerIndexSize() {
    return byPlayer.size();
  }

  public synchronized int sessionIndexSize() {
    return bySession.size();
  }

  public Clock clock() {
    return clock;
  }
}
