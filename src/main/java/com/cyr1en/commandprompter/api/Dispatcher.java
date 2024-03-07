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

package com.cyr1en.commandprompter.api;

import com.cyr1en.commandprompter.CommandPrompter;
import fr.euphyllia.energie.model.SchedulerType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Player command dispatcher for Support with CommandPrompter.
 *
 * <p>
 * Because CommandPrompter cannot catch commands that were dispatched from
 * {@link org.bukkit.Bukkit#dispatchCommand(CommandSender, String)}, plugins
 * need a special way to execute player commands.
 * </p>
 */
public class Dispatcher {

    /**
     * Dispatches command by forcing a player to chat the command.
     * This will allow plugins to support CommandPrompter.
     *
     * @param sender  command sender (in menu's, then the item clicker)
     * @param command command that would be dispatched.
     */
    public static void dispatchCommand(Player sender, String command) {
        final String checked = command.codePointAt(0) == 0x2F ? command : "/" + command;
        CommandPrompter.scheduler.runTask(SchedulerType.SYNC, task -> sender.chat(checked));
    }

    /**
     * Dispatch the command as Console.
     *
     * @param command command that would be dispatched.
     */
    public static void dispatchConsole(final String command) {
        final String checked = command.codePointAt(0) == 0x2F ? command.substring(1) : command;
        CommandPrompter.scheduler.runTask(SchedulerType.SYNC, task -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), checked));
    }

    /**
     * Dispatch a command for a player with a PermissionAttachment that contains
     * all the whitelisted commands.
     *
     * @param plugin  Instance of plugin.
     * @param sender  command sender (in menu's, then the item clicker)
     * @param command command that would be dispatched.
     * @param ticks   Number of ticks before the attachment expires
     * @param perms   Permissions to set to the PermissionAttachment
     */
    public static void dispatchWithAttachment(Plugin plugin, Player sender, String command, int ticks,
            @NotNull String[] perms) {
        var commandPrompter = (CommandPrompter) plugin;
        var logger = commandPrompter.getPluginLogger();

        logger.debug("Dispatching command with permission attachment");

        var attachment = sender.addAttachment(plugin, ticks);
        if (attachment == null) {
            logger.err("Unable to create PermissionAttachment for " + sender.getName());
            return;
        }

        for (String perm : perms) {
            logger.debug("Attached Perm: " + perm);
            attachment.setPermission(perm, true);
        }
        attachment.getPermissible().recalculatePermissions();
        final String checked = command.codePointAt(0) == 0x2F ? command.substring(1) : command;
        CommandPrompter.scheduler.runTask(SchedulerType.SYNC, task -> Bukkit.dispatchCommand(sender, checked));
        //dispatchCommand(plugin, sender, command);
        sender.removeAttachment(attachment);
    }

}
