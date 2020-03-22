package com.cyr1en.cp.listener;

import com.cyr1en.cp.gui.PlayerList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener {
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e)
    {
        PlayerList.uncachePlayer(e.getPlayer());
    }
}
