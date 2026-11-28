package dev.cyr1en.promptpaper.screen.playerui;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.hook.hooks.GeyserHook;
import dev.cyr1en.promptui.AnvilInputScreen;
import dev.cyr1en.promptui.AnvilItemPresentation;
import dev.cyr1en.promptui.ComponentUtil;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.gui.ChestGui;
import dev.cyr1en.promptui.gui.GuiItem;
import dev.cyr1en.promptui.gui.Slot;
import dev.cyr1en.promptui.pane.PaginatedPane;
import dev.cyr1en.promptui.pane.StaticPane;
import dev.cyr1en.promptui.util.BedrockUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Prompt screen that displays online players as clickable heads in a paginated chest GUI, with
 * search and navigation controls.
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
  private String searchQuery;
  private AnvilInputScreen searchAnvil;
  private final AtomicLong lifecycleToken = new AtomicLong();
  private boolean open;
  private Listener searchListener;
  private Listener quitListener;

  public PlayerUIScreen(
      CommandPrompter plugin,
      Player player,
      PromptTag tag,
      dev.cyr1en.promptpaper.preset.PlayerUiPrompt puiPrompt,
      List<ScreenProvider> providers) {
    this.plugin = plugin;
    this.player = player;
    this.tag = tag;
    this.puiPrompt = puiPrompt;
    this.providers = providers;
  }

  /**
   * Opens the chest GUI with the player's filtered head list.
   *
   * <p>The display list comes from Bukkit; the head cache only memoizes rendered head items.
   */
  @Override
  public void open() {
    var token = lifecycleToken.get();
    plugin
        .getPluginLogger()
        .debug(
            "PlayerUI open for "
                + player.getName()
                + " filter="
                + tag.filter()
                + " (display decoupled from head cache)");
    openInternal(token);
  }

  private void openInternal(long token) {
    if (lifecycleToken.get() != token) return;
    var heads = getFilteredHeads();
    if (searchQuery != null) {
      var term = searchQuery.toLowerCase(Locale.ROOT);
      var serializer = PlainTextComponentSerializer.plainText();
      heads =
          heads.stream()
              .filter(
                  head -> {
                    var meta = head.getItemMeta();
                    return meta != null
                        && meta.displayName() != null
                        && serializer
                            .serialize(meta.displayName())
                            .toLowerCase(Locale.ROOT)
                            .contains(term);
                  })
              .toList();
    }

    registerQuitListener();

    plugin
        .getPluginLogger()
        .debug(
            "Opening PlayerUI for "
                + player.getName()
                + " heads="
                + heads.size()
                + " filter="
                + tag.filter());

    var promptConfig = plugin.getConfigLoader().getPromptConfig();
    var size = promptConfig.playerUISize();
    int rows = size / 9;

    var openingGui = new ChestGui(plugin, rows);
    gui = openingGui;
    gui.setTitle(ComponentUtil.mini(tag.displayText()));
    gui.setPlayerInventoryUsed(false);

    // Cancel top clicks to prevent item theft from empty slots.
    gui.setOnTopClick(event -> event.setCancelled(true));

    headPane = new PaginatedPane(9, rows - 1);
    int pageSize = 9 * (rows - 1);
    if (heads.isEmpty()) {
      var emptyPane = new StaticPane(9, rows - 1);
      emptyPane.addItem(buildEmptyStateItem(promptConfig), 4, Math.max(0, (rows - 2) / 2));
      headPane.addPane(emptyPane);
    } else {
      for (int i = 0; i < heads.size(); i += pageSize) {
        StaticPane page = new StaticPane(9, rows - 1);
        for (int j = i; j < Math.min(i + pageSize, heads.size()); j++) {
          ItemStack head = heads.get(j).clone();
          int x = (j - i) % 9;
          int y = (j - i) / 9;
          page.addItem(new GuiItem(head, event -> selectHead(head)), x, y);
        }
        headPane.addPane(page);
      }
    }
    gui.addPane(Slot.of(0, 0), headPane);

    var controlY = rows - 1;
    var controlPane = buildControlPane(promptConfig);
    gui.addPane(Slot.of(0, controlY), controlPane);

    gui.setOnClose(
        event -> {
          if (gui == openingGui && open) {
            open = false;
            gui = null;
            unregisterListeners();
            if (callback != null) {
              callback.accept(ScreenResult.guiExit());
            }
          }
        });

    gui.update();
    player.openInventory(gui.getInventory());
    open = true;
  }

  /**
   * Builds the single non-clickable empty-state item shown when the filtered player list is empty,
   * using the configured {@code PlayerUI.Empty-Message}.
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
   * Applies the tag's filter (world, radial, self, or custom) to the head cache, returning the
   * matching player heads.
   */
  private List<ItemStack> getFilteredHeads() {
    var headCache = plugin.getHeadCache();
    var promptConfig = plugin.getConfigLoader().getPromptConfig();

    var filters = headCache.extractFilters(tag.filter());
    if (filters.isEmpty()) {
      var heads =
          Bukkit.getOnlinePlayers().stream()
              .filter(p -> !headCache.isVanished(p))
              .map(headCache::getHeadFor)
              .filter(java.util.Optional::isPresent)
              .map(java.util.Optional::get)
              .toList();
      plugin.getPluginLogger().debug("PlayerUI no filter, heads=" + heads.size());
      var result = new ArrayList<>(heads);
      if (promptConfig.sorted()) {
        sortHeads(result);
      }
      return result;
    }

    plugin
        .getPluginLogger()
        .debug("PlayerUI applying " + filters.size() + " filters: " + tag.filter());
    var filteredPlayers =
        new ArrayList<Player>(
            Bukkit.getOnlinePlayers().stream().filter(p -> !headCache.isVanished(p)).toList());
    for (var filter : filters) {
      filteredPlayers.retainAll(filter.filter(player));
    }
    var heads =
        filteredPlayers.stream()
            .map(headCache::getHeadFor)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .toList();
    plugin.getPluginLogger().debug("PlayerUI filtered heads=" + heads.size());
    var result = new ArrayList<>(heads);
    if (promptConfig.sorted()) {
      sortHeads(result);
    }
    return applyFirstFilterFormat(result, filters.get(0), promptConfig);
  }

  private static void sortHeads(List<ItemStack> heads) {
    var serializer = PlainTextComponentSerializer.plainText();
    heads.sort(
        Comparator.comparing(
            (ItemStack head) -> {
              var meta = head.getItemMeta();
              var displayName = meta != null ? meta.displayName() : null;
              return displayName != null ? serializer.serialize(displayName) : "";
            },
            String.CASE_INSENSITIVE_ORDER));
  }

  /** Answers the prompt with the player the clicked head belongs to, if it belongs to one. */
  void selectHead(ItemStack head) {
    var headCache = plugin.getHeadCache();
    var name = headCache.getOwnerName(head);
    if (name == null) return;
    var target = Bukkit.getPlayerExact(name);
    if (target == null
        || !target.isOnline()
        || headCache.isVanished(target)
        || headCache.extractFilters(tag.filter()).stream()
            .anyMatch(filter -> !filter.filter(player).contains(target))) return;
    close();
    if (callback != null) callback.accept(ScreenResult.answer(name));
  }

  /**
   * Applies the display format of the first (leftmost) combined filter to the given heads,
   * returning cloned items so the shared head cache is never mutated. When the filter has no
   * specific format configured (the {@code "%s"} default), the heads are returned unchanged and
   * keep the global {@code PlayerUI.Skull-Name-Format} applied at cache time.
   *
   * <p>Only the first filter's format is queried; remaining filters contribute player sets only.
   */
  private List<ItemStack> applyFirstFilterFormat(
      List<ItemStack> heads, CacheFilter firstFilter, PromptConfig config) {
    var format = firstFilter.getFormat(config);
    if (format == null || format.isBlank() || "%s".equals(format)) return heads;
    var headCache = plugin.getHeadCache();
    var formatted = new ArrayList<ItemStack>(heads.size());
    for (ItemStack head : heads) {
      var clone = head.clone();
      var name = headCache.getOwnerName(clone);
      var meta = clone.getItemMeta();
      if (name != null && meta != null) {
        meta.displayName(ComponentUtil.mini("<!italic>" + format.formatted(name)));
        clone.setItemMeta(meta);
      }
      formatted.add(clone);
    }
    return formatted;
  }

  private int validateSlot(int slot, String buttonName) {
    if (slot < 0 || slot > 8) {
      plugin
          .getPluginLogger()
          .warn(
              "PlayerUI slot for "
                  + buttonName
                  + " out of bounds (0-8): "
                  + slot
                  + ", clamping to range");
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
        control.addItem(
            buildItem(
                btn.buttonIcon(),
                btn.customModelData(),
                btn.buttonText(),
                btn.buttonHoverText(),
                event -> {
                  headPane.previous();
                  gui.update();
                }),
            slot,
            0);
      }
    } else {
      int slot = validateSlot(cfg.previousColumn() - 1, "previous_column");
      control.addItem(
          buildItem(
              cfg.previousItem(),
              cfg.previousCustomModelData(),
              cfg.previousText(),
              null,
              event -> {
                headPane.previous();
                gui.update();
              }),
          slot,
          0);
    }

    if (isPreset && puiPrompt.nextButton() != null) {
      var btn = puiPrompt.nextButton();
      if (btn.show()) {
        int slot = validateSlot(btn.slot(), "next_button");
        control.addItem(
            buildItem(
                btn.buttonIcon(),
                btn.customModelData(),
                btn.buttonText(),
                btn.buttonHoverText(),
                event -> {
                  headPane.next();
                  gui.update();
                }),
            slot,
            0);
      }
    } else {
      int slot = validateSlot(cfg.nextColumn() - 1, "next_column");
      control.addItem(
          buildItem(
              cfg.nextItem(),
              cfg.nextCustomModelData(),
              cfg.nextText(),
              null,
              event -> {
                headPane.next();
                gui.update();
              }),
          slot,
          0);
    }

    if (isPreset && puiPrompt.cancelButton() != null) {
      var btn = puiPrompt.cancelButton();
      if (btn.show()) {
        int slot = validateSlot(btn.slot(), "cancel_button");
        control.addItem(
            buildItem(
                btn.buttonIcon(),
                btn.customModelData(),
                btn.buttonText(),
                btn.buttonHoverText(),
                event -> {
                  close();
                  if (callback != null) {
                    callback.accept(ScreenResult.manualCancel());
                  }
                }),
            slot,
            0);
      }
    } else {
      int slot = validateSlot(cfg.cancelColumn() - 1, "cancel_column");
      control.addItem(
          buildItem(
              cfg.cancelItem(),
              cfg.cancelCustomModelData(),
              cfg.cancelText(),
              null,
              event -> {
                close();
                if (callback != null) {
                  callback.accept(ScreenResult.manualCancel());
                }
              }),
          slot,
          0);
    }

    int searchSlot = validateSlot(cfg.searchColumn() - 1, "search_column");
    control.addItem(
        buildItem(
            cfg.searchItem(),
            cfg.searchCustomModelData(),
            cfg.searchText(),
            null,
            event -> startSearch()),
        searchSlot,
        0);

    return control;
  }

  /**
   * Starts a search for a player: tries each {@link ScreenProvider} to open an anvil input first,
   * and falls back to a chat message when no provider succeeds.
   */
  private void startSearch() {
    var token = lifecycleToken.incrementAndGet();
    plugin.getPluginLogger().debug("PlayerUI search started for " + player.getName());
    open = false;
    gui = null;
    player.closeInventory();
    openSearch(0, token, player.getUniqueId());
  }

  private void openSearch(int firstProvider, long token, UUID playerUuid) {
    if (lifecycleToken.get() != token) return;
    for (int i = firstProvider; i < providers.size(); i++) {
      if (tryOpenAnvilSearch(i, token, playerUuid)) return;
    }
    startChatSearch(token, playerUuid);
  }

  /**
   * Attempts to open an anvil search screen through the given provider.
   *
   * @return true if the provider produced an {@link AnvilInputScreen} that was configured and
   *     opened; false otherwise
   */
  private boolean tryOpenAnvilSearch(int providerIndex, long token, UUID playerUuid) {
    var provider = providers.get(providerIndex);
    AnvilInputScreen attempted = null;
    try {
      var cfg = plugin.getConfigLoader().getPromptConfig();
      var candidate = provider.createAnvil(plugin, player, cfg.searchAnvilItemTitle());
      if (candidate instanceof AnvilInputScreen anvilScreen) {
        attempted = anvilScreen;
        searchAnvil = anvilScreen;
        boolean bedrock = BedrockUtil.isBedrockPlayer(player);
        var presentation = getSearchAnvilPresentation(playerUuid, bedrock);
        boolean nativeBedrock = bedrock && presentation.isEmpty();
        var config = new HashMap<String, String>();
        config.put("enableTitle", "true");
        config.put("customTitle", cfg.searchAnvilItemTitle());
        config.put("promptMessage", cfg.searchAnvilItemText());
        config.put("anvilItem", cfg.searchAnvilItem());
        config.put("itemCustomModelData", String.valueOf(cfg.searchAnvilItemCustomModelData()));
        config.put("displayText", cfg.searchAnvilItemText());
        config.put("geyserAnvilPatch", String.valueOf(presentation.isPresent()));
        config.put("enableCancelItem", String.valueOf(!nativeBedrock));
        config.put("anvilCancelItem", cfg.cancelItem());
        if (nativeBedrock) {
          // Bedrock predicts a rename recipe with matching input and result items.
          var material = cfg.anvilResultItem();
          var modelData = String.valueOf(cfg.resultItemCustomModelData());
          var hideTooltips = String.valueOf(cfg.resultItemHideTooltips());
          var enchanted = String.valueOf(cfg.resultItemAnvilEnchanted());
          config.put("enableFirstItem", "true");
          config.put("anvilItem", material);
          config.put("anvilResultItem", material);
          config.put("itemCustomModelData", modelData);
          config.put("resultItemCustomModelData", modelData);
          config.put("itemHideTooltips", hideTooltips);
          config.put("resultItemHideTooltips", hideTooltips);
          config.put("itemAnvilEnchanted", enchanted);
          config.put("resultItemAnvilEnchanted", enchanted);
        }
        anvilScreen.configure(config);
        presentation.ifPresent(anvilScreen::setItemPresentation);
        anvilScreen.onResult(result -> handleSearchResult(result, token, anvilScreen));
        anvilScreen.onOpenFailure(
            failure -> {
              if (lifecycleToken.get() != token || searchAnvil != anvilScreen) return;
              closeSearchAnvil();
              openSearch(providerIndex + 1, token, playerUuid);
            });
        if (searchAnvil == anvilScreen) {
          if (lifecycleToken.get() == token) anvilScreen.open();
          else closeSearchAnvil();
        }
        plugin
            .getPluginLogger()
            .debug(
                "PlayerUI anvil search provider succeeded: " + provider.getClass().getSimpleName());
        return true;
      }
    } catch (Throwable t) {
      if (attempted != null) {
        if (searchAnvil != attempted) return true;
        closeSearchAnvil();
      }
      plugin
          .getPluginLogger()
          .debug(
              "PlayerUI anvil search provider "
                  + provider.getClass().getSimpleName()
                  + " failed: "
                  + t.getMessage());
    }
    return false;
  }

  private Optional<AnvilItemPresentation> getSearchAnvilPresentation(
      UUID playerUuid, boolean bedrock) {
    var standalone = plugin.getGeyserStandaloneSupport();
    var presentation =
        standalone == null
            ? Optional.<AnvilItemPresentation>empty()
            : standalone.itemPresentation(playerUuid);
    var hooks = plugin.getHookContainer();
    if (presentation.isPresent() || !bedrock || hooks == null) {
      return presentation;
    }
    return hooks
        .getHook(GeyserHook.class)
        .filter(GeyserHook::isAnvilPatchEnabled)
        .map(GeyserHook::itemPresentation);
  }

  private void handleSearchResult(ScreenResult result, long token, AnvilInputScreen source) {
    if (lifecycleToken.get() != token || searchAnvil != source) return;
    closeSearchAnvil();
    plugin
        .getPluginLogger()
        .debug(
            "PlayerUI anvil search result for "
                + player.getName()
                + " cancelled="
                + result.cancelled()
                + " source="
                + source.getClass().getSimpleName());
    try {
      var task =
          player
              .getScheduler()
              .run(
                  plugin,
                  st -> {
                    if (lifecycleToken.get() != token) return;
                    if (!result.cancelled()) searchQuery = result.answer();
                    openInternal(token);
                  },
                  () -> {});
      if (task == null) discardScreenState(player.getUniqueId());
    } catch (Exception e) {
      discardScreenState(player.getUniqueId());
    }
  }

  /**
   * Falls back to the chat-based search: instructs the player to type the search term and listens
   * for the next chat message to use as the search term.
   */
  private void startChatSearch(long token, UUID playerUuid) {
    player.sendMessage(
        plugin.getConfigLoader().getI18n().get("player_ui.search_instruction", player));

    if (searchListener != null) {
      HandlerList.unregisterAll(searchListener);
      searchListener = null;
    }
    var listener =
        new org.bukkit.event.Listener() {
          private final AtomicBoolean claimed = new AtomicBoolean();

          @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
          public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
            if (!event.getPlayer().getUniqueId().equals(playerUuid)) return;
            if (lifecycleToken.get() != token || !claimed.compareAndSet(false, true)) return;
            event.setCancelled(true);
            var search = PlainTextComponentSerializer.plainText().serialize(event.message());

            try {
              var task =
                  player
                      .getScheduler()
                      .run(
                          plugin,
                          st -> {
                            if (lifecycleToken.get() != token) return;
                            HandlerList.unregisterAll(this);
                            searchListener = null;
                            searchQuery = search;
                            openInternal(token);
                          },
                          () -> {});
              if (task == null) discardScreenState(playerUuid);
            } catch (Exception e) {
              discardScreenState(playerUuid);
            }
          }
        };
    this.searchListener = listener;
    Bukkit.getPluginManager().registerEvents(listener, plugin);
  }

  private void closeSearchAnvil() {
    var anvil = searchAnvil;
    searchAnvil = null;
    if (anvil == null) return;
    try {
      anvil.close();
    } catch (Throwable failure) {
      plugin
          .getPluginLogger()
          .debug("PlayerUI anvil search cleanup failed: " + failure.getMessage());
    }
  }

  private void registerQuitListener() {
    if (quitListener != null) {
      HandlerList.unregisterAll(quitListener);
    }
    var listener =
        new Listener() {
          @org.bukkit.event.EventHandler
          public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
            if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
              invalidateCallbacks();
              unregisterListeners();
              closeSearchAnvil();
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

  private GuiItem buildItem(
      String materialName,
      int cmd,
      String displayName,
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
        var loreComponents =
            Arrays.stream(lines).map(line -> ComponentUtil.mini("<!italic>" + line)).toList();
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
    plugin
        .getPluginLogger()
        .debug(
            "PlayerUI closing for "
                + player.getName()
                + " searchActive="
                + (searchListener != null));
    unregisterListeners();
    closeSearchAnvil();
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
