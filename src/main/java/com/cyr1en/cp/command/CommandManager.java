/*
 * MIT License
 *
 * Copyright (c) 2019 Ethan Bacurio
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

package com.cyr1en.cp.command;

import com.cyr1en.cp.CommandPrompter;
import com.google.common.collect.Lists;
import org.bukkit.Bukkit;
import org.bukkit.command.*;

import java.util.Arrays;
import java.util.List;


public class CommandManager implements CommandExecutor {

  private CommandPrompter commandPrompter;
  private CommandMessenger messenger;

  private List<AbstractCommand> commands;

  public CommandManager(CommandPrompter plugin) {
    this.commandPrompter = plugin;
    commands = Lists.newArrayList();
    messenger = new CommandMessenger(plugin);
  }

  public void registerCommand(AbstractCommand command) {
    commands.add(command);
  }

  public void registerTabCompleter(CommandTabCompleter commandTabCompleter) {
    PluginCommand command = Bukkit.getPluginCommand("commandprompter");
    command.setTabCompleter(commandTabCompleter);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      messenger.sendMessage(sender, commandPrompter.getI18N().getProperty("CommandInvalidArgs"));
      return false;
    }
    for (AbstractCommand cmd : commands)
      if (args[0].equalsIgnoreCase(cmd.getName()) || cmd.getAlias().contains(args[0])) {
        if (sender instanceof ConsoleCommandSender && cmd.isPlayerOnly()) {
          messenger.sendMessage(sender, commandPrompter.getI18N().getProperty("CommandPlayerOnly"));
          return false;
        }
        return cmd.onCommand(sender, Arrays.copyOfRange(args, 1, args.length));
      } else
        messenger.sendMessage(sender, commandPrompter.getI18N().getFormattedProperty("CommandInvalid", args[0]));
    return false;
  }

  public List<AbstractCommand> getCommands() {
    return commands;
  }
}
