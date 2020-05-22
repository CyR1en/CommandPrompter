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

package com.cyr1en.cp.commands;


import com.cyr1en.cp.CommandPrompter;
import com.cyr1en.kiso.mc.command.AbstractCommand;
import com.cyr1en.kiso.mc.command.CommandMessenger;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class Reload extends AbstractCommand {

  private final CommandPrompter commandPrompter;

  public Reload(JavaPlugin plugin, CommandMessenger messenger) {
    super(plugin, messenger);
    commandPrompter = (CommandPrompter) plugin;
    this.commandName = "reload";
    this.alias = new String[]{"rload", "r"};
    this.permission = "commandprompter.reload";
  }

  @Override
  public void doCommand(CommandSender sender, String[] args) {
    commandPrompter.reload(true);
    String message = commandPrompter.getI18N().getProperty("CommandReloadSuccess");
    this.messenger.sendMessage(sender, message);
  }
}
