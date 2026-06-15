package dev.cyr1en.promptui.pane;

import dev.cyr1en.promptui.gui.Gui;
import dev.cyr1en.promptui.gui.GuiComponent;
import dev.cyr1en.promptui.gui.GuiItemContainer;
import dev.cyr1en.promptui.gui.Slot;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A pane that manages multiple pages of child panes.
 *
 * <p>Only the current page is displayed. Use {@link #addPane(Pane)} to add pages,
 * {@link #setPage(int)} to navigate, and {@link #next()} / {@link #previous()} to cycle.</p>
 */
public final class PaginatedPane extends Pane {

    private final List<Pane> pages = new ArrayList<>();
    private int pageIndex;

    /**
     * Creates a paginated pane of the given dimensions.
     */
    public PaginatedPane(int length, int height) {
        super(length, height);
    }

    // -- pages --

    /**
     * Adds a pane as a new page.
     */
    public void addPane(@NotNull Pane pane) {
        pages.add(pane);
    }

    /**
     * Returns the current page pane, or null if no pages exist.
     */
    private Pane getCurrentPane() {
        if (pages.isEmpty()) return null;
        return pages.get(pageIndex);
    }

    /**
     * Navigates to a specific page by index. Clamped to valid range.
     */
    public void setPage(int index) {
        if (!pages.isEmpty()) {
            this.pageIndex = Math.max(0, Math.min(index, pages.size() - 1));
        }
    }

    /**
     * Returns the current page index.
     */
    public int getPage() {
        return pageIndex;
    }

    /**
     * Returns the total number of pages.
     */
    public int getPageCount() {
        return pages.size();
    }

    /**
     * Advances to the next page, wrapping around.
     */
    public void next() {
        if (!pages.isEmpty()) {
            pageIndex = (pageIndex + 1) % pages.size();
        }
    }

    /**
     * Goes to the previous page, wrapping around.
     */
    public void previous() {
        if (!pages.isEmpty()) {
            pageIndex = (pageIndex - 1 + pages.size()) % pages.size();
        }
    }

    // -- display --

    @NotNull
    @Override
    public GuiItemContainer display() {
        Pane current = getCurrentPane();
        if (current == null || !current.isVisible()) {
            return new GuiItemContainer(getLength(), getHeight());
        }
        return current.display();
    }

    @Override
    public boolean click(@NotNull Gui gui, @NotNull GuiComponent component,
                          @NotNull InventoryClickEvent event, @NotNull Slot slot) {
        Pane current = getCurrentPane();
        if (current == null) return false;
        return current.click(gui, component, event, slot);
    }

    // -- copy --

    @NotNull
    @Override
    public PaginatedPane copy() {
        PaginatedPane copy = new PaginatedPane(getLength(), getHeight());
        copy.setVisible(isVisible());
        copy.setPriority(getPriority());
        copy.setOnClick(getOnClick());
        for (Pane page : pages) {
            copy.pages.add(page.copy());
        }
        copy.pageIndex = this.pageIndex;
        return copy;
    }
}
