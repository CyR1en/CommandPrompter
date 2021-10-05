package com.cyr1en.commandprompter.unsafe;

import com.cyr1en.commandprompter.CommandPrompter;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;

import java.util.Arrays;
import java.util.Map;

public class ModifiedCommandMap extends SimpleCommandMap {

    private final Server server;
    private final CommandPrompter plugin;

    public ModifiedCommandMap(Server server, CommandPrompter plugin) {
        super(server);
        this.server = server;
        this.plugin = plugin;
        rebuildKnownCommands();
    }

    private void rebuildKnownCommands() {
        try {
            var commandMap = grabCommandMap();
            var originalKnownCommands = grabKnownCommandsFromMap(commandMap);
            this.knownCommands.putAll(originalKnownCommands);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> grabKnownCommandsFromMap(SimpleCommandMap commandMap)
            throws NoSuchFieldException, IllegalAccessException {
        var mapField = commandMap.getClass().getSuperclass().getDeclaredField("knownCommands");
        mapField.setAccessible(true);
        return (Map<String, Command>) mapField.get(commandMap);
    }

    private SimpleCommandMap grabCommandMap() throws NoSuchFieldException, IllegalAccessException {
        var commandMapField = server.getClass().getDeclaredField("commandMap");
        commandMapField.setAccessible(true);
        return (SimpleCommandMap) commandMapField.get(server);
    }

    @Override
    public boolean dispatch(CommandSender sender, String commandLine) throws CommandException {
        var event = new CommandDispatchEvent(sender, commandLine);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled())
            return true;
        return super.dispatch(sender, commandLine);
    }


}
