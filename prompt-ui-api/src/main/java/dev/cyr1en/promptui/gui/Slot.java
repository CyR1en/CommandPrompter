package dev.cyr1en.promptui.gui;

/**
 * Immutable grid cell position (x, y) used as a map key in pane and item placement.
 */
public record Slot(int x, int y) {

    public Slot {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Slot coordinates must be non-negative, got (" + x + ", " + y + ")");
        }
    }

    /**
     * Creates a Slot at the given coordinates.
     *
     * @throws IllegalArgumentException if x or y is negative
     */
    public static Slot of(int x, int y) {
        return new Slot(x, y);
    }
}
