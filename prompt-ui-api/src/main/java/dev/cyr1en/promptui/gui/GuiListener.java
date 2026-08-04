package dev.cyr1en.promptui.gui;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central event handler for all GUI lifecycle events.
 *
 * <p>A single instance is created per plugin and registered as a Bukkit
 * {@link Listener}. All {@link Gui} instances are tracked and their
 * callbacks are invoked through this listener.</p>
 */
public final class GuiListener implements Listener {

    private final JavaPlugin plugin;
    private final Set<Gui> activeGuis = ConcurrentHashMap.newKeySet();

    /**
     * Creates and registers this listener with the Bukkit plugin manager.
     */
    public GuiListener(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // -- Inventory Open --

    /**
     * Tracks the GUI when a player opens its inventory.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        Gui gui = Gui.getGui(event.getInventory());
        if (gui != null && !event.isCancelled()) {
            gui.markViewer(event.getPlayer());
            activeGuis.add(gui);
        }
    }

    // -- Inventory Click --

    /**
     * Routes click events to the appropriate GUI callbacks (global, top/bottom, outside)
     * and dispatches to the component for pane-level handling.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        Gui gui = Gui.getGui(event.getInventory());
        if (gui == null) return;

        gui.callOnGlobalClick(event);

        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        Inventory clicked = event.getClickedInventory();

        if (clicked == null) {
            gui.callOnOutsideClick(event);
            return;
        }

        if (clicked.equals(top)) {
            gui.callOnTopClick(event);
        } else {
            gui.callOnBottomClick(event);
            if (!gui.isPlayerInventoryUsed()) {
                event.setCancelled(true);
            }
        }

        boolean consumed = gui.click(event);
        if (consumed) {
            event.setCancelled(true);
        }
    }

    // -- Inventory Drag --

    /**
     * Routes drag events to the appropriate GUI callbacks and cancels drags that touch the top inventory.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        Gui gui = Gui.getGui(event.getInventory());
        if (gui == null) return;

        gui.callOnGlobalDrag(event);

        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();

        boolean touchesTop = event.getRawSlots().stream().anyMatch(s -> s < top.getSize());
        boolean touchesBottom = event.getRawSlots().stream().anyMatch(s -> s >= top.getSize());

        if (touchesTop) {
            gui.callOnTopDrag(event);
        }
        if (touchesBottom) {
            gui.callOnBottomDrag(event);
        }

        // Cancel drag on top inventory to prevent item movement
        if (touchesTop || (touchesBottom && !gui.isPlayerInventoryUsed())) {
            event.setCancelled(true);
        }
    }

    // -- Inventory Close --

    /**
     * Fires the close callback, unregisters the GUI, and restores the player's cached inventory.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        Gui gui = Gui.getGui(event.getInventory());
        if (gui == null) return;

        HumanEntity player = event.getPlayer();
        boolean lastViewer = gui.removeViewer(player);
        gui.getHumanEntityCache().restoreAndForget(player);
        if (lastViewer) {
            activeGuis.remove(gui);
            Gui.removeInventories(gui);
        }

        try {
            gui.callOnClose(event);
        } finally {
            // Close callbacks are user code and may open the next prompt. The
            // registry/cache cleanup deliberately happens before the callback,
            // while this finally block makes the cleanup idempotent if the
            // callback throws or a second close event is emitted.
            if (!gui.isViewer(player)) {
                gui.removeViewer(player);
                gui.getHumanEntityCache().restoreAndForget(player);
                Gui.removeInventoryIfUnused(gui);
                activeGuis.remove(gui);
            }
        }
    }

    // -- Entity Pickup Item --

    /**
     * Intercepts item pickups for players with an open GUI, adding the item to the cache
     * instead of the player's inventory.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPickupItem(@NotNull EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof HumanEntity player)) return;
        for (Gui gui : Gui.getActiveGuis()) {
            if (gui.isViewer(player) && gui.getHumanEntityCache().contains(player)) {
                boolean stored = gui.getHumanEntityCache().add(player, event.getItem().getItemStack());
                if (stored) {
                    event.getItem().remove();
                }
                // A full snapshot is still protected: cancelling without
                // removing leaves the world item untouched for a later,
                // non-temporary pickup instead of allowing it into the
                // transient inventory.
                event.setCancelled(true);
                break;
            }
        }
    }

    /**
     * Closes all active GUIs. Called on plugin disable.
     */
    public void closeAll() {
        Set<Gui> guis = new HashSet<>(Gui.getGuis());
        guis.addAll(Gui.getActiveGuis());
        guis.addAll(new HashSet<>(activeGuis));
        for (Gui gui : guis) {
            Set<HumanEntity> viewers = new HashSet<>(gui.getViewers());
            Inventory guiInventory = gui.getInventory();
            if (guiInventory != null) {
                viewers.addAll(new HashSet<>(guiInventory.getViewers()));
            }
            for (HumanEntity viewer : viewers) {
                try {
                    viewer.closeInventory();
                } finally {
                    gui.removeViewer(viewer);
                    gui.getHumanEntityCache().restoreAndForget(viewer);
                }
            }
            // A close event is not guaranteed during plugin shutdown. Restore
            // any cached entity not present in the platform viewer list too.
            gui.getHumanEntityCache().restoreAll();
            Gui.removeInventories(gui);
            gui.removeViewerAll();
        }
        activeGuis.clear();
        Gui.clearRegistry();
    }
}
