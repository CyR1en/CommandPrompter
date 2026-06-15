package dev.cyr1en.promptpaper.util;

import dev.cyr1en.promptpaper.CommandPrompter;
import org.bukkit.Bukkit;

/**
 * Folia-safe {@link Scheduler} implementation that delegates to
 * {@link Bukkit#getGlobalRegionScheduler()} for sync tasks and
 * {@link Bukkit#getAsyncScheduler()} for async tasks.
 */
public final class PaperScheduler implements Scheduler {

    private final CommandPrompter plugin;

    public PaperScheduler(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    /** Runs the task on Folia's global region scheduler (main thread). */
    @Override
    public void runSync(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    /** Runs the task immediately on the async scheduler (off the main thread). */
    @Override
    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    /** Schedules the task to run after the given delay on the global region scheduler. */
    @Override
    public CancellableTask runLater(Runnable task, long delayTicks) {
        var scheduledTask = Bukkit.getGlobalRegionScheduler()
                .runDelayed(plugin, st -> task.run(), delayTicks);
        return scheduledTask::cancel;
    }
}
