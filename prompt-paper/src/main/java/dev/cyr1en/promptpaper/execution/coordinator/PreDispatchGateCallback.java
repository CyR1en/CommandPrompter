package dev.cyr1en.promptpaper.execution.coordinator;

/**
 * Completion callback for asynchronous pre-dispatch gate evaluations.
 */
@FunctionalInterface
public interface PreDispatchGateCallback {

  /**
   * Invoked when gate evaluation completes.
   *
   * @param result the typed evaluation result
   */
  void onResult(PreDispatchGateResult result);
}
