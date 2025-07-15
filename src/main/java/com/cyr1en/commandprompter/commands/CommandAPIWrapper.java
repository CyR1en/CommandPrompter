package com.cyr1en.commandprompter.commands;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.util.AdventureUtil;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;

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
        var config = new CommandAPIBukkitConfig(plugin);
        config.beLenientForMinorVersions(true);
        config.skipReloadDatapacks(true);
        config.useLatestNMSVersion(false);
        config = plugin.getConfiguration().debugMode() ? config.silentLogs(false).verboseOutput(true)
                : config.silentLogs(true).verboseOutput(false);

        var msg = plugin.getI18N().getProperty("DelegateConsoleOnly");
        config.missingExecutorImplementationMessage(AdventureUtil.legacyColor(msg));
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
