package dev.cyr1en.promptui.v26_1;

import dev.cyr1en.promptui.AnvilInputScreen;
import dev.cyr1en.promptui.ComponentUtil;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.gui.AnvilGui;
import dev.cyr1en.promptui.gui.GuiItem;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Adapter that wraps the new framework's {@link AnvilGui} to implement
 * the existing {@link AnvilInputScreen} contract.
 *
 * <p>This replaces the old {@link AnvilScreenImpl} which used raw NMS.
 * The NMS details are now encapsulated in {@link AnvilInventoryImpl}.</p>
 */
public final class FrameworkAnvilScreen implements AnvilInputScreen {

    private final JavaPlugin plugin;
    private final Player player;
    private final String displayText;
    private Map<String, String> config = new HashMap<>();
    private AnvilGui anvilGui;
    private AnvilInventoryImpl inventoryImpl;
    private Consumer<ScreenResult> callback;
    private boolean open;

    public FrameworkAnvilScreen(@NotNull JavaPlugin plugin, @NotNull Player player,
                                 @NotNull String displayText) {
        this.plugin = plugin;
        this.player = player;
        this.displayText = displayText;
    }

    @Override
    public void configure(Map<String, String> config) {
        this.config = new HashMap<>(config);
    }

    /**
     * Schedules the anvil GUI construction on the player's thread: creates the
     * NMS inventory, applies config, wires close/result callbacks, then opens it.
     */
    @Override
    public void open() {
        player.getScheduler().run(plugin, scheduledTask -> {
            inventoryImpl = new AnvilInventoryImpl(player);
            anvilGui = new AnvilGui(plugin, inventoryImpl);

            configureTitle();
            setupItems();

            anvilGui.setOnClose(event -> {
                if (open) {
                    open = false;
                    if (callback != null) {
                        callback.accept(ScreenResult.cancel());
                    }
                }
            });

            // Prevent item theft from anvil slots
            anvilGui.setOnTopClick(event -> event.setCancelled(true));

            // Direct callback bypasses UUID matching since NMS strips result item UUID tags
            anvilGui.setOnResultClick(event -> {
                if (!open) return;
                open = false;
                String answer = inventoryImpl.getRenameText();
                plugin.getSLF4JLogger().debug(
                    "FrameworkAnvilScreen result: player={} answer={}",
                    player.getName(), answer);
                if (callback != null) {
                    callback.accept(ScreenResult.answer(answer));
                }
                closeInternal();
            });

            // Create NMS container, render items via framework, and open
            anvilGui.createInventory();
            anvilGui.update();
            inventoryImpl.open();

            open = true;
            plugin.getSLF4JLogger().debug("FrameworkAnvilScreen opened: player={}", player.getName());
        }, null);
    }

    /**
     * Applies the anvil title from config, falling back to the display text
     * (truncated at {@code {br}}) when no custom title is set.
     */
    private void configureTitle() {
        boolean enableTitle = Boolean.parseBoolean(config.getOrDefault("enableTitle", "true"));
        if (!enableTitle) {
            anvilGui.setTitle(""); // Ensure title is non-null when disabled
            return;
        }
        String customTitle = config.getOrDefault("customTitle", "");
        if (!customTitle.isEmpty()) {
            anvilGui.setTitle(customTitle);
            return;
        }
        int brIndex = displayText.indexOf("{br}");
        String title = brIndex >= 0 ? displayText.substring(0, brIndex) : displayText;
        anvilGui.setTitle(title.isEmpty() ? " " : title); // Fallback to space to avoid null title
    }

    /**
     * Builds and places the input, result, and optional cancel items into the
     * anvil GUI components based on config values.
     */
    private void setupItems() {
        // First item (rename input)
        ItemStack firstItem = buildConfiguredItem(
            config.getOrDefault("anvilItem", "Paper"),
            config.getOrDefault("itemHideTooltips", "false"),
            config.getOrDefault("itemCustomModelData", "0"),
            config.getOrDefault("itemAnvilEnchanted", "false"));

        String promptMsg = config.getOrDefault("promptMessage", "");
        if (!promptMsg.isEmpty()) {
            var meta = firstItem.getItemMeta();
            if (meta != null) {
                meta.displayName(ComponentUtil.mini("<!italic>" + promptMsg));
                firstItem.setItemMeta(meta);
            }
        }

        GuiItem firstGuiItem = new GuiItem(firstItem, null); // Input is handled by anvil typing
        anvilGui.getFirstItemComponent().addItem(firstGuiItem, 0, 0);

        // Result item triggers submission; clicks are routed via setOnResultClick because NMS strips item UUIDs
        ItemStack resultItem = buildConfiguredItem(
            config.getOrDefault("anvilResultItem", "Paper"),
            config.getOrDefault("resultItemHideTooltips", "false"),
            config.getOrDefault("resultItemCustomModelData", "0"),
            config.getOrDefault("resultItemAnvilEnchanted", "false"));

        GuiItem resultGuiItem = new GuiItem(resultItem, null);
        anvilGui.getResultComponent().addItem(resultGuiItem, 0, 0);

        // Optional cancel item
        boolean enableCancel = Boolean.parseBoolean(config.getOrDefault("enableCancelItem", "false"));
        if (enableCancel) {
            ItemStack cancelItem = buildConfiguredItem(
                config.getOrDefault("anvilCancelItem", "Barrier"),
                config.getOrDefault("cancelItemHideTooltips", "false"),
                config.getOrDefault("cancelItemCustomModelData", "0"),
                config.getOrDefault("cancelItemAnvilEnchanted", "false"));

            String hoverText = config.getOrDefault("cancelItemHoverText", "");
            if (!hoverText.isEmpty()) {
                var meta = cancelItem.getItemMeta();
                if (meta != null) {
                    meta.lore(java.util.List.of(ComponentUtil.mini("<!italic>" + hoverText)));
                    cancelItem.setItemMeta(meta);
                }
            }

            GuiItem cancelGuiItem = new GuiItem(cancelItem, event -> {
                if (!open) return;
                open = false;
                plugin.getSLF4JLogger().debug("FrameworkAnvilScreen cancel: player={}", player.getName());
                if (callback != null) {
                    callback.accept(ScreenResult.cancel());
                }
                closeInternal();
            });
            anvilGui.getSecondItemComponent().addItem(cancelGuiItem, 0, 0);
        }
    }

    /**
     * Builds an {@link ItemStack} from config strings for material, tooltip visibility,
     * custom model data, and enchant-glint, falling back to {@link Material#PAPER}.
     */
    private ItemStack buildConfiguredItem(String materialName, String hideTooltips,
                                           String customModelData, String enchanted) {
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
        if (!open) return;
        open = false;
        closeInternal();
    }

    /** Schedules the NMS container close and Bukkit inventory close on the player's thread. */
    private void closeInternal() {
        player.getScheduler().run(plugin, scheduledTask -> {
            if (inventoryImpl != null) {
                inventoryImpl.close();
            }
            player.closeInventory();
        }, null);
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
