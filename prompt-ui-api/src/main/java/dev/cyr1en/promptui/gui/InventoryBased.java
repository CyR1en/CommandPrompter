package dev.cyr1en.promptui.gui;

import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * A GUI that controls its own {@link Inventory} creation.
 *
 * <p>Implementations typically also implement {@link InventoryHolder} so that
 * the framework can resolve them from click events. For inventory types where
 * Bukkit ignores the holder (e.g., anvils), the static {@link Gui#GUI_INVENTORIES}
 * map provides fallback resolution.</p>
 */
public interface InventoryBased {

    /**
     * Creates and returns the inventory for this GUI.
     */
    @NotNull
    Inventory createInventory();
}
