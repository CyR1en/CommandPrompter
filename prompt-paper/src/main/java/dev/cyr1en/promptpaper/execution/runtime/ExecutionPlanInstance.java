package dev.cyr1en.promptpaper.execution.runtime;

import dev.cyr1en.promptpaper.util.CancellableTask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduler-owned active instance of an executing command plan.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Strict, legal stage transitions.
 *   <li>Atomic single primary execution claim.
 *   <li>Caller verification (initiator UUID, incarnation, execution ID).
 *   <li>Notice / log-once flags.
 *   <li>Owned cancellable handles and terminal cleanup hooks executed idempotently.
 *   <li>No live Bukkit Player references retained.
 * </ul>
 */
public class ExecutionPlanInstance {

  private final ExecutionId executionId;
  private final InputCompletion inputCompletion;
  private final long createdAtMillis;

  private final Object lock = new Object();
  private ExecutionStage stage;
  private boolean primaryClaimed;
  private boolean cleanedUp;
  private final Set<NoticeFlag> notices;

  private final List<CancellableTask> cancellableTasks;
  private final List<Runnable> cleanupHooks;

  /**
   * Constructs a new ExecutionPlanInstance starting in {@link ExecutionStage#PRE_DISPATCH_GATES}.
   *
   * @param executionId unique execution ID
   * @param inputCompletion immutable input completion record
   */
  public ExecutionPlanInstance(ExecutionId executionId, InputCompletion inputCompletion) {
    this(executionId, inputCompletion, ExecutionStage.PRE_DISPATCH_GATES);
  }

  /**
   * Constructs a new ExecutionPlanInstance starting in the specified stage.
   *
   * @param executionId unique execution ID
   * @param inputCompletion immutable input completion record
   * @param initialStage starting stage
   */
  public ExecutionPlanInstance(
      ExecutionId executionId, InputCompletion inputCompletion, ExecutionStage initialStage) {
    this.executionId = Objects.requireNonNull(executionId, "executionId must not be null");
    this.inputCompletion =
        Objects.requireNonNull(inputCompletion, "inputCompletion must not be null");
    Objects.requireNonNull(initialStage, "initialStage must not be null");

    this.createdAtMillis = System.currentTimeMillis();
    this.stage = initialStage;
    this.primaryClaimed = false;
    this.cleanedUp = false;
    this.notices = Collections.synchronizedSet(EnumSet.noneOf(NoticeFlag.class));
    this.cancellableTasks = new ArrayList<>();
    this.cleanupHooks = new ArrayList<>();

    if (initialStage.isTerminal()) {
      runTerminalCleanup();
    }
  }

  public ExecutionId getExecutionId() {
    return executionId;
  }

  public InputCompletion getInputCompletion() {
    return inputCompletion;
  }

  public UUID getInitiatorUuid() {
    return inputCompletion.initiatorUuid();
  }

  public long getIncarnation() {
    return inputCompletion.incarnation();
  }

  public long getCreatedAtMillis() {
    return createdAtMillis;
  }

  public ExecutionStage getStage() {
    synchronized (lock) {
      return stage;
    }
  }

  public boolean isTerminal() {
    synchronized (lock) {
      return stage.isTerminal();
    }
  }

  /**
   * Attempts an atomic stage transition to {@code target}.
   *
   * @param target target stage
   * @return {@code true} if the transition succeeded; {@code false} if the current stage is
   *     terminal or if the transition is illegal
   */
  public boolean tryTransitionTo(ExecutionStage target) {
    Objects.requireNonNull(target, "target stage must not be null");
    List<CancellableTask> tasksToCancel = null;
    List<Runnable> hooksToRun = null;
    synchronized (lock) {
      if (stage.isTerminal() || !stage.canTransitionTo(target)) {
        return false;
      }
      stage = target;
      if (target.isTerminal() && !cleanedUp) {
        cleanedUp = true;
        tasksToCancel = new ArrayList<>(cancellableTasks);
        cancellableTasks.clear();
        hooksToRun = new ArrayList<>(cleanupHooks);
        cleanupHooks.clear();
      }
    }
    if (tasksToCancel != null) {
      executeCleanup(tasksToCancel, hooksToRun);
    }
    return true;
  }

  /**
   * Enforces a legal stage transition to {@code target}.
   *
   * @param target target stage
   * @throws IllegalStateException if the transition is illegal or if already in a terminal stage
   */
  public void transitionTo(ExecutionStage target) {
    Objects.requireNonNull(target, "target stage must not be null");
    List<CancellableTask> tasksToCancel = null;
    List<Runnable> hooksToRun = null;
    synchronized (lock) {
      if (stage.isTerminal()) {
        throw new IllegalStateException(
            "Cannot transition from terminal stage " + stage + " to " + target);
      }
      if (!stage.canTransitionTo(target)) {
        throw new IllegalStateException(
            "Illegal stage transition from " + stage + " to " + target);
      }
      stage = target;
      if (target.isTerminal() && !cleanedUp) {
        cleanedUp = true;
        tasksToCancel = new ArrayList<>(cancellableTasks);
        cancellableTasks.clear();
        hooksToRun = new ArrayList<>(cleanupHooks);
        cleanupHooks.clear();
      }
    }
    if (tasksToCancel != null) {
      executeCleanup(tasksToCancel, hooksToRun);
    }
  }

