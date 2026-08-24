package dev.cyr1en.promptpaper.execution.postaction;

import dev.cyr1en.promptpaper.util.CancellableTask;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Seam for scheduling delayed post-actions on a player's Folia-safe entity scheduler.
 */
@FunctionalInterface
public interface PostActionScheduler {

    /**
     * Schedules a task to run after the specified tick delay on the target player's entity scheduler.
     *
     * @param player the initiator player
     * @param task the task to execute after delay
     * @param retired callback invoked if the player's scheduler is retired or unavailable
     * @param delayTicks delay in server ticks (0..72000)
     * @return a {@link CancellableTask} handle to cancel the scheduled task
     */
    CancellableTask scheduleDelayed(Player player, Runnable task, Runnable retired, long delayTicks);

    /**
     * Creates a production {@link PostActionScheduler} backed by Paper's entity scheduler.
     *
     * @param plugin the owning plugin
     * @return a PostActionScheduler instance
     */
    static PostActionScheduler forPlugin(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin must not be null");
        return (player, task, retired, delayTicks) -> {
            if (player == null) {
                if (retired != null) {
                    retired.run();
                }
                return () -> {};
            }
            try {
                var scheduledTask = player.getScheduler().runDelayed(
                        plugin,
                        st -> task.run(),
                        retired,
                        delayTicks
                );
                if (scheduledTask == null) {
                    if (retired != null) {
                        retired.run();
                    }
                    return () -> {};
                }
                return scheduledTask::cancel;
            } catch (Throwable t) {
                if (retired != null) {
                    retired.run();
                }
                return () -> {};
            }
        };
    }
}
