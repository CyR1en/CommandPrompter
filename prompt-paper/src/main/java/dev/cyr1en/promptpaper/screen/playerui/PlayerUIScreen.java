package dev.cyr1en.promptpaper.screen.playerui;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptui.AnvilInputScreen;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenProvider;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    private final List<ScreenProvider> providers;
    private Consumer<ScreenResult> callback;
    private ChestGui gui;
    private PaginatedPane headPane;
    private List<ItemStack> currentHeads;
    private final AtomicLong lifecycleToken = new AtomicLong();
    private boolean open;
    private Listener searchListener;
    private Listener quitListener;

    public PlayerUIScreen(CommandPrompter plugin, Player player, PromptTag tag,
                          dev.cyr1en.promptpaper.preset.PlayerUiPrompt puiPrompt,
                          List<ScreenProvider> providers) {
        this.plugin = plugin;
        this.player = player;
        this.tag = tag;
        this.puiPrompt = puiPrompt;
        this.providers = providers;
        this.currentHeads = null;
    }

    /**
     * Opens the chest GUI with the player's filtered head list.
     *
     * <p>The display list is derived fresh from Bukkit inside
     * {@link #getFilteredHeads()}; the head cache is only a bounded
     * memoization layer and never gates the open (2.x parity, #85).</p>
     */
    @Override
    public void open() {
        var token = lifecycleToken.get();
        plugin.getPluginLogger().debug("PlayerUI open for " + player.getName()
                + " filter=" + tag.filter() + " (display decoupled from head cache)");
        openInternal(token);
    }

    private void openInternal() {
        openInternal(lifecycleToken.get());
    }

    private void openInternal(long token) {
        if (lifecycleToken.get() != token) return;
        if (currentHeads == null)
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

        // Cancel top clicks to prevent item theft from empty slots.
        gui.setOnTopClick(event -> event.setCancelled(true));

        headPane = new PaginatedPane(9, rows - 1);
        int pageSize = 9 * (rows - 1);
        if (currentHeads.isEmpty()) {
            // Empty state (#86): a single non-clickable item centered in the
            // pagination area replaces the head pages. The control pane stays
            // so navigation semantics and the quit listener are unchanged.
            var emptyPane = new StaticPane(9, rows - 1);
            emptyPane.addItem(buildEmptyStateItem(promptConfig),
                    4, Math.max(0, (rows - 2) / 2));
            headPane.addPane(emptyPane);
        } else {
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
        }
        gui.addPane(Slot.of(0, 0), headPane);

        var controlY = rows - 1;
        var controlPane = buildControlPane(promptConfig);
        gui.addPane(Slot.of(0, controlY), controlPane);

        gui.setOnClose(event -> {
            if (open) {
                open = false;
                unregisterListeners();
                if (callback != null) {
                    callback.accept(ScreenResult.cancel(dev.cyr1en.promptcore.CancelReason.GUI_EXIT));
                }
            }
        });

        gui.update();
        player.openInventory(gui.getInventory());
        open = true;
    }

    /**
     * Builds the single non-clickable empty-state item shown when the
     * filtered player list is empty, using the configured
     * {@code PlayerUI.Empty-Message}.
     */
    private GuiItem buildEmptyStateItem(PromptConfig cfg) {
        var item = new ItemStack(Material.BARRIER);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ComponentUtil.mini("<!italic>" + cfg.emptyMessage()));
            item.setItemMeta(meta);
        }
        return new GuiItem(item);
    }

    /**
     * Applies the tag's filter (world, radial, self, or custom) to the
     * head cache, returning the matching player heads.
     */
    private List<ItemStack> getFilteredHeads() {
        var headCache = plugin.getHeadCache();
        var promptConfig = plugin.getConfigLoader().getPromptConfig();

        if (tag.filter() == null || tag.filter().isBlank()) {
            // The display list comes fresh from Bukkit; the head cache is
            // only a memoization layer (2.x parity), so an empty or
            // half-loaded cache never hides online players.
            var heads = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !headCache.isVanished(p))
                    .map(headCache::getHeadFor)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .toList();
            plugin.getPluginLogger().debug("PlayerUI no filter, heads=" + heads.size());
            var result = new ArrayList<>(heads);
            if (promptConfig.sorted()) {
                var serializer = PlainTextComponentSerializer.plainText();
                result.sort((s1, s2) -> {
                    var d1 = s1.getItemMeta() != null ? s1.getItemMeta().displayName() : null;
                    var d2 = s2.getItemMeta() != null ? s2.getItemMeta().displayName() : null;
                    var n1 = d1 != null ? serializer.serialize(d1) : "";
                    var n2 = d2 != null ? serializer.serialize(d2) : "";
                    return n1.compareToIgnoreCase(n2);
                });
            }
            return result;
        }

        var filters = headCache.extractFilters(tag.filter());
        if (!filters.isEmpty()) {
            plugin.getPluginLogger().debug("PlayerUI applying " + filters.size()
                    + " filters: " + tag.filter());
            var filteredPlayers = new ArrayList<Player>(Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !headCache.isVanished(p))
                    .toList());
            for (var filter : filters) {
                filteredPlayers.retainAll(filter.filter(player));
            }
            var heads = filteredPlayers.stream()
                    .map(headCache::getHeadFor)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .toList();
            plugin.getPluginLogger().debug("PlayerUI filtered heads=" + heads.size());
            var result = new ArrayList<>(heads);
            if (promptConfig.sorted()) {
                var serializer = PlainTextComponentSerializer.plainText();
                result.sort((s1, s2) -> {
                    var d1 = s1.getItemMeta() != null ? s1.getItemMeta().displayName() : null;
                    var d2 = s2.getItemMeta() != null ? s2.getItemMeta().displayName() : null;
                    var n1 = d1 != null ? serializer.serialize(d1) : "";
                    var n2 = d2 != null ? serializer.serialize(d2) : "";
                    return n1.compareToIgnoreCase(n2);
                });
            }
            return applyFirstFilterFormat(result, filters.get(0), promptConfig);
        }

        plugin.getPluginLogger().debug("PlayerUI no matching filter, using all heads");
        return promptConfig.sorted() ? headCache.getHeadsSorted() : headCache.getHeads();
    }

    /**
     * Applies the display format of the first (leftmost) combined filter to
     * the given heads, returning cloned items so the shared head cache is
     * never mutated. When the filter has no specific format configured
     * (the {@code "%s"} default), the heads are returned unchanged and keep
     * the global {@code PlayerUI.Skull-Name-Format} applied at cache time.
     *
     * <p>Only the first filter's format is queried; remaining filters
     * contribute player sets only (2.16.0 semantics).</p>
     */
    private List<ItemStack> applyFirstFilterFormat(List<ItemStack> heads, CacheFilter firstFilter,
                                                   PromptConfig config) {
        var format = firstFilter.getFormat(config);
        if (format == null || format.isBlank() || "%s".equals(format)) return heads;
        var formatted = new ArrayList<ItemStack>(heads.size());
        for (ItemStack head : heads) {
            var clone = head.clone();
            var meta = clone.getItemMeta();
            if (meta instanceof SkullMeta skull && skull.getOwningPlayer() != null
                    && skull.getOwningPlayer().getName() != null) {
                skull.displayName(ComponentUtil.mini("<!italic>"
                        + format.formatted(skull.getOwningPlayer().getName())));
                clone.setItemMeta(skull);
            }
            formatted.add(clone);
        }
        return formatted;
    }

    private int validateSlot(int slot, String buttonName) {
        if (slot < 0 || slot > 8) {
            plugin.getPluginLogger().warn("PlayerUI slot for " + buttonName + " out of bounds (0-8): " + slot + ", clamping to range");
            return Math.max(0, Math.min(8, slot));
        }
        return slot;
    }

    private StaticPane buildControlPane(PromptConfig cfg) {
        var control = new StaticPane(9, 1);

        boolean isPreset = puiPrompt != null && !puiPrompt.id().startsWith("inline-");

        if (isPreset && puiPrompt.previousButton() != null) {
            var btn = puiPrompt.previousButton();
            if (btn.show()) {
                int slot = validateSlot(btn.slot(), "previous_button");
                control.addItem(buildItem(btn.buttonIcon(), btn.customModelData(),
                        btn.buttonText(), btn.buttonHoverText(), event -> {
                            headPane.previous();
                            gui.update();
                        }), slot, 0);
            }
        } else {
            int slot = validateSlot(cfg.previousColumn() - 1, "previous_column");
            control.addItem(buildItem(cfg.previousItem(), cfg.previousCustomModelData(),
                    cfg.previousText(), null, event -> {
                        headPane.previous();
                        gui.update();
                    }), slot, 0);
        }

        if (isPreset && puiPrompt.nextButton() != null) {
            var btn = puiPrompt.nextButton();
            if (btn.show()) {
                int slot = validateSlot(btn.slot(), "next_button");
                control.addItem(buildItem(btn.buttonIcon(), btn.customModelData(),
                        btn.buttonText(), btn.buttonHoverText(), event -> {
                            headPane.next();
                            gui.update();
                        }), slot, 0);
            }
        } else {
            int slot = validateSlot(cfg.nextColumn() - 1, "next_column");
            control.addItem(buildItem(cfg.nextItem(), cfg.nextCustomModelData(),
                    cfg.nextText(), null, event -> {
                        headPane.next();
                        gui.update();
                    }), slot, 0);
        }

        if (isPreset && puiPrompt.cancelButton() != null) {
            var btn = puiPrompt.cancelButton();
            if (btn.show()) {
                int slot = validateSlot(btn.slot(), "cancel_button");
                control.addItem(buildItem(btn.buttonIcon(), btn.customModelData(),
                        btn.buttonText(), btn.buttonHoverText(), event -> {
                            close();
                            if (callback != null) {
                                callback.accept(ScreenResult.cancel(dev.cyr1en.promptcore.CancelReason.MANUAL));
                            }
                        }), slot, 0);
            }
        } else {
            int slot = validateSlot(cfg.cancelColumn() - 1, "cancel_column");
            control.addItem(buildItem(cfg.cancelItem(), cfg.cancelCustomModelData(),
                    cfg.cancelText(), null, event -> {
                        close();
                        if (callback != null) {
                            callback.accept(ScreenResult.cancel(dev.cyr1en.promptcore.CancelReason.MANUAL));
                        }
                    }), slot, 0);
        }

        int searchSlot = validateSlot(cfg.searchColumn() - 1, "search_column");
        control.addItem(buildItem(cfg.searchItem(), cfg.searchCustomModelData(),
                cfg.searchText(), null, event -> startSearch()), searchSlot, 0);

        return control;
    }

    /**
     * Starts a search for a player: tries each {@link ScreenProvider} to open
     * an anvil input first, and falls back to a chat message when no provider
     * succeeds. The result filters the currently displayed heads and reopens
     * the GUI with the matching subset.
     */
    private void startSearch() {
        var token = lifecycleToken.get();
        var playerUuid = player.getUniqueId();
        plugin.getPluginLogger().debug("PlayerUI search started for " + player.getName());
        open = false;
        player.closeInventory();

        for (var provider : providers) {
            if (tryOpenAnvilSearch(provider, token, playerUuid)) return;
        }
        startChatSearch(token, playerUuid);
    }

    /**
     * Attempts to open an anvil search screen through the given provider.
     *
     * @return true if the provider produced an {@link AnvilInputScreen} that
     *         was configured and opened; false otherwise
     */
    private boolean tryOpenAnvilSearch(ScreenProvider provider, long token, UUID playerUuid) {
        try {
            var cfg = plugin.getConfigLoader().getPromptConfig();
            var candidate = provider.createAnvil(plugin, player, cfg.searchAnvilItemTitle());
            if (candidate instanceof AnvilInputScreen anvilScreen) {
                var config = new HashMap<String, String>();
                config.put("enableTitle", "true");
                config.put("customTitle", cfg.searchAnvilItemTitle());
                config.put("promptMessage", cfg.searchAnvilItemText());
                config.put("anvilItem", cfg.searchAnvilItem());
                config.put("itemCustomModelData", String.valueOf(cfg.searchAnvilItemCustomModelData()));
                config.put("displayText", cfg.searchAnvilItemText());
                config.put("enableCancelItem", "true");
                config.put("anvilCancelItem", cfg.cancelItem());
                anvilScreen.configure(config);
                anvilScreen.onResult(result -> handleSearchResult(result, token, anvilScreen));
                anvilScreen.onOpenFailure(failure ->
                        continueWithNextProviderOrChatFallback(provider, token, playerUuid));
                anvilScreen.open();
                plugin.getPluginLogger().debug("PlayerUI anvil search provider succeeded: "
                        + provider.getClass().getSimpleName());
                return true;
            }
        } catch (Throwable t) {
            plugin.getPluginLogger().debug("PlayerUI anvil search provider "
                    + provider.getClass().getSimpleName() + " failed: " + t.getMessage());
        }
        return false;
    }

    /**
     * Continues the search after an anvil provider failed asynchronously:
     * tries the remaining providers, then falls back to chat.
     */
    private void continueWithNextProviderOrChatFallback(
            ScreenProvider failedProvider, long token, UUID playerUuid) {
        if (lifecycleToken.get() != token) return;
        plugin.getPluginLogger().debug("PlayerUI anvil search provider failed asynchronously: "
                + failedProvider.getClass().getSimpleName());
        for (var provider : providers) {
            if (provider == failedProvider) continue;
            if (tryOpenAnvilSearch(provider, token, playerUuid)) return;
        }
        startChatSearch(token, playerUuid);
    }

    /**
     * Handles the result of an anvil search: a cancellation reopens the GUI
     * with the existing heads, otherwise the answer filters the current head
     * list (case-insensitive display-name match) before reopening.
     */
    private void handleSearchResult(ScreenResult result, long token, AnvilInputScreen source) {
        if (lifecycleToken.get() != token) return;
        plugin.getPluginLogger().debug("PlayerUI anvil search result for " + player.getName()
                + " cancelled=" + result.cancelled()
                + " source=" + source.getClass().getSimpleName());
        if (result.cancelled()) {
            open();
            return;
        }
        var term = result.answer();
        try {
            var task = player.getScheduler().run(plugin, st -> {
                if (lifecycleToken.get() != token || currentHeads == null) return;
                plugin.getPluginLogger().debug("PlayerUI search started; pre-filter="
                        + currentHeads.size());
                var serializer = PlainTextComponentSerializer.plainText();
                var filtered = currentHeads.stream()
                        .filter(item -> {
                            var meta = item.getItemMeta();
                            if (meta == null) return false;
                            var d = meta.displayName();
                            var name = d != null ? serializer.serialize(d) : "";
                            return name.toLowerCase().contains(term.toLowerCase());
                        })
                        .toList();
                currentHeads = new ArrayList<>(filtered);
                open();
            }, () -> {});
            if (task == null) discardScreenState(player.getUniqueId());
        } catch (Exception e) {
            discardScreenState(player.getUniqueId());
        }
    }

    /**
     * Falls back to the chat-based search: instructs the player to type the
     * search term and listens for the next chat message to filter the
     * currently displayed heads.
     */
    private void startChatSearch(long token, UUID playerUuid) {
        player.sendMessage(plugin.getConfigLoader().getI18n().get("player_ui.search_instruction", player));

        if (searchListener != null) {
            HandlerList.unregisterAll(searchListener);
            searchListener = null;
        }
        var listener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(playerUuid)) return;
                if (lifecycleToken.get() != token) return;
                event.setCancelled(true);
                var search = PlainTextComponentSerializer.plainText().serialize(event.message());

                try {
                    var task = player.getScheduler().run(plugin, st -> {
                        if (lifecycleToken.get() != token || currentHeads == null) return;
                        plugin.getPluginLogger().debug("PlayerUI chat search started; pre-filter="
                                + currentHeads.size());
                        HandlerList.unregisterAll(this);
                        searchListener = null;
                        var serializer = PlainTextComponentSerializer.plainText();
                        var filtered = currentHeads.stream()
                                .filter(item -> {
                                    var meta = item.getItemMeta();
                                    if (meta == null) return false;
                                    var d = meta.displayName();
                                    var name = d != null ? serializer.serialize(d) : "";
                                    return name.toLowerCase().contains(search.toLowerCase());
                                })
                                .toList();
                        currentHeads = new ArrayList<>(filtered);
                        open();
                    }, () -> {});
                    if (task == null) discardScreenState(playerUuid);
                } catch (Exception e) {
                    discardScreenState(playerUuid);
                }
            }
        };
        this.searchListener = listener;
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    private void registerQuitListener() {
        if (quitListener != null) {
            HandlerList.unregisterAll(quitListener);
        }
        var listener = new Listener() {
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                    invalidateCallbacks();
                    unregisterListeners();
                }
            }
        };
        quitListener = listener;
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    private void unregisterListeners() {
        if (searchListener != null) {
            HandlerList.unregisterAll(searchListener);
            searchListener = null;
        }
        if (quitListener != null) {
            HandlerList.unregisterAll(quitListener);
            quitListener = null;
        }
    }

    @SuppressWarnings("deprecation")
    private void applyCustomModelData(ItemMeta meta, int cmd) {
        try {
            var comp = meta.getCustomModelDataComponent();
            comp.setFloats(List.of((float) cmd));
            meta.setCustomModelDataComponent(comp);
        } catch (NoSuchMethodError e) {
            meta.setCustomModelData(cmd);
        }
    }

    private GuiItem buildItem(String materialName, int cmd, String displayName,
                              String hoverText,
                              Consumer<InventoryClickEvent> action) {
        var mat = Material.matchMaterial(materialName);
        if (mat == null) mat = Material.PAPER;
        var item = new ItemStack(mat);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ComponentUtil.mini("<!italic>" + displayName));
            if (hoverText != null && !hoverText.isBlank()) {
                var lines = hoverText.split("\\{br\\}|\\r?\\n|\\\\n");
                var loreComponents = Arrays.stream(lines)
                        .map(line -> (Component) ComponentUtil.mini("<!italic>" + line))
                        .toList();
                meta.lore(loreComponents);
            }
            if (cmd != 0) applyCustomModelData(meta, cmd);
            item.setItemMeta(meta);
        }
        return new GuiItem(item, action);
    }

    @Override
    public void close() {
        lifecycleToken.incrementAndGet();
        open = false;
        plugin.getPluginLogger().debug("PlayerUI closing for " + player.getName()
                + " searchActive=" + (searchListener != null));
        unregisterListeners();
        if (gui != null) {
            player.closeInventory();
            gui = null;
        }
    }

    public void invalidateCallbacks() {
        lifecycleToken.incrementAndGet();
        open = false;
    }

    private void discardScreenState(java.util.UUID uuid) {
        var manager = plugin.getScreenManager();
        if (manager != null) manager.discardState(uuid);
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
