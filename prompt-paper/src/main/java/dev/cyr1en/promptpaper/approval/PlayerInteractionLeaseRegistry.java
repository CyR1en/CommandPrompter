package dev.cyr1en.promptpaper.approval;

import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Thread-safe registry managing exclusive player interaction claims and leases.
 *
 * <p>Enforces collision rejection across approval executions and prompt sessions, and exact
 * compare-and-release by owner (execution ID or prompt incarnation).
 */
public final class PlayerInteractionLeaseRegistry {

  private final Clock clock;
  private final Map<UUID, PlayerInteractionLease> leases = new HashMap<>();

  public PlayerInteractionLeaseRegistry() {
    this(Clock.systemUTC());
  }

  public PlayerInteractionLeaseRegistry(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Attempts to acquire an exclusive interaction lease on a player for the specified execution.
   *
   * @param player the player UUID to lease
   * @param executionId the execution acquiring the lease
   * @param ttl lease duration
   * @return optional containing the lease if acquired, or empty if rejected due to collision
   */
  public synchronized Optional<PlayerInteractionLease> acquire(
      UUID player, ExecutionId executionId, Duration ttl) {
    Objects.requireNonNull(player, "player must not be null");
    Objects.requireNonNull(executionId, "executionId must not be null");
    Objects.requireNonNull(ttl, "ttl must not be null");
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("ttl must be positive");
    }

    Instant now = clock.instant();
    PlayerInteractionLease existing = leases.get(player);
    if (existing != null) {
      if (!existing.isExpired(now)) {
        // Active lease exists
        if (!existing.isApproval() || !existing.executionId().equals(executionId)) {
          // Held by different execution or prompt claim -> collision rejection!
          return Optional.empty();
        }
      }
    }

    PlayerInteractionLease lease =
        PlayerInteractionLease.forApproval(player, executionId, now, now.plus(ttl));
    leases.put(player, lease);
    return Optional.of(lease);
  }

  /**
   * Attempts to acquire an exclusive prompt session claim on a player for the specified session
   * incarnation.
   *
   * @param player the player UUID to claim
   * @param incarnation the prompt session incarnation
   * @return optional containing the claim if acquired, or empty if rejected due to collision
   */
  public synchronized Optional<PlayerInteractionLease> acquirePrompt(
      UUID player, long incarnation) {
    return acquirePrompt(player, incarnation, null);
  }

  /**
   * Attempts to acquire an exclusive prompt session claim on a player for the specified session
   * incarnation with a TTL.
   *
   * @param player the player UUID to claim
   * @param incarnation the prompt session incarnation
   * @param ttl optional lease duration (or null for indefinite until released)
   * @return optional containing the claim if acquired, or empty if rejected due to collision
   */
  public synchronized Optional<PlayerInteractionLease> acquirePrompt(
      UUID player, long incarnation, Duration ttl) {
    Objects.requireNonNull(player, "player must not be null");
    if (ttl != null && (ttl.isNegative() || ttl.isZero())) {
      throw new IllegalArgumentException("ttl must be positive");
    }

    Instant now = clock.instant();
    PlayerInteractionLease existing = leases.get(player);
    if (existing != null) {
      if (!existing.isExpired(now)) {
        // Active lease exists
        if (!existing.isPrompt() || !Objects.equals(existing.promptIncarnation(), incarnation)) {
          // Held by different prompt incarnation or approval execution -> collision rejection!
          return Optional.empty();
        }
        return Optional.of(existing);
      }
    }

    Instant expiresAt = ttl != null ? now.plus(ttl) : null;
    PlayerInteractionLease lease =
        PlayerInteractionLease.forPrompt(player, incarnation, now, expiresAt);
    leases.put(player, lease);
    return Optional.of(lease);
  }

  /**
   * Compares and releases a player's approval lease if held by the exact execution ID.
   *
   * @param player the leased player UUID
   * @param executionId the expected execution ID
   * @return true if the lease was held by this execution ID and removed, false otherwise
   */
  public synchronized boolean releaseIfExact(UUID player, ExecutionId executionId) {
    if (player == null || executionId == null) {
      return false;
    }
    PlayerInteractionLease existing = leases.get(player);
    if (existing != null
        && existing.isApproval()
        && Objects.equals(existing.executionId(), executionId)) {
      leases.remove(player);
      return true;
    }
    return false;
  }

  /**
   * Compares and releases a player's prompt claim if held by the exact session incarnation.
   *
   * @param player the claimed player UUID
   * @param incarnation the expected session incarnation
   * @return true if the prompt claim was held by this incarnation and removed, false otherwise
   */
  public synchronized boolean releasePromptIfExact(UUID player, long incarnation) {
    if (player == null) {
      return false;
    }
    PlayerInteractionLease existing = leases.get(player);
    if (existing != null
        && existing.isPrompt()
        && Objects.equals(existing.promptIncarnation(), incarnation)) {
      leases.remove(player);
      return true;
    }
    return false;
  }

  /**
   * Releases any lease or claim held for the player.
   *
   * @param player player UUID
   * @return true if a lease was removed
   */
  public synchronized boolean releaseAllForPlayer(UUID player) {
    if (player == null) {
      return false;
    }
    return leases.remove(player) != null;
  }

  /**
   * Gets the active lease for a player, if any. Expired leases are pruned on lookup.
   *
   * @param player player UUID
   * @return optional containing active lease
   */
  public synchronized Optional<PlayerInteractionLease> getLease(UUID player) {
    if (player == null) {
      return Optional.empty();
    }
    PlayerInteractionLease existing = leases.get(player);
    if (existing != null) {
      if (existing.isExpired(clock.instant())) {
        leases.remove(player);
        return Optional.empty();
      }
      return Optional.of(existing);
    }
    return Optional.empty();
  }

  /**
   * Checks whether a player currently has an active, unexpired lease or claim.
   *
   * @param player player UUID
   * @return true if leased
   */
  public synchronized boolean isLeased(UUID player) {
    return getLease(player).isPresent();
  }

  /**
   * Releases all approval leases held by a given execution ID.
   *
   * @param executionId execution ID
   * @return number of leases released
   */
  public synchronized int releaseAllForExecution(ExecutionId executionId) {
    if (executionId == null) {
      return 0;
    }
    List<UUID> toRemove = new ArrayList<>();
    for (Map.Entry<UUID, PlayerInteractionLease> entry : leases.entrySet()) {
      PlayerInteractionLease lease = entry.getValue();
      if (lease.isApproval() && executionId.equals(lease.executionId())) {
        toRemove.add(entry.getKey());
      }
    }
    for (UUID player : toRemove) {
      leases.remove(player);
    }
    return toRemove.size();
  }

  /**
   * Prunes all expired leases from the registry.
   *
   * @return count of pruned leases
   */
  public synchronized int cleanExpired() {
    Instant now = clock.instant();
    List<UUID> expired = new ArrayList<>();
    for (Map.Entry<UUID, PlayerInteractionLease> entry : leases.entrySet()) {
      if (entry.getValue().isExpired(now)) {
        expired.add(entry.getKey());
      }
    }
    for (UUID player : expired) {
      leases.remove(player);
    }
    return expired.size();
  }

  /** Clears all leases. */
  public synchronized void clear() {
    leases.clear();
  }

  /** Returns current active count (after pruning expired). */
  public synchronized int size() {
    cleanExpired();
    return leases.size();
  }

  @Override
  public String toString() {
    return "PlayerInteractionLeaseRegistry[activeCount=" + size() + "]";
  }
}
