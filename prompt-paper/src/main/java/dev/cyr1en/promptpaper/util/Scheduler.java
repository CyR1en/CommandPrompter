package dev.cyr1en.promptpaper.util;

/**
 * Abstraction over server task scheduling so that callers never touch
 * {@code Bukkit.getScheduler()} directly. Implementations must be
 * Folia-safe (e.g. {@link PaperScheduler}).
 */
public interface Scheduler {

    /** Runs the task on the main server thread. */
    void runSync(Runnable task);

    /**
     * Run a task asynchronously, off the main server thread.
     * Safe for I/O or heavy computation that shouldn't block the tick loop.
     */
    void runAsync(Runnable task);

    /** Schedules the task to run after the specified delay in ticks; returns a cancellable handle. */
    CancellableTask runLater(Runnable task, long delayTicks);

}
