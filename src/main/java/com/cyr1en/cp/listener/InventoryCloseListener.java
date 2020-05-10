package com.cyr1en.cp.listener;

import com.cyr1en.cp.CommandPrompter;
import com.cyr1en.cp.gui.PlayerList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.List;

public class InventoryCloseListener implements Listener {

    private CommandPrompter plugin;

    public InventoryCloseListener(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {

        List<PlayerList> playerLists = PlayerList.getPlayerlists();
        for (PlayerList playerList : playerLists) {
            if (!e.getInventory().equals(playerList.getInventory()))
                return;
            playerList.drop();
            playerList.close();
            break;
        }
    }
}