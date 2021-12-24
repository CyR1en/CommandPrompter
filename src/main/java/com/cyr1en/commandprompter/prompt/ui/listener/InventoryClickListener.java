/*
 * MIT License
 *
 * Copyright (c) 2020 Ethan Bacurio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.cyr1en.commandprompter.prompt.ui.listener;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.prompt.ui.PlayerList;
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
                    if (event.getCurrentItem().getType() == Material.FEATHER) {
                        String name = event.getCurrentItem().getItemMeta().getDisplayName();
                        if (name.equals("=>"))
                            playerList.nextPage();
                        else if (name.equals("<="))
                            playerList.prevPage();

                        event.setCancelled(true);
                        p.updateInventory();
                        return;
                    }
                }
            }
            event.setCancelled(true);
            p.updateInventory();

        }

    }
}
