package com.cyr1en.cp.listener;

import com.cyr1en.cp.CommandPrompter;
import com.cyr1en.cp.gui.PlayerList;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class InventoryClickListener implements Listener {

    private CommandPrompter plugin;

    public InventoryClickListener(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        List<PlayerList> playerLists = PlayerList.getPlayerlists();

        for (PlayerList playerList : playerLists) {
            if (!event.getInventory().equals(playerList.getInventory()))
                continue;
            Player p = playerList.getPlayer();
            if (event.getClick() == ClickType.LEFT) {
                if (event.getCurrentItem() != null) {
                    if (event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                        String name = event.getCurrentItem().getItemMeta().getDisplayName();
                        playerList.drop();
                        playerList.complete(name);
                        p.closeInventory();
                        return;
                    }
                }
            }
            event.setCancelled(true);
            p.updateInventory();

        }

    }
}
