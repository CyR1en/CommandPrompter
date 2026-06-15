package dev.cyr1en.promptui.gui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * A 2D grid of {@link GuiItem} used internally by {@link GuiComponent} during rendering.
 */
public final class GuiItemContainer {

    private final int length;
    private final int height;
    private final GuiItem[] items;

    /**
     * Creates a container of the given dimensions.
     *
     * @param length horizontal size in slots
     * @param height vertical size in rows
     */
    public GuiItemContainer(int length, int height) {
        if (length <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive, got " + length + "x" + height);
        }
        this.length = length;
        this.height = height;
        this.items = new GuiItem[length * height];
    }

    /**
     * Places an item at the given grid slot.
     */
    public void setItem(@NotNull Slot slot, @Nullable GuiItem item) {
        if (slot.x() < 0 || slot.x() >= length || slot.y() < 0 || slot.y() >= height) {
            throw new IndexOutOfBoundsException(
                "Slot " + slot + " is out of bounds for container " + length + "x" + height);
        }
        items[index(slot)] = item;
    }

    /**
     * Places an item at the given coordinates.
     */
    public void setItem(int x, int y, @Nullable GuiItem item) {
        setItem(Slot.of(x, y), item);
    }

    /**
     * Retrieves the item at the given grid slot, or null.
     */
    @Nullable
    public GuiItem getItem(@NotNull Slot slot) {
        if (slot.x() < 0 || slot.x() >= length || slot.y() < 0 || slot.y() >= height) {
            return null;
        }
        return items[index(slot)];
    }

    /**
     * Retrieves the item at the given coordinates, or null.
     */
    @Nullable
    public GuiItem getItem(int x, int y) {
        return getItem(Slot.of(x, y));
    }

    /**
     * Clears all items from the container.
     */
    public void clear() {
        Arrays.fill(items, null);
    }

    /**
     * Fills every cell with the given item (cloned per cell).
     */
    public void fill(@Nullable GuiItem item) {
        for (int i = 0; i < items.length; i++) {
            items[i] = (item != null) ? item.copy() : null;
        }
    }

    /**
     * Returns the number of copies of the given item's item would be used to fill the container.
     *
     * @param y row index (0-based)
     * @return the items in the row, or null if the row is empty
     */
    @Nullable
    public GuiItem[] getRow(int y) {
        if (y < 0 || y >= height) return null;
        GuiItem[] row = new GuiItem[length];
        System.arraycopy(items, y * length, row, 0, length);
        return row;
    }

    public int getLength() { return length; }
    public int getHeight() { return height; }
    public int getCellCount() { return items.length; }

    private int index(@NotNull Slot slot) {
        return slot.y() * length + slot.x();
    }
}
