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

package com.cyr1en.commandprompter.prompt.prompts;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.hooks.VanishHook;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptParser;
import com.cyr1en.commandprompter.prompt.ui.HeadCache;
import com.cyr1en.commandprompter.prompt.ui.inventory.ControlPane;
import com.cyr1en.commandprompter.util.Util;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class PlayerUIPrompt extends AbstractPrompt {

    private final int size;
    private final ChestGui gui;
    private final HeadCache headCache;
    private final VanishHook vanishHook;

    public PlayerUIPrompt(CommandPrompter plugin, PromptContext context, String prompt,
            List<PromptParser.PromptArgument> args) {
        super(plugin, context, prompt, args);
        var cfgSize = getPlugin().getPromptConfig().playerUISize();
        var parts = Arrays.asList(getPrompt().split("\\{br}"));
        size = Math.max((cfgSize - (cfgSize % 9)) / 9, 2);
        gui = new ChestGui(size, color(parts.get(0)));
        this.headCache = plugin.getHeadCache();
        vanishHook = plugin.getHookContainer().getVanishHook().get();
    }

    private void send(Player p) {
        gui.setOnClose(e -> getPromptManager().cancel(p));

        var skullPane = new PaginatedPane(0, 0, 9, size - 1);

        var isSorted = getPlugin().getPromptConfig().sorted();
        var isPerWorld = getPlugin().getPromptConfig().isPerWorld();
        var skulls = isPerWorld
                ? (isSorted ? headCache.getHeadsSortedFor(p.getWorld().getPlayers())
                        : headCache.getHeadsFor(p.getWorld().getPlayers()))
                : (isSorted ? headCache.getHeadsSorted() : headCache.getHeads());

        var headCacheStr = headCache.toString();
        getPlugin().getPluginLogger().debug("Head Cache: " + headCacheStr.substring(headCacheStr.indexOf('@')));
        getPlugin().getPluginLogger().debug("|-- Size: " + headCache.getHeads().size());

        skullPane.populateWithItemStacks(skulls);
        skullPane.setOnClick(this::processClick);

        gui.addPane(skullPane);
        gui.addPane(new ControlPane(getPlugin(), skullPane, gui, getContext(), size));

        gui.show((HumanEntity) getContext().getSender());
    }

    @Override
    public void sendPrompt() {
        var p = (Player) getContext().getSender();
        if (headCache.isEmpty()) {
            getPlugin().getMessenger().sendMessage(p, getPlugin().getPromptConfig().emptyMessage());
            getPromptManager().cancel(p);
            return;
        }
        
        var missingCached = Bukkit.getOnlinePlayers().stream().filter(player -> !vanishHook.isInvisible(player))
                .count() > headCache.getHeads().size();
        if (missingCached) {
            getPlugin().getPluginLogger().debug("Missing heads in cache, rebuilding before sending...");
            headCache.reBuildCache().thenAccept(cache -> {
                getPlugin().getPluginLogger().debug("Rebuilt cache!");
                send(p);
            });
        } else {
            send(p);
        }

    }

    private void processClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (Objects.isNull(e.getCurrentItem()))
            return;
        var name = Objects.requireNonNull(
                Objects.requireNonNull(Objects.requireNonNull((SkullMeta) (e.getCurrentItem()).getItemMeta()))
                        .getOwningPlayer())
                .getName();
        name = Util.stripColor(name);
        var ctx = new PromptContext.Builder()
                .setSender((Player) getContext().getSender())
                .setContent(name).build();
        getPlugin().getPromptManager().processPrompt(ctx);
        gui.setOnClose(null);
        ((Player) getContext().getSender()).closeInventory();
    }
}
