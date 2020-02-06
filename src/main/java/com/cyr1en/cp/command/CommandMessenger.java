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
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class CommandMessenger {

  private String prefix;

  public CommandMessenger(CommandPrompter plugin) {
    prefix = plugin.getConfiguration().getString("Prompt-Prefix") + "&r";
  }

  public void sendMessage(CommandSender sender, String message) {
    sendMessage(sender, message, true);
  }

  public void sendMessage(CommandSender sender, String message, boolean configuredPrefix) {
    String prefix = configuredPrefix ? this.prefix : "&6[&aCommandPrompter&6] ";
    String formattedMsg = ChatColor.translateAlternateColorCodes('&', prefix + message);
    sender.sendMessage(formattedMsg);
  }
}
