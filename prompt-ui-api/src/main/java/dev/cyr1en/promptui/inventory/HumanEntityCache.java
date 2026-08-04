package dev.cyr1en.promptui.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches player inventories when a GUI is shown and restores them on close.
 *
 * <p>Before opening a GUI, the player's inventory (slots 0-35) is saved and cleared
 * so the framework can place items in the bottom inventory without the player's own
 * items interfering. On close, the original items are restored.</p>
 */
public final class HumanEntityCache {

    /**
     * The cache is accessed by inventory events and by player-affine tasks.  A
     * concurrent map keeps the lifecycle safe when those paths happen on
     * different region threads; the individual snapshots are synchronized when
     * their slots are modified or restored.
     */
    private final Map<HumanEntity, ItemStack[]> cache = new ConcurrentHashMap<>();

    /**
     * Saves and clears the player's inventory (slots 0-35).
     *
     * @param entity the human entity whose inventory to cache
     */
    public synchronized boolean storeAndClear(@NotNull HumanEntity entity) {
        /*
         * The contains/put pair is protected by this cache's monitor. Calling
         * show() twice for the same viewer must not replace the original
         * snapshot with the already empty temporary inventory from the first
         * call.
         */
        if (cache.containsKey(entity)) {
            return false;
        }

        cache.put(entity, snapshotAndClear(entity));
        return true;
    }

    private ItemStack[] snapshotAndClear(HumanEntity entity) {
        PlayerInventory inventory = entity.getInventory();
        ItemStack[] saved = new ItemStack[36];
        try {
            for (int i = 0; i < 36; i++) {
                ItemStack item = inventory.getItem(i);
                saved[i] = item == null ? null : item.clone();
                inventory.setItem(i, null);
            }
            return saved;
        } catch (RuntimeException | Error failure) {
            // Do not leave a partially cleared inventory if the platform API
            // rejects one of the slot operations.
            for (int i = 0; i < 36; i++) {
                inventory.setItem(i, saved[i]);
            }
            throw failure;
        }
    }

    /**
     * Restores the player's previously cached inventory and removes the entry.
     *
     * @param entity the human entity whose inventory to restore
     */
    public synchronized void restoreAndForget(@NotNull HumanEntity entity) {
        ItemStack[] saved = cache.remove(entity);
        if (saved != null) {
            synchronized (saved) {
                PlayerInventory inventory = entity.getInventory();
                for (int i = 0; i < 36; i++) {
                    inventory.setItem(i, saved[i]);
                }
            }
        }
    }

    /**
     * Adds an item to the cached inventory if the player is currently cached.
     * Used to prevent the player picking up items dropped during GUI interaction.
     *
     * @param entity the human entity
     * @param item   the item to add to the cached inventory
     * @return true if the item was stored, or false if the entity is not cached or the cache is full
     */
    public synchronized boolean add(@NotNull HumanEntity entity, @NotNull ItemStack item) {
        ItemStack[] saved = cache.get(entity);
        if (saved != null) {
            synchronized (saved) {
                for (int i = 0; i < 36; i++) {
                    if (saved[i] == null) {
                        saved[i] = item.clone();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns whether the entity has a cached inventory.
     */
    public synchronized boolean contains(@NotNull HumanEntity entity) {
        return cache.containsKey(entity);
    }

    /**
     * Returns a stable snapshot of entities with cached inventories.
     */
    @NotNull
    public List<HumanEntity> getCachedEntities() {
        return new ArrayList<>(cache.keySet());
    }

    /**
     * Restores every cached inventory and clears the cache.  This is used by
     * plugin-wide GUI shutdown so a close event is not required for cleanup.
     */
    public synchronized void restoreAll() {
        for (HumanEntity entity : getCachedEntities()) {
            restoreAndForget(entity);
        }
        cache.clear();
    }

    /**
     * Clears all cached inventories without restoring them.
     */
    public void clear() {
        cache.clear();
    }
}
