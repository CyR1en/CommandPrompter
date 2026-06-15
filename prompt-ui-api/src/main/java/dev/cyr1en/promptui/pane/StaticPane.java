package dev.cyr1en.promptui.pane;

import dev.cyr1en.promptui.gui.Gui;
import dev.cyr1en.promptui.gui.GuiComponent;
import dev.cyr1en.promptui.gui.GuiItem;
import dev.cyr1en.promptui.gui.GuiItemContainer;
import dev.cyr1en.promptui.gui.Slot;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * A pane that holds items at fixed grid positions.
 *
 * <p>Items are stored in a map keyed by {@link Slot}. Use {@link #addItem(GuiItem, int, int)}
 * or {@link #addItem(GuiItem, Slot)} to place items at specific coordinates.</p>
 */
public final class StaticPane extends Pane {

    private final Map<Slot, GuiItem> items = new HashMap<>();

    /**
     * Creates a static pane of the given dimensions.
     */
    public StaticPane(int length, int height) {
        super(length, height);
    }

    /**
     * Places an item at the given coordinates.
     */
    public void addItem(@NotNull GuiItem item, int x, int y) {
        if (x < 0 || x >= getLength() || y < 0 || y >= getHeight()) {
            throw new IllegalArgumentException(
                "Coordinates (" + x + ", " + y + ") out of bounds for " + getLength() + "x" + getHeight() + " pane");
        }
        items.put(Slot.of(x, y), item);
    }

    /**
     * Places an item at the given slot.
     */
    public void addItem(@NotNull GuiItem item, @NotNull Slot slot) {
        addItem(item, slot.x(), slot.y());
    }

    @NotNull
    @Override
    public GuiItemContainer display() {
        GuiItemContainer container = new GuiItemContainer(getLength(), getHeight());
        for (Map.Entry<Slot, GuiItem> entry : items.entrySet()) {
            container.setItem(entry.getKey(), entry.getValue());
        }
        return container;
    }

    @Override
    public boolean click(@NotNull Gui gui, @NotNull GuiComponent component,
                          @NotNull InventoryClickEvent event, @NotNull Slot slot) {
        GuiItem item = items.get(slot);
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

    @NotNull
    @Override
    public StaticPane copy() {
        StaticPane copy = new StaticPane(getLength(), getHeight());
        copy.setVisible(isVisible());
        copy.setPriority(getPriority());
        copy.setOnClick(getOnClick());
        for (Map.Entry<Slot, GuiItem> entry : items.entrySet()) {
            copy.items.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }
}
