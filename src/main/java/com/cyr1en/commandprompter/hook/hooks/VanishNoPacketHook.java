package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import com.cyr1en.commandprompter.prompt.ui.HeadCache;
import de.myzelyam.api.vanish.PlayerVanishStateChangeEvent;
import de.myzelyam.api.vanish.VanishAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.kitteh.vanish.VanishPlugin;
import org.kitteh.vanish.event.VanishStatusChangeEvent;

@TargetPlugin(pluginName = "VanishNoPacket")
public class VanishNoPacketHook extends VanishHook implements Listener {

    private VanishPlugin vanishPlugin;

    private VanishNoPacketHook(CommandPrompter plugin) {
        super(plugin);
        var jPlugin = Bukkit.getServer().getPluginManager().getPlugin("VanishNoPacket");
        if (jPlugin instanceof VanishPlugin)
            this.vanishPlugin = (VanishPlugin) jPlugin;
        else
            plugin.getPluginLogger().warn("VanishNoPacketHook cannot be initialized without VanishNoPacket");
    }

    public boolean isInvisible(Player p) {
        if(vanishPlugin == null) return false;
        return vanishPlugin.getManager().isVanished(p);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVisibilityStateChange(VanishStatusChangeEvent event) {
        onStateChange(event.getPlayer(), event::isVanishing);
    }
}
