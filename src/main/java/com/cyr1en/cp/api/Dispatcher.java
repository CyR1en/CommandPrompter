package com.cyr1en.cp.api;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Player command dispatcher for Support with CommandPrompter.
 *
 * <p>Because CommandPrompter cannot catch commands that were dispatched from
 * {@link org.bukkit.Bukkit#dispatchCommand(CommandSender, String)}, plugins
 * need a special way to execute player commands.</p>
 */
public class Dispatcher {

  /**
   * Dispatches command by forcing a player to chat the command.
   * This will allow plugins to support CommandPrompter.
   *
   * @param plugin Instance of plugin.
   * @param sender command sender (in menu's, then the item clicker)
   * @param command command that would be dispatched.
   */
  public static void dispatchCommand(Plugin plugin, Player sender, String command) {
    final String checked = command.codePointAt(0) == 0x2F ? command : "/" + command;
    new BukkitRunnable() {
      public void run() {
        sender.chat(checked);
      }
    }.runTask(plugin);
  }
}
