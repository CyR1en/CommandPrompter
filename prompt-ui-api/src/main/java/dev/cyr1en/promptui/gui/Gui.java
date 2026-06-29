package dev.cyr1en.promptui.gui;

import dev.cyr1en.promptui.inventory.HumanEntityCache;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Abstract base class for all GUI types in the framework.
 *
 * <p>Provides the core lifecycle ({@link #show}, {@link #update}, {@link #click}),
 * event callbacks, and a static registry mapping {@link Inventory} instances to
 * their owning {@link Gui}. The registry uses a {@link WeakHashMap} to work around
 * Bukkit's refusal to set {@link InventoryHolder} on certain inventory types (e.g., anvils).</p>
 */
public abstract class Gui {

    /** Maps inventories to their owning Gui, bypassing Holder limitations. */
    protected static final Map<Inventory, Gui> GUI_INVENTORIES = new WeakHashMap<>();

    /** The singleton event listener, created lazily on first Gui construction. */
    private static GuiListener listener;

    protected final JavaPlugin plugin;
    protected Inventory inventory;
    protected final HumanEntityCache humanEntityCache = new HumanEntityCache();

    // -- event callbacks --
    private Consumer<InventoryClickEvent> onTopClick;
    private Consumer<InventoryClickEvent> onBottomClick;
    private Consumer<InventoryClickEvent> onGlobalClick;
    private Consumer<InventoryClickEvent> onOutsideClick;
    private Consumer<InventoryDragEvent>  onTopDrag;
    private Consumer<InventoryDragEvent>  onBottomDrag;
    private Consumer<InventoryDragEvent>  onGlobalDrag;
    private Consumer<InventoryCloseEvent> onClose;

    private boolean updating;
    private boolean isPlayerInventoryUsed = true;

    @SuppressWarnings("PMD.AssignmentToNonFinalStatic")
    protected Gui(@NotNull JavaPlugin plugin, @Nullable Inventory inventory) {
        this.plugin = plugin;
        this.inventory = inventory;
        if (listener == null) {
            listener = new GuiListener(plugin);
        }
    }

    // -- lifecycle --

    /**
     * Opens this GUI for the given player. Caches the player's inventory first,
     * then opens the inventory. Subclasses must call {@code super.show(humanEntity)}
     * after preparing the inventory.
     */
    public void show(@NotNull HumanEntity humanEntity) {
        humanEntityCache.storeAndClear(humanEntity);
        if (inventory != null) {
            humanEntity.openInventory(inventory);
        }
    }

    /**
     * Updates the GUI contents. Called before showing and when dirty.
     */
    public abstract void update();

    /**
     * Handles a click event within this GUI.
     *
     * @param event the click event
     * @return true if the click was handled and should be cancelled
     */
    public abstract boolean click(@NotNull InventoryClickEvent event);

    /**
     * Returns whether the player inventory is rendered as part of this GUI's view.
     */
    public boolean isPlayerInventoryUsed() {
        return isPlayerInventoryUsed;
    }

    /**
     * Sets whether the player inventory is rendered as part of this GUI's view.
     */
    public void setPlayerInventoryUsed(boolean playerInventoryUsed) {
        this.isPlayerInventoryUsed = playerInventoryUsed;
    }

    // -- event callback registration --

    public void setOnTopClick(@Nullable Consumer<InventoryClickEvent> onTopClick) {
        this.onTopClick = onTopClick;
    }

    public void setOnBottomClick(@Nullable Consumer<InventoryClickEvent> onBottomClick) {
        this.onBottomClick = onBottomClick;
    }

    public void setOnGlobalClick(@Nullable Consumer<InventoryClickEvent> onGlobalClick) {
        this.onGlobalClick = onGlobalClick;
    }

    public void setOnOutsideClick(@Nullable Consumer<InventoryClickEvent> onOutsideClick) {
        this.onOutsideClick = onOutsideClick;
    }

    public void setOnTopDrag(@Nullable Consumer<InventoryDragEvent> onTopDrag) {
        this.onTopDrag = onTopDrag;
    }

    public void setOnBottomDrag(@Nullable Consumer<InventoryDragEvent> onBottomDrag) {
        this.onBottomDrag = onBottomDrag;
    }

    public void setOnGlobalDrag(@Nullable Consumer<InventoryDragEvent> onGlobalDrag) {
        this.onGlobalDrag = onGlobalDrag;
    }

    public void setOnClose(@Nullable Consumer<InventoryCloseEvent> onClose) {
        this.onClose = onClose;
    }

    // -- event callback invocation (called by GuiListener) --

    protected void callOnTopClick(@NotNull InventoryClickEvent event) {
        if (onTopClick != null) onTopClick.accept(event);
    }

    protected void callOnBottomClick(@NotNull InventoryClickEvent event) {
        if (onBottomClick != null) onBottomClick.accept(event);
    }

    protected void callOnGlobalClick(@NotNull InventoryClickEvent event) {
        if (onGlobalClick != null) onGlobalClick.accept(event);
    }

    protected void callOnOutsideClick(@NotNull InventoryClickEvent event) {
        if (onOutsideClick != null) onOutsideClick.accept(event);
    }

    protected void callOnTopDrag(@NotNull InventoryDragEvent event) {
        if (onTopDrag != null) onTopDrag.accept(event);
    }

    protected void callOnBottomDrag(@NotNull InventoryDragEvent event) {
        if (onBottomDrag != null) onBottomDrag.accept(event);
    }

    protected void callOnGlobalDrag(@NotNull InventoryDragEvent event) {
        if (onGlobalDrag != null) onGlobalDrag.accept(event);
    }

    protected void callOnClose(@NotNull InventoryCloseEvent event) {
        if (onClose != null) onClose.accept(event);
    }

    // -- dirty tracking --

    /**
     * Returns whether this GUI has pending changes requiring re-render.
     * Always returns {@code true}; subclasses may override for optimization.
     */
    public boolean isDirty() {
        return true;
    }

    /**
     * Marks the GUI as needing an update before the next render.
     */
    public void markChanges() {
        // Default: always dirty. Subclasses may override for optimization.
    }

    // -- updating guard --

    boolean isUpdating() { return updating; }
    void setUpdating(boolean updating) { this.updating = updating; }

    // -- inventory --

    @Nullable
    public Inventory getInventory() {
        return inventory;
    }

    @NotNull
    public JavaPlugin getPlugin() {
        return plugin;
    }

    @NotNull
    public HumanEntityCache getHumanEntityCache() {
        return humanEntityCache;
    }

    // -- static registry --

    /**
     * Registers a gui-inventory association. Called when inventory is created.
     */
    public static void addInventory(@NotNull Inventory inventory, @NotNull Gui gui) {
        GUI_INVENTORIES.put(inventory, gui);
    }

    /**
     * Looks up the Gui for an inventory, first checking the static map then the holder.
     */
    @Nullable
    public static Gui getGui(@NotNull Inventory inventory) {
        Gui gui = GUI_INVENTORIES.get(inventory);
        if (gui != null) return gui;
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Gui) {
            return (Gui) holder;
        }
        return null;
    }

    /**
     * Returns the set of all currently tracked GUIs.
     */
    @NotNull
    public static Set<Gui> getGuis() {
        return new HashSet<>(GUI_INVENTORIES.values());
    }
}
