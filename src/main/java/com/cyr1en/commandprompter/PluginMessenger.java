package com.cyr1en.commandprompter;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class PluginMessenger {

    private String prefix;

    public PluginMessenger(String prefix) {
        this.prefix = prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void sendMessage(CommandSender sender, String message) {
        if (message.isBlank()) return;
        var whole = prefix + message;
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', whole));
    }
}
