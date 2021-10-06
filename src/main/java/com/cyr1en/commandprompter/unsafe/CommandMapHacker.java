package com.cyr1en.commandprompter.unsafe;

import com.cyr1en.commandprompter.CommandPrompter;
import org.bukkit.command.PluginCommandYamlParser;
import org.bukkit.command.SimpleCommandMap;

public class CommandMapHacker {

    private final PvtFieldMutator mutator;
    private final CommandPrompter plugin;

    public CommandMapHacker(CommandPrompter plugin) throws NoSuchFieldException, IllegalAccessException {
        mutator = new PvtFieldMutator();
        this.plugin = plugin;
        logWarning();
    }

    public void hackCommandMapIn(Object object) throws NoSuchFieldException, IllegalAccessException {
        var newMap = new ModifiedCommandMap(object, plugin);
        registerPluginCommands(newMap);
        mutator.forField("commandMap").in(object).replaceWith(newMap);
        plugin.getLogger().warning("Changed command map in '" + object.getClass().getSimpleName() + "' " +
                "to '" + newMap.getClass().getSimpleName() + "'");
    }

    /**
     * Helper function that would register CommandPrompter's commands to the new map.
     *
     * <p>
     * It seems like CommandPrompter's plugin description file commands are not loaded when changing the command
     * map on the onLoad() function of this plugin. Therefore, we have to register all commands to the modified
     * command map manually before setting it as the new command map of the encapsulating object.
     *
     * @param map the modified map.
     */
    private void registerPluginCommands(SimpleCommandMap map) {
        var pluginCommands = PluginCommandYamlParser.parse(plugin);
        if(!pluginCommands.isEmpty())
            map.registerAll(plugin.getDescription().getName(), pluginCommands);
    }

    private void logWarning() {
        plugin.getLogger().warning("Warning! CommandPrompter is now going to use the modified command map.");
        plugin.getLogger().warning("Changing the value of a private final variable can make your program unstable.");
        plugin.getLogger().warning("If you experience any problem, please disable this feature immediately!");
    }
}
