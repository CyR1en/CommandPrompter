package dev.cyr1en.promptui.v26_2;

import dev.cyr1en.promptui.AnvilInputScreen;
import dev.cyr1en.promptui.ComponentUtil;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.gui.AnvilGui;
import dev.cyr1en.promptui.gui.Gui;
import dev.cyr1en.promptui.gui.GuiItem;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/** {@link AnvilInputScreen} backed by {@link AnvilGui} and the version-specific NMS inventory. */
public final class FrameworkAnvilScreen implements AnvilInputScreen {

  private final JavaPlugin plugin;
  private final Player player;
  private final String displayText;
  private Map<String, String> config = new HashMap<>();
  private AnvilGui anvilGui;
  private AnvilInventoryImpl inventoryImpl;
  private Consumer<ScreenResult> callback;
  private Consumer<Throwable> openFailure;
  private ScheduledTask openTask;
  private State state = State.NEW;

  private enum State {
    NEW,
    CLOSED,
    OPENING,
    OPEN
  }

  public FrameworkAnvilScreen(
      @NotNull JavaPlugin plugin, @NotNull Player player, @NotNull String displayText) {
    this.plugin = plugin;
    this.player = player;
    this.displayText = displayText;
  }

  @Override
  public void configure(Map<String, String> config) {
    this.config = new HashMap<>(config);
  }

  /**
   * Schedules the anvil GUI construction on the player's thread: creates the NMS inventory, applies
   * config, wires close/result callbacks, then opens it.
   */
  @Override
  public void open() {
    synchronized (this) {
      if (state != State.NEW) return;
      state = State.OPENING;
    }

    ScheduledTask scheduled;
    try {
      scheduled =
          player
              .getScheduler()
              .run(
                  plugin,
                  ignored -> openOnPlayerThread(),
                  () -> failOpen(new IllegalStateException("Player scheduler retired")));
    } catch (Throwable failure) {
      failOpen(failure);
      return;
    }
    synchronized (this) {
      if (state == State.OPENING) {
        openTask = scheduled;
      } else if (scheduled != null) {
        scheduled.cancel();
      }
    }
    if (scheduled == null) {
      failOpen(new IllegalStateException("Player scheduler returned no task"));
    }
  }

  private void openOnPlayerThread() {
    synchronized (this) {
      if (state != State.OPENING) return;
      openTask = null;
    }
    try {
      inventoryImpl = new AnvilInventoryImpl(player);
      anvilGui = new AnvilGui(plugin, inventoryImpl);

      configureTitle();
      setupItems();

      anvilGui.setOnClose(event -> complete(ScreenResult.guiExit(), false));
      anvilGui.setOnTopClick(event -> event.setCancelled(true));
      anvilGui.setOnResultClick(
          event -> {
            if (!isOpen()) return;
            String answer = inventoryImpl.getRenameText();
            plugin
                .getSLF4JLogger()
                .debug("FrameworkAnvilScreen result: player={}", player.getName());
            complete(ScreenResult.answer(answer), true);
          });

      anvilGui.createInventory();
      anvilGui.update();
      synchronized (this) {
        if (state != State.OPENING) {
          cleanupScreen(anvilGui, inventoryImpl, false);
          return;
        }
      }
      // The NMS packet path does not emit Bukkit's normal open event, so
      // perform the same viewer/cache registration explicitly.
      anvilGui.getHumanEntityCache().storeAndClear(player);
      anvilGui.markViewer(player);
      inventoryImpl.open();
      synchronized (this) {
        if (state == State.OPENING) {
          state = State.OPEN;
        } else {
          cleanupScreen(anvilGui, inventoryImpl, false);
          return;
        }
      }
      plugin.getSLF4JLogger().debug("FrameworkAnvilScreen opened: player={}", player.getName());
    } catch (Throwable failure) {
      failOpen(failure);
    }
  }

  /**
   * Applies the anvil title from config, falling back to the display text (truncated at {@code
   * {br}}) when no custom title is set.
   */
  private void configureTitle() {
    boolean enableTitle = Boolean.parseBoolean(config.getOrDefault("enableTitle", "true"));
    if (!enableTitle) {
      anvilGui.setTitle("");
      return;
    }
    String customTitle = config.getOrDefault("customTitle", "");
    if (!customTitle.isEmpty()) {
      anvilGui.setTitle(customTitle);
      return;
    }
    int brIndex = displayText.indexOf("{br}");
    String title = brIndex >= 0 ? displayText.substring(0, brIndex) : displayText;
    anvilGui.setTitle(title.isEmpty() ? " " : title);
  }

