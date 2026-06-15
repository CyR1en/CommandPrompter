package dev.cyr1en.promptui.gui;

import dev.cyr1en.promptui.inventory.UUIDTagType;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Wraps an {@link ItemStack} with an action {@link Consumer} and a UUID for click identification.
 *
 * <p>UUIDs are embedded in the item's {@link org.bukkit.persistence.PersistentDataContainer}
 * via {@link UUIDTagType} so the framework can match clicked items even when they have been
 * moved between slots.</p>
 */
public final class GuiItem {

    private static final NamespacedKey UUID_KEY = new NamespacedKey("commandprompter", "gui-item-uuid");

    private ItemStack item;
    private final Consumer<InventoryClickEvent> action;
    private final UUID uuid;

    /**
     * Creates a GuiItem with the given item and action.
     *
     * @param item   the ItemStack to display (cloned internally)
     * @param action the click handler, may be null for display-only items
     */
    public GuiItem(@NotNull ItemStack item, @Nullable Consumer<InventoryClickEvent> action) {
        Objects.requireNonNull(item, "item must not be null");
        this.item = item.clone();
        this.uuid = UUID.randomUUID();
        this.action = action;
    }

    /**
     * Creates a GuiItem with no action (display-only).
     */
    public GuiItem(@NotNull ItemStack item) {
        this(item, null);
    }

    /**
     * Applies the item's UUID to the {@code PersistentDataContainer}.
     */
    public void applyUUID() {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(UUID_KEY, UUIDTagType.INSTANCE, uuid);
            item.setItemMeta(meta);
        }
    }

    /**
     * Returns the UUID embedded in this GuiItem.
     */
    @NotNull
    public UUID getUUID() {
        return uuid;
    }

    /**
     * Invokes the item's click action, if present.
     */
    public void callAction(@NotNull InventoryClickEvent event) {
        if (action != null) {
            action.accept(event);
        }
    }

    /**
     * Returns whether this item has a click action.
     */
    public boolean hasAction() {
        return action != null;
    }

    /** Returns the underlying ItemStack (clone). */
    @NotNull
    public ItemStack getItem() {
        return item;
    }

    /** Replaces the underlying ItemStack (cloned internally). */
    public void setItem(@NotNull ItemStack item) {
        this.item = Objects.requireNonNull(item, "item must not be null").clone();
    }

    /** Returns a shallow copy of this GuiItem (cloned ItemStack, same action reference). */
    @NotNull
    public GuiItem copy() {
        GuiItem copy = new GuiItem(item.clone(), action);
        return copy;
    }

    /**
     * The NamespacedKey used for UUID identification in PersistentDataContainer.
     */
    @NotNull
    public static NamespacedKey getUUIDKey() {
        return UUID_KEY;
    }

    /**
     * Reads the UUID from an ItemStack's PersistentDataContainer, if present.
     */
    @Nullable
    public static UUID readUUID(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(UUID_KEY, UUIDTagType.INSTANCE);
    }
}
