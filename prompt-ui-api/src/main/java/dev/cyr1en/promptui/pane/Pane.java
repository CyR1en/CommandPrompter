package dev.cyr1en.promptui.pane;

import dev.cyr1en.promptui.gui.Gui;
import dev.cyr1en.promptui.gui.GuiComponent;
import dev.cyr1en.promptui.gui.GuiItem;
import dev.cyr1en.promptui.gui.GuiItemContainer;
import dev.cyr1en.promptui.gui.Slot;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Abstract base for all pane types in the GUI framework.
 *
 * <p>A pane occupies a rectangular region of a {@link GuiComponent} and manages its own
 * set of {@link GuiItem} placements. Panes are rendered by priority (lowest first).</p>
 */
public abstract class Pane {

    private final int length;
    private final int height;
    private boolean visible = true;
    private int priority;
    private final UUID uuid;
    private Consumer<InventoryClickEvent> onClick;

    protected Pane(int length, int height) {
        if (length <= 0 || height <= 0) {
            throw new IllegalArgumentException("Pane dimensions must be positive, got " + length + "x" + height);
        }
        this.length = length;
        this.height = height;
        this.uuid = UUID.randomUUID();
    }

    /**
     * Renders this pane's items into a {@link GuiItemContainer} sized {@code length x height}.
     * Callers should check {@link #isVisible()} before calling.
     */
    @NotNull
    public abstract GuiItemContainer display();

    /**
     * Handles a click within this pane's region.
     *
     * @param gui       the parent GUI
     * @param component the parent GUI component
     * @param event     the click event
     * @param slot      the relative slot within this pane
     * @return true if the click was consumed
     */
    public abstract boolean click(@NotNull Gui gui, @NotNull GuiComponent component,
                                   @NotNull InventoryClickEvent event, @NotNull Slot slot);

    /**
     * Returns a deep copy of this pane.
     */
    @NotNull
    public abstract Pane copy();

    // -- visibility --

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    // -- priority --

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    // -- click handler --

    public void setOnClick(@Nullable Consumer<InventoryClickEvent> onClick) {
        this.onClick = onClick;
    }

    @Nullable
    public Consumer<InventoryClickEvent> getOnClick() {
        return onClick;
    }

    // -- dimensions --

    public int getLength() { return length; }
    public int getHeight() { return height; }

    // -- uuid --

    @NotNull
    public UUID getUUID() { return uuid; }

    /**
     * Checks whether the given GuiItem matches the clicked ItemStack by comparing UUIDs.
     */
    public boolean matchesItem(@NotNull GuiItem guiItem, @NotNull ItemStack itemStack) {
        UUID guiUUID = guiItem.getUUID();
        UUID itemUUID = GuiItem.readUUID(itemStack);
        return guiUUID.equals(itemUUID);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Pane other)) return false;
        return uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