  /**
   * Builds and places the input, result, and optional cancel items into the anvil GUI components
   * based on config values.
   */
  private void setupItems() {
    boolean enableFirstItem = Boolean.parseBoolean(config.getOrDefault("enableFirstItem", "true"));
    if (enableFirstItem) {
      ItemStack firstItem =
          buildConfiguredItem(
              config.getOrDefault("anvilItem", "Paper"),
              config.getOrDefault("itemHideTooltips", "false"),
              config.getOrDefault("itemCustomModelData", "0"),
              config.getOrDefault("itemAnvilEnchanted", "false"));

      var meta = firstItem.getItemMeta();
      if (meta != null) {
        String promptMsg = config.getOrDefault("promptMessage", "");
        if (!promptMsg.isEmpty()) {
          meta.displayName(ComponentUtil.mini("<!italic>" + promptMsg));
        }
        String hoverText = config.getOrDefault("itemHoverText", "");
        if (!hoverText.isEmpty()) {
          meta.lore(List.of(ComponentUtil.mini("<!italic>" + hoverText)));
        }
        firstItem.setItemMeta(meta);
      }

      GuiItem firstGuiItem = new GuiItem(firstItem);
      anvilGui.getFirstItemComponent().addItem(firstGuiItem, 0, 0);
    }

    // Result item (triggers submission). Click is routed by AnvilGui
    // through setOnResultClick below — NMS overwrites the result ItemStack
    // on submit, stripping the UUID tag the pane system relies on, so a
    // GuiItem-level click action would never fire here.
    ItemStack resultItem =
        buildConfiguredItem(
            config.getOrDefault("anvilResultItem", "Paper"),
            config.getOrDefault("resultItemHideTooltips", "false"),
            config.getOrDefault("resultItemCustomModelData", "0"),
            config.getOrDefault("resultItemAnvilEnchanted", "false"));

    GuiItem resultGuiItem = new GuiItem(resultItem);
    anvilGui.getResultComponent().addItem(resultGuiItem, 0, 0);

    boolean enableCancel = Boolean.parseBoolean(config.getOrDefault("enableCancelItem", "false"));
    if (enableCancel) {
      ItemStack cancelItem =
          buildConfiguredItem(
              config.getOrDefault("anvilCancelItem", "Barrier"),
              config.getOrDefault("cancelItemHideTooltips", "false"),
              config.getOrDefault("cancelItemCustomModelData", "0"),
              config.getOrDefault("cancelItemAnvilEnchanted", "false"));

      var meta = cancelItem.getItemMeta();
      if (meta != null) {
        String cancelMsg = config.getOrDefault("cancelItemMessage", "");
        if (!cancelMsg.isEmpty()) {
          meta.displayName(ComponentUtil.mini("<!italic>" + cancelMsg));
        }
        String hoverText = config.getOrDefault("cancelItemHoverText", "");
        if (!hoverText.isEmpty()) {
          meta.lore(List.of(ComponentUtil.mini("<!italic>" + hoverText)));
        }
        cancelItem.setItemMeta(meta);
      }

      GuiItem cancelGuiItem =
          new GuiItem(
              cancelItem,
              event -> {
                if (!isOpen()) return;
                plugin
                    .getSLF4JLogger()
                    .debug("FrameworkAnvilScreen cancel: player={}", player.getName());
                complete(ScreenResult.manualCancel(), true);
              });
      anvilGui.getSecondItemComponent().addItem(cancelGuiItem, 0, 0);
    }
  }

