package dev.cyr1en.promptui.gui;

import dev.cyr1en.promptui.pane.Pane;
import dev.cyr1en.promptui.pane.PositionedPane;
import dev.cyr1en.promptui.pane.StaticPane;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A rectangular grid of {@link GuiItem}s managed through a collection of
 * {@link Pane} objects.
 *
 * <p>Panes are rendered in priority order (lowest first) into a merged
 * {@link GuiItemContainer}. Direct items placed via {@link #addItem}
 * are stored in a background {@link StaticPane} (priority 0).</p>
 */
public final class GuiComponent {

    private final int length;
    private final int height;
    private final StaticPane itemsStaticPane;
    private final List<PositionedPane> panes;

    /**
     * Creates a component of the given slot dimensions.
     *
     * @param length horizontal slot count
     * @param height vertical row count
     */
    public GuiComponent(int length, int height) {
        this.length = length;
        this.height = height;
        this.itemsStaticPane = new StaticPane(length, height);
        this.panes = new ArrayList<>();
    }

    // -- dimensions --

    public int getLength() { return length; }
    public int getHeight() { return height; }

    // -- direct item placement (background pane) --

    /**
     * Places an item at the given coordinates in the background pane (priority 0).
     */
    public void addItem(@NotNull GuiItem item, int x, int y) {
        itemsStaticPane.addItem(item, x, y);
    }

    /**
     * Places an item at the given slot in the background pane (priority 0).
     */
    public void addItem(@NotNull GuiItem item, @NotNull Slot slot) {
        itemsStaticPane.addItem(item, slot);
    }

    // -- pane management --

    /**
     * Adds a pane at the given offset within the component.
     * The pane's priority determines render order.
     */
    public void addPane(@NotNull Slot offset, @NotNull Pane pane) {
        panes.add(new PositionedPane(pane, pane.getPriority(), offset));
    }

    /**
     * Adds a pane at the origin.
     */
    public void addPane(@NotNull Pane pane) {
        addPane(Slot.of(0, 0), pane);
    }

    @NotNull
    public List<Pane> getPanes() {
        return panes.stream()
            .map(PositionedPane::pane)
            .toList();
    }

    /**
     * Returns all GuiItems across all panes.
     */
    @NotNull
    public List<GuiItem> getItems() {
        List<GuiItem> all = new ArrayList<>();
        GuiItemContainer rendered = display();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < length; x++) {
                GuiItem item = rendered.getItem(x, y);
                if (item != null) all.add(item);
            }
        }
        return all;
    }

    // -- display/rendering --

    /**
     * Renders all panes into a merged {@link GuiItemContainer}.
     */
    @NotNull
    public GuiItemContainer display() {
        GuiItemContainer container = new GuiItemContainer(length, height);
        // 1. Render items static pane (background)
        mergeContainer(container, itemsStaticPane.display(), 0, 0, false);
        // 2. Render panes sorted by priority (lowest first → rendered behind)
        panes.stream()
            .sorted()
            .filter(pp -> pp.pane().isVisible())
            .forEach(pp -> {
                GuiItemContainer paneOutput = pp.pane().display();
                mergeContainer(container, paneOutput, pp.offset().x(), pp.offset().y(), true);
            });
        return container;
    }

    // -- click handling --

    /**
     * Dispatches a click event to the appropriate pane.
     *
     * @return true if a pane consumed the click
     */
    public boolean click(@NotNull Gui gui, @NotNull InventoryClickEvent event, int rawSlot) {
        Slot slot = slotFromRaw(rawSlot);
        if (slot == null) return false;
        // Try panes in reverse priority (foreground first)
        for (int i = panes.size() - 1; i >= 0; i--) {
            PositionedPane pp = panes.get(i);
            if (!pp.pane().isVisible()) continue;
            // Translate component-level slot to pane-local coordinates
            int localX = slot.x() - pp.offset().x();
            int localY = slot.y() - pp.offset().y();
            if (localX < 0 || localX >= pp.pane().getLength()
                    || localY < 0 || localY >= pp.pane().getHeight()) {
                continue;
            }
            Slot paneSlot = Slot.of(localX, localY);
            if (pp.pane().click(gui, this, event, paneSlot)) {
                return true;
            }
        }
        // Fall back to items static pane
        return itemsStaticPane.click(gui, this, event, slot);
    }

    // -- inventory placement --

    /**
     * Writes rendered items into the top rows of an inventory starting at the given offset.
     */
    public void placeItems(@NotNull Inventory inventory, int offsetX, int offsetY) {
        GuiItemContainer container = display();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < length; x++) {
                GuiItem item = container.getItem(x, y);
                int invX = offsetX + x;
                int invY = offsetY + y;
                int invSlot = invY * 9 + invX;
                if (invSlot >= 0 && invSlot < inventory.getSize()) {
                    if (item != null) {
                        item.applyUUID();
                        inventory.setItem(invSlot, item.getItem());
                    }
                }
            }
        }
    }

    /**
     * Writes rendered items into a player inventory (bottom part of view).
     * Player inventory slots are laid out in reversed row order.
     */
    public void placeItems(@NotNull PlayerInventory inventory, int offsetX, int offsetY) {
        GuiItemContainer container = display();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < length; x++) {
                GuiItem item = container.getItem(x, y);
                int invX = offsetX + x;
                int invY = offsetY + y;
                // Player inventory: 9 hotbar slots, then 27 storage slots (rows 1-3)
                // The inventory view shows storage rows first, then hotbar
                int invSlot = invY * 9 + invX;
                if (invSlot >= 0 && invSlot < 36) {
                    if (item != null) {
                        item.applyUUID();
                        inventory.setItem(invSlot, item.getItem());
                    }
                }
            }
        }
    }

    // -- helpers --

    /**
     * Converts a raw slot index to a component-local Slot, or null if out of bounds.
     */
    private Slot slotFromRaw(int rawSlot) {
        int x = rawSlot % 9;
        int y = rawSlot / 9;
        if (x >= 0 && x < length && y >= 0 && y < height) {
            return Slot.of(x, y);
        }
        return null;
    }

    /**
     * Merges a source container into a target at the given offset.
     * When {@code overwrite} is true, source items replace existing items.
     */
    private static void mergeContainer(@NotNull GuiItemContainer target,
                                        @NotNull GuiItemContainer source,
                                        int offsetX, int offsetY, boolean overwrite) {
        int srcLen = source.getLength();
        int srcHgt = source.getHeight();
        int tgtLen = target.getLength();
        int tgtHgt = target.getHeight();
        for (int y = 0; y < srcHgt; y++) {
            for (int x = 0; x < srcLen; x++) {
                int tx = offsetX + x;
                int ty = offsetY + y;
                if (tx >= 0 && tx < tgtLen && ty >= 0 && ty < tgtHgt) {
                    GuiItem srcItem = source.getItem(x, y);
                    if (srcItem != null && (!overwrite || target.getItem(tx, ty) == null || overwrite)) {
                        target.setItem(tx, ty, srcItem);
                    }
                }
            }
        }
    }
}
