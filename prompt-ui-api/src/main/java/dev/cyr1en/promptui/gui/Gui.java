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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Abstract base class for all GUI types in the framework.
 *
 * <p>Provides the core lifecycle ({@link #show}, {@link #update}, {@link #click}),
 * event callbacks, and a static registry mapping {@link Inventory} instances to
 * their owning {@link Gui}. The registry is explicit because Bukkit refuses to
 * set {@link InventoryHolder} on certain inventory types (e.g., anvils). Entries
 * are removed when the last viewer closes the inventory.</p>
 */
public abstract class Gui {

    /** Maps inventories to their owning Gui, bypassing Holder limitations. */
    protected static final ConcurrentMap<Inventory, Gui> GUI_INVENTORIES = new ConcurrentHashMap<>();

    /** GUIs with at least one currently tracked viewer. */
    private static final Set<Gui> ACTIVE_GUIS = ConcurrentHashMap.newKeySet();

    /** The singleton event listener, created lazily on first Gui construction. */
    private static GuiListener listener;

    protected final JavaPlugin plugin;
    protected Inventory inventory;
    protected final HumanEntityCache humanEntityCache = new HumanEntityCache();
    private final Set<HumanEntity> viewers = ConcurrentHashMap.newKeySet();

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
    public synchronized void show(@NotNull HumanEntity humanEntity) {
        if (inventory == null) {
            return;
        }

        // Re-register on every open because a final close removes the explicit
        // association and anvil inventories cannot rely on their holder.
        addInventory(inventory, this);
        boolean wasViewer = isViewer(humanEntity);
        boolean stored = false;
        try {
            stored = humanEntityCache.storeAndClear(humanEntity);
            // Paper returns null when opening is cancelled or the inventory is
            // otherwise not viewable. Treat it like an exception so the
            // player's original inventory is never lost.
            if (humanEntity.openInventory(inventory) == null) {
                throw new IllegalStateException("Inventory opening was cancelled");
            }
            markViewer(humanEntity);
        } catch (RuntimeException | Error failure) {
            if (stored) {
                humanEntityCache.restoreAndForget(humanEntity);
            }
            if (!wasViewer) {
                removeViewer(humanEntity);
            }
            removeInventoryIfUnused(this);
            throw failure;
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
        synchronized (gui) {
            GUI_INVENTORIES.put(inventory, gui);
        }
    }

    /**
     * Removes an inventory association if it still belongs to the supplied GUI.
     * The conditional form prevents an old GUI from removing a newer mapping.
     */
    public static void removeInventory(@NotNull Inventory inventory, @NotNull Gui gui) {
        synchronized (gui) {
            GUI_INVENTORIES.remove(inventory, gui);
        }
    }

    /** Removes an inventory association without requiring the owning GUI. */
    public static void removeInventory(@NotNull Inventory inventory) {
        GUI_INVENTORIES.remove(inventory);
    }

    /** Removes every inventory association owned by the supplied GUI. */
    public static void removeInventories(@NotNull Gui gui) {
        synchronized (gui) {
            GUI_INVENTORIES.entrySet().removeIf(entry -> entry.getValue() == gui);
        }
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
        return Collections.unmodifiableSet(new HashSet<>(GUI_INVENTORIES.values()));
    }

    /** Returns a stable snapshot of GUIs that currently have viewers. */
    @NotNull
    public static Set<Gui> getActiveGuis() {
        return Collections.unmodifiableSet(new HashSet<>(ACTIVE_GUIS));
    }

    /** Marks a viewer as active for this GUI. */
    public synchronized void markViewer(@NotNull HumanEntity humanEntity) {
        viewers.add(humanEntity);
        ACTIVE_GUIS.add(this);
    }

    /** Removes a viewer and reports whether no viewers remain. */
    public synchronized boolean removeViewer(@NotNull HumanEntity humanEntity) {
        viewers.remove(humanEntity);
        if (viewers.isEmpty()) {
            ACTIVE_GUIS.remove(this);
            return true;
        }
        return false;
    }

    /** Removes all tracked viewers from this GUI's active state. */
    public synchronized void removeViewerAll() {
        viewers.clear();
        ACTIVE_GUIS.remove(this);
    }

    /** Returns whether the supplied entity is an active viewer of this GUI. */
    public synchronized boolean isViewer(@NotNull HumanEntity humanEntity) {
        return viewers.contains(humanEntity);
    }

    /** Returns whether at least one viewer is currently active. */
    public synchronized boolean hasViewers() {
        return !viewers.isEmpty();
    }

    /** Returns a stable snapshot of this GUI's active viewers. */
    @NotNull
    public synchronized Set<HumanEntity> getViewers() {
        return Collections.unmodifiableSet(new HashSet<>(viewers));
    }

    /**
     * Removes this GUI from the explicit registry only after its final viewer
     * has gone away.
     */
    public static void removeInventoryIfUnused(@NotNull Gui gui) {
        synchronized (gui) {
            if (!gui.hasViewers()) {
                GUI_INVENTORIES.entrySet().removeIf(entry -> entry.getValue() == gui);
                ACTIVE_GUIS.remove(gui);
            }
        }
    }

    /** Clears the global registry and active set after viewer restoration. */
    public static void clearRegistry() {
        GUI_INVENTORIES.clear();
        ACTIVE_GUIS.clear();
    }
}
