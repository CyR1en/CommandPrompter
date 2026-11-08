package dev.cyr1en.promptpaper.execution.dispatch;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Fires a {@link PlayerCommandPreprocessEvent} for a finished prompt command before it is
 * dispatched, so plugins that watch typed commands (alerts, logs, aliases) also see commands that
 * went through a prompt. The original event was cancelled when the prompt opened.
 */
public final class CommandEventBridge {

  private static final ThreadLocal<Boolean> FIRING = ThreadLocal.withInitial(() -> false);

  private CommandEventBridge() {}

  /** True while the bridge fires its own event, so the prompt listener can skip it. */
  public static boolean isFiring() {
    return FIRING.get();
  }

  /**
   * Fires the event and returns the command to run (without the leading slash), or null when a
   * listener cancelled it.
   */
  public static String fire(Player player, String command) {
    PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/" + command);
    FIRING.set(true);
    try {
      Bukkit.getPluginManager().callEvent(event);
    } finally {
      FIRING.set(false);
    }
    if (event.isCancelled()) {
      return null;
    }
    return DispatchSanitizer.stripLeadingSlash(event.getMessage());
  }
}
