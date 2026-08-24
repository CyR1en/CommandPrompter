package dev.cyr1en.promptpaper.item.snapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Accessor for player inventory slots that routes slots to their explicit Bukkit methods (such as armor and offhand)
 * and enforces defensive copying on all reads and writes.
 */
public final class PlayerInventoryAccessor {

    private PlayerInventoryAccessor() {
    }

    /**
     * Reads a defensive copy of the {@link ItemStack} at the specified logical player slot.
     *
     * @param inventory  Player inventory (must not be null)
     * @param playerSlot Player slot index (0..40)
     * @return Defensive copy of ItemStack at the slot, or {@link ItemStack#empty()} if empty/air
     * @throws IllegalArgumentException if inventory is null or slot is invalid
     */
    public static ItemStack getItem(PlayerInventory inventory, int playerSlot) {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory cannot be null");
        }
        if (!ItemSlotMapping.isValidPlayerSlot(playerSlot)) {
            throw new IllegalArgumentException("Invalid player slot: " + playerSlot);
        }

        ItemStack raw = switch (playerSlot) {
            case ItemSlotMapping.PLAYER_BOOTS -> inventory.getBoots();
            case ItemSlotMapping.PLAYER_LEGGINGS -> inventory.getLeggings();
            case ItemSlotMapping.PLAYER_CHESTPLATE -> inventory.getChestplate();
            case ItemSlotMapping.PLAYER_HELMET -> inventory.getHelmet();
            case ItemSlotMapping.PLAYER_OFFHAND -> inventory.getItemInOffHand();
            default -> inventory.getItem(playerSlot);
        };

        return cloneOrEmpty(raw);
    }

    /**
     * Reads a defensive copy of the {@link ItemStack} corresponding to a GUI slot.
     *
     * @param inventory Player inventory (must not be null)
     * @param guiSlot   GUI container slot index
     * @return Defensive copy of ItemStack at the mapped slot
     * @throws IllegalArgumentException if inventory is null or guiSlot is unmapped
     */
    public static ItemStack getItemByGuiSlot(PlayerInventory inventory, int guiSlot) {
        OptionalInt playerSlot = ItemSlotMapping.toPlayerSlot(guiSlot);
        if (playerSlot.isEmpty()) {
            throw new IllegalArgumentException("Unmapped GUI slot: " + guiSlot);
        }
        return getItem(inventory, playerSlot.getAsInt());
    }

    /**
     * Sets a defensive copy of the {@link ItemStack} at the specified logical player slot.
     *
     * @param inventory  Player inventory (must not be null)
     * @param playerSlot Player slot index (0..40)
     * @param item       ItemStack to set (may be null/air to clear slot)
     * @throws IllegalArgumentException if inventory is null or slot is invalid
     */
    public static void setItem(PlayerInventory inventory, int playerSlot, ItemStack item) {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory cannot be null");
        }
        if (!ItemSlotMapping.isValidPlayerSlot(playerSlot)) {
            throw new IllegalArgumentException("Invalid player slot: " + playerSlot);
        }

        ItemStack defensiveCopy = cloneOrEmpty(item);

        switch (playerSlot) {
            case ItemSlotMapping.PLAYER_BOOTS -> inventory.setBoots(defensiveCopy);
            case ItemSlotMapping.PLAYER_LEGGINGS -> inventory.setLeggings(defensiveCopy);
            case ItemSlotMapping.PLAYER_CHESTPLATE -> inventory.setChestplate(defensiveCopy);
            case ItemSlotMapping.PLAYER_HELMET -> inventory.setHelmet(defensiveCopy);
            case ItemSlotMapping.PLAYER_OFFHAND -> inventory.setItemInOffHand(defensiveCopy);
            default -> inventory.setItem(playerSlot, defensiveCopy);
        }
    }

    /**
     * Sets a defensive copy of the {@link ItemStack} at the player slot mapped to the given GUI slot.
     *
     * @param inventory Player inventory (must not be null)
     * @param guiSlot   GUI container slot index
     * @param item      ItemStack to set
     * @throws IllegalArgumentException if inventory is null or guiSlot is unmapped
     */
    public static void setItemByGuiSlot(PlayerInventory inventory, int guiSlot, ItemStack item) {
        OptionalInt playerSlot = ItemSlotMapping.toPlayerSlot(guiSlot);
        if (playerSlot.isEmpty()) {
            throw new IllegalArgumentException("Unmapped GUI slot: " + guiSlot);
        }
        setItem(inventory, playerSlot.getAsInt(), item);
    }

    /**
     * Captures defensive copies of all 41 player inventory slots (0..40).
     *
     * @param inventory Player inventory (must not be null)
     * @return Unmodifiable map from player slot index to defensive copy of ItemStack
     */
    public static Map<Integer, ItemStack> captureAll(PlayerInventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory cannot be null");
        }
        Map<Integer, ItemStack> map = new LinkedHashMap<>(ItemSlotMapping.TOTAL_PLAYER_SLOTS);
        for (int slot = 0; slot < ItemSlotMapping.TOTAL_PLAYER_SLOTS; slot++) {
            map.put(slot, getItem(inventory, slot));
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Helper to clone an item or return {@link ItemStack#empty()} if null or air.
     */
    public static ItemStack cloneOrEmpty(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return ItemStack.empty();
        }
        return item.clone();
    }
}