  /**
   * Builds an {@link ItemStack} from config strings for material, tooltip visibility, custom model
   * data, and enchant-glint, falling back to {@link Material#PAPER}.
   */
  private ItemStack buildConfiguredItem(
      String materialName, String hideTooltips, String customModelData, String enchanted) {
    boolean hide = Boolean.parseBoolean(hideTooltips);
    int cmd = Integer.parseInt(customModelData);
    boolean isEnchanted = Boolean.parseBoolean(enchanted);

    Material mat = Material.matchMaterial(materialName);
    if (mat == null) mat = Material.PAPER;

    ItemStack item = new ItemStack(mat);
    var meta = item.getItemMeta();
    if (meta != null) {
      if (cmd != 0) meta.setCustomModelData(cmd);
      if (isEnchanted) {
        meta.addEnchant(Enchantment.FLAME, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
      }
      if (hide) meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
      item.setItemMeta(meta);
    }
    return item;
  }

  @Override
  public void close() {
    AnvilGui gui;
    AnvilInventoryImpl impl;
    ScheduledTask task;
    synchronized (this) {
      if (state == State.CLOSED) return;
      state = State.CLOSED;
      task = openTask;
      openTask = null;
      gui = anvilGui;
      impl = inventoryImpl;
      anvilGui = null;
      inventoryImpl = null;
      callback = null;
      openFailure = null;
    }
    cancel(task);
    scheduleCleanup(gui, impl, true);
  }

  private void complete(ScreenResult result, boolean closePlayer) {
    AnvilGui gui;
    AnvilInventoryImpl impl;
    ScheduledTask task;
    Consumer<ScreenResult> resultCallback;
    synchronized (this) {
      if (state == State.CLOSED) return;
      state = State.CLOSED;
      task = openTask;
      openTask = null;
      gui = anvilGui;
      impl = inventoryImpl;
      anvilGui = null;
      inventoryImpl = null;
      resultCallback = callback;
      callback = null;
    }
    cancel(task);
    try {
      cleanupScreen(gui, impl, closePlayer);
    } finally {
      try {
        if (resultCallback != null) {
          resultCallback.accept(result);
        }
      } finally {
        cleanupScreen(gui, impl, false);
      }
    }
  }

  private void failOpen(Throwable failure) {
    AnvilGui gui;
    AnvilInventoryImpl impl;
    ScheduledTask task;
    Consumer<Throwable> failureCallback;
    synchronized (this) {
      if (state == State.CLOSED) return;
      state = State.CLOSED;
      task = openTask;
      openTask = null;
      gui = anvilGui;
      impl = inventoryImpl;
      anvilGui = null;
      inventoryImpl = null;
      failureCallback = openFailure;
      openFailure = null;
      callback = null;
    }
    cancel(task);
    try {
      cleanupScreen(gui, impl, true);
    } finally {
      try {
        if (failureCallback != null) {
          failureCallback.accept(failure);
        }
      } finally {
        cleanupScreen(gui, impl, false);
      }
    }
  }

  private void scheduleCleanup(AnvilGui gui, AnvilInventoryImpl impl, boolean closePlayer) {
    if (gui == null && impl == null) return;
    try {
      ScheduledTask task =
          player
              .getScheduler()
              .run(
                  plugin,
                  ignored -> cleanupScreen(gui, impl, closePlayer),
                  () -> cleanupScreen(gui, impl, closePlayer));
      if (task == null) {
        cleanupScreen(gui, impl, closePlayer);
      }
    } catch (Throwable failure) {
      cleanupScreen(gui, impl, closePlayer);
    }
  }

  private void cleanupScreen(AnvilGui gui, AnvilInventoryImpl impl, boolean closePlayer) {
    try {
      if (impl != null) {
        impl.clearCallbacks();
        impl.close();
      }
    } finally {
      if (gui != null) {
        gui.setOnClose(null);
        gui.setOnResultClick(null);
        gui.setOnTopClick(null);
        Gui.removeInventories(gui);
        gui.getHumanEntityCache().restoreAndForget(player);
        gui.removeViewer(player);
        Gui.removeInventoryIfUnused(gui);

        if (closePlayer && gui.getInventory() != null) {
          try {
            if (player.getOpenInventory().getTopInventory().equals(gui.getInventory())) {
              player.closeInventory();
            }
          } catch (RuntimeException ignored) {
            // Retired/disconnected players may reject inventory
            // access; the NMS and registry cleanup already ran.
          }
        }
      }
    }
  }

  private static void cancel(ScheduledTask task) {
    if (task != null) {
      task.cancel();
    }
  }

  @Override
  public boolean isOpen() {
    synchronized (this) {
      return state == State.OPEN;
    }
  }

  @Override
  public void onResult(Consumer<ScreenResult> callback) {
    this.callback = callback;
  }

  @Override
  public void onOpenFailure(Consumer<Throwable> callback) {
    this.openFailure = callback;
  }
}
