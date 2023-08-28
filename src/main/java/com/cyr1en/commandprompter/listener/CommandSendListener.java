package com.cyr1en.commandprompter.listener;

import com.cyr1en.commandprompter.CommandPrompter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Arrays;

public class CommandSendListener implements Listener {

    private final CommandPrompter plugin;

    private static final String[] keys = {
            "commandprompter:cmdp",
            "commandprompter:commandprompter",
            "commandprompter:cmdprompter",
            "cmdp",
            "commandprompter",
            "cmdprompter"
    };

    public CommandSendListener(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onCommandSend(PlayerCommandSendEvent event) {
        plugin.getPluginLogger().debug("CommandSendEvent caught.");
        plugin.getPluginLogger().debug("Complete: " + plugin.getConfiguration().commandTabComplete());
        if (!plugin.getConfiguration().commandTabComplete())
            event.getCommands().removeAll(Arrays.stream(keys).toList());
    }
}
