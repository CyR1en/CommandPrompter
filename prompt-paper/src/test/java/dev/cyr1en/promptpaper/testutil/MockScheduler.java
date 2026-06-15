package dev.cyr1en.promptpaper.testutil;

import dev.cyr1en.promptpaper.util.CancellableTask;
import dev.cyr1en.promptpaper.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class MockScheduler implements Scheduler {

    private final Plugin plugin;

    public MockScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runSync(Runnable task) {
        task.run();
    }

    @Override
    public void runAsync(Runnable task) {
        // In tests, run immediately (synchronously) for deterministic execution.
        // Real async behavior would complicate test assertions.
        task.run();
    }

    @Override
    public CancellableTask runLater(Runnable task, long delayTicks) {
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, task, delayTicks);
        return () -> Bukkit.getScheduler().cancelTask(taskId);
    }
}
