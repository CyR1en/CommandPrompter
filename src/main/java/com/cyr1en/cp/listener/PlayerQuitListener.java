package com.cyr1en.cp.listener;

import com.cyr1en.cp.gui.PlayerList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e)
    {
        PlayerList.uncachePlayer(e.getPlayer());
    }
}
