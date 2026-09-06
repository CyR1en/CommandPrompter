package dev.cyr1en.promptpaper.execution.dispatch;

/**
 * Dispatcher for immediate actions (player or console), verifying trust provenance and console
 * privileges.
 */
public interface ImmediateActionDispatcher {

  /**
   * Dispatches an immediate action according to request provenance and authorization rules. The
   * callback is guaranteed to be invoked exactly once, and may report outcomes from any thread.
   *
   * @param request the action request parameters
   * @param callback the completion callback invoked exactly once with the typed outcome
   */
  void dispatch(ImmediateActionRequest request, ActionDispatchCallback callback);
}
