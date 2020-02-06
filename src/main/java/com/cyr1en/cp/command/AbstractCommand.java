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

package com.cyr1en.cp.command;

import com.cyr1en.cp.CommandPrompter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.bukkit.command.CommandSender;

import java.util.List;

public abstract class AbstractCommand implements ArgumentCounter {

  private CommandPrompter plugin;
  protected String name;
  protected List<String> alias;
  protected String argument;
  protected String permission;
  protected List<SubCommand> children;
  protected boolean playerOnly;
  protected CommandMessenger messenger;

  public AbstractCommand(CommandPrompter plugin) {
    this.plugin = plugin;
    this.name = "";
    this.alias = Lists.newArrayList();
    this.argument = "";
    this.permission = "";
    this.children = Lists.newArrayList();
    this.playerOnly = false;
    messenger = new CommandMessenger(plugin);
  }

  public void sendMessage(CommandSender p, String message) {
    messenger.sendMessage(p, message);
  }

  public void sendErrMessage(CommandSender p, String message) {
    sendMessage(p, "&c" + message);
  }

  boolean onCommand(CommandSender sender, String[] args) {
    if (!sender.hasPermission(permission)) {
      String message = plugin.getI18N().getProperty("CommandNoPerm");
      sendErrMessage(sender, message);
      return false;
    }
    if (countRequiredArgs() != args.length) {
      String message = plugin.getI18N().getProperty("CommandInvalidArgs");
      String usageMsg = plugin.getI18N().getFormattedProperty("CommandUsage", String.format("/cp %s %s", getName(), getArgument()));
      sendErrMessage(sender, message);
      sendErrMessage(sender, usageMsg);
      return false;
    }
    doCommand(sender, args);
    return true;
  }

  public CommandPrompter getPlugin() {
    return plugin;
  }

  public String getName() {
    return name;
  }

  public List<String> getAlias() {
    return alias;
  }

  public String getArgument() {
    return argument;
  }

  public String getPermission() {
    return permission;
  }

  public List<SubCommand> getChildren() {
    return children;
  }

  public boolean isPlayerOnly() {
    return playerOnly;
  }

  @Override
  public String getArguments() {
    return argument;
  }

  public abstract void doCommand(CommandSender sender, String[] args);

  public List<String> onTabComplete(String[] args) {
    return ImmutableList.of();
  }
}
