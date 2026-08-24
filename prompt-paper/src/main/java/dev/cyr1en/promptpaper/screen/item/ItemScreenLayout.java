package dev.cyr1en.promptpaper.screen.item;

import dev.cyr1en.promptpaper.item.snapshot.ItemSlotMapping;
import dev.cyr1en.promptui.ComponentUtil;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Slot layout constants and display-item factories for {@link ItemPromptScreen}.
 *
 * <p>Inventory/armor mode (54 slots):
 * <ul>
 *   <li>{@code 0..26} — main storage snapshots (player slots 9..35)</li>
 *   <li>{@code 27..35} — hotbar snapshots (player slots 0..8)</li>
 *   <li>{@code 36..44} — divider row</li>
 *   <li>{@code 45} offhand, {@code 46} boots, {@code 47} leggings, {@code 48} chest, {@code 49} helmet</li>
 *   <li>{@code 50} info, {@code 51..52} filler, {@code 53} cancel</li>
 * </ul>
 *
 * <p>Hand mode (9 slots): {@code 4} captured main hand, {@code 8} cancel, rest filler.
 *
 * <p>Catalog mode (54 slots): {@code 0..44} page content, {@code 45} previous page,
 * {@code 49} page info, {@code 52} next page, {@code 53} cancel.
 */
public final class ItemScreenLayout {

    // Full-size layout (inventory + armor modes).
    public static final int FULL_SIZE = 54;
    public static final int STORAGE_START = 0;
    public static final int STORAGE_END = 26;
    public static final int HOTBAR_START = 27;
    public static final int HOTBAR_END = 35;
    public static final int DIVIDER_START = 36;
    public static final int DIVIDER_END = 44;
    public static final int OFFHAND_SLOT = 45;
    public static final int BOOTS_SLOT = 46;
    public static final int LEGGINGS_SLOT = 47;
    public static final int CHEST_SLOT = 48;
    public static final int HELMET_SLOT = 49;
    public static final int INFO_SLOT = 50;
    public static final int FILLER_START = 51;
    public static final int FILLER_END = 52;
    public static final int CANCEL_SLOT = 53;

    // Hand mode layout.
    public static final int HAND_SIZE = 9;
    public static final int HAND_ITEM_SLOT = 4;
    public static final int HAND_CANCEL_SLOT = 8;

    // Catalog mode layout.
    public static final int CATALOG_SIZE = 54;
    public static final int CATALOG_CONTENT_START = 0;
    public static final int CATALOG_CONTENT_END = 44;
    public static final int CATALOG_PAGE_SIZE = CATALOG_CONTENT_END - CATALOG_CONTENT_START + 1; // 45
    public static final int CATALOG_PREV_SLOT = 45;
    public static final int CATALOG_PAGE_INFO_SLOT = 49;
    public static final int CATALOG_NEXT_SLOT = 52;
    public static final int CATALOG_CANCEL_SLOT = 53;
    public static final int CATALOG_EMPTY_SLOT = 22;

    /** Number of failed selection attempts tolerated before the prompt cancels itself. */
    public static final int MAX_MISMATCH_STRIKES = 3;

    private ItemScreenLayout() {}

    /** Whether the given GUI slot is the cancel control for the mode. */
    public static boolean isCancelSlot(ItemScreenMode mode, int guiSlot) {
        return mode == ItemScreenMode.HAND ? guiSlot == HAND_CANCEL_SLOT : guiSlot == CANCEL_SLOT;
    }

    /**
     * Whether the given GUI slot represents a selectable entry for the mode.
     * Cancel and navigation controls are handled separately.
     */
    public static boolean isSelectableGuiSlot(ItemScreenMode mode, int guiSlot) {
        return switch (mode) {
            case INVENTORY -> ItemSlotMapping.isValidGuiSlot(guiSlot);
            case ARMOR -> guiSlot >= OFFHAND_SLOT && guiSlot <= HELMET_SLOT;
            case HAND -> guiSlot == HAND_ITEM_SLOT;
            case CATALOG -> guiSlot >= CATALOG_CONTENT_START && guiSlot <= CATALOG_CONTENT_END;
        };
    }

    public static ItemStack dividerItem() {
        return named(Material.GRAY_STAINED_GLASS_PANE, ComponentUtil.mini("<!italic> "));
    }

    public static ItemStack fillerItem() {
        return dividerItem();
    }

    public static ItemStack cancelItem() {
        return named(Material.BARRIER, ComponentUtil.mini("<!italic><red>Cancel</red>"));
    }

    public static ItemStack previousPageItem() {
        return named(Material.ARROW, ComponentUtil.mini("<!italic><yellow>Previous Page</yellow>"));
    }

    public static ItemStack nextPageItem() {
        return named(Material.ARROW, ComponentUtil.mini("<!italic><yellow>Next Page</yellow>"));
    }

    public static ItemStack emptyCatalogItem() {
        return named(Material.GRAY_DYE, ComponentUtil.mini("<!italic><gray>No items available</gray>"));
    }

    /** Info item for physical modes: a short header with the prompt text as lore. */
    public static ItemStack infoItem(String header, Component promptText) {
        var item = named(Material.PAPER, ComponentUtil.mini("<!italic><yellow>" + header + "</yellow>"));
        if (promptText != null && !Component.empty().equals(promptText)) {
            var meta = item.getItemMeta();
            if (meta != null) {
                meta.lore(List.of(promptText));
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /** Page indicator for catalog mode; degrades to "No items" when the category is empty. */
    public static ItemStack pageInfoItem(int page, int pageCount, int totalEntries) {
        if (pageCount <= 0) {
            return named(Material.PAPER, ComponentUtil.mini("<!italic><gray>No items</gray>"));
        }
        var item = named(Material.PAPER,
                ComponentUtil.mini("<!italic><aqua>Page " + (page + 1) + " of " + pageCount + "</aqua>"));
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.lore(List.of(ComponentUtil.mini("<!italic><gray>" + totalEntries + " items</gray>")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack named(Material material, Component name) {
        var item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
