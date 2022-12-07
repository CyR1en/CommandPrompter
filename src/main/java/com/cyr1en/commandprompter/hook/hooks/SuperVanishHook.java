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

import java.util.Objects;

@TargetPlugin(pluginName = "SuperVanish")
public class SuperVanishHook extends BaseHook implements Listener {

    private final HeadCache headCache;

    private SuperVanishHook(CommandPrompter plugin) {
        super(plugin);
        this.headCache = plugin.getHeadCache();
    }

    public boolean isInvisible(Player p) {
        return VanishAPI.isInvisible(p);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVisibilityStateChange(PlayerVanishStateChangeEvent e) {
        getPlugin().getPluginLogger().debug("Pre Vanish State Change: " + headCache.getHeads().stream().map(i ->
                Objects.requireNonNull(i.getItemMeta()).getDisplayName()).toList());
        if (e.isVanishing())
            headCache.invalidate(Bukkit.getPlayer(e.getUUID()));
        else
            headCache.getHeadFor(Objects.requireNonNull(Bukkit.getPlayer(e.getUUID())));
        getPlugin().getPluginLogger().debug("Post Vanish State Change: " + headCache.getHeads().stream().map(i ->
                Objects.requireNonNull(i.getItemMeta()).getDisplayName()).toList());
    }
}
