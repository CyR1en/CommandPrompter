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

  private static final ThreadLocal<PlayerCommandPreprocessEvent> CURRENT = new ThreadLocal<>();

  private CommandEventBridge() {}

  /**
   * True only for the event the bridge is firing, so the prompt listener can skip it. Any other
   * command event raised by a listener during that call is still treated normally.
   */
  public static boolean isBridgeEvent(PlayerCommandPreprocessEvent event) {
    return event != null && CURRENT.get() == event;
  }

  /**
   * Fires the event and returns the command to run (without the leading slash), or null when a
   * listener cancelled it.
   */
  public static String fire(Player player, String command) {
    PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/" + command);
    PlayerCommandPreprocessEvent previous = CURRENT.get();
    CURRENT.set(event);
    try {
      Bukkit.getPluginManager().callEvent(event);
    } finally {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
    if (event.isCancelled()) {
      return null;
    }
    return DispatchSanitizer.stripLeadingSlash(event.getMessage());
  }
}
