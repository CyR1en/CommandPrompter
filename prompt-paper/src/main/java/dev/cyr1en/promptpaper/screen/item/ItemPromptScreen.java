package dev.cyr1en.promptpaper.screen.item;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptcore.ItemOutputFormat;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.item.catalog.CatalogEntry;
import dev.cyr1en.promptpaper.item.catalog.CatalogSnapshot;
import dev.cyr1en.promptpaper.item.snapshot.ItemSelectionVerifier;
import dev.cyr1en.promptpaper.item.snapshot.ItemSlotMapping;
import dev.cyr1en.promptpaper.item.snapshot.ItemSnapshot;
import dev.cyr1en.promptpaper.item.snapshot.VerificationResult;
import dev.cyr1en.promptpaper.preset.ItemPrompt;
import dev.cyr1en.promptui.ComponentUtil;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Interactive item selector screen supporting {@link ItemScreenMode#INVENTORY},
 * {@link ItemScreenMode#HAND}, {@link ItemScreenMode#ARMOR}, and {@link ItemScreenMode#CATALOG}.
 *
 * <p>Lifecycle conforms to {@link InputScreen} contract:
 * <ul>
 *   <li>Opens and operates on player entity scheduler.</li>
 *   <li>Unconditionally denies top/bottom clicks, drags, swaps, shift-clicks, and creative actions at {@link EventPriority#HIGHEST}.</li>
 *   <li>Revalidates physical inventory selections against immutable {@link ItemSnapshot} data.</li>
 *   <li>Tolerates up to 2 physical mismatches with player notifications before cancelling with {@link CancelReason#MANUAL} on 3rd mismatch.</li>
 *   <li>Emits exactly-once {@link ScreenResult} and unregisters listeners on every terminal path.</li>
 * </ul>
 */
public class ItemPromptScreen implements InputScreen, Listener {

    private final Plugin plugin;
    private final Player player;
    private final ItemPrompt prompt;
    private final ItemScreenMode mode;
    private final ItemOutputFormat outputFormat;
    private final String promptText;
    private final String catalogCategory;
    private final String soundKey;
    private final CatalogSnapshot catalogSnapshot;

    private Consumer<ScreenResult> callback;
    private Consumer<Throwable> openFailureCallback;

    private ItemScreenHolder holder;
    private Inventory inventory;

    private Map<Integer, ItemSnapshot> snapshots;
    private int capturedHandSlot;
    private ItemSnapshot capturedHandSnapshot;

    private int catalogPage;
    private int mismatchStrikes;

    private boolean open;
    private boolean programmaticClose;
    private boolean listenerRegistered;

    public ItemPromptScreen(Plugin plugin, Player player, ItemPrompt prompt) {
        this(plugin, player, prompt, resolveCatalogSnapshot(plugin));
    }

    public ItemPromptScreen(CommandPrompter plugin, Player player, ItemPrompt prompt) {
        this(plugin, player, prompt, resolveCatalogSnapshot(plugin));
    }

    public ItemPromptScreen(Plugin plugin, Player player, ItemPrompt prompt, CatalogSnapshot catalogSnapshot) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.prompt = Objects.requireNonNull(prompt, "prompt must not be null");
        this.mode = ItemScreenMode.fromSource(prompt.source());
        this.outputFormat = prompt.outputFormat() != null ? prompt.outputFormat() : ItemOutputFormat.KEY;
        this.promptText = prompt.promptText() != null ? prompt.promptText() : "";
        this.catalogCategory = prompt.category() != null && !prompt.category().isBlank() ? prompt.category() : "all";
        this.soundKey = prompt.sound();
        this.catalogSnapshot = catalogSnapshot != null ? catalogSnapshot : CatalogSnapshot.empty();
    }

    public ItemPromptScreen(CommandPrompter plugin, Player player, ItemPrompt prompt, CatalogSnapshot catalogSnapshot) {
        this((Plugin) plugin, player, prompt, catalogSnapshot);
    }

    public ItemPromptScreen(
            Plugin plugin,
            Player player,
            ItemScreenMode mode,
            ItemOutputFormat outputFormat,
            String promptText,
            String category,
            String soundKey,
            CatalogSnapshot catalogSnapshot) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.prompt = null;
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.outputFormat = outputFormat != null ? outputFormat : ItemOutputFormat.KEY;
        this.promptText = promptText != null ? promptText : "";
        this.catalogCategory = category != null && !category.isBlank() ? category : "all";
        this.soundKey = soundKey;
        this.catalogSnapshot = catalogSnapshot != null ? catalogSnapshot : CatalogSnapshot.empty();
    }

    private static CatalogSnapshot resolveCatalogSnapshot(Plugin plugin) {
        if (plugin instanceof CommandPrompter cp && cp.getItemCatalogRegistry() != null) {
            return cp.getItemCatalogRegistry().snapshot();
        }
        return CatalogSnapshot.empty();
    }

    @Override
    public synchronized void open() {
        if (open) return;
        this.open = true;
        this.programmaticClose = false;
        this.mismatchStrikes = 0;

        registerListener();

        if (mode == ItemScreenMode.HAND) {
            int heldSlot = player.getInventory().getHeldItemSlot();
            this.capturedHandSlot = heldSlot;
            this.capturedHandSnapshot = ItemSnapshot.capture(player.getInventory(), heldSlot);
            if (capturedHandSnapshot.isEmpty()) {
                this.open = false;
                this.programmaticClose = true;
                unregisterListener();
                Consumer<ScreenResult> cb = this.callback;
                this.callback = null;
                this.openFailureCallback = null;
                if (cb != null) {
                    cb.accept(ScreenResult.cancel(CancelReason.MANUAL));
                }
                return;
            }
        }

        try {
            openInventorySync();
        } catch (Throwable t) {
            this.open = false;
            this.programmaticClose = true;
            unregisterListener();
            Consumer<Throwable> failCb = this.openFailureCallback;
            this.openFailureCallback = null;
            this.callback = null;
            if (failCb != null) {
                failCb.accept(t);
            } else if (t instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(t);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void openInventorySync() {
        playOpenSound();

        this.holder = new ItemScreenHolder(mode, this);
        Component title = ComponentUtil.mini(promptText.isBlank() ? "Select Item" : promptText);
        try {
            this.inventory = Bukkit.createInventory(holder, mode.size(), title);
        } catch (Throwable t) {
            var legacyTitle = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(title);
            this.inventory = Bukkit.createInventory(holder, mode.size(), legacyTitle);
        }
        holder.setInventory(inventory);

        populateInventory();

        player.openInventory(inventory);
    }

    private void populateInventory() {
        switch (mode) {
            case INVENTORY, ARMOR -> {
                this.snapshots = ItemSnapshot.captureInventory(player.getInventory());

                // 0..26: main storage snapshots (player slots 9..35)
                for (int gui = ItemScreenLayout.STORAGE_START; gui <= ItemScreenLayout.STORAGE_END; gui++) {
                    int pSlot = ItemSlotMapping.toPlayerSlot(gui).orElse(gui + 9);
                    ItemSnapshot snap = snapshots.get(pSlot);
                    if (snap != null && !snap.isEmpty()) {
                        inventory.setItem(gui, snap.item());
                    }
                }

                // 27..35: hotbar snapshots (player slots 0..8)
                for (int gui = ItemScreenLayout.HOTBAR_START; gui <= ItemScreenLayout.HOTBAR_END; gui++) {
                    int pSlot = ItemSlotMapping.toPlayerSlot(gui).orElse(gui - 27);
                    ItemSnapshot snap = snapshots.get(pSlot);
                    if (snap != null && !snap.isEmpty()) {
                        inventory.setItem(gui, snap.item());
                    }
                }

                // 36..44: divider row
                for (int gui = ItemScreenLayout.DIVIDER_START; gui <= ItemScreenLayout.DIVIDER_END; gui++) {
                    inventory.setItem(gui, ItemScreenLayout.dividerItem());
                }

                // 45: offhand (player slot 40)
                ItemSnapshot offhandSnap = snapshots.get(ItemSlotMapping.PLAYER_OFFHAND);
                if (offhandSnap != null && !offhandSnap.isEmpty()) {
                    inventory.setItem(ItemScreenLayout.OFFHAND_SLOT, offhandSnap.item());
                }

                // 46..49: boots, leggings, chestplate, helmet (player slots 36..39)
                ItemSnapshot bootsSnap = snapshots.get(ItemSlotMapping.PLAYER_BOOTS);
                if (bootsSnap != null && !bootsSnap.isEmpty()) {
                    inventory.setItem(ItemScreenLayout.BOOTS_SLOT, bootsSnap.item());
                }
                ItemSnapshot legsSnap = snapshots.get(ItemSlotMapping.PLAYER_LEGGINGS);
                if (legsSnap != null && !legsSnap.isEmpty()) {
                    inventory.setItem(ItemScreenLayout.LEGGINGS_SLOT, legsSnap.item());
                }
                ItemSnapshot chestSnap = snapshots.get(ItemSlotMapping.PLAYER_CHESTPLATE);
                if (chestSnap != null && !chestSnap.isEmpty()) {
                    inventory.setItem(ItemScreenLayout.CHEST_SLOT, chestSnap.item());
                }
                ItemSnapshot helmSnap = snapshots.get(ItemSlotMapping.PLAYER_HELMET);
                if (helmSnap != null && !helmSnap.isEmpty()) {
                    inventory.setItem(ItemScreenLayout.HELMET_SLOT, helmSnap.item());
                }

                // 50: info, 51..52: filler, 53: cancel
                Component promptComp = ComponentUtil.mini(promptText);
                inventory.setItem(ItemScreenLayout.INFO_SLOT, ItemScreenLayout.infoItem("Information", promptComp));
                for (int gui = ItemScreenLayout.FILLER_START; gui <= ItemScreenLayout.FILLER_END; gui++) {
                    inventory.setItem(gui, ItemScreenLayout.fillerItem());
                }
                inventory.setItem(ItemScreenLayout.CANCEL_SLOT, ItemScreenLayout.cancelItem());
            }
            case HAND -> {
                int heldSlot = player.getInventory().getHeldItemSlot();
                this.capturedHandSlot = heldSlot;
                this.capturedHandSnapshot = ItemSnapshot.capture(player.getInventory(), heldSlot);

                for (int i = 0; i < ItemScreenLayout.HAND_SIZE; i++) {
                    if (i == ItemScreenLayout.HAND_ITEM_SLOT) {
                        if (!capturedHandSnapshot.isEmpty()) {
                            inventory.setItem(i, capturedHandSnapshot.item());
                        }
                    } else if (i == ItemScreenLayout.HAND_CANCEL_SLOT) {
                        inventory.setItem(i, ItemScreenLayout.cancelItem());
                    } else {
                        inventory.setItem(i, ItemScreenLayout.fillerItem());
                    }
                }
            }
            case CATALOG -> {
                this.catalogPage = 0;
                renderCatalogPage();
            }
        }
    }

    private void renderCatalogPage() {
        int totalEntries = catalogSnapshot.entryCount(catalogCategory);
        int pageCount = catalogSnapshot.getPageCount(catalogCategory, ItemScreenLayout.CATALOG_PAGE_SIZE);
        List<CatalogEntry> pageEntries = catalogSnapshot.getPage(
                catalogCategory, catalogPage, ItemScreenLayout.CATALOG_PAGE_SIZE);

        for (int i = ItemScreenLayout.CATALOG_CONTENT_START; i <= ItemScreenLayout.CATALOG_CONTENT_END; i++) {
            inventory.setItem(i, null);
        }

        if (totalEntries == 0) {
            inventory.setItem(ItemScreenLayout.CATALOG_EMPTY_SLOT, ItemScreenLayout.emptyCatalogItem());
        } else {
            for (int i = 0; i < pageEntries.size(); i++) {
                CatalogEntry entry = pageEntries.get(i);
                int slot = ItemScreenLayout.CATALOG_CONTENT_START + i;
                inventory.setItem(slot, new ItemStack(entry.material()));
            }
        }

        inventory.setItem(ItemScreenLayout.CATALOG_PREV_SLOT, ItemScreenLayout.previousPageItem());
        for (int i = 46; i <= 48; i++) {
            inventory.setItem(i, ItemScreenLayout.fillerItem());
        }
        inventory.setItem(ItemScreenLayout.CATALOG_PAGE_INFO_SLOT,
                ItemScreenLayout.pageInfoItem(catalogPage, pageCount, totalEntries));
        for (int i = 50; i <= 51; i++) {
            inventory.setItem(i, ItemScreenLayout.fillerItem());
        }
        inventory.setItem(ItemScreenLayout.CATALOG_NEXT_SLOT, ItemScreenLayout.nextPageItem());
        inventory.setItem(ItemScreenLayout.CATALOG_CANCEL_SLOT, ItemScreenLayout.cancelItem());
    }

    private void playOpenSound() {
        if (soundKey == null || soundKey.isBlank()) return;
        try {
            var key = Key.key(soundKey);
            var sound = Sound.sound(key, Sound.Source.MASTER, 1.0f, 1.0f);
            player.playSound(sound);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public synchronized void close() {
        if (!open) return;
        this.open = false;
        this.programmaticClose = true;
        this.callback = null;
        this.openFailureCallback = null;
        closeInventoryIfCurrent();
        unregisterListener();
    }

    private void closeInventoryIfCurrent() {
        try {
            InventoryView view = player.getOpenInventory();
            if (view != null) {
                Inventory top = view.getTopInventory();
                if (top != null) {
                    if (top.equals(this.inventory)
                            || (top.getHolder() instanceof ItemScreenHolder h && (h == this.holder || h.getScreen() == this))) {
                        player.closeInventory();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public synchronized boolean isOpen() {
        return open;
    }

    @Override
    public synchronized void onResult(Consumer<ScreenResult> callback) {
        this.callback = callback;
    }

    @Override
    public synchronized void onOpenFailure(Consumer<Throwable> callback) {
        this.openFailureCallback = callback;
    }

    // -- Event Handlers --

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isMatchingInventory(event.getView(), event.getInventory(), event.getWhoClicked())) return;

        // Hardened: unconditionally deny all top and bottom clicks, drags, swaps, shift-clicks, hotbar drops
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);

        if (!open) return;

        var clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getView().getTopInventory())) {
            return;
        }

        int slot = event.getSlot();
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }

        if (ItemScreenLayout.isCancelSlot(mode, slot)) {
            deliverResultAndClose(ScreenResult.cancel(CancelReason.MANUAL));
            return;
        }

        if (mode == ItemScreenMode.CATALOG) {
            handleCatalogClick(slot);
        } else {
            handlePhysicalClick(slot);
        }
    }

    private void handleCatalogClick(int slot) {
        if (slot == ItemScreenLayout.CATALOG_PREV_SLOT) {
            if (catalogPage > 0) {
                catalogPage--;
                renderCatalogPage();
            }
            return;
        }

        if (slot == ItemScreenLayout.CATALOG_NEXT_SLOT) {
            int pageCount = catalogSnapshot.getPageCount(catalogCategory, ItemScreenLayout.CATALOG_PAGE_SIZE);
            if (catalogPage + 1 < pageCount) {
                catalogPage++;
                renderCatalogPage();
            }
            return;
        }

        if (ItemScreenLayout.isSelectableGuiSlot(mode, slot)) {
            int index = slot - ItemScreenLayout.CATALOG_CONTENT_START;
            List<CatalogEntry> pageEntries = catalogSnapshot.getPage(
                    catalogCategory, catalogPage, ItemScreenLayout.CATALOG_PAGE_SIZE);
            if (index < 0 || index >= pageEntries.size()) {
                return;
            }

            CatalogEntry entry = pageEntries.get(index);
            try {
                String token = ItemAnswerTokens.fromCatalogEntry(outputFormat, entry);
                deliverResultAndClose(ScreenResult.answer(token));
            } catch (Throwable t) {
                deliverResultAndClose(ScreenResult.cancel(CancelReason.ERROR));
            }
        }
    }

    private void handlePhysicalClick(int slot) {
        if (!ItemScreenLayout.isSelectableGuiSlot(mode, slot)) {
            return;
        }

        int playerSlot;
        ItemSnapshot expectedSnapshot;

        if (mode == ItemScreenMode.HAND) {
            playerSlot = capturedHandSlot;
            expectedSnapshot = capturedHandSnapshot;
        } else {
            OptionalInt pSlot = ItemSlotMapping.toPlayerSlot(slot);
            if (pSlot.isEmpty()) {
                return;
            }
            playerSlot = pSlot.getAsInt();
            expectedSnapshot = snapshots != null ? snapshots.get(playerSlot) : null;
        }

        if (expectedSnapshot == null || expectedSnapshot.isEmpty()) {
            handleMismatchOrEmptySelection();
            return;
        }

        VerificationResult vResult = ItemSelectionVerifier.verifyPlayerSlot(
                player.getInventory(), playerSlot, expectedSnapshot);

        if (vResult.isFailure()) {
            handleMismatchOrEmptySelection();
            return;
        }

        ItemStack verifiedItem = vResult.getItem().orElse(ItemStack.empty());
        if (verifiedItem.isEmpty() || verifiedItem.getType().isAir() || verifiedItem.getAmount() <= 0) {
            handleMismatchOrEmptySelection();
            return;
        }

        try {
            String token = ItemAnswerTokens.fromSelection(outputFormat, expectedSnapshot, verifiedItem);
            deliverResultAndClose(ScreenResult.answer(token));
        } catch (Throwable t) {
            deliverResultAndClose(ScreenResult.cancel(CancelReason.ERROR));
        }
    }

    private void handleMismatchOrEmptySelection() {
        mismatchStrikes++;
        if (mismatchStrikes >= ItemScreenLayout.MAX_MISMATCH_STRIKES) {
            deliverResultAndClose(ScreenResult.cancel(CancelReason.MANUAL));
        } else {
            player.sendMessage(ComponentUtil.mini("<red>The item in that slot has changed. Please select again.</red>"));
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

        Consumer<ScreenResult> cb = null;
        synchronized (this) {
            if (!open || programmaticClose) {
                unregisterListener();
                return;
            }
            this.open = false;
            this.programmaticClose = true;
            unregisterListener();
            cb = this.callback;
            this.callback = null;
            this.openFailureCallback = null;
        }

        if (cb != null) {
            cb.accept(ScreenResult.cancel(CancelReason.MANUAL));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;

        Consumer<ScreenResult> cb = null;
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
            this.openFailureCallback = null;
        }

        if (cb != null) {
            cb.accept(ScreenResult.cancel(CancelReason.MANUAL));
        }
    }

    private void deliverResultAndClose(ScreenResult result) {
        Consumer<ScreenResult> cb = null;
        synchronized (this) {
            if (!open) return;
            this.open = false;
            this.programmaticClose = true;
            cb = this.callback;
            this.callback = null;
            this.openFailureCallback = null;
            closeInventoryIfCurrent();
            unregisterListener();
        }

        if (cb != null) {
            cb.accept(result);
        }
    }

    private boolean isMatchingInventory(InventoryView view, Inventory inv, HumanEntity who) {
        if (who == null || !who.getUniqueId().equals(player.getUniqueId())) return false;
        if (inventory == null && holder == null) return false;

        var top = view != null ? view.getTopInventory() : null;
        if (top != null) {
            if (inventory != null && top.equals(inventory)) return true;
            if (top.getHolder() instanceof ItemScreenHolder h && (h == this.holder || h.getScreen() == this)) return true;
        }
        if (inv != null) {
            if (inventory != null && inv.equals(inventory)) return true;
            if (inv.getHolder() instanceof ItemScreenHolder h && (h == this.holder || h.getScreen() == this)) return true;
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

    // -- Accessors / Metadata --

    public Player getPlayer() {
        return player;
    }

    public ItemPrompt getPrompt() {
        return prompt;
    }

    public ItemScreenMode getMode() {
        return mode;
    }

    public ItemOutputFormat getOutputFormat() {
        return outputFormat;
    }

    public CatalogSnapshot getCatalogSnapshot() {
        return catalogSnapshot;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public ItemScreenHolder getHolder() {
        return holder;
    }

    public int getCatalogPage() {
        return catalogPage;
    }

    public int getMismatchStrikes() {
        return mismatchStrikes;
    }

    public synchronized boolean isListenerRegistered() {
        return listenerRegistered;
    }
}
