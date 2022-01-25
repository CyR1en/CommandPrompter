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
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.ui.SkullCache;
import com.cyr1en.commandprompter.util.Util;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

public class PlayerUIPrompt extends AbstractPrompt {

    private final int size;
    private final ChestGui gui;

    public PlayerUIPrompt(CommandPrompter plugin, PromptContext context, String prompt) {
        super(plugin, context, prompt);
        var cfgSize = getPlugin().getPromptConfig().playerUISize();
        var parts = Arrays.asList(getPrompt().split("\\{br}"));
        size = Math.max((cfgSize - (cfgSize % 9)) / 9, 2);
        gui = new ChestGui(size, color(parts.get(0)));
    }

    @Override
    public void sendPrompt() {
        Bukkit.getScheduler().runTaskLater(getPlugin(), this::send, 2L);
    }

    private void send() {
        gui.setOnClose(e -> getPromptManager().cancel(getContext().getSender()));
        var skullPane = new PaginatedPane(0, 0, 9, size - 1);

        var isSorted = getPlugin().getPromptConfig().sorted();
        var skulls = isSorted ? SkullCache.getSkullsSorted() : SkullCache.getSkulls();
        
        skullPane.populateWithItemStacks(skulls);
        skullPane.setOnClick(this::processClick);

        gui.addPane(skullPane);
        gui.addPane(new ControlPane(getPlugin(), skullPane, gui, getContext(), size));

        gui.show((HumanEntity) getContext().getSender());
    }

    private void processClick(InventoryClickEvent e) {
        e.setCancelled(true);
        var name = Objects.requireNonNull(Objects.requireNonNull
                (e.getCurrentItem()).getItemMeta()).getDisplayName();
        name = Util.stripColor(name);
        var ctx = new PromptContext(null, (Player) getContext().getSender(), name);
        getPlugin().getPromptManager().processPrompt(ctx);
        gui.setOnClose(null);
        ((Player) getContext().getSender()).closeInventory();
    }


    private static class ControlPane extends StaticPane {

        private static final int DEFAULT_PREV_LOC = 2;
        private static final int DEFAULT_NEXT_LOC = 6;
        private static final int DEFAULT_CANCEL_LOC = 4;

        private final CommandPrompter plugin;
        private final PaginatedPane paginatedPane;
        private final ChestGui gui;
        private final PromptContext ctx;

        private int prevLoc;
        private int nextLoc;
        private int cancelLoc;

        private ControlPane(CommandPrompter plugin, PaginatedPane pane, ChestGui gui, PromptContext ctx, int numCols) {
            super(0, numCols - 1, 9, 1);
            this.plugin = plugin;
            prevLoc = plugin.getPromptConfig().previousColumn() - 1;
            nextLoc = plugin.getPromptConfig().nextColumn() - 1;
            cancelLoc = plugin.getPromptConfig().cancelColumn() - 1;
            this.paginatedPane = pane;
            this.ctx = ctx;
            this.gui = gui;
            verifyLocs();
            setupButtons();
        }

        private void verifyLocs() {
            if (prevLoc == nextLoc || prevLoc == cancelLoc || nextLoc == cancelLoc) {
                this.prevLoc = DEFAULT_PREV_LOC;
                this.nextLoc = DEFAULT_NEXT_LOC;
                this.cancelLoc = DEFAULT_CANCEL_LOC;
            }
        }

        private void setupButtons() {
            var pages = paginatedPane.getPages() - 1;

            var prevMatString = plugin.getPromptConfig().previousItem();
            var prevIS = new ItemStack(Util.getCheckedMaterial(prevMatString, Material.FEATHER));
            addItem(plugin.getPromptConfig().previousText(), prevIS, prevLoc,
                    c -> {
                        c.setCancelled(true);
                        var next = Math.max((paginatedPane.getPage() - 1), 0);
                        paginatedPane.setPage(next);
                        gui.update();
                    });

            var nextMatString = plugin.getPromptConfig().nextItem();
            var nextIS = new ItemStack(Util.getCheckedMaterial(nextMatString, Material.FEATHER));
            addItem(plugin.getPromptConfig().nextText(), nextIS, nextLoc,
                    c -> {
                        c.setCancelled(true);
                        var next = Math.min((paginatedPane.getPage() + 1), pages);
                        paginatedPane.setPage(next);
                        gui.update();
                    });

            var cancelMatString = plugin.getPromptConfig().cancelItem();
            var cancelIS = new ItemStack(Util.getCheckedMaterial(cancelMatString, Material.FEATHER));
            addItem(plugin.getPromptConfig().cancelText(), cancelIS, cancelLoc,
                    c -> {
                        c.setCancelled(true);
                        plugin.getPromptManager().cancel(ctx.getSender());
                        ((Player) ctx.getSender()).closeInventory();
                    });
        }

        private void addItem(String name, ItemStack itemStack, int x, Consumer<InventoryClickEvent> consumer) {
            var itemMeta = itemStack.getItemMeta();
            Objects.requireNonNull(itemMeta).setDisplayName(Util.color(name));
            itemStack.setItemMeta(itemMeta);
            addItem(new GuiItem(itemStack, consumer), x, 0);
        }

    }
}
