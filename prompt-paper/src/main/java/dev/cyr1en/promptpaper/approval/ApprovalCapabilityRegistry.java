package dev.cyr1en.promptpaper.approval;

import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Thread-safe cryptographic approval capability registry.
 *
 * <p>Generates command-safe nonces namespaced with prefix {@code a_} and at least 128 bits of
 * entropy (16 bytes encoded as URL-safe Base64 without padding). Tracks capabilities across four
 * thread-safe indexes (nonce, initiator, target, execution). Enforces a busy-fail-closed policy of
 * at most one pending capability per target approver.
 */
public final class ApprovalCapabilityRegistry {

  public static final String NONCE_PREFIX = "a_";
  public static final int MAX_NONCE_GENERATION_ATTEMPTS = 16;
  private static final int NONCE_BYTE_LENGTH = 16; // 128 bits entropy

  private final SecureRandom secureRandom;
  private final Clock clock;

  private final Map<String, ApprovalCapability> byNonce = new HashMap<>();
  private final Map<UUID, Set<String>> byInitiator = new HashMap<>();
  private final Map<UUID, String> byTarget = new HashMap<>();
  private final Map<ExecutionId, String> byExecution = new HashMap<>();

  public ApprovalCapabilityRegistry() {
    this(new SecureRandom(), Clock.systemUTC());
  }

  public ApprovalCapabilityRegistry(Clock clock) {
    this(new SecureRandom(), clock);
  }

