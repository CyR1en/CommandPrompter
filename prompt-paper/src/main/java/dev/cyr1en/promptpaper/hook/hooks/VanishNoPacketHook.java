package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.kitteh.vanish.VanishPerms;
import org.kitteh.vanish.VanishPlugin;
import org.kitteh.vanish.event.VanishStatusChangeEvent;

/**
 * Hook for the VanishNoPacket plugin. Checks invisibility via
 * {@link VanishPlugin#getManager()} and also respects the
 * {@code vanish.hooks.dynmap.alwayshidden} and join-vanished permissions.
 */
@TargetPlugin(pluginName = "VanishNoPacket")
public class VanishNoPacketHook extends VanishHook implements Listener {

    private VanishPlugin vanishPlugin;

    public VanishNoPacketHook(CommandPrompter plugin) {
        super(plugin);
        var jPlugin = Bukkit.getPluginManager().getPlugin("VanishNoPacket");
        if (jPlugin instanceof VanishPlugin vp)
            this.vanishPlugin = vp;
        else
            plugin.getLogger().warning("VanishNoPacketHook cannot be initialized without VanishNoPacket");
    }

    /** Checks invisibility via VanishNoPacket's manager, including always-hidden and join-vanished permissions. */
    @Override
    public boolean isInvisible(Player player) {
        if (vanishPlugin == null) return false;
        if (player.hasPermission("vanish.hooks.dynmap.alwayshidden") || VanishPerms.joinVanished(player))
            return true;
        return vanishPlugin.getManager().isVanished(player);
    }

    /** Delegates visibility state changes to the shared {@link VanishHook#onStateChange} logic. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVisibilityStateChange(VanishStatusChangeEvent event) {
        onStateChange(event.getPlayer(), event::isVanishing);
    }
}
