package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import de.myzelyam.api.vanish.PlayerVanishStateChangeEvent;
import de.myzelyam.api.vanish.VanishAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@TargetPlugin(pluginName = "SuperVanish")
public class SuperVanishHook extends VanishHook implements Listener {

    private SuperVanishHook(CommandPrompter plugin) {
        super(plugin);
    }

    public boolean isInvisible(Player p) {
        return VanishAPI.isInvisible(p);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVisibilityStateChange(PlayerVanishStateChangeEvent e) {
        onStateChange(Bukkit.getPlayer(e.getUUID()), e::isVanishing);
    }
}