  public ApprovalCapabilityRegistry(SecureRandom secureRandom, Clock clock) {
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Checks whether a given string is classified as an approval nonce.
   *
   * @param nonce the raw nonce token
   * @return true if namespaced with approval nonce prefix
   */
  public static boolean isApprovalNonce(String nonce) {
    return nonce != null && nonce.startsWith(NONCE_PREFIX);
  }

  /**
   * Generates a command-safe random nonce token namespaced with {@code a_} and >= 128 bits entropy.
   *
   * @return namespaced URL-safe Base64 encoded token without padding
   */
  public String generateNonce() {
    byte[] bytes = new byte[NONCE_BYTE_LENGTH];
    secureRandom.nextBytes(bytes);
    return NONCE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Truncates a nonce to a safe prefix suitable for logging or diagnostics without leaking raw
   * token.
   *
   * @param nonce the raw nonce token
   * @return safe truncated representation
   */
  public static String truncateNonce(String nonce) {
    if (nonce == null) {
      return "null";
    }
    if (nonce.length() <= 8) {
      return NONCE_PREFIX + "***";
    }
    return nonce.substring(0, 8) + "...";
  }

  /**
   * Attempts to register a new approval capability with a relative TTL.
   *
   * <p>If the target approver or execution already has an active pending capability, registration
   * fails closed and returns {@link Optional#empty()}.
   *
   * @param executionId execution ID
   * @param gateId gate preset ID
   * @param initiator initiator UUID
   * @param initiatorIncarnation initiator incarnation
   * @param target target approver UUID
   * @param ttl time to live duration
   * @return optional containing the registered capability, or empty if target is busy or collision
   *     exhausted
   */
  public synchronized Optional<ApprovalCapability> register(
      ExecutionId executionId,
      String gateId,
      UUID initiator,
      long initiatorIncarnation,
      UUID target,
      Duration ttl) {
    Objects.requireNonNull(executionId, "executionId must not be null");
    Objects.requireNonNull(gateId, "gateId must not be null");
    Objects.requireNonNull(initiator, "initiator must not be null");
    Objects.requireNonNull(target, "target must not be null");
    Objects.requireNonNull(ttl, "ttl must not be null");
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("ttl must be positive");
    }

    Instant now = clock.instant();

    // Check if target has an existing active capability (busy-fail-closed)
    String existingTargetNonce = byTarget.get(target);
    if (existingTargetNonce != null) {
      ApprovalCapability existing = byNonce.get(existingTargetNonce);
      if (existing != null && !existing.isExpired(now)) {
        return Optional.empty();
      }
      if (existing != null) {
        removeCapability(existing);
      } else {
        byTarget.remove(target);
      }
    }

    // Check if execution already has an existing active capability
    String existingExecNonce = byExecution.get(executionId);
    if (existingExecNonce != null) {
      ApprovalCapability existingExec = byNonce.get(existingExecNonce);
      if (existingExec != null && !existingExec.isExpired(now)) {
        return Optional.empty();
      }
      if (existingExec != null) {
        removeCapability(existingExec);
      } else {
        byExecution.remove(executionId);
      }
    }

    Instant expiresAt = now.plus(ttl);

    for (int attempt = 0; attempt < MAX_NONCE_GENERATION_ATTEMPTS; attempt++) {
      String nonce = generateNonce();
      ApprovalCapability existingNonceCap = byNonce.get(nonce);
      if (existingNonceCap != null) {
        if (!existingNonceCap.isExpired(now)) {
          // Collision with unexpired nonce, retry with next attempt
          continue;
        }
        removeCapability(existingNonceCap);
      }

      ApprovalCapability capability =
          new ApprovalCapability(
              nonce, executionId, gateId, initiator, initiatorIncarnation, target, expiresAt);

      if (byNonce.putIfAbsent(nonce, capability) == null) {
        byInitiator
            .computeIfAbsent(capability.initiator(), k -> new HashSet<>())
            .add(capability.nonce());
        byTarget.put(capability.target(), capability.nonce());
        byExecution.put(capability.executionId(), capability.nonce());
        return Optional.of(capability);
      }
    }

    // Collision exhaustion: fail closed without overwriting
    return Optional.empty();
  }

  /**
   * Registers a pre-constructed capability.
   *
   * <p>Rejects pre-constructed duplicates if the nonce is already in use, or if the
   * target/execution is already busy with an active capability.
   *
   * @param capability the capability to register
   * @return true if registered, false if nonce collision or target/execution is busy with another
   *     active capability
   */
  public synchronized boolean registerCapability(ApprovalCapability capability) {
    Objects.requireNonNull(capability, "capability must not be null");
    Instant now = clock.instant();

    String existingTargetNonce = byTarget.get(capability.target());
    if (existingTargetNonce != null) {
      ApprovalCapability existing = byNonce.get(existingTargetNonce);
      if (existing != null && !existing.isExpired(now)) {
        return false;
      }
      if (existing != null) {
        removeCapability(existing);
      } else {
        byTarget.remove(capability.target());
      }
    }

    String existingExecNonce = byExecution.get(capability.executionId());
    if (existingExecNonce != null) {
      ApprovalCapability existingExec = byNonce.get(existingExecNonce);
      if (existingExec != null && !existingExec.isExpired(now)) {
        return false;
      }
      if (existingExec != null) {
        removeCapability(existingExec);
      } else {
        byExecution.remove(capability.executionId());
      }
    }

    ApprovalCapability existingNonceCap = byNonce.get(capability.nonce());
    if (existingNonceCap != null) {
      if (!existingNonceCap.isExpired(now)) {
        return false;
      }
      removeCapability(existingNonceCap);
    }

    if (byNonce.putIfAbsent(capability.nonce(), capability) != null) {
      return false;
    }

    byInitiator
        .computeIfAbsent(capability.initiator(), k -> new HashSet<>())
        .add(capability.nonce());
    byTarget.put(capability.target(), capability.nonce());
    byExecution.put(capability.executionId(), capability.nonce());
    return true;
  }

  private void removeCapability(ApprovalCapability capability) {
    byNonce.remove(capability.nonce());

    Set<String> initSet = byInitiator.get(capability.initiator());
    if (initSet != null) {
      initSet.remove(capability.nonce());
      if (initSet.isEmpty()) {
        byInitiator.remove(capability.initiator());
      }
    }

    String currentTargetNonce = byTarget.get(capability.target());
    if (Objects.equals(currentTargetNonce, capability.nonce())) {
      byTarget.remove(capability.target());
    }

    String currentExecNonce = byExecution.get(capability.executionId());
    if (Objects.equals(currentExecNonce, capability.nonce())) {
      byExecution.remove(capability.executionId());
    }
  }

  /**
   * Atomically validates and consumes an approval capability.
   *
   * <p>Validates that the nonce exists, has not expired, and that {@code responderTarget} matches
   * the capability's bound target. If responder does not match, the capability is <b>not</b>
   * consumed or removed.
   *
   * @param nonce the raw nonce token
   * @param responderTarget the UUID of the player attempting to respond
   * @return optional containing the consumed capability if valid and consumed, or empty otherwise
   */
  public synchronized Optional<ApprovalCapability> consume(String nonce, UUID responderTarget) {
    if (nonce == null || responderTarget == null) {
      return Optional.empty();
    }
    ApprovalCapability capability = byNonce.get(nonce);
    if (capability == null) {
      return Optional.empty();
    }
    Instant now = clock.instant();
    if (capability.isExpired(now)) {
      removeCapability(capability);
      return Optional.empty();
    }
    if (!capability.matchesResponder(responderTarget)) {
      // Responder mismatch: do NOT consume or remove
      return Optional.empty();
    }
    removeCapability(capability);
    return Optional.of(capability);
  }

  /** Looks up a capability by its nonce without consuming it. */
  public synchronized Optional<ApprovalCapability> getByNonce(String nonce) {
    if (nonce == null) {
      return Optional.empty();
    }
    ApprovalCapability capability = byNonce.get(nonce);
    if (capability != null) {
      if (capability.isExpired(clock.instant())) {
        removeCapability(capability);
        return Optional.empty();
      }
      return Optional.of(capability);
    }
    return Optional.empty();
  }

  /** Looks up the active capability for a target approver. */
  public synchronized Optional<ApprovalCapability> getByTarget(UUID target) {
    if (target == null) {
      return Optional.empty();
    }
    String nonce = byTarget.get(target);
    if (nonce != null) {
      return getByNonce(nonce);
    }
    return Optional.empty();
  }

  /** Looks up the active capability for an execution ID. */
  public synchronized Optional<ApprovalCapability> getByExecution(ExecutionId executionId) {
    if (executionId == null) {
      return Optional.empty();
    }
    String nonce = byExecution.get(executionId);
    if (nonce != null) {
      return getByNonce(nonce);
    }
    return Optional.empty();
  }

  /** Checks whether a target approver currently has an active pending capability. */
  public synchronized boolean isTargetBusy(UUID target) {
    return getByTarget(target).isPresent();
  }

  /**
   * Invalidates a specific capability by nonce.
   *
   * @param nonce nonce token
   * @return true if found and removed
   */
  public synchronized boolean invalidateNonce(String nonce) {
    if (nonce == null) {
      return false;
    }
    ApprovalCapability cap = byNonce.get(nonce);
    if (cap != null) {
      removeCapability(cap);
      return true;
    }
    return false;
  }

  /**
   * Invalidates any active capability for a target approver.
   *
   * @param target target UUID
   * @return optional containing the removed capability
   */
  public synchronized Optional<ApprovalCapability> invalidateTarget(UUID target) {
    if (target == null) {
      return Optional.empty();
    }
    String nonce = byTarget.get(target);
    if (nonce != null) {
      ApprovalCapability cap = byNonce.get(nonce);
      if (cap != null) {
        removeCapability(cap);
        return Optional.of(cap);
      }
      byTarget.remove(target);
    }
    return Optional.empty();
  }

  /**
   * Invalidates any active capability for an execution ID.
   *
   * @param executionId execution ID
   * @return optional containing the removed capability
   */
  public synchronized Optional<ApprovalCapability> invalidateExecution(ExecutionId executionId) {
    if (executionId == null) {
      return Optional.empty();
    }
    String nonce = byExecution.get(executionId);
    if (nonce != null) {
      ApprovalCapability cap = byNonce.get(nonce);
      if (cap != null) {
        removeCapability(cap);
        return Optional.of(cap);
      }
      byExecution.remove(executionId);
    }
    return Optional.empty();
  }

  /**
   * Invalidates all capabilities initiated by the given initiator.
   *
   * @param initiator initiator UUID
   * @return list of invalidated capabilities
   */
  public synchronized List<ApprovalCapability> invalidateInitiator(UUID initiator) {
    if (initiator == null) {
      return List.of();
    }
    Set<String> nonces = byInitiator.get(initiator);
    if (nonces == null || nonces.isEmpty()) {
      return List.of();
    }
    List<ApprovalCapability> removed = new ArrayList<>();
    for (String nonce : new HashSet<>(nonces)) {
      ApprovalCapability cap = byNonce.get(nonce);
      if (cap != null) {
        removeCapability(cap);
        removed.add(cap);
      }
    }
    return List.copyOf(removed);
  }

  /**
   * Invalidates all capabilities initiated by the given initiator and session incarnation.
   *
   * @param initiator initiator UUID
   * @param incarnation initiator incarnation
   * @return list of invalidated capabilities
   */
  public synchronized List<ApprovalCapability> invalidateInitiator(
      UUID initiator, long incarnation) {
    if (initiator == null) {
      return List.of();
    }
    Set<String> nonces = byInitiator.get(initiator);
    if (nonces == null || nonces.isEmpty()) {
      return List.of();
    }
    List<ApprovalCapability> removed = new ArrayList<>();
    for (String nonce : new HashSet<>(nonces)) {
      ApprovalCapability cap = byNonce.get(nonce);
      if (cap != null && cap.initiatorIncarnation() == incarnation) {
        removeCapability(cap);
        removed.add(cap);
      }
    }
    return List.copyOf(removed);
  }

  /**
   * Prunes all expired capabilities from all indexes.
   *
   * @return number of expired capabilities removed
   */
  public synchronized int cleanExpired() {
    Instant now = clock.instant();
    List<ApprovalCapability> expired = new ArrayList<>();
    for (ApprovalCapability cap : byNonce.values()) {
      if (cap.isExpired(now)) {
        expired.add(cap);
      }
    }
    for (ApprovalCapability cap : expired) {
      removeCapability(cap);
    }
    return expired.size();
  }

  /** Clears all capabilities from all indexes. */
  public synchronized void clear() {
    byNonce.clear();
    byInitiator.clear();
    byTarget.clear();
    byExecution.clear();
  }

  /** Returns the count of active capabilities in the registry after pruning expired ones. */
  public synchronized int size() {
    cleanExpired();
    return byNonce.size();
  }

  @Override
  public String toString() {
    return "ApprovalCapabilityRegistry[activeCount=" + size() + "]";
  }
}
