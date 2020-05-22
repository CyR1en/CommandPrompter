/*
 * MIT License
 *
 * Copyright (c) 2020 Ethan Bacurio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.cyr1en.cp.api;

import org.bukkit.Bukkit;
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

  /**
   * Dispatch the command as op
   *
   * @param plugin Instance of plugin.
   * @param sender command sender (in menu's, then the item clicker)
   * @param command command that would be dispatched.
   */
  public static void dispathcOP(Plugin plugin, Player sender, String command){
    Bukkit.dispatchCommand(sender, command);
  }
}
