package dev.cyr1en.promptui.pane;

import dev.cyr1en.promptui.gui.Slot;

import java.util.Comparator;

/**
 * Associates a {@link Pane} with a display priority and grid offset, used by
 * {@link GuiComponent} for sorted rendering and click dispatch.
 *
 * <p>Lower priority values render first (background), higher values render
 * later (foreground).  The {@code offset} determines where within the parent
 * component the pane begins.</p>
 */
public record PositionedPane(Pane pane, int priority, Slot offset) implements Comparable<PositionedPane> {

    public PositionedPane(Pane pane, int priority) {
        this(pane, priority, Slot.of(0, 0));
    }

    @SuppressWarnings("null")
    private static final Comparator<PositionedPane> COMPARATOR =
        Comparator.comparingInt(PositionedPane::priority);

    /** Orders panes by ascending priority (lower priority renders first). */
    @Override
    public int compareTo(PositionedPane other) {
        return COMPARATOR.compare(this, other);
    }
}
