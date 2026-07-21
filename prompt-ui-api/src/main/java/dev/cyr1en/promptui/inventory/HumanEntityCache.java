package dev.cyr1en.promptui.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches player inventories when a GUI is shown and restores them on close.
 *
 * <p>Before opening a GUI, the player's inventory (slots 0-35) is saved and cleared
 * so the framework can place items in the bottom inventory without the player's own
 * items interfering. On close, the original items are restored.</p>
 */
public final class HumanEntityCache {

    private final Map<HumanEntity, ItemStack[]> cache = new HashMap<>();

    /**
     * Saves and clears the player's inventory (slots 0-35).
     *
     * @param entity the human entity whose inventory to cache
     */
    public void storeAndClear(@NotNull HumanEntity entity) {
        PlayerInventory inventory = entity.getInventory();
        ItemStack[] saved = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            saved[i] = inventory.getItem(i);
            inventory.setItem(i, null);
        }
        cache.put(entity, saved);
    }

    /**
     * Restores the player's previously cached inventory and removes the entry.
     *
     * @param entity the human entity whose inventory to restore
     */
    public void restoreAndForget(@NotNull HumanEntity entity) {
        ItemStack[] saved = cache.remove(entity);
        if (saved != null) {
            PlayerInventory inventory = entity.getInventory();
            for (int i = 0; i < 36; i++) {
                inventory.setItem(i, saved[i]);
            }
        }
    }

    /**
     * Adds an item to the cached inventory if the player is currently cached.
     * Used to prevent the player picking up items dropped during GUI interaction.
     *
     * @param entity the human entity
     * @param item   the item to add to the cached inventory
     */
    public void add(@NotNull HumanEntity entity, @NotNull ItemStack item) {
        ItemStack[] saved = cache.get(entity);
        if (saved != null) {
            for (int i = 0; i < 36; i++) {
                if (saved[i] == null) {
                    saved[i] = item;
                    return;
                }
            }
        }
    }

    /**
     * Returns whether the entity has a cached inventory.
     */
    public boolean contains(@NotNull HumanEntity entity) {
        return cache.containsKey(entity);
    }

    /**
     * Clears all cached inventories without restoring them.
     */
    public void clear() {
        cache.clear();
    }
}
