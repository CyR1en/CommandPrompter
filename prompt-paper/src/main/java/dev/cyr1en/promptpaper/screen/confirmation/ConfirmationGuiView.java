package dev.cyr1en.promptpaper.screen.confirmation;

import dev.cyr1en.promptcore.CancelReason;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

/**
 * Hardened 27-slot chest GUI implementation of {@link ConfirmationView}.
 *
 * <p>Slots layout by default:
 * <ul>
 *   <li>Slot 11: Confirm button</li>
 *   <li>Slot 13: Info item</li>
 *   <li>Slot 15: Decline button</li>
 *   <li>All other slots: Gray glass filler</li>
 * </ul>
 *
 * <p>Cancels all top and bottom inventory interactions at {@link EventPriority#HIGHEST}.
 * All inventory operations run on the player's entity scheduler. Emits {@link ConfirmationOutcome#Declined}
 * on player ESC/close, and emits nothing on programmatic close.
 */
public class ConfirmationGuiView implements ConfirmationView, Listener {

    private final Plugin plugin;
    private final Player player;
    private final ConfirmationGuiConfig config;

    private Consumer<ConfirmationOutcome> callback;
    private Consumer<Throwable> failureCallback;

    private ConfirmationGuiHolder holder;
    private Inventory inventory;

    private boolean open;
    private boolean programmaticClose;
    private boolean listenerRegistered;

    public ConfirmationGuiView(Plugin plugin, Player player) {
        this(plugin, player, ConfirmationGuiConfig.builder().build());
    }

    public ConfirmationGuiView(Plugin plugin, Player player, Component title, Component infoMessage) {
        this(plugin, player, ConfirmationGuiConfig.createDefault(title, infoMessage));
    }

    public ConfirmationGuiView(Plugin plugin, Player player, ConfirmationGuiConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public synchronized void open(Consumer<ConfirmationOutcome> callback) {
        if (open) return;
        this.callback = Objects.requireNonNull(callback, "callback must not be null");
        this.programmaticClose = false;

        try {
            this.holder = new ConfirmationGuiHolder(this);
            var title = config.title() != null ? config.title() : Component.empty();
            try {
                this.inventory = Bukkit.createInventory(holder, config.size(), title);
            } catch (Throwable t) {
                var legacyTitle = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(title);
                this.inventory = Bukkit.createInventory(holder, config.size(), legacyTitle);
            }
            holder.setInventory(inventory);

            // Populate slots
            for (int i = 0; i < config.size(); i++) {
                if (i == config.confirmSlot()) {
                    inventory.setItem(i, config.confirmItem());
                } else if (i == config.infoSlot()) {
                    inventory.setItem(i, config.infoItem());
                } else if (i == config.declineSlot()) {
                    inventory.setItem(i, config.declineItem());
                } else {
                    inventory.setItem(i, config.fillerItem());
                }
            }

            registerListener();
            this.open = true;

            // Open inventory on player entity scheduler
            player.getScheduler().run(plugin, task -> {
                synchronized (this) {
                    if (!open) return;
                    player.openInventory(inventory);
                }
            }, null);

        } catch (Throwable t) {
            this.open = false;
            unregisterListener();
            if (failureCallback != null) {
                failureCallback.accept(t);
            } else if (t instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(t);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (!open) return;
        this.open = false;
        this.programmaticClose = true;
        this.callback = null;
        unregisterListener();

        player.getScheduler().run(plugin, task -> player.closeInventory(), null);
    }

    @Override
    public synchronized boolean isOpen() {
        return open;
    }

    @Override
    public synchronized void onOpenFailure(Consumer<Throwable> failureCallback) {
        this.failureCallback = failureCallback;
    }

    // -- Interaction handlers --

    private void handleConfirm() {
        Consumer<ConfirmationOutcome> cb = null;
        synchronized (this) {
            if (!open) return;
            this.open = false;
            this.programmaticClose = true;
            unregisterListener();
            cb = this.callback;
            this.callback = null;
        }

        player.getScheduler().run(plugin, task -> player.closeInventory(), null);

        if (cb != null) {
            cb.accept(ConfirmationOutcome.confirmed());
        }
    }

    private void handleDecline() {
        Consumer<ConfirmationOutcome> cb = null;
        synchronized (this) {
            if (!open) return;
            this.open = false;
            this.programmaticClose = true;
            unregisterListener();
            cb = this.callback;
            this.callback = null;
        }

        player.getScheduler().run(plugin, task -> player.closeInventory(), null);

        if (cb != null) {
            cb.accept(ConfirmationOutcome.declined());
        }
    }

    // -- Bukkit Event Handlers --

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isMatchingInventory(event.getView(), event.getInventory(), event.getWhoClicked())) return;

        // Hardened: unconditionally cancel all top, bottom, hotbar, shift-click, offhand, and creative interactions
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);

        if (!open) return;

        var clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getView().getTopInventory())) {
            return;
        }

        int slot = event.getSlot();
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
            if (slot == config.confirmSlot()) {
                handleConfirm();
            } else if (slot == config.declineSlot()) {
                handleDecline();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isMatchingInventory(event.getView(), event.getInventory(), event.getWhoClicked())) return;

        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!isMatchingInventory(event.getView(), event.getInventory(), event.getPlayer())) return;

        Consumer<ConfirmationOutcome> cb = null;
        synchronized (this) {
            if (!open || programmaticClose) {
                unregisterListener();
                return;
            }
            this.open = false;
            unregisterListener();
            cb = this.callback;
            this.callback = null;
        }

        if (cb != null) {
            cb.accept(ConfirmationOutcome.declined());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;

        Consumer<ConfirmationOutcome> cb = null;
        synchronized (this) {
            if (!open) {
                unregisterListener();
                return;
            }
            this.open = false;
            this.programmaticClose = true;
            unregisterListener();
            cb = this.callback;
            this.callback = null;
        }

        if (cb != null) {
            cb.accept(ConfirmationOutcome.cancelled(CancelReason.MANUAL));
        }
    }

    // -- Utilities --

    private boolean isMatchingInventory(InventoryView view, Inventory inv, HumanEntity who) {
        if (who == null || !who.getUniqueId().equals(player.getUniqueId())) return false;
        if (inventory == null) return false;

        var top = view != null ? view.getTopInventory() : null;
        if (top != null) {
            if (top.equals(inventory)) return true;
            if (top.getHolder() instanceof ConfirmationGuiHolder h && h == this.holder) return true;
        }
        if (inv != null) {
            if (inv.equals(inventory)) return true;
            if (inv.getHolder() instanceof ConfirmationGuiHolder h && h == this.holder) return true;
        }
        return false;
    }

    private synchronized void registerListener() {
        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }
    }

    private synchronized void unregisterListener() {
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
    }

    // -- Accessors for diagnostics and testing --

    public Inventory getInventory() {
        return inventory;
    }

    public ConfirmationGuiConfig getConfig() {
        return config;
    }

    public Player getPlayer() {
        return player;
    }

    public synchronized boolean isListenerRegistered() {
        return listenerRegistered;
    }
}