  /**
   * Atomically claims permission to execute the primary command exactly once.
   *
   * @return {@code true} if this caller successfully claimed the primary execution; {@code false}
   *     if already claimed, or if the stage is terminal or post-actions
   */
  public boolean claimPrimaryExecution() {
    synchronized (lock) {
      if (stage.isTerminal() || stage == ExecutionStage.POST_ACTIONS || primaryClaimed) {
        return false;
      }
      primaryClaimed = true;
      return true;
    }
  }

  /** Returns {@code true} if the primary command has already been claimed. */
  public boolean isPrimaryClaimed() {
    synchronized (lock) {
      return primaryClaimed;
    }
  }

  /**
   * Verifies that the caller's expected initiator UUID, incarnation, and execution ID match this
   * instance.
   *
   * @param expectedInitiator expected initiator UUID
   * @param expectedIncarnation expected incarnation counter
   * @param expectedExecutionId expected execution ID
   * @return true if all identifiers match
   */
  public boolean verify(
      UUID expectedInitiator, long expectedIncarnation, ExecutionId expectedExecutionId) {
    return Objects.equals(this.inputCompletion.initiatorUuid(), expectedInitiator)
        && this.inputCompletion.incarnation() == expectedIncarnation
        && Objects.equals(this.executionId, expectedExecutionId);
  }

  /**
   * Verifies that the caller matches initiator UUID and incarnation.
   *
   * @param expectedInitiator expected initiator UUID
   * @param expectedIncarnation expected incarnation counter
   * @return true if matches
   */
  public boolean verifyCaller(UUID expectedInitiator, long expectedIncarnation) {
    return Objects.equals(this.inputCompletion.initiatorUuid(), expectedInitiator)
        && this.inputCompletion.incarnation() == expectedIncarnation;
  }

  /**
   * Records a notice flag. Returns {@code true} if this is the first time the flag has been
   * recorded for this instance (log-once semantics).
   *
   * @param flag notice flag to mark
   * @return true if newly added, false if previously marked
   */
  public boolean recordNotice(NoticeFlag flag) {
    Objects.requireNonNull(flag, "flag must not be null");
    return notices.add(flag);
  }

  /** Checks if a notice flag has been recorded. */
  public boolean hasNotice(NoticeFlag flag) {
    if (flag == null) return false;
    return notices.contains(flag);
  }

  /** Returns an immutable snapshot of all recorded notice flags. */
  public Set<NoticeFlag> getNotices() {
    synchronized (notices) {
      return Set.copyOf(notices);
    }
  }

  /**
   * Registers a scheduled cancellable task owned by this plan instance.
   *
   * @param task cancellable task handle
   */
  public void registerCancellable(CancellableTask task) {
    if (task == null) return;
    boolean cancelNow = false;
    synchronized (lock) {
      if (cleanedUp || stage.isTerminal()) {
        cancelNow = true;
      } else {
        cancellableTasks.add(task);
      }
    }
    if (cancelNow) {
      try {
        task.cancel();
      } catch (Throwable ignored) {
      }
    }
  }

  /**
   * Registers a terminal cleanup hook to be executed when this plan terminates.
   *
   * @param hook cleanup hook
   */
  public void registerCleanupHook(Runnable hook) {
    if (hook == null) return;
    boolean runNow = false;
    synchronized (lock) {
      if (cleanedUp || stage.isTerminal()) {
        runNow = true;
      } else {
        cleanupHooks.add(hook);
      }
    }
    if (runNow) {
      try {
        hook.run();
      } catch (Throwable ignored) {
      }
    }
  }

  /**
   * Cancels this plan instance idempotently, transitioning to {@link ExecutionStage#CANCELLED} and
   * running terminal cleanup.
   *
   * @return true if this call changed the state to CANCELLED, false if already terminal
   */
  public boolean cancel() {
    return tryTransitionTo(ExecutionStage.CANCELLED);
  }

  /**
   * Internal terminal cleanup routine executed exactly once when entering any terminal stage.
   */
  void runTerminalCleanup() {
    List<CancellableTask> tasksToCancel = null;
    List<Runnable> hooksToRun = null;
    synchronized (lock) {
      if (!cleanedUp) {
        cleanedUp = true;
        tasksToCancel = new ArrayList<>(cancellableTasks);
        cancellableTasks.clear();
        hooksToRun = new ArrayList<>(cleanupHooks);
        cleanupHooks.clear();
      }
    }
    if (tasksToCancel != null) {
      executeCleanup(tasksToCancel, hooksToRun);
    }
  }

  private void executeCleanup(List<CancellableTask> tasks, List<Runnable> hooks) {
    for (CancellableTask task : tasks) {
      try {
        task.cancel();
      } catch (Throwable ignored) {
      }
    }
    for (Runnable hook : hooks) {
      try {
        hook.run();
      } catch (Throwable ignored) {
      }
    }
  }

  /** Query for cleanup state in unit tests. */
  public boolean isCleanedUp() {
    synchronized (lock) {
      return cleanedUp;
    }
  }
}
