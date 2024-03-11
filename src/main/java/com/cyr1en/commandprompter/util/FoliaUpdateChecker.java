package com.cyr1en.commandprompter.util;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.kiso.mc.UpdateChecker;
import fr.euphyllia.energie.model.SchedulerType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class FoliaUpdateChecker extends UpdateChecker {
    public FoliaUpdateChecker(JavaPlugin plugin, int resourceID) {
        super(plugin, resourceID);
    }

    @Override
    public void onJoin(PlayerJoinEvent event) {
        CommandPrompter.getScheduler().runTask(SchedulerType.SYNC, task -> {
            this.sendUpdateAvailableMessage(event.getPlayer());
        });
    }
}
