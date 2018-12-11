package com.cyr1en.cp.commands;

import co.aikar.commands.BaseCommand;
import com.cyr1en.cp.CommandPrompter;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class CPBaseCommand extends BaseCommand {

  protected CommandPrompter plugin;

  CPBaseCommand(CommandPrompter plugin) {
    this.plugin = plugin;
  }

  public void sendMessage(Player p, String message) {
    String prefix = plugin.getConfiguration().getString("Prompt-Prefix");
    p.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + message));
  }
}
