package dev.cyr1en.promptpaper.screen.playerui;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.gui.ChestGui;
import dev.cyr1en.promptui.gui.GuiItem;
import dev.cyr1en.promptui.gui.Slot;
import dev.cyr1en.promptui.pane.PaginatedPane;
import dev.cyr1en.promptui.pane.StaticPane;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptui.ComponentUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Prompt screen that displays online players as clickable heads in a
 * paginated chest GUI, with search and navigation controls.
 */
public class PlayerUIScreen implements InputScreen {

    private final CommandPrompter plugin;
    private final Player player;
    private final PromptTag tag;
    private final dev.cyr1en.promptpaper.preset.PlayerUiPrompt puiPrompt;
    private Consumer<ScreenResult> callback;
    private ChestGui gui;
    private PaginatedPane headPane;
    private List<ItemStack> currentHeads;
    private boolean open;
    private org.bukkit.event.Listener searchListener;

    public PlayerUIScreen(CommandPrompter plugin, Player player, PromptTag tag, dev.cyr1en.promptpaper.preset.PlayerUiPrompt puiPrompt) {
        this.plugin = plugin;
        this.player = player;
        this.tag = tag;
        this.puiPrompt = puiPrompt;
        this.currentHeads = new ArrayList<>();
    }

    /**
     * Checks for cache staleness and rebuilds if needed, then opens
     * the chest GUI with the player's filtered head list.
     */
    @Override
    public void open() {
        var headCache = plugin.getHeadCache();
        int onlineCount = (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> !headCache.isVanished(p))
                .count();
        if (onlineCount != headCache.size()) {
            plugin.getPluginLogger().debug("PlayerUI cache mismatch: cache="
                    + headCache.size() + " online=" + onlineCount
                    + " — rebuilding before open");
            headCache.buildCache(this::openInternal);
            return;
        }
        openInternal();
    }

    private void openInternal() {
        if (currentHeads.isEmpty())
            currentHeads = getFilteredHeads();

        registerQuitListener();

        plugin.getPluginLogger().debug("Opening PlayerUI for " + player.getName()
                + " heads=" + currentHeads.size() + " filter=" + tag.filter());

        var promptConfig = plugin.getConfigLoader().getPromptConfig();
        var size = promptConfig.playerUISize();
        int rows = size / 9;

        gui = new ChestGui(plugin, rows);
        gui.setTitle(ComponentUtil.mini(tag.displayText()));
        gui.setPlayerInventoryUsed(false);

        // Cancel all top-inventory clicks to prevent item theft from empty slots.
        // Pane-level click actions still fire via gui.click() regardless of cancellation.
        gui.setOnTopClick(event -> event.setCancelled(true));

        // Build the player-head paginated pane
        headPane = new PaginatedPane(9, rows - 1);
        int pageSize = 9 * (rows - 1);
        for (int i = 0; i < currentHeads.size(); i += pageSize) {
            StaticPane page = new StaticPane(9, rows - 1);
            for (int j = i; j < Math.min(i + pageSize, currentHeads.size()); j++) {
                ItemStack head = currentHeads.get(j).clone();
                int x = (j - i) % 9;
                int y = (j - i) / 9;
                page.addItem(new GuiItem(head, event -> {
                    var meta = head.getItemMeta();
                    if (meta instanceof SkullMeta skullMeta
                            && skullMeta.getOwningPlayer() != null) {
                        var name = skullMeta.getOwningPlayer().getName();
                        if (name != null) {
                            close();
                            if (callback != null) {
                                callback.accept(ScreenResult.answer(name));
                            }
                        }
                    }
                }), x, y);
            }
            headPane.addPane(page);
        }
        gui.addPane(Slot.of(0, 0), headPane);

        // Build the navigation control pane
        var controlY = rows - 1;
        var controlPane = buildControlPane(promptConfig);
        gui.addPane(Slot.of(0, controlY), controlPane);

        gui.setOnClose(event -> {
            if (open) {
                open = false;
                if (callback != null) {
                    callback.accept(ScreenResult.cancel());
                }
            }
        });

        gui.update();
        player.openInventory(gui.getInventory());
        open = true;
    }

    /**
     * Applies the tag's filter (world, radial, self, or custom) to the
     * head cache, returning the matching player heads.
     */
    private List<ItemStack> getFilteredHeads() {
        var headCache = plugin.getHeadCache();
        var promptConfig = plugin.getConfigLoader().getPromptConfig();

        if (tag.filter() == null || tag.filter().isBlank()) {
            int onlineCount = (int) Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !headCache.isVanished(p))
                    .count();
            var heads = promptConfig.sorted() ? headCache.getHeadsSorted() : headCache.getHeads();
            // Cache staleness is handled proactively in open() via cache-mismatch check.
            // If we arrive here with empty heads despite online players, log and
            // return empty — open() will eventually rebuild and re-trigger.
            if (heads.isEmpty() && onlineCount > 0) {
                plugin.getPluginLogger().debug("PlayerUI heads empty but onlineVisible="
                        + onlineCount + " cacheSize=" + headCache.size()
                        + " — cache may still be loading");
            }
            plugin.getPluginLogger().debug("PlayerUI no filter, heads=" + heads.size()
                    + " cacheSize=" + headCache.size()
                    + " onlineVisible=" + onlineCount);
            return heads;
        }

        var filterOpt = headCache.getFilters().stream()
                .filter(f -> f.getRegexKey().matcher(tag.filter()).find())
                .map(f -> f.reConstruct(tag.filter()))
                .findFirst();

