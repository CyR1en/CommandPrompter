package dev.cyr1en.promptpaper.execution.dispatch;

/** Asynchronous / scheduler-safe dispatcher for primary prompt commands. */
public interface PrimaryCommandDispatcher {

  /**
   * Dispatches a primary command according to the request parameters. The callback is guaranteed to
   * be invoked exactly once, and may report outcomes from any thread.
   *
   * @param request the dispatch parameters
   * @param callback the completion callback invoked exactly once with the typed outcome
   */
  void dispatch(PrimaryDispatchRequest request, PrimaryDispatchCallback callback);
}
