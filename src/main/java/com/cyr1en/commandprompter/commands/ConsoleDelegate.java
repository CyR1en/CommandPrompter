package com.cyr1en.commandprompter.commands;

import java.util.Arrays;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.PluginMessenger;

public class ConsoleDelegate implements CommandExecutor {

    private final CommandPrompter commandPrompter;
    private final PluginLogger logger;
    private final PluginMessenger messenger;

    public ConsoleDelegate(JavaPlugin plugin) {
        this.commandPrompter = (CommandPrompter) plugin;
        this.logger = commandPrompter.getPluginLogger();
        this.messenger = commandPrompter.getMessenger();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            messenger.sendMessage(sender, "Only console can use this command.");
            return true;
        } 
        logger.debug("Command: " + command);
        logger.debug("Label: " + label);
        logger.debug("Args: " + Arrays.toString(args));
        return true;
    }

}