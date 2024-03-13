package com.cyr1en.commandprompter.util;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.kiso.mc.UpdateChecker;
import fr.euphyllia.energie.model.SchedulerType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class FoliaUpdateChecker extends UpdateChecker {
    public FoliaUpdateChecker(JavaPlugin plugin, int resourceID) {
        super(plugin, resourceID);
    }

    @Override
    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        CommandPrompter.getScheduler().runTask(SchedulerType.SYNC, event.getPlayer() , task -> {
            this.sendUpdateAvailableMessage(event.getPlayer());
        }, null);
    }
}
