package dev.cyr1en.promptui.pane;

import org.jetbrains.annotations.NotNull;

/**
 * Generates a {@link Mask} from a glyph pattern.
 *
 * <p>A pattern is a series of lines where '1' means enabled and '0' means disabled.
 * The pattern can repeat naturally or be wrapped to fit a target container size.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * Pattern pattern = new Pattern(
 *     "111111111",
 *     "100000001",
 *     "100000001",
 *     "111111111"
 * );
 * Mask mask = pattern.generateMask(9, 6);
 * }</pre>
 */
public final class Pattern {

    private final String[] lines;

    /**
     * Creates a pattern from one or more glyph lines. Each line should contain
     * {@code '1'} (enabled) and {@code '0'} (disabled) characters.
     */
    public Pattern(@NotNull String... lines) {
        this.lines = lines.clone();
    }

    /**
     * Generates a mask of the given dimensions by repeating this pattern.
     */
    @NotNull
    public Mask generateMask(int targetLength, int targetHeight) {
        Mask mask = new Mask(targetLength, targetHeight);
        int patternWidth = getPatternWidth();
        int patternHeight = lines.length;

        for (int y = 0; y < targetHeight; y++) {
            String line = lines[y % patternHeight];
            for (int x = 0; x < targetLength; x++) {
                char c = line.charAt(x % patternWidth);
                mask.setEnabled(x, y, c == '1');
            }
        }
        return mask;
    }

    /**
     * Alias for {@link #generateMask(int, int)}.
     */
    @NotNull
    public Mask generateMaskWrapping(int targetLength, int targetHeight) {
        return generateMask(targetLength, targetHeight);
    }

    /**
     * Generates a mask by repeating the pattern naturally.
     */
    @NotNull
    public Mask generateMaskRepeating(int targetLength, int targetHeight) {
        return generateMask(targetLength, targetHeight);
    }

    private int getPatternWidth() {
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, line.length());
        }
        return width;
    }
}
