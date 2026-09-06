package dev.cyr1en.promptpaper.execution.runtime;

import dev.cyr1en.promptcore.plan.PlanLifecycleStage;
import java.util.Objects;

/**
 * Paper-level lifecycle stages for an executing command plan.
 *
 * <p>Stages transition sequentially:
 *
 * <ol>
 *   <li>{@link #PRE_DISPATCH_GATES} - Evaluating pre-dispatch approval/condition gates.
 *   <li>{@link #PRIMARY_DISPATCH} - Executing the primary command (at most once).
 *   <li>{@link #POST_ACTIONS} - Executing immediate, delayed, and conditional post-actions.
 * </ol>
 *
 * <p>Terminal states:
 *
 * <ul>
 *   <li>{@link #COMPLETED} - All plan actions finished successfully.
 *   <li>{@link #CANCELLED} - Plan cancelled or aborted before or during execution.
 *   <li>{@link #ERROR} - Execution aborted due to an unrecoverable failure.
 * </ul>
 */
public enum ExecutionStage {
  PRE_DISPATCH_GATES,
  PRIMARY_DISPATCH,
  POST_ACTIONS,
  COMPLETED,
  CANCELLED,
  ERROR;

  /**
   * Returns {@code true} if this stage is terminal (cannot transition to any other stage).
   *
   * @return true if stage is terminal
   */
  public boolean isTerminal() {
    return this == COMPLETED || this == CANCELLED || this == ERROR;
  }

  /**
   * Validates whether a transition from this stage to {@code next} is legally permissible.
   *
   * @param next the target stage to transition to
   * @return true if legal transition
   */
  public boolean canTransitionTo(ExecutionStage next) {
    Objects.requireNonNull(next, "next stage must not be null");
    if (this.isTerminal()) {
      return false;
    }
    if (this == next) {
      return false;
    }
    return switch (this) {
      case PRE_DISPATCH_GATES ->
          next == PRIMARY_DISPATCH || next == POST_ACTIONS || next == CANCELLED || next == ERROR;
      case PRIMARY_DISPATCH ->
          next == POST_ACTIONS || next == COMPLETED || next == CANCELLED || next == ERROR;
      case POST_ACTIONS -> next == COMPLETED || next == CANCELLED || next == ERROR;
      case COMPLETED, CANCELLED, ERROR -> false;
    };
  }

  /**
   * Maps this Paper {@link ExecutionStage} to its platform-neutral {@link PlanLifecycleStage}
   * counterpart.
   *
   * @return the corresponding PlanLifecycleStage
   */
  public PlanLifecycleStage toPlanLifecycleStage() {
    return switch (this) {
      case PRE_DISPATCH_GATES -> PlanLifecycleStage.PRE_DISPATCH_GATES;
      case PRIMARY_DISPATCH -> PlanLifecycleStage.PRIMARY_DISPATCH;
      case POST_ACTIONS -> PlanLifecycleStage.POST_ACTIONS;
      case COMPLETED -> PlanLifecycleStage.TERMINATED;
      case CANCELLED, ERROR -> PlanLifecycleStage.CANCEL_ABORT;
    };
  }
}
