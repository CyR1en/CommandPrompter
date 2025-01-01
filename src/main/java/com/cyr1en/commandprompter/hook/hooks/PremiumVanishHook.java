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

@TargetPlugin(pluginName = "PremiumVanish")
public class PremiumVanishHook extends VanishHook implements Listener {

    public PremiumVanishHook(CommandPrompter plugin) {
        super(plugin);
    }

    @Override
    public boolean isInvisible(Player p) {
        return VanishAPI.isInvisible(p);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onStateChange(PlayerVanishStateChangeEvent e) {
        System.out.println("onStateCHange: "+e.isVanishing());
        var player = Bukkit.getPlayer(e.getUUID());
        if (player == null) return;
        onStateChange(player, e::isVanishing);
    }

}
