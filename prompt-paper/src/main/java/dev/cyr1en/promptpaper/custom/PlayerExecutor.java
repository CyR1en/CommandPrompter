package dev.cyr1en.promptpaper.custom;

import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Narrow execution abstraction to schedule work on a target player's Folia-safe entity scheduler.
 */
@FunctionalInterface
public interface PlayerExecutor {

  /**
   * Executes or queues work on the target player's entity scheduler.
   *
   * @param task the work to execute on the player entity thread
   * @param retired callback invoked if the player's scheduler is retired / unable to run the task
   */
  void execute(Runnable task, Runnable retired);

  /**
   * Convenience method to execute work without a specific retired callback.
   *
   * @param task the work to execute on the player entity thread
   */
  default void execute(Runnable task) {
    execute(task, null);
  }

  /**
   * Checks whether the current thread owns the given player's entity/region context using Paper's
   * {@link Bukkit#isOwnedByCurrentRegion(org.bukkit.entity.Entity)} API.
   *
   * @param player the player to check
   * @return true if the current thread owns the player's region; false otherwise
   */
  static boolean isCurrentThreadOwningPlayer(Player player) {
    if (player == null) {
      return false;
    }
    try {
      return Bukkit.isOwnedByCurrentRegion(player);
    } catch (Throwable ignored) {
      return false;
    }
  }

  /**
   * Creates a production {@link PlayerExecutor} backed by Paper's Folia-safe entity scheduler and
   * regional ownership checks.
   *
   * <p>If the current thread already owns the target player's region context according to Paper's
   * {@link Bukkit#isOwnedByCurrentRegion(org.bukkit.entity.Entity)}, the task is executed inline
   * synchronously to avoid TOCTOU races. Otherwise, the task is queued on the player's entity
   * scheduler with retirement handling.
   *
   * @param plugin the plugin owning the task
   * @param player the target player
   * @return a PlayerExecutor instance
   */
  static PlayerExecutor forPlayer(Plugin plugin, Player player) {
    return forPlayer(plugin, player, PlayerExecutor::isCurrentThreadOwningPlayer);
  }

  /**
   * Creates a {@link PlayerExecutor} with a custom ownership predicate, primarily for testing or
   * custom regional routing.
   *
   * @param plugin the plugin owning the task
   * @param player the target player
   * @param ownershipPredicate predicate returning true if the current thread owns the player
   *     context
   * @return a PlayerExecutor instance
   */
  static PlayerExecutor forPlayer(
      Plugin plugin, Player player, Predicate<Player> ownershipPredicate) {
    return (task, retired) -> {
      if (player == null) {
        if (retired != null) {
          retired.run();
        }
        return;
      }

      boolean owned = false;
      try {
        if (ownershipPredicate != null && ownershipPredicate.test(player)) {
          owned = true;
        }
      } catch (Throwable ignored) {
        owned = false;
      }

      if (owned) {
        task.run();
        return;
      }

      try {
        var scheduledTask =
            player
                .getScheduler()
                .run(plugin, st -> task.run(), retired != null ? retired : () -> {});
        if (scheduledTask == null && retired != null) {
          retired.run();
        }
      } catch (Throwable t) {
        if (retired != null) {
          retired.run();
        }
      }
    };
  }
}
