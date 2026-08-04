package dev.cyr1en.promptpaper.testutil;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Minimal Folia entity-scheduler implementation for MockBukkit tests. */
public final class MockEntityScheduler implements EntityScheduler {

    @Override
    public boolean execute(Plugin plugin, Runnable task, Runnable retired, long delay) {
        if (delay <= 0) {
            task.run();
        } else {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, task, delay);
        }
        return true;
    }

    @Override
    public ScheduledTask run(
            Plugin plugin, Consumer<ScheduledTask> task, Runnable retired) {
        var scheduled = new MockScheduledTask(plugin);
        scheduled.state = ScheduledTask.ExecutionState.RUNNING;
        task.accept(scheduled);
        scheduled.state = ScheduledTask.ExecutionState.FINISHED;
        return scheduled;
    }

    @Override
    public ScheduledTask runDelayed(
            Plugin plugin,
            Consumer<ScheduledTask> task,
            Runnable retired,
            long delay) {
        var scheduled = new MockScheduledTask(plugin);
        scheduled.taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(
                plugin,
                () -> {
                    if (scheduled.cancelled) return;
                    scheduled.state = ScheduledTask.ExecutionState.RUNNING;
                    task.accept(scheduled);
                    scheduled.state = ScheduledTask.ExecutionState.FINISHED;
                },
                delay);
        return scheduled;
    }

    @Override
    public ScheduledTask runAtFixedRate(
            Plugin plugin,
            Consumer<ScheduledTask> task,
            Runnable retired,
            long initialDelay,
            long period) {
        var scheduled = new MockScheduledTask(plugin);
        scheduled.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                () -> {
                    if (!scheduled.cancelled) {
                        scheduled.state = ScheduledTask.ExecutionState.RUNNING;
                        task.accept(scheduled);
                        scheduled.state = ScheduledTask.ExecutionState.IDLE;
                    }
                },
                initialDelay,
                period);
        return scheduled;
    }

    private static final class MockScheduledTask implements ScheduledTask {
        private final Plugin plugin;
        private int taskId = -1;
        private boolean cancelled;
        private ExecutionState state = ExecutionState.IDLE;

        private MockScheduledTask(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public Plugin getOwningPlugin() {
            return plugin;
        }

        @Override
        public boolean isRepeatingTask() {
            return false;
        }

        @Override
        public CancelledState cancel() {
            if (cancelled) return CancelledState.CANCELLED_ALREADY;
            cancelled = true;
            if (taskId >= 0) Bukkit.getScheduler().cancelTask(taskId);
            state = ExecutionState.CANCELLED;
            return CancelledState.CANCELLED_BY_CALLER;
        }

        @Override
        public ExecutionState getExecutionState() {
            return state;
        }
    }
}
