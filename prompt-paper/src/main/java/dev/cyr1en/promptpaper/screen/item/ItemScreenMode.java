package dev.cyr1en.promptpaper.screen.item;

import dev.cyr1en.promptcore.ItemSource;

/**
 * The four item-prompt screen layouts, derived from the prompt's {@link ItemSource}.
 */
public enum ItemScreenMode {

    /** Full player inventory mirror (54 slots). */
    INVENTORY(ItemScreenLayout.FULL_SIZE),

    /** Captured main-hand item only (9 slots). */
    HAND(ItemScreenLayout.HAND_SIZE),

    /** Armor and offhand only (54 slots, equipment row selectable). */
    ARMOR(ItemScreenLayout.FULL_SIZE),

    /** Server-defined item catalog with pagination (54 slots). */
    CATALOG(ItemScreenLayout.CATALOG_SIZE);

    private final int size;

    ItemScreenMode(int size) {
        this.size = size;
    }

    /** Number of inventory slots used by this mode's GUI. */
    public int size() {
        return size;
    }

    /** Whether this mode reads and revalidates the player's physical inventory. */
    public boolean isPhysical() {
        return this != CATALOG;
    }

    /**
     * Maps an {@link ItemSource} to its screen mode. {@code null} degrades to
     * {@link #INVENTORY}, matching the {@code ItemPrompt} default.
     */
    public static ItemScreenMode fromSource(ItemSource source) {
        if (source == null) {
            return INVENTORY;
        }
        return switch (source) {
            case INVENTORY -> INVENTORY;
            case HAND -> HAND;
            case ARMOR -> ARMOR;
            case CATALOG -> CATALOG;
        };
    }
}
