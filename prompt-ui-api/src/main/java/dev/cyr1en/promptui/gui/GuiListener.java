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

/**
 * Central event handler for all GUI lifecycle events.
 *
 * <p>A single instance is created per plugin and registered as a Bukkit
 * {@link Listener}. All {@link Gui} instances are tracked and their
 * callbacks are invoked through this listener.</p>
 */
public final class GuiListener implements Listener {

    private final JavaPlugin plugin;
    private final Set<Gui> activeGuis = new HashSet<>();

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
        if (gui != null) {
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

        // Global callback fires before any specific handling
        gui.callOnGlobalClick(event);

        // Determine if top or bottom inventory
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
            // Allow bottom clicks unless explicitly prevented
            if (!gui.isPlayerInventoryUsed()) {
                event.setCancelled(true);
            }
        }

        // Dispatch to GUI for component-level handling
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

        // Cancel drag if it touches top inventory (prevent item movement in GUI)
        if (touchesTop) {
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

        gui.callOnClose(event);
        activeGuis.remove(gui);

        // Restore cached player inventory
        HumanEntity player = event.getPlayer();
        gui.getHumanEntityCache().restoreAndForget(player);
    }

    // -- Entity Pickup Item --

    /**
     * Intercepts item pickups for players with an open GUI, adding the item to the cache
     * instead of the player's inventory.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPickupItem(@NotNull EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof HumanEntity player)) return;
        for (Gui gui : activeGuis) {
            if (gui.getHumanEntityCache().contains(player)) {
                gui.getHumanEntityCache().add(player, event.getItem().getItemStack());
                event.getItem().remove();
                event.setCancelled(true);
                break;
            }
        }
    }

    /**
     * Closes all active GUIs. Called on plugin disable.
     */
    public void closeAll() {
        for (Gui gui : new HashSet<>(activeGuis)) {
            for (HumanEntity viewer : new HashSet<>(gui.getInventory().getViewers())) {
                viewer.closeInventory();
            }
        }
        activeGuis.clear();
    }
}
