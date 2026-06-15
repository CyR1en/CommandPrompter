package dev.cyr1en.promptpaper.util;

/**
 * Handle returned by {@link Scheduler#runLater} that allows a scheduled
 * task to be cancelled before it executes.
 */
public interface CancellableTask {
    /** Cancels the pending task. */
    void cancel();
}
