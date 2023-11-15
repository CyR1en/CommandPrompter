package com.cyr1en.commandprompter.commands;

import com.cyr1en.commandprompter.CommandPrompter;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;

/**
 * Wrapper for CommandAPI.
 * 
 * <p>
 * Since CommandPrompter loads CommandAPI dynamically, we need a wrapper to prevent
 * CommandAPI imports on the main class.
 */
public class CommandAPIWrapper {

    private final CommandPrompter plugin;

    public CommandAPIWrapper(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    public void load() {
        CommandAPI.onLoad(new CommandAPIBukkitConfig(plugin).silentLogs(true));
    }

    public void onEnable() {
        CommandAPI.onEnable();
    }

    public void onDisable() {
        CommandAPI.onDisable();
    }

    public void registerCommands() {
        new MainCommand(plugin).register();
        new ConsoleDelegate(plugin).register();
    }
}
