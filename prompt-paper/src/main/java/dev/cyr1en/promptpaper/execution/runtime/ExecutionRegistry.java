package dev.cyr1en.promptpaper.execution.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe registry for managing active {@link ExecutionPlanInstance}s.
 *
 * <p>Indexed by initiator {@link UUID} and unique {@link ExecutionId}.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Rejects concurrent execution for the same initiator UUID.
 *   <li>Compare-remove by exact {@link ExecutionId} preventing accidental removal of newer
 *       executions.
 *   <li>Idempotent cancellation and removal.
 *   <li>Strict caller verification before exposing active instances for callback execution.
 * </ul>
 */
public class ExecutionRegistry {

  private final ConcurrentMap<UUID, ExecutionPlanInstance> byInitiator;
  private final ConcurrentMap<ExecutionId, ExecutionPlanInstance> byExecutionId;
  private final Object initiatorLock;

  public ExecutionRegistry() {
    this.byInitiator = new ConcurrentHashMap<>();
    this.byExecutionId = new ConcurrentHashMap<>();
    this.initiatorLock = new Object();
  }

  /**
   * Result of an attempt to register an {@link ExecutionPlanInstance}.
   */
  public sealed interface RegistrationResult {
    record Success(ExecutionPlanInstance instance) implements RegistrationResult {}

    record RejectedAlreadyActive(ExecutionPlanInstance existingActiveInstance)
        implements RegistrationResult {}

    record RejectedTerminal(ExecutionPlanInstance instance) implements RegistrationResult {}
  }

  /**
   * Attempts to register a new {@link ExecutionPlanInstance}.
   *
   * <p>Fails and returns {@link RegistrationResult.RejectedAlreadyActive} if an active (non-terminal)
   * execution already exists for the same initiator UUID.
   *
   * @param instance execution instance to register
   * @return registration result
   */
  public RegistrationResult register(ExecutionPlanInstance instance) {
    Objects.requireNonNull(instance, "instance must not be null");
    if (instance.isTerminal()) {
      return new RegistrationResult.RejectedTerminal(instance);
    }

    UUID initiator = instance.getInitiatorUuid();
    synchronized (initiatorLock) {
      ExecutionPlanInstance existing = byInitiator.get(initiator);
      if (existing != null && !existing.isTerminal()) {
        return new RegistrationResult.RejectedAlreadyActive(existing);
      }

      // If an existing execution is present but terminal, remove its stale executionId mapping
      if (existing != null) {
        byExecutionId.remove(existing.getExecutionId());
      }

      byInitiator.put(initiator, instance);
      byExecutionId.put(instance.getExecutionId(), instance);

      // Auto cleanup hook on terminal transition
      instance.registerCleanupHook(() -> removeIfExact(initiator, instance.getExecutionId()));

      return new RegistrationResult.Success(instance);
    }
  }

  /**
   * Verifies caller identity and returns the active {@link ExecutionPlanInstance} if valid and
   * registered.
   *
   * @param executionId expected execution ID
   * @param initiator expected initiator UUID
   * @param incarnation expected incarnation counter
   * @return optional containing the verified instance, or empty if invalid / terminal / not found
   */
  public Optional<ExecutionPlanInstance> verifyAndGet(
      ExecutionId executionId, UUID initiator, long incarnation) {
    if (executionId == null || initiator == null) {
      return Optional.empty();
    }
    ExecutionPlanInstance instance = byExecutionId.get(executionId);
    if (instance == null) {
      return Optional.empty();
    }
    if (!instance.verify(initiator, incarnation, executionId)) {
      return Optional.empty();
    }
    if (instance.isTerminal()) {
      return Optional.empty();
    }
    return Optional.of(instance);
  }

  /** Looks up an instance by exact ExecutionId without caller verification. */
  public Optional<ExecutionPlanInstance> getByExecutionId(ExecutionId executionId) {
    if (executionId == null) return Optional.empty();
    return Optional.ofNullable(byExecutionId.get(executionId));
  }

  /** Looks up an active instance by initiator UUID. */
  public Optional<ExecutionPlanInstance> getByInitiator(UUID initiatorUuid) {
    if (initiatorUuid == null) return Optional.empty();
    ExecutionPlanInstance instance = byInitiator.get(initiatorUuid);
    if (instance != null && instance.isTerminal()) {
      // Lazy cleanup if found terminal
      removeIfExact(initiatorUuid, instance.getExecutionId());
      return Optional.empty();
    }
    return Optional.ofNullable(instance);
  }

  /** Checks whether an active non-terminal execution exists for the given initiator. */
  public boolean hasActiveExecution(UUID initiatorUuid) {
    return getByInitiator(initiatorUuid).isPresent();
  }

  /**
   * Compares and removes an instance by exact initiator UUID and {@link ExecutionId}.
   *
   * @param initiator initiator UUID
   * @param executionId exact execution ID to remove
   * @return true if an instance was removed
   */
  public boolean removeIfExact(UUID initiator, ExecutionId executionId) {
    if (initiator == null || executionId == null) {
      return false;
    }
    synchronized (initiatorLock) {
      ExecutionPlanInstance current = byInitiator.get(initiator);
      if (current != null && Objects.equals(current.getExecutionId(), executionId)) {
        byInitiator.remove(initiator);
        byExecutionId.remove(executionId);
        return true;
      }
      return byExecutionId.remove(executionId) != null;
    }
  }

  /**
   * Cancels and removes the execution corresponding to the given {@link ExecutionId} idempotently.
   *
   * @param executionId execution ID
   * @return true if found and cancelled/removed
   */
  public boolean cancelAndRemove(ExecutionId executionId) {
    if (executionId == null) return false;
    ExecutionPlanInstance instance = byExecutionId.get(executionId);
    if (instance == null) {
      return false;
    }
    instance.cancel();
    removeIfExact(instance.getInitiatorUuid(), executionId);
    return true;
  }

  /**
   * Cancels and removes the active execution for the given initiator UUID idempotently.
   *
   * @param initiator initiator UUID
   * @return true if found and cancelled/removed
   */
  public boolean cancelAndRemove(UUID initiator) {
    if (initiator == null) return false;
    ExecutionPlanInstance instance = byInitiator.get(initiator);
    if (instance == null) {
      return false;
    }
    instance.cancel();
    removeIfExact(initiator, instance.getExecutionId());
    return true;
  }

  /**
   * Cancels all registered executions and clears the registry.
   */
  public void cancelAll() {
    synchronized (initiatorLock) {
      for (ExecutionPlanInstance instance : byExecutionId.values()) {
        try {
          instance.cancel();
        } catch (Throwable ignored) {
        }
      }
      byInitiator.clear();
      byExecutionId.clear();
    }
  }

  /** Returns the count of currently registered execution instances. */
  public int size() {
    return byExecutionId.size();
  }

  /** Returns an unmodifiable collection snapshot of all active instances. */
  public Collection<ExecutionPlanInstance> getAllActive() {
    return List.copyOf(byExecutionId.values());
  }
}
