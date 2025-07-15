package com.cyr1en.commandprompter.prompt.ui.inventory;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.prompts.PlayerUIPrompt;
import com.cyr1en.commandprompter.prompt.ui.anvil.AnvilGUI;
import com.cyr1en.commandprompter.util.ModelDataComponent;
import com.cyr1en.commandprompter.util.ServerUtil;
import com.cyr1en.commandprompter.util.Util;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;

import static com.cyr1en.commandprompter.util.AdventureUtil.*;

@SuppressWarnings("UnstableApiUsage")
public class ControlPane extends StaticPane {
    private static final int DEFAULT_PREV_LOC = 2;
    private static final int DEFAULT_NEXT_LOC = 6;
    private static final int DEFAULT_CANCEL_LOC = 4;
    private static final int DEFAULT_SEARCH_LOC = 8;

    private final CommandPrompter plugin;
    private final PaginatedPane paginatedPane;
    private final ChestGui gui;
    private final PromptContext ctx;
    private final PlayerUIPrompt playerUIPrompt;

    private int prevLoc;
    private int nextLoc;
    private int cancelLoc;
    private int searchLoc;

    public ControlPane(CommandPrompter plugin, PaginatedPane pane, ChestGui gui, PromptContext ctx, int numCols,
            PlayerUIPrompt playerUIPrompt) {
        super(0, numCols - 1, 9, 1);
        this.plugin = plugin;
        this.playerUIPrompt = playerUIPrompt;
        prevLoc = plugin.getPromptConfig().previousColumn() - 1;
        nextLoc = plugin.getPromptConfig().nextColumn() - 1;
        cancelLoc = plugin.getPromptConfig().cancelColumn() - 1;
        searchLoc = plugin.getPromptConfig().searchColumn() - 1;

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
            this.searchLoc = DEFAULT_SEARCH_LOC;
        }
    }

    private void updatePage(InventoryClickEvent event, int nextPage) {
        event.setCancelled(true);
        try {
            paginatedPane.setPage(nextPage);
            gui.update();
        } catch (IndexOutOfBoundsException ignore) {
            plugin.getPluginLogger().debug("Could not update page.");
        }
    }

    private void setupButtons() {
        var pages = paginatedPane.getPages() - 1;

        var prevMatString = plugin.getPromptConfig().previousItem();
        var prevCMD = plugin.getPromptConfig().previousCustomModelData();
        var prevIS = new ItemStack(Util.getCheckedMaterial(prevMatString, Material.FEATHER));
        addItem(plugin.getPromptConfig().previousText(), prevIS, prevLoc, prevCMD,
                c -> updatePage(c, Math.max((paginatedPane.getPage() - 1), 0)));

        var nextMatString = plugin.getPromptConfig().nextItem();
        var nextCMD = plugin.getPromptConfig().nextCustomModelData();
        var nextIS = new ItemStack(Util.getCheckedMaterial(nextMatString, Material.FEATHER));
        addItem(plugin.getPromptConfig().nextText(), nextIS, nextLoc, nextCMD,
                c -> updatePage(c, Math.min((paginatedPane.getPage() + 1), pages)));

        var cancelMatString = plugin.getPromptConfig().cancelItem();
        var cancelCMD = plugin.getPromptConfig().cancelCustomModelData();
        var cancelIS = new ItemStack(Util.getCheckedMaterial(cancelMatString, Material.FEATHER));
        addItem(plugin.getPromptConfig().cancelText(), cancelIS, cancelLoc, cancelCMD,
                c -> {
                    c.setCancelled(true);
                    plugin.getPromptManager().cancel(ctx.getPromptedPlayer());
                    ((Player) ctx.getPromptedPlayer()).closeInventory();
                });

        var searchMatString = plugin.getPromptConfig().searchItem();
        var searchCMD = plugin.getPromptConfig().searchCustomModelData();
        var searchIS = new ItemStack(Util.getCheckedMaterial(searchMatString, Material.NAME_TAG));
        addItem(plugin.getPromptConfig().searchText(), searchIS, searchLoc, searchCMD, this::search);
    }

    private void search(InventoryClickEvent e) {
        playerUIPrompt.setSearching(true);
        var builder = new AnvilGUI.Builder();
        builder.onClick((slot, stateSnapshot) -> {
            if (slot != AnvilGUI.Slot.OUTPUT) {
                return Collections.emptyList();
            }

            var paginatedPane = gui.getPanes().stream().filter(pane -> pane instanceof PaginatedPane)
                    .map(pane -> (PaginatedPane) pane).findFirst().orElse(null);
            if (paginatedPane == null) {
                plugin.getPluginLogger().debug("PaginatedPane not found.");
                return Collections.singletonList(AnvilGUI.ResponseAction.close());
            }

            var input = plain(stateSnapshot.getText());
            plugin.getPluginLogger().debug("Search: " + input);

            var heads = paginatedPane.getItems().stream().map(GuiItem::getItem).filter(item -> {
                var meta = item.getItemMeta();
                if (meta == null)
                    return false;
                var displayName = plain(Objects.requireNonNull(meta.displayName()));
                return displayName.toLowerCase().contains(input.toLowerCase());
            }).toList();
            paginatedPane.clear();
            paginatedPane.populateWithItemStacks(heads);
            gui.show(e.getWhoClicked());
            return Collections.singletonList(AnvilGUI.ResponseAction.close());
        });

        builder.title(plain(plugin.getPromptConfig().searchAnvilItemTitle()));
        builder.plugin(plugin);
        builder.itemLeft(getSearchLeftItem());
        builder.onClose(c -> playerUIPrompt.setSearching(false));
        builder.open((Player) e.getWhoClicked());
    }

    private ItemStack getSearchLeftItem() {
        var item = new ItemStack(Material.PAPER);
        var itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            plugin.getPluginLogger().debug("ItemMeta is null.");
            return item;
        }
        var configText = plugin.getPromptConfig().searchAnvilItemText();
        itemMeta.displayName(color(configText));

        var data = ModelDataComponent.legacy(plugin.getPromptConfig().searchAnvilItemCustomModelData());
        itemMeta.setCustomModelDataComponent(data);

        if (ServerUtil.isAtOrAbove("1.21.2"))
            itemMeta.setHideTooltip(true);

        item.setItemMeta(itemMeta);
        return item;
    }

    private void addItem(String name, ItemStack itemStack, int x, int customModelData,
            Consumer<InventoryClickEvent> consumer) {
        var itemMeta = itemStack.getItemMeta();
        Objects.requireNonNull(itemMeta).displayName(color(name));

        var builder = ModelDataComponent.builder();
        if (customModelData != 0)
            builder.floatsFromInt(customModelData);
        itemMeta.setCustomModelDataComponent(builder.build());

        itemStack.setItemMeta(itemMeta);
        addItem(new GuiItem(itemStack, consumer), x, 0);
    }
}
