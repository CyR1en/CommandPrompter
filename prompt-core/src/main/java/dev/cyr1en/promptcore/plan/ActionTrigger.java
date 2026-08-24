package dev.cyr1en.promptcore.plan;

/**
 * Execution trigger for post-actions in an execution plan.
 *
 * <ul>
 *   <li>{@link #ON_SUCCESS} - action executes after successful primary command dispatch.
 *   <li>{@link #ON_CANCEL} - action executes when the session is cancelled or a gate is denied.
 * </ul>
 */
public enum ActionTrigger {
  ON_SUCCESS,
  ON_CANCEL;

  public boolean isOnSuccess() {
    return this == ON_SUCCESS;
  }

  public boolean isOnCancel() {
    return this == ON_CANCEL;
  }
}
