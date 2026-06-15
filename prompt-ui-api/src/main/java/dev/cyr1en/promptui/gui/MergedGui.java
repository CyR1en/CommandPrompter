package dev.cyr1en.promptui.gui;

import dev.cyr1en.promptui.pane.Pane;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A GUI whose top and bottom inventories are rendered as a single merged
 * {@link GuiComponent}, typically used by chest-style GUIs.
 */
public interface MergedGui {

    /**
     * Adds a pane at the given position within the merged component.
     */
    void addPane(@NotNull Slot offset, @NotNull Pane pane);

    /**
     * Returns the panes registered on this merged GUI.
     */
    @NotNull
    List<Pane> getPanes();

    /**
     * Returns the flattened list of all GuiItems across all panes.
     */
    @NotNull
    default List<GuiItem> getItems() {
        return getGuiComponent().getItems();
    }

    /**
     * Returns the underlying {@link GuiComponent} for this merged GUI.
     */
    @NotNull
    GuiComponent getGuiComponent();
}
