package dev.cyr1en.promptpaper.listener;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Intercepts player commands at {@link EventPriority#LOWEST} to detect prompt tags
 * in the command line. If the command contains prompts, it starts a session via
 * {@link ScreenManager} and cancels the original command event so the raw command
 * is not dispatched until all prompts are answered.
 * Commands in the {@code ignored-commands} config list and the plugin's own
 * commands are excluded from interception.
 */
public class PlayerCommandListener implements Listener {

    private final CommandPrompter plugin;
    private final ScreenManager screenManager;

    public PlayerCommandListener(CommandPrompter plugin, ScreenManager screenManager) {
        this.plugin = plugin;
        this.screenManager = screenManager;
    }

    /**
     * Checks incoming player commands for prompt tags. If the command has active
     * prompts, starts a session and cancels the event. Players with an active screen
     * cannot run commands not in the {@code allowed-while-in-prompt} list.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            plugin.getPluginLogger().debug("Command already cancelled, skipping");
            return;
        }
        var player = event.getPlayer();
        var message = event.getMessage();
        var commandName = (message.startsWith("/") ? message.substring(1) : message)
                .split(" ", 2)[0].toLowerCase();
        var hasActiveScreen = screenManager.hasActiveScreen(player);

        plugin.getPluginLogger().debug("Command received: player=" + player.getName()
                + " cmd=" + commandName + " hasScreen=" + hasActiveScreen);

        if (commandName.startsWith("commandprompter") || commandName.startsWith("cmdp")) {
            plugin.getPluginLogger().debug("Command is plugin command, not intercepting");
            return;
        }

        var config = plugin.getConfigLoader().getConfig();
        if (config.ignoredCommands().stream()
                .anyMatch(c -> c.equalsIgnoreCase(commandName))) {
            plugin.getPluginLogger().debug("Command is in ignored-commands list, not intercepting");
            return;
        }
        if (hasActiveScreen
                && config.allowedWhileInPrompt().stream()
                        .noneMatch(c -> c.equalsIgnoreCase(commandName))) {
            plugin.getPluginLogger().debug("Player has active screen, cancelling event");
            event.setCancelled(true);
        }

        var commandLine = message.startsWith("/") ? message.substring(1) : message;
        screenManager.startSession(player, commandLine);
        if (screenManager.hasActiveScreen(player)) {
            plugin.getPluginLogger().debug("Session started, cancelling event");
            event.setCancelled(true);
        }
    }
}
