package dev.cyr1en.promptcore.plan;

/**
 * Platform-neutral representation of execution plan lifecycle stages.
 *
 * <p>The stage machine progresses sequentially:
 *
 * <ol>
 *   <li>{@link #INPUT_COLLECTION} - collecting answers from prompting player.
 *   <li>{@link #PRE_DISPATCH_GATES} - evaluating pre-dispatch gates (e.g. target approval).
 *   <li>{@link #PRIMARY_DISPATCH} - executing the assembled primary command (at most once).
 *   <li>{@link #POST_ACTIONS} - executing post-command actions (immediate, delayed, conditional).
 * </ol>
 *
 * <p>Terminal exit stages:
 *
 * <ul>
 *   <li>{@link #CANCEL_ABORT} - session cancelled or gate denied; primary command is never run.
 *   <li>{@link #TERMINATED} - all actions completed and plan execution is finished.
 * </ul>
 */
public enum PlanLifecycleStage {
  INPUT_COLLECTION,
  PRE_DISPATCH_GATES,
  PRIMARY_DISPATCH,
  POST_ACTIONS,
  CANCEL_ABORT,
  TERMINATED;

  /**
   * Returns {@code true} if this stage is terminal (cannot transition further).
   *
   * @return true if terminal stage
   */
  public boolean isTerminal() {
    return this == CANCEL_ABORT || this == TERMINATED;
  }
}
