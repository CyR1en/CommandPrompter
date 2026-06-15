package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * Hook for the PlaceholderAPI plugin. Resolves PlaceholderAPI placeholders
 * in strings scoped to a specific player.
 */
@TargetPlugin(pluginName = "PlaceholderAPI")
public class PapiHook extends BaseHook {

    public PapiHook(CommandPrompter plugin) {
        super(plugin);
    }

    /**
     * Resolves PlaceholderAPI placeholders in the text for the given player.
     * Returns the text unchanged if no placeholders are present.
     */
    public String setPlaceholder(Player player, String text) {
        if (!containsPlaceholders(text)) return text;
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    /** Returns {@code true} if the text contains any PlaceholderAPI placeholders. */
    public boolean containsPlaceholders(String text) {
        return PlaceholderAPI.containsPlaceholders(text);
    }
}
