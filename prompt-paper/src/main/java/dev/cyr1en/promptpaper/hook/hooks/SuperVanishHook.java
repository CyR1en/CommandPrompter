package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import de.myzelyam.api.vanish.PlayerVanishStateChangeEvent;
import de.myzelyam.api.vanish.VanishAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Hook for the SuperVanish plugin. Overrides {@link VanishHook#isInvisible}
 * with SuperVanish's {@link VanishAPI} and listens for visibility state changes.
 */
@TargetPlugin(pluginName = "SuperVanish")
public class SuperVanishHook extends VanishHook implements Listener {

    public SuperVanishHook(CommandPrompter plugin) {
        super(plugin);
    }

    /** Uses SuperVanish's {@link VanishAPI} to check player invisibility. */
    @Override
    public boolean isInvisible(Player player) {
        return VanishAPI.isInvisible(player);
    }

    /** Delegates visibility state changes to the shared {@link VanishHook#onStateChange} logic. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVisibilityStateChange(PlayerVanishStateChangeEvent event) {
        var player = Bukkit.getPlayer(event.getUUID());
        if (player == null) return;
        onStateChange(player, event::isVanishing);
    }
}
