package dev.cyr1en.promptui.pane;

import dev.cyr1en.promptui.gui.Gui;
import dev.cyr1en.promptui.gui.GuiComponent;
import dev.cyr1en.promptui.gui.GuiItem;
import dev.cyr1en.promptui.gui.GuiItemContainer;
import dev.cyr1en.promptui.gui.Slot;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A pane that lays out items in reading order (horizontally or vertically)
 * respecting a {@link Mask} that controls which cells are enabled.
 *
 * <p>Items are placed sequentially into enabled mask cells. If there are more items
 * than enabled cells, extra items are not displayed. Pagination can be handled by
 * a parent {@link PaginatedPane}.</p>
 */
public final class OutlinePane extends Pane {

    /**
     * Direction in which items are placed into enabled mask cells.
     */
    public enum Orientation {
        HORIZONTAL,
        VERTICAL
    }

    private final List<GuiItem> items = new ArrayList<>();
    private Orientation orientation = Orientation.HORIZONTAL;
    private Mask mask;

    /**
     * Creates an empty outline pane of the given dimensions.
     */
    public OutlinePane(int length, int height) {
        super(length, height);
    }

    /**
     * Creates an outline pane pre-populated with the given items.
     */
    public OutlinePane(int length, int height, @NotNull List<GuiItem> items) {
        super(length, height);
        this.items.addAll(items);
    }

    // -- items --

    /** Appends an item to the end of the item list. */
    public void addItem(@NotNull GuiItem item) {
        items.add(item);
    }

    /** Removes the given item from this pane. */
    public void removeItem(@NotNull GuiItem item) {
        items.remove(item);
    }

    /** Removes all items from this pane. */
    public void clear() {
        items.clear();
    }

    @NotNull
    public List<GuiItem> getItems() {
        return new ArrayList<>(items);
    }

    // -- orientation --

    /**
     * Sets the layout direction for item placement.
     */
    public void setOrientation(@NotNull Orientation orientation) {
        this.orientation = orientation;
    }

    @NotNull
    public Orientation getOrientation() {
        return orientation;
    }

    // -- mask --

    /**
     * Sets the mask that controls which cells receive items.
     */
    public void setMask(@NotNull Mask mask) {
        this.mask = mask;
    }

    // -- display --

    @NotNull
    @Override
    public GuiItemContainer display() {
        GuiItemContainer container = new GuiItemContainer(getLength(), getHeight());
        Iterator<GuiItem> iterator = items.iterator();
        for (int y = 0; y < getHeight(); y++) {
            for (int x = 0; x < getLength(); x++) {
                if (iterator.hasNext() && isMaskEnabled(x, y)) {
                    container.setItem(x, y, iterator.next());
                }
            }
        }
        return container;
    }

    @Override
    public boolean click(@NotNull Gui gui, @NotNull GuiComponent component,
                          @NotNull InventoryClickEvent event, @NotNull Slot slot) {
        GuiItem item = getItemAt(slot);
        if (item != null) {
            if (!matchesItem(item, event.getCurrentItem())) {
                return false;
            }
            item.callAction(event);
            if (getOnClick() != null) {
                getOnClick().accept(event);
            }
            return true;
        }
        return false;
    }

    /**
     * Returns the GuiItem at the given grid position based on current layout.
     */
    private GuiItem getItemAt(@NotNull Slot slot) {
        Iterator<GuiItem> iterator = items.iterator();
        for (int y = 0; y < getHeight(); y++) {
            for (int x = 0; x < getLength(); x++) {
                if (isMaskEnabled(x, y)) {
                    if (iterator.hasNext()) {
                        GuiItem item = iterator.next();
                        if (x == slot.x() && y == slot.y()) {
                            return item;
                        }
                    } else {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private boolean isMaskEnabled(int x, int y) {
        return mask == null || mask.isEnabled(x, y);
    }

    // -- copy --

    @NotNull
    @Override
    public OutlinePane copy() {
        OutlinePane copy = new OutlinePane(getLength(), getHeight());
        copy.setVisible(isVisible());
        copy.setPriority(getPriority());
        copy.setOnClick(getOnClick());
        copy.orientation = this.orientation;
        copy.mask = (this.mask != null) ? this.mask.copy() : null;
        for (GuiItem item : items) {
            copy.items.add(item.copy());
        }
        return copy;
    }
}
