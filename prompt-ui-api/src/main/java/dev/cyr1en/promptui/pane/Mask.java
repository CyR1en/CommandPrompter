package dev.cyr1en.promptui.pane;

import org.jetbrains.annotations.NotNull;

/**
 * A boolean grid used by {@link OutlinePane} to control which slots display items.
 *
 * <p>Cells set to {@code true} will show items; cells set to {@code false} will remain empty.
 * Typically constructed via {@link Pattern#generateMask(int, int)}.</p>
 */
public final class Mask {

    private final boolean[][] values;
    private final int rowCount;
    private final int rowLength;

    /**
     * Creates a mask with all cells set to {@code true}.
     */
    public Mask(int rowLength, int rowCount) {
        this.rowLength = rowLength;
        this.rowCount = rowCount;
        this.values = new boolean[rowCount][rowLength];
        for (int y = 0; y < rowCount; y++) {
            for (int x = 0; x < rowLength; x++) {
                values[y][x] = true;
            }
        }
    }

    /**
     * Sets whether a cell is enabled.
     */
    public void setEnabled(int x, int y, boolean enabled) {
        if (x >= 0 && x < rowLength && y >= 0 && y < rowCount) {
            values[y][x] = enabled;
        }
    }

    /**
     * Returns whether the cell at (x, y) is enabled.
     */
    public boolean isEnabled(int x, int y) {
        if (x >= 0 && x < rowLength && y >= 0 && y < rowCount) {
            return values[y][x];
        }
        return false;
    }

    public int getRowLength() { return rowLength; }
    public int getRowCount() { return rowCount; }

    /**
     * Creates a deep copy of this mask.
     */
    @NotNull
    public Mask copy() {
        Mask copy = new Mask(rowLength, rowCount);
        for (int y = 0; y < rowCount; y++) {
            System.arraycopy(values[y], 0, copy.values[y], 0, rowLength);
        }
        return copy;
    }
}
