package dev.cyr1en.promptpaper.listener;

import dev.cyr1en.promptpaper.CommandPrompter;
import java.util.List;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

/**
 * Hides CommandPrompter's own commands from tab-completion when the
 * {@code command-tab-complete} config option is disabled.
 */
public class CommandSendListener implements Listener {

    private static final List<String> KEYS = List.of(
            "commandprompter", "cmdp",
            "consoledelegate", "cd",
            "playerdelegate", "pd"
    );

    private final CommandPrompter plugin;

    public CommandSendListener(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    /** Strips plugin commands from the player's tab-complete list when the config option is off. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        var config = plugin.getConfigLoader().getConfig();
        if (!config.commandTabComplete()) {
            event.getCommands().removeAll(KEYS);
            plugin.getPluginLogger().debug("Removed " + KEYS.size()
                    + " internal commands from tab complete for " + event.getPlayer().getName());
        }
    }
}