        if (filterOpt.isPresent()) {
            plugin.getPluginLogger().debug("PlayerUI applying filter: " + tag.filter());
            var filteredPlayers = filterOpt.get().filter(player);
            var heads = filteredPlayers.stream()
                    .map(headCache::getHeadFor)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .toList();
            plugin.getPluginLogger().debug("PlayerUI filtered heads=" + heads.size());
            if (promptConfig.sorted()) {
                var sorted = new ArrayList<>(heads);
                sorted.sort((s1, s2) -> {
                    var n1 = s1.getItemMeta() != null ? s1.getItemMeta().getDisplayName() : "";
                    var n2 = s2.getItemMeta() != null ? s2.getItemMeta().getDisplayName() : "";
                    return n1.compareToIgnoreCase(n2);
                });
                return sorted;
            }
            return heads;
        }

        plugin.getPluginLogger().debug("PlayerUI no matching filter, using all heads");
        return promptConfig.sorted() ? headCache.getHeadsSorted() : headCache.getHeads();
    }

    private StaticPane buildControlPane(PromptConfig cfg) {
        var control = new StaticPane(9, 1);

        boolean isPreset = puiPrompt != null && !puiPrompt.id().startsWith("inline-");

        if (isPreset && puiPrompt.previousButton() != null) {
            if (puiPrompt.previousButton().show()) {
                control.addItem(buildItem(puiPrompt.previousButton().buttonIcon(), puiPrompt.previousButton().customModelData(),
                        puiPrompt.previousButton().buttonText(), event -> {
                            headPane.previous();
                            gui.update();
                        }), puiPrompt.previousButton().slot(), 0);
            }
        } else {
            control.addItem(buildItem(cfg.previousItem(), cfg.previousCustomModelData(),
                    cfg.previousText(), event -> {
                        headPane.previous();
                        gui.update();
                    }), cfg.previousColumn() - 1, 0);
        }

        if (isPreset && puiPrompt.nextButton() != null) {
            if (puiPrompt.nextButton().show()) {
                control.addItem(buildItem(puiPrompt.nextButton().buttonIcon(), puiPrompt.nextButton().customModelData(),
                        puiPrompt.nextButton().buttonText(), event -> {
                            headPane.next();
                            gui.update();
                        }), puiPrompt.nextButton().slot(), 0);
            }
        } else {
            control.addItem(buildItem(cfg.nextItem(), cfg.nextCustomModelData(),
                    cfg.nextText(), event -> {
                        headPane.next();
                        gui.update();
                    }), cfg.nextColumn() - 1, 0);
        }

        if (isPreset && puiPrompt.cancelButton() != null) {
            if (puiPrompt.cancelButton().show()) {
                control.addItem(buildItem(puiPrompt.cancelButton().buttonIcon(), puiPrompt.cancelButton().customModelData(),
                        puiPrompt.cancelButton().buttonText(), event -> {
                            close();
                            if (callback != null) {
                                callback.accept(ScreenResult.cancel());
                            }
                        }), puiPrompt.cancelButton().slot(), 0);
            }
        } else {
            control.addItem(buildItem(cfg.cancelItem(), cfg.cancelCustomModelData(),
                    cfg.cancelText(), event -> {
                        close();
                        if (callback != null) {
                            callback.accept(ScreenResult.cancel());
                        }
                    }), cfg.cancelColumn() - 1, 0);
        }

        control.addItem(buildItem(cfg.searchItem(), cfg.searchCustomModelData(),
                cfg.searchText(), event -> startSearch()), cfg.searchColumn() - 1, 0);

        return control;
    }

    /**
     * Closes the GUI and listens for the player's next chat message
     * to use as a search term, then reopens with filtered results.
     */
    private void startSearch() {
        plugin.getPluginLogger().debug("PlayerUI search started for " + player.getName());
        open = false;
        player.closeInventory();
        player.sendMessage(plugin.getConfigLoader().getI18n().get("player_ui.search_instruction"));

        var listener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                event.setCancelled(true);
                var search = event.getMessage();

                player.getScheduler().run(plugin, st -> {
                    plugin.getPluginLogger().debug("PlayerUI search: term=" + search
                            + " pre-filter=" + currentHeads.size());
                    org.bukkit.event.HandlerList.unregisterAll(this);
                    searchListener = null;
                    var filtered = currentHeads.stream()
                            .filter(item -> {
                                var meta = item.getItemMeta();
                                if (meta == null) return false;
                                var name = meta.getDisplayName();
                                return name.toLowerCase().contains(search.toLowerCase());
                            })
                            .toList();
                    currentHeads = new ArrayList<>(filtered);
                    open();
                }, null);
            }
        };
        this.searchListener = listener;
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    private void registerQuitListener() {
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                    if (searchListener != null) {
                        org.bukkit.event.HandlerList.unregisterAll(searchListener);
                        searchListener = null;
                    }
                }
            }
        }, plugin);
    }

    private GuiItem buildItem(String materialName, int cmd, String displayName,
                              Consumer<InventoryClickEvent> action) {
        var mat = Material.matchMaterial(materialName);
        if (mat == null) mat = Material.PAPER;
        var item = new ItemStack(mat);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ComponentUtil.mini("<!italic>" + displayName));
            if (cmd != 0) meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        return new GuiItem(item, action);
    }

    @Override
    public void close() {
        if (!open && gui == null) return;
        open = false;
        plugin.getPluginLogger().debug("PlayerUI closing for " + player.getName()
                + " searchActive=" + (searchListener != null));
        if (searchListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(searchListener);
            searchListener = null;
        }
        if (gui != null) {
            player.closeInventory();
            gui = null;
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void onResult(Consumer<ScreenResult> callback) {
        this.callback = callback;
    }
}
