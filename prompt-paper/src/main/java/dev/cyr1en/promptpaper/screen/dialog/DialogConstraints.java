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
        /* text-only */    int maxLength, boolean multiline, int multilineMaxLines,
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
            // TEXT and unknown filters both land here. Unrecognized filter
            // keywords fall through silently to a text field — the user did
            // not opt into a specific kind, so defaulting to text is the
            // principle of least surprise.
            case TEXT -> parseText(bracketContent, defaults);
            case CHOICE -> parseChoice(bracketContent, defaults);
            case NUMBER -> parseNumber(bracketContent, defaults);
            case TAB -> parseTab(bracketContent, defaults);
            // TITLE rows are consumed by DialogPromptScreen's constructor and
            // never reach buildInputs(). This case exists only to satisfy the
            // exhaustive switch; treat it as title constraints.
            case TITLE -> parseTitle(bracketContent, defaults);
        };
    }

    private static DialogConstraints parseTitle(String bracket, DialogConfig d) {
        return new DialogConstraints(
                DialogInputKind.TITLE, bracket,
                0, false, 0,
                List.of(),
                0f, 0f, 0f, 0f,
                null);
    }

    private static DialogConstraints parseText(String bracket, DialogConfig d) {
        var dText = d.text();
        var maxLength = dText.maxLength();
        // bracket is "min,max" — min is unused for text; we treat both as length bounds
        // for forward-compat (e.g. text[0,64] means "length 0..64").
        if (!bracket.isEmpty()) {
            var parts = bracket.split(",");
            if (parts.length >= 2) {
                try { maxLength = clampInt(Integer.parseInt(parts[1].trim()), 1, 8192); }
                catch (NumberFormatException ignored) {}
            }
        }
        return new DialogConstraints(
                DialogInputKind.TEXT, bracket,
                maxLength, dText.multiline(), dText.multilineMaxLines(),
                List.of(),
                0f, 0f, 0f, 0f,
                null);
    }

    private static DialogConstraints parseChoice(String bracket, DialogConfig d) {
        // Bracket content is a comma-separated list of option labels. The
        // dropdown order in the dialog mirrors the list order. Empty /
        // missing bracket falls back to the configured default options;
        // if both are empty the prompt still renders a choice input, just
        // with no selectable options (the client may present it as a
        // disabled / empty dropdown).
        var options = d.choice().defaultOptions();
        if (!bracket.isBlank()) {
            options = List.of(bracket.split(","));
        }
        return new DialogConstraints(
                DialogInputKind.CHOICE, bracket,
                0, false, 0,
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
        // Track whether the per-tag supplied its own min/max and whether it
        // pinned a per-tag initial. The config default `effectiveInitial()`
        // is resolved against the CONFIG range; reusing it after a per-tag
        // range override leaves the initial out of the per-tag bounds and
        // Paper's NumberRangeDialogInput rejects it with an
        // IllegalArgumentException at build time. Re-resolve against the
        // per-tag range when the per-tag overrode range but not initial.
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
            } catch (NumberFormatException ignored) { /* fall back to defaults */ }
        }
        // Paper requires min < max and step > 0; clamp defensively.
        if (min >= max) max = min + 1f;
        if (step <= 0f) step = 1f;
        // Per-tag supplied a range but did not pin an initial — re-resolve
        // against the per-tag range so the slider lands at the midpoint
        // rather than carrying a config-range initial that no longer fits.
        if (rangeOverridden && !perTagInitialSupplied) {
            initial = (min + max) / 2.0f;
        }
        // Safety net: Paper's NumberRangeDialogInput rejects initial < min
        // or initial > max. The re-resolution above covers the common case;
        // the clamp covers future code paths, config drift, and the case
        // where the per-tag pinned an initial outside its own range.
        initial = Math.max(min, Math.min(max, initial));
        return new DialogConstraints(
                DialogInputKind.NUMBER, bracket,
                0, false, 0,
                List.of(),
                min, max, step, initial,
                null);
    }

    private static DialogConstraints parseTab(String bracket, DialogConfig d) {
        // The bracket content is an optional single integer N — the per-tag
        // threshold. Absent / malformed bracket falls back to the config
        // default (`DialogUI.Defaults.Tab.MaxButtons`). N must be >= 1 to
        // make sense; we silently clamp any zero/negative value up to 1.
        Integer maxButtons = d.tab().maxButtons();
        if (!bracket.isEmpty()) {
            try {
                maxButtons = Integer.parseInt(bracket.trim());
                if (maxButtons < 1) maxButtons = 1;
            } catch (NumberFormatException ignored) {}
        }
        return new DialogConstraints(
                DialogInputKind.TAB, bracket,
                0, false, 0,
                List.of(),
                0f, 0f, 0f, 0f,
                maxButtons);
    }

    private static String extractBracket(String s) {
        var end = s.indexOf(']');
        if (end < 0) return s.substring(1); // malformed — return contents anyway
        return s.substring(1, end).trim();
    }

    private static int clampInt(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
