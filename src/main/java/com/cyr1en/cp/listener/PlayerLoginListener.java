package com.cyr1en.cp.listener;

import com.cyr1en.cp.gui.PlayerList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class PlayerLoginListener implements Listener {
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent e)
    {
        PlayerList.cachePlayer(e.getPlayer());
    }

}
