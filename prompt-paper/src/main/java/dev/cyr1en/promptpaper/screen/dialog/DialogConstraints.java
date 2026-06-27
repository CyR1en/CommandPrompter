package dev.cyr1en.promptpaper.screen.dialog;

import dev.cyr1en.promptpaper.config.sub.DialogConfig;
import java.util.List;

/**
 * Per-tag input constraints parsed from a {@code PromptTag} filter slot,
 * merged with {@link DialogConfig} defaults. Each kind (TEXT, NUMBER,
 * CHOICE, TAB) has its own subset of fields; unused fields are zeroed.
 */
public record DialogConstraints(
        DialogInputKind kind,
        String rawFilter,
        /* text-only */    int maxLength, boolean multiline, int multilineMaxLines, int width,
        /* choice-only */  List<String> options,
        /* number-only */  float min, float max, float step, float initial,
        /* tab-only */     Integer maxButtons
) {
    /**
     * Parses a raw filter string into constraints, falling back to the
     * given {@link DialogConfig} defaults for any unspecified values.
     */
    public static DialogConstraints from(String filter, DialogConfig defaults) {
        var kind = DialogInputKind.parse(filter);
        var bracketIdx = filter == null ? -1 : filter.indexOf('[');
        var bracketContent = bracketIdx >= 0
                ? extractBracket(filter.substring(bracketIdx))
                : "";

        return switch (kind) {
            // Unknown filters fall through to a text field.
            case TEXT -> parseText(bracketContent, defaults);
            case CHOICE -> parseChoice(bracketContent, defaults);
            case NUMBER -> parseNumber(bracketContent, defaults);
            case TAB -> parseTab(bracketContent, defaults);
            // TITLE rows are handled by DialogPromptScreen's constructor.
            case TITLE -> parseTitle(bracketContent, defaults);
            case BODY -> parseBody(bracketContent, defaults);
        };
    }

    private static DialogConstraints parseTitle(String bracket, DialogConfig d) {
        return new DialogConstraints(
                DialogInputKind.TITLE, bracket,
                0, false, 0, 200,
                List.of(),
                0f, 0f, 0f, 0f,
                null);
    }

    private static DialogConstraints parseBody(String bracket, DialogConfig d) {
        return new DialogConstraints(
                DialogInputKind.BODY, bracket,
                0, false, 0, 200,
                List.of(),
                0f, 0f, 0f, 0f,
                null);
    }

    private static DialogConstraints parseText(String bracket, DialogConfig d) {
        var dText = d.text();
        var maxLength = dText.maxLength();
        var maxLines = dText.multiline() ? dText.multilineMaxLines() : 1;
        var width = dText.width();

        if (!bracket.isEmpty()) {
            if (bracket.contains("=")) {
                // Key-value parsing.
                var parts = bracket.split(",");
                for (var part : parts) {
                    var p = part.trim();
                    if (p.startsWith("max_length=")) {
                        try { maxLength = clampInt(Integer.parseInt(p.substring(11)), 1, 8192); }
                        catch (NumberFormatException ignored) {}
                    } else if (p.startsWith("max_lines=")) {
                        try { maxLines = clampInt(Integer.parseInt(p.substring(10)), 1, 8192); }
                        catch (NumberFormatException ignored) {}
                    } else if (p.startsWith("width=")) {
                        try { width = clampInt(Integer.parseInt(p.substring(6)), 1, 8192); }
                        catch (NumberFormatException ignored) {}
                    }
                }
            } else {
                // Positional parsing.
                var parts = bracket.split(",");
                if (parts.length >= 1) {
                    try { maxLength = clampInt(Integer.parseInt(parts[0].trim()), 1, 8192); }
                    catch (NumberFormatException ignored) {}
                }
                if (parts.length >= 2) {
                    try { maxLines = clampInt(Integer.parseInt(parts[1].trim()), 1, 8192); }
                    catch (NumberFormatException ignored) {}
                }
                if (parts.length >= 3) {
                    try { width = clampInt(Integer.parseInt(parts[2].trim()), 1, 8192); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return new DialogConstraints(
                DialogInputKind.TEXT, bracket,
                maxLength, maxLines > 1, maxLines, width,
                List.of(),
                0f, 0f, 0f, 0f,
                null);
    }

    private static DialogConstraints parseChoice(String bracket, DialogConfig d) {
        var options = d.choice().defaultOptions();
        if (!bracket.isBlank()) {
            options = List.of(bracket.split(","));
        }
        return new DialogConstraints(
                DialogInputKind.CHOICE, bracket,
                0, false, 0, 200,
                options,
                0f, 0f, 0f, 0f,
                null);
    }

    private static DialogConstraints parseNumber(String bracket, DialogConfig d) {
        var dNum = d.number();
        var min = dNum.min();
        var max = dNum.max();
        var step = dNum.step();
        var initial = dNum.effectiveInitial();
        // Resolve initial value based on per-tag range to avoid out-of-bounds exceptions.
        var rangeOverridden = false;
        var perTagInitialSupplied = false;
        if (!bracket.isEmpty()) {
            var parts = bracket.split(",");
            try {
                if (parts.length >= 1 && !parts[0].isBlank()) {
                    min = Float.parseFloat(parts[0].trim());
                    rangeOverridden = true;
                }
                if (parts.length >= 2 && !parts[1].isBlank()) {
                    max = Float.parseFloat(parts[1].trim());
                    rangeOverridden = true;
                }
                if (parts.length >= 3 && !parts[2].isBlank()) step = Float.parseFloat(parts[2].trim());
                if (parts.length >= 4 && !parts[3].isBlank()) {
                    initial = Float.parseFloat(parts[3].trim());
                    perTagInitialSupplied = true;
                }
            } catch (NumberFormatException ignored) {}
        }
        // Clamp min, max, and step defensively.
        if (min >= max) max = min + 1f;
        if (step <= 0f) step = 1f;
        // Re-resolve midpoint initial value when range is overridden.
        if (rangeOverridden && !perTagInitialSupplied) {
            initial = (min + max) / 2.0f;
        }
        // Clamp initial value to the valid range.
        initial = Math.max(min, Math.min(max, initial));
        return new DialogConstraints(
                DialogInputKind.NUMBER, bracket,
                0, false, 0, 200,
                List.of(),
                min, max, step, initial,
                null);
    }

    private static DialogConstraints parseTab(String bracket, DialogConfig d) {
        // Parse optional threshold N from bracket, clamping to a minimum of 1.
        Integer maxButtons = d.tab().maxButtons();
        if (!bracket.isEmpty()) {
            try {
                maxButtons = Integer.parseInt(bracket.trim());
                if (maxButtons < 1) maxButtons = 1;
            } catch (NumberFormatException ignored) {}
        }
        return new DialogConstraints(
                DialogInputKind.TAB, bracket,
                0, false, 0, 200,
                List.of(),
                0f, 0f, 0f, 0f,
                maxButtons);
    }

    private static String extractBracket(String s) {
        var end = s.indexOf(']');
        if (end < 0) return s.substring(1);
        return s.substring(1, end).trim();
    }

    private static int clampInt(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
