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
