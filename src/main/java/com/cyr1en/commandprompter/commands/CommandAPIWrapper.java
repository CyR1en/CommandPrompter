package com.cyr1en.commandprompter.commands;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.util.Util;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import dev.jorel.commandapi.CommandAPIPaperConfig;

/**
 * Wrapper for CommandAPI.
 *
 * <p>
 * Since CommandPrompter loads CommandAPI dynamically, we need a wrapper to
 * prevent
 * CommandAPI imports on the main class.
 */
public class CommandAPIWrapper {

    private final CommandPrompter plugin;

    public CommandAPIWrapper(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    public void load() {
        var config = new CommandAPIPaperConfig(plugin);
        config.fallbackToLatestNMS(true);
        config = plugin.getConfiguration().debugMode() ? config.silentLogs(false).verboseOutput(true)
                : config.silentLogs(true).verboseOutput(false);

        var msg = plugin.getI18N().getProperty("DelegateConsoleOnly");
        config.missingExecutorImplementationMessage(Util.color(msg));
        CommandAPI.onLoad(config);
    }

    public void onEnable() {
        CommandAPI.onEnable();
    }

    public void onDisable() {
        CommandAPI.onDisable();
    }

    public void registerCommands() {
        new MainCommand(plugin).register();
        new DelegateCommand.ConsoleDelegate(plugin).register();
        new DelegateCommand.PlayerDelegate(plugin).register();
    }
}
