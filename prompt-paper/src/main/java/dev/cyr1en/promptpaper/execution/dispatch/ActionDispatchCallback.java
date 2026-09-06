package dev.cyr1en.promptpaper.execution.dispatch;

/**
 * Callback invoked upon completion of an immediate action dispatch. May report outcomes from any
 * thread.
 */
@FunctionalInterface
public interface ActionDispatchCallback {

  /**
   * Invoked when action dispatch completes, either successfully or with a typed error.
   *
   * @param outcome the typed dispatch outcome
   */
  void onComplete(DispatchOutcome outcome);
}
