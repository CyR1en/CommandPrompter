package dev.cyr1en.promptpaper.execution.postaction;

/**
 * Callback invoked upon completion of post-actions execution. Guaranteed to be invoked exactly
 * once.
 */
@FunctionalInterface
public interface PostActionCallback {

  /**
   * Invoked when post-action execution finishes, reporting success or failure.
   *
   * @param result the typed execution result
   */
  void onComplete(PostActionResult result);
}
